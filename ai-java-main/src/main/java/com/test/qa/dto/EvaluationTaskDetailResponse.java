package com.test.qa.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 评测任务详情响应 DTO
 */
@Data
@Schema(description = "评测任务详情")
public class EvaluationTaskDetailResponse {
    @Schema(description = "任务ID")
    private String taskId;

    @Schema(description = "总评测数")
    private int total;

    @Schema(description = "已完成数")
    private int completed;

    @Schema(description = "失败数")
    private int failed;

    @Schema(description = "平均答案相关性")
    private double avgAnswerRelevance;

    @Schema(description = "平均上下文忠实度")
    private double avgContextFaithfulness;

    @Schema(description = "平均幻觉风险评分")
    private double avgHallucinationScore;

    @Schema(description = "平均上下文召回率")
    private double avgContextRecall;

    @Schema(description = "平均检索精准度")
    private double avgRetrievalPrecision;

    @Schema(description = "评测记录列表")
    private List<EvaluationRecordResponse> records;
}
