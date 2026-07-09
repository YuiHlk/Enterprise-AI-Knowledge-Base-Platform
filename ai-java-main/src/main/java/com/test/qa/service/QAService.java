package com.test.qa.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.test.qa.domain.ChatLog;
import com.test.qa.domain.PromptTemplate;
import com.test.qa.dto.QAResult;
import com.test.qa.mapper.ChatLogMapper;
import com.test.qa.mapper.PromptTemplateMapper;
import jakarta.servlet.AsyncContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

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
public class QAService {

    private static final int TOP_K_SMALL = 3;
    private static final int TOP_K_DEFAULT = 5;
    private static final int MAX_TOKENS_THRESHOLD = 2048;
    private static final int LOG_QUESTION_MAX_LEN = 50;

    private final RetrievalService retrievalService;
    private final PromptRenderService promptRenderService;
    private final PromptTemplateMapper promptTemplateMapper;
    private final ChatLogMapper chatLogMapper;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final Executor taskExecutor;

    public QAService(RetrievalService retrievalService, PromptRenderService promptRenderService,
                     PromptTemplateMapper promptTemplateMapper, ChatLogMapper chatLogMapper,
                     ChatClient chatClient, ObjectMapper objectMapper,
                     @Qualifier("taskExecutor") Executor taskExecutor) {
        this.retrievalService = retrievalService;
        this.promptRenderService = promptRenderService;
        this.promptTemplateMapper = promptTemplateMapper;
        this.chatLogMapper = chatLogMapper;
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
        this.taskExecutor = taskExecutor;
    }

    // ================================================================
    // 公共 API
    // ================================================================

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
        long t0 = System.currentTimeMillis(); //初加载

        PromptTemplate template = loadTemplate(promptTemplateId);
        long t1 = System.currentTimeMillis(); //加载提示词模板

        int topK = resolveTopK(template);
        List<String> retrievedChunks = retrievalService.retrieve(question, documentId, topK);
        String context = String.join("\n\n---\n\n", retrievedChunks);
        long t2 = System.currentTimeMillis(); //向量库检索分片

        String fullPrompt = promptRenderService.render(template.getUserTemplate(), buildVariables(question, context));
        long t3 = System.currentTimeMillis(); //模板变量渲染

        ChatResponse chatResponse = chatClient.prompt()
                .system(template.getSystemPrompt())
                .user(fullPrompt)
                .call()
                .chatResponse();
        String answer = extractAnswer(chatResponse);
        long t4 = System.currentTimeMillis(); //大模型远程调用

        long latency = t4 - t0;
        String sid = resolveSessionId(sessionId);
        int tokens = estimateTokens(chatResponse);

        ChatLog logEntry = buildChatLog(sid, question, promptTemplateId, documentId,
                retrievedChunks, fullPrompt, answer, latency, tokens);
        chatLogMapper.insert(logEntry);

        log.info("RAG问答完成: sessionId={}, question='{}', total={}ms | "
                        + "取模板={}ms, 向量检索={}ms, 渲染={}ms, LLM调用={}ms, chunks={}",
                sid, truncateQuestion(question), latency,
                t1 - t0, t2 - t1, t3 - t2, t4 - t3, retrievedChunks.size());

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
            AtomicBoolean completed = new AtomicBoolean(false);
            Runnable safeComplete = () -> {
                if (completed.compareAndSet(false, true)) {
                    asyncContext.complete();
                }
            };

            try {
                long t0 = System.currentTimeMillis();

                PromptTemplate template = loadTemplate(promptTemplateId);
                long t1 = System.currentTimeMillis();

                int topK = resolveTopK(template);
                List<String> retrievedChunks = retrievalService.retrieve(question, documentId, topK);
                String context = String.join("\n\n---\n\n", retrievedChunks);
                long t2 = System.currentTimeMillis();

                String fullPrompt = promptRenderService.render(template.getUserTemplate(),
                        buildVariables(question, context));
                String systemPrompt = template.getSystemPrompt();
                long t3 = System.currentTimeMillis();

                String sid = resolveSessionId(sessionId);

                writeMeta(out, sid, t3 - t0, retrievedChunks);

                // 流式调用 LLM，逐 token 写到 response
                StringBuilder answerBuf = new StringBuilder();
                long t4 = System.currentTimeMillis();
                chatClient.prompt()
                        .system(systemPrompt)
                        .user(fullPrompt)
                        .stream()
                        .chatResponse()
                        .doOnNext(cr -> {
                            String token = extractToken(cr);
                            if (!token.isEmpty()) {
                                answerBuf.append(token);
                                writeSse(out, "token", token);
                            }
                        })
                        .doOnComplete(() -> {
                            long latency = System.currentTimeMillis() - t0;
                            String answer = answerBuf.toString();
                            writeSse(out, "done", "{\"latencyMs\":" + latency + "}");
                            persistLog(sid, question, promptTemplateId, documentId,
                                    retrievedChunks, fullPrompt, answer, latency);
                            log.info("RAG流式问答完成: sessionId={}, question='{}', total={}ms | "
                                            + "取模板={}ms, 向量检索={}ms, 渲染={}ms, LLM流式={}ms, chunks={}",
                                    sid, truncateQuestion(question), latency,
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

    // ================================================================
    // 管线步骤
    // ================================================================

    /** 加载提示词模板，不存在时抛异常 */
    private PromptTemplate loadTemplate(Long promptTemplateId) {
        PromptTemplate template = promptTemplateMapper.selectById(promptTemplateId);
        if (template == null) {
            throw new IllegalArgumentException("提示词模板不存在: id=" + promptTemplateId);
        }
        return template;
    }

    /** 根据模板配置决定检索 topK */
    private int resolveTopK(PromptTemplate template) {
        return template.getMaxTokens() != null && template.getMaxTokens() < MAX_TOKENS_THRESHOLD
                ? TOP_K_SMALL : TOP_K_DEFAULT;
    }

    /** 构建 Prompt 渲染变量 */
    private Map<String, String> buildVariables(String question, String context) {
        Map<String, String> vars = new LinkedHashMap<>(); //构建双向链表
        vars.put("question", question);
        vars.put("context", context);
        return vars;
    }

    /** 从 ChatResponse 提取文本回答 */
    private String extractAnswer(ChatResponse response) {
        return response != null && response.getResult() != null
                ? response.getResult().getOutput().getText()
                : "模型未返回有效回答";
    }

    /** 从流式 ChatResponse 提取单 token */
    private String extractToken(ChatResponse cr) {
        return cr.getResult() != null && cr.getResult().getOutput() != null
                ? cr.getResult().getOutput().getText()
                : "";
    }

    // ================================================================
    // 辅助方法
    // ================================================================

    private String resolveSessionId(String sessionId) {
        return (sessionId != null && !sessionId.isEmpty()) ? sessionId : UUID.randomUUID().toString();
    }

    private String truncateQuestion(String question) {
        return question.substring(0, Math.min(LOG_QUESTION_MAX_LEN, question.length()));
    }

    private ChatLog buildChatLog(String sid, String question, Long promptTemplateId, Long documentId,
                                  List<String> retrievedChunks, String fullPrompt, String answer,
                                  long latency, int tokens) {
        ChatLog logEntry = new ChatLog();
        logEntry.setSessionId(sid);
        logEntry.setPromptTemplateId(promptTemplateId);
        logEntry.setRagDocumentId(documentId);
        logEntry.setUserQuestion(question);
        logEntry.setRetrievedChunks(retrievalService.chunksToJson(retrievedChunks));
        logEntry.setFullPrompt(fullPrompt);
        logEntry.setModelResponse(answer);
        logEntry.setLatencyMs(latency);
        logEntry.setTotalTokens(tokens);
        return logEntry;
    }

    /** 非流式场景：从 ChatResponse 元数据估算 Token 数 */
    private int estimateTokens(ChatResponse response) {
        try {
            return objectMapper.writeValueAsString(response).length() / 3;
        } catch (JsonProcessingException e) {
            return 0;
        }
    }

    // ================================================================
    // SSE 输出
    // ================================================================

    private void writeMeta(OutputStream out, String sid, long retrievalMs, List<String> sources) {
        try {
            String metaJson = objectMapper.writeValueAsString(
                    Map.of("sessionId", sid, "retrievalMs", retrievalMs, "sources", sources));
            writeSse(out, "meta", metaJson);
        } catch (Exception ignored) {
        }
    }

    private void writeSse(OutputStream out, String event, String data) {
        try {
            out.write(("event: " + event + "\ndata: " + data + "\n\n")
                    .getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (Exception ignored) {
        }
    }

    // ================================================================
    // 日志持久化
    // ================================================================

    private void persistLog(String sid, String question, Long promptTemplateId, Long documentId,
                            List<String> retrievedChunks, String fullPrompt, String answer, long latency) {
        try {
            ChatLog logEntry = buildChatLog(sid, question, promptTemplateId, documentId,
                    retrievedChunks, fullPrompt, answer, latency, answer.length() / 3);
            chatLogMapper.insert(logEntry);
        } catch (Exception e) {
            log.error("对话日志持久化失败", e);
        }
    }

}
