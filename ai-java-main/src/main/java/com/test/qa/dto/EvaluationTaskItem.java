package com.test.qa.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评测任务列表项 DTO
 */
@Data
@AllArgsConstructor
@Schema(description = "评测任务列表项")
public class EvaluationTaskItem {
    @Schema(description = "任务ID")
    private String taskId;

    @Schema(description = "总评测数")
    private int total;

    @Schema(description = "已完成数")
    private int completed;

    @Schema(description = "失败数")
    private int failed;

    @Schema(description = "待执行数")
    private int pending;

    @Schema(description = "最新记录时间")
    private LocalDateTime latestTime;
}
