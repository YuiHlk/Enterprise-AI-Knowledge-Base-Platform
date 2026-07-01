package com.test.qa.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消融实验任务
 * 自动对比不同RAG参数组合对问答质量的影响
 *
 * base_config: 基准配置 {"promptTemplateId": 1, "chunkSize": 512, "topK": 5}
 * variable_configs: 变量定义 [{"variable": "chunkSize", "values": [256, 512, 1024]}, ...]
 * summary_report: 汇总对比报告JSON，所有实验组完成后的聚合结果
 */
@Data
@TableName("ablation_experiment")
@Schema(description = "消融实验任务")
public class AblationExperiment {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "实验名称", example = "分块大小与TopK消融实验")
    @TableField("experiment_name")
    private String experimentName;

    @Schema(description = "使用的评测集名称")
    @TableField("test_set_name")
    private String testSetName;

    /**
     * 基准配置JSON
     * {"promptTemplateId": 1, "chunkSize": 512, "topK": 5}
     */
    @Schema(description = "基准配置（JSON）")
    @TableField("base_config")
    private String baseConfig;

    /**
     * 变量配置JSON
     * [{"variable": "chunkSize", "values": [256, 512, 1024]}, {"variable": "topK", "values": [3, 5, 10]}]
     */
    @Schema(description = "变量配置列表（JSON）")
    @TableField("variable_configs")
    private String variableConfigs;

    @Schema(description = "总评测任务数（实验组数）")
    @TableField("total_tasks")
    private Integer totalTasks;

    @Schema(description = "已完成任务数")
    @TableField("completed_tasks")
    private Integer completedTasks;

    @Schema(description = "汇总报告JSON（所有实验对比结果）")
    @TableField("summary_report")
    private String summaryReport;

    @Schema(description = "状态: PENDING, RUNNING, COMPLETED, FAILED")
    private String status;

    @Schema(description = "创建时间")
    @TableField("create_time")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField("update_time")
    private LocalDateTime updateTime;
}
