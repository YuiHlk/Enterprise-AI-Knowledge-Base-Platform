package com.test.qa.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评测问题响应 DTO
 */
@Data
@Schema(description = "评测问题响应")
public class TestSetResponse {
    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "评测集名称")
    private String setName;

    @Schema(description = "评测问题")
    private String question;

    @Schema(description = "参考答案")
    private String referenceAnswer;

    @Schema(description = "期望关键词列表（JSON数组）")
    private String expectedKeywords;

    @Schema(description = "问题分类")
    private String category;

    @Schema(description = "难度: EASY, MEDIUM, HARD")
    private String difficulty;

    @Schema(description = "状态: ACTIVE-启用, ARCHIVED-归档")
    private String status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
