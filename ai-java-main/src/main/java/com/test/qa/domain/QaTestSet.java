package com.test.qa.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 标准评测问题集
 * 用于自动化评测的标准问答对，包含参考答案和期望关键词
 */
@Data
@TableName("qa_test_set")
@Schema(description = "标准评测问题集")
public class QaTestSet {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "评测集名称（同一批次问题共享）", example = "客服FAQ评测集")
    @TableField("set_name")
    private String setName;

    @Schema(description = "评测问题")
    private String question;

    @Schema(description = "参考答案（用于LLM-as-Judge对比）")
    @TableField("reference_answer")
    private String referenceAnswer;

    /**
     * 期望关键词列表，JSON数组格式
     * ["关键词1","关键词2"]
     * 用于计算检索召回率
     */
    @Schema(description = "期望关键词列表（JSON数组）", example = "[\"退款\",\"流程\",\"7天\"]")
    @TableField("expected_keywords")
    private String expectedKeywords;

    @Schema(description = "问题分类", example = "售后")
    private String category;

    @Schema(description = "难度: EASY, MEDIUM, HARD", example = "MEDIUM")
    private String difficulty;

    @Schema(description = "状态: ACTIVE-启用, ARCHIVED-归档", example = "ACTIVE")
    private String status;

    @Schema(description = "创建时间")
    @TableField("create_time")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField("update_time")
    private LocalDateTime updateTime;
}
