package com.test.qa.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 启动评测任务响应 DTO
 */
@Data
@AllArgsConstructor
@Schema(description = "启动评测任务响应")
public class RunEvaluationResponse {
    @Schema(description = "任务ID")
    private String taskId;

    @Schema(description = "任务状态", example = "STARTED")
    private String status;
}
