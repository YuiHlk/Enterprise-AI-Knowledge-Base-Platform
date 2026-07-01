package com.test.qa.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.test.qa.domain.ChatLog;
import com.test.qa.domain.Result;
import com.test.qa.mapper.ChatLogMapper;
import com.test.qa.service.QAService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/**
 * RAG 问答 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/qa")
@RequiredArgsConstructor
@Tag(name = "RAG问答", description = "检索增强生成问答接口")
public class QAController {

    private final QAService qaService;
    private final ChatLogMapper chatLogMapper;

    @PostMapping("/ask")
    @Operation(summary = "RAG问答", description = "提交问题，检索知识库后由LLM生成回答")
    public Result<Map<String, Object>> ask(
            @Parameter(description = "用户问题") @RequestParam String question,
            @Parameter(description = "提示词模板ID") @RequestParam Long promptTemplateId,
            @Parameter(description = "限定文档ID（可选，null=全局搜索）") @RequestParam(required = false) Long documentId,
            @Parameter(description = "会话ID（可选，null=新建会话）") @RequestParam(required = false) String sessionId) {
        QAService.QAResult result = qaService.ask(question, promptTemplateId, documentId, sessionId);
        return Result.success(Map.of(
                "answer", result.getAnswer(),
                "sessionId", result.getSessionId(),
                "sources", result.getSources(),
                "latencyMs", result.getLatencyMs()
        ));
    }

    @PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "RAG流式问答", description = "提交问题后通过SSE推送token，用户无需等待完整回答")
    public void askStream(
            @Parameter(description = "用户问题") @RequestParam String question,
            @Parameter(description = "提示词模板ID") @RequestParam Long promptTemplateId,
            @Parameter(description = "限定文档ID（可选，null=全局搜索）") @RequestParam(required = false) Long documentId,
            @Parameter(description = "会话ID（可选，null=新建会话）") @RequestParam(required = false) String sessionId,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        // 设置 SSE 响应头
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");
        OutputStream out = response.getOutputStream();

        // 启动异步上下文，超时 10 分钟
        AsyncContext asyncContext = request.startAsync();
        asyncContext.setTimeout(600_000);

        // 交给 QAService 在后台线程中执行检索+LLM流式调用
        qaService.askStream(question, promptTemplateId, documentId, sessionId, out, asyncContext);
    }

    @GetMapping("/chat-history")
    @Operation(summary = "查询会话对话历史")
    public Result<List<ChatLog>> chatHistory(
            @Parameter(description = "会话ID") @RequestParam String sessionId) {
        LambdaQueryWrapper<ChatLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatLog::getSessionId, sessionId)
                .orderByAsc(ChatLog::getCreateTime);
        return Result.success(chatLogMapper.selectList(wrapper));
    }
}
