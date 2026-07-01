package com.test.qa.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话日志
 * 记录每次RAG问答的完整上下文，便于后续分析和评测
 */
@Data
@TableName("chat_log")
@Schema(description = "对话日志")
public class ChatLog {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "会话ID（UUID，同一会话多次对话共享）")
    private String sessionId;

    @Schema(description = "使用的提示词模板ID")
    private Long promptTemplateId;

    @Schema(description = "关联的RAG文档ID（如有）")
    private Long ragDocumentId;

    @Schema(description = "用户问题")
    private String userQuestion;

    @Schema(description = "召回的文本块（JSON数组）")
    private String retrievedChunks;

    @Schema(description = "拼接后的完整Prompt（含上下文）")
    private String fullPrompt;

    @Schema(description = "模型生成的回答")
    private String modelResponse;

    @Schema(description = "消耗总Token数")
    private Integer totalTokens;

    @Schema(description = "请求耗时(ms)")
    private Long latencyMs;

    @Schema(description = "对话时间")
    private LocalDateTime createTime;
}
