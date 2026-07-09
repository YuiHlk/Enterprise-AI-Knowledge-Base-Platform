package com.test.qa.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评测记录响应 DTO
 */
@Data
@Schema(description = "评测记录响应")
public class EvaluationRecordResponse {
    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "评测任务ID")
    private String taskId;

    @Schema(description = "关联评测问题ID")
    private Long testQuestionId;

    @Schema(description = "提示词模板ID")
    private Long promptTemplateId;

    @Schema(description = "RAG配置快照（JSON）")
    private String ragConfigSnapshot;

    @Schema(description = "模型生成答案")
    private String modelResponse;

    @Schema(description = "检索精准度")
    private Double retrievalPrecision;

    @Schema(description = "上下文召回率")
    private Double contextRecall;

    @Schema(description = "答案相关性（LLM-as-Judge 1-5）")
    private Double answerRelevance;

    @Schema(description = "上下文忠实度（LLM-as-Judge 1-5）")
    private Double contextFaithfulness;

    @Schema(description = "幻觉风险评分（LLM-as-Judge 1-5，越低越好）")
    private Double hallucinationScore;

    @Schema(description = "LLM-as-Judge原始返回JSON")
    private String judgeRawResponse;

    @Schema(description = "请求耗时(ms)")
    private Long latencyMs;

    @Schema(description = "状态: PENDING, RUNNING, COMPLETED, FAILED")
    private String status;

    @Schema(description = "错误信息")
    private String errorMsg;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
