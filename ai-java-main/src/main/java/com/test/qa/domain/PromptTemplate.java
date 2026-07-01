package com.test.qa.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 提示词模板实体
 * 核心设计：同一场景(scene)可以有多个版本(version)，支持版本管理和A/B测试
 */
@Data
@TableName("prompt_template")
@Schema(description = "提示词模板")
public class PromptTemplate {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "场景名称", example = "客服问答")
    private String scene;

    @Schema(description = "系统提示词", example = "你是一个专业的客服助手，请根据提供的知识库内容回答用户问题...")
    private String systemPrompt;

    @Schema(description = "用户提示词模板（支持 {{variable}} 占位符）", example = "用户问题：{{question}}\n\n参考资料：{{context}}")
    private String userTemplate;

    /**
     * Few-Shot示例，JSON数组格式
     * [{"input": "示例输入", "output": "示例输出"}]
     */
    @Schema(description = "Few-Shot示例（JSON数组）")
    @TableField(value = "few_shot_examples")
    private String fewShotExamples;

    @Schema(description = "模型温度参数 0.0-2.0", example = "0.7")
    private Double temperature;

    @Schema(description = "模型top_p参数", example = "1.0")
    private Double topP;

    @Schema(description = "最大生成Token数", example = "2048")
    private Integer maxTokens;

    @Schema(description = "版本号（同一场景递增）", example = "1")
    private Integer version;

    @Schema(description = "状态: ACTIVE-启用, ARCHIVED-归档, DRAFT-草稿", example = "ACTIVE")
    private String status;

    @Schema(description = "版本备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
