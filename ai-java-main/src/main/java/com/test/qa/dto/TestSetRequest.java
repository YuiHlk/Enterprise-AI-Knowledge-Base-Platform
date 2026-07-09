package com.test.qa.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 创建评测问题请求 DTO
 */
@Data
@Schema(description = "创建评测问题请求")
public class TestSetRequest {
    @Schema(description = "评测集名称（同一批次问题共享）", example = "客服FAQ评测集")
    private String setName;

    @Schema(description = "评测问题")
    private String question;

    @Schema(description = "参考答案（用于LLM-as-Judge对比）")
    private String referenceAnswer;

    @Schema(description = "期望关键词列表（JSON数组）", example = "[\"退款\",\"流程\",\"7天\"]")
    private String expectedKeywords;

    @Schema(description = "问题分类", example = "售后")
    private String category;

    @Schema(description = "难度: EASY, MEDIUM, HARD", example = "MEDIUM")
    private String difficulty;
}
