package com.test.qa.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.test.qa.domain.ChatLog;
import com.test.qa.domain.PromptTemplate;
import com.test.qa.mapper.ChatLogMapper;
import com.test.qa.mapper.PromptTemplateMapper;
import jakarta.servlet.AsyncContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;
import reactor.core.scheduler.Schedulers;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RAG 问答编排服务
 *
 * 核心链路：检索 → 上下文拼接 → Prompt渲染 → LLM调用 → 日志记录
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QAService {

    private final RetrievalService retrievalService;
    private final PromptRenderService promptRenderService;
    private final PromptTemplateMapper promptTemplateMapper;
    private final ChatLogMapper chatLogMapper;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final Executor taskExecutor;

    /**
     * RAG 问答
     *
     * @param question         用户问题
     * @param promptTemplateId 使用的提示词模板ID
     * @param documentId       限定知识库文档（null=全局搜索）
     * @param sessionId        会话ID（null=新建会话）
     * @return 问答结果
     */
    public QAResult ask(String question, Long promptTemplateId, Long documentId, String sessionId) {
        long t0 = System.currentTimeMillis();

        // 1. 获取提示词模板
        PromptTemplate template = promptTemplateMapper.selectById(promptTemplateId);
        if (template == null) {
            throw new IllegalArgumentException("提示词模板不存在: id=" + promptTemplateId);
        }
        long t1 = System.currentTimeMillis();

        // 2. 向量检索
        int topK = template.getMaxTokens() != null && template.getMaxTokens() < 2048 ? 3 : 5;
        List<String> retrievedChunks = retrievalService.retrieve(question, documentId, topK);
        String context = String.join("\n\n---\n\n", retrievedChunks);
        long t2 = System.currentTimeMillis();

        // 3. Prompt 渲染：将 {{question}} 和 {{context}} 替换到模板中
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("question", question);
        variables.put("context", context);
        String fullPrompt = promptRenderService.render(template.getUserTemplate(), variables);
        long t3 = System.currentTimeMillis();

        // 4. 构建系统 + 用户消息，调用大模型
        String systemPrompt = template.getSystemPrompt();
        ChatResponse chatResponse = chatClient.prompt()
                .system(systemPrompt)
                .user(fullPrompt)
                .call()
                .chatResponse();
        String answer = chatResponse != null && chatResponse.getResult() != null
                ? chatResponse.getResult().getOutput().getText()
                : "模型未返回有效回答";
        long t4 = System.currentTimeMillis();

        long latency = t4 - t0;

        // 5. 对话日志持久化
        String sid = (sessionId != null && !sessionId.isEmpty()) ? sessionId : UUID.randomUUID().toString();
        ChatLog logEntry = new ChatLog();
        logEntry.setSessionId(sid);
        logEntry.setPromptTemplateId(promptTemplateId);
        logEntry.setRagDocumentId(documentId);
        logEntry.setUserQuestion(question);
        logEntry.setRetrievedChunks(retrievalService.chunksToJson(retrievedChunks));
        logEntry.setFullPrompt(fullPrompt);
        logEntry.setModelResponse(answer);
        logEntry.setLatencyMs(latency);
        logEntry.setTotalTokens(chatResponse != null && chatResponse.getMetadata() != null
                ? estimateTokens(chatResponse) : 0);
        chatLogMapper.insert(logEntry);

        log.info("RAG问答完成: sessionId={}, question='{}', total={}ms | " +
                        "取模板={}ms, 向量检索={}ms, 渲染={}ms, LLM调用={}ms, chunks={}",
                sid, question.substring(0, Math.min(50, question.length())), latency,
                t1 - t0, t2 - t1, t3 - t2, t4 - t3, retrievedChunks.size());

        // 6. 构建返回结果
        QAResult result = new QAResult();
        result.setAnswer(answer);
        result.setSessionId(sid);
        result.setSources(retrievedChunks);
        result.setLatencyMs(latency);
        return result;
    }

    /**
     * RAG 流式问答（SSE）
     *
     * Controller 传入 AsyncContext 后立即返回，检索+LLM 流式在后台线程执行，
     * 彻底避免 StreamResponseBody + blockLast() 的超时中断问题。
     */
    public void askStream(String question, Long promptTemplateId, Long documentId, String sessionId,
                          OutputStream out, AsyncContext asyncContext) {
        taskExecutor.execute(() -> {
            // 确保 asyncContext.complete() 最多调用一次，避免 Reactor
            // doOnComplete/doOnError/blockLast 异常导致的重复完成
            AtomicBoolean completed = new AtomicBoolean(false);
            Runnable safeComplete = () -> {
                if (completed.compareAndSet(false, true)) {
                    asyncContext.complete();
                }
            };

            try {
                long t0 = System.currentTimeMillis();

                // 1. 获取提示词模板
                PromptTemplate template = promptTemplateMapper.selectById(promptTemplateId);
                if (template == null) {
                    writeSse(out, "error", "提示词模板不存在: id=" + promptTemplateId);
                    safeComplete.run();
                    return;
                }
                long t1 = System.currentTimeMillis();

                // 2. 向量检索
                int topK = template.getMaxTokens() != null && template.getMaxTokens() < 2048 ? 3 : 5;
                List<String> retrievedChunks = retrievalService.retrieve(question, documentId, topK);
                String context = String.join("\n\n---\n\n", retrievedChunks);
                long t2 = System.currentTimeMillis();

                // 3. Prompt 渲染
                Map<String, String> variables = new LinkedHashMap<>();
                variables.put("question", question);
                variables.put("context", context);
                String fullPrompt = promptRenderService.render(template.getUserTemplate(), variables);
                String systemPrompt = template.getSystemPrompt();
                long t3 = System.currentTimeMillis();

                String sid = (sessionId != null && !sessionId.isEmpty()) ? sessionId : UUID.randomUUID().toString();

                // 推送 meta 事件（包含来源片段供前端展示）
                try {
                    String metaJson = objectMapper.writeValueAsString(
                            Map.of("sessionId", sid, "retrievalMs", t3 - t0, "sources", retrievedChunks));
                    writeSse(out, "meta", metaJson);
                } catch (Exception ignored) {}

                // 4. 流式调用 LLM，逐 token 写到 response
                StringBuilder answerBuf = new StringBuilder();
                long t4 = System.currentTimeMillis();
                chatClient.prompt()
                        .system(systemPrompt)
                        .user(fullPrompt)
                        .stream()
                        .chatResponse()
                        .publishOn(Schedulers.boundedElastic())
                        .doOnNext(cr -> {
                            String token = cr.getResult() != null && cr.getResult().getOutput() != null
                                    ? cr.getResult().getOutput().getText() : "";
                            if (!token.isEmpty()) {
                                answerBuf.append(token);
                                writeSse(out, "token", token);
                            }
                        })
                        .doOnComplete(() -> {
                            long latency = System.currentTimeMillis() - t0;
                            writeSse(out, "done", "{\"latencyMs\":" + latency + "}");
                            persistLog(sid, question, promptTemplateId, documentId,
                                    retrievedChunks, fullPrompt, answerBuf.toString(), latency);
                            log.info("RAG流式问答完成: sessionId={}, question='{}', total={}ms | "
                                            + "取模板={}ms, 向量检索={}ms, 渲染={}ms, LLM流式={}ms, chunks={}",
                                    sid, question.substring(0, Math.min(50, question.length())), latency,
                                    t1 - t0, t2 - t1, t3 - t2,
                                    System.currentTimeMillis() - t4, retrievedChunks.size());
                            safeComplete.run();
                        })
                        .doOnError(e -> {
                            log.error("LLM流式调用失败", e);
                            writeSse(out, "error", e.getMessage() != null ? e.getMessage() : "unknown");
                            safeComplete.run();
                        })
                        .blockLast();
            } catch (Exception e) {
                log.error("RAG流式问答异常", e);
                writeSse(out, "error", e.getMessage() != null ? e.getMessage() : "unknown");
                safeComplete.run();
            }
        });
    }

    /** 写入一条 SSE 事件 */
    private void writeSse(java.io.OutputStream out, String event, String data) {
        try {
            out.write(("event: " + event + "\ndata: " + data + "\n\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.flush();
        } catch (Exception ignored) {}
    }

    private void persistLog(String sid, String question, Long promptTemplateId, Long documentId,
                            List<String> retrievedChunks, String fullPrompt, String answer, long latency) {
        try {
            ChatLog logEntry = new ChatLog();
            logEntry.setSessionId(sid);
            logEntry.setPromptTemplateId(promptTemplateId);
            logEntry.setRagDocumentId(documentId);
            logEntry.setUserQuestion(question);
            logEntry.setRetrievedChunks(retrievalService.chunksToJson(retrievedChunks));
            logEntry.setFullPrompt(fullPrompt);
            logEntry.setModelResponse(answer);
            logEntry.setLatencyMs(latency);
            logEntry.setTotalTokens(answer.length() / 3);
            chatLogMapper.insert(logEntry);
        } catch (Exception e) {
            log.error("对话日志持久化失败", e);
        }
    }

    private int estimateTokens(ChatResponse response) {
        try {
            return objectMapper.writeValueAsString(response).length() / 3;
        } catch (JsonProcessingException e) {
            return 0;
        }
    }

    /**
     * 问答结果 DTO
     */
    @lombok.Data
    public static class QAResult {
        private String answer;
        private String sessionId;
        private List<String> sources;
        private long latencyMs;
    }
}
