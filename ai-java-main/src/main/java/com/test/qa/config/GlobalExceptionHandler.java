package com.test.qa.config;

import com.test.qa.domain.Result;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

/**
 * 全局异常处理
 * 统一捕获所有未处理异常，避免堆栈信息直接暴露给前端
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 异步请求超时（如 SSE 流式连接超时），响应已提交，不能返回 JSON，
     * 仅记录日志，避免 "No converter for Result with Content-Type 'text/event-stream'" 错误
     */
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public void handleAsyncTimeout(AsyncRequestTimeoutException e, HttpServletResponse response) {
        if (response.isCommitted()) {
            log.warn("SSE 异步请求超时，连接已关闭");
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("参数异常: {}", e.getMessage());
        return Result.error(400, e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Object handleRuntime(RuntimeException e, HttpServletResponse response) {
        if (isSseResponse(response)) {
            log.warn("SSE流异常（跳过JSON错误返回）: {}", e.getMessage());
            return null;
        }
        log.error("运行时异常", e);
        return Result.error(500, "服务器内部错误: " + e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Object handleUnknown(Exception e, HttpServletResponse response) {
        if (isSseResponse(response)) {
            log.warn("SSE流异常（跳过JSON错误返回）: {}", e.getMessage());
            return null;
        }
        log.error("未知异常", e);
        return Result.error(500, "服务器内部错误");
    }

    /**
     * 判断当前响应是否为 SSE 流式响应（已设置 text/event-stream Content-Type）
     * 对于 SSE 响应，不能返回 JSON，只能返回 null 让 Tomcat 自行处理
     */
    private boolean isSseResponse(HttpServletResponse response) {
        if (response.isCommitted()) {
            return true;
        }
        String contentType = response.getContentType();
        return contentType != null && contentType.contains("text/event-stream");
    }
}
