package com.test.qa.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Python微调任务记录
 * Java负责业务调度（发起、状态查询），Python负责模型训练执行
 *
 * 工程说明：Java不写模型训练代码，仅通过WebClient调用Python服务HTTP接口。
 * Python服务返回JSON，Java负责持久化和状态管理。
 */
@Data
@TableName("train_task")
@Schema(description = "Python微调任务记录")
public class TrainTask {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "微调任务名称", example = "客服FAQ问答微调")
    @TableField("task_name")
    private String taskName;

    @Schema(description = "基座模型", example = "Qwen2-7B-Instruct")
    @TableField("model_base")
    private String modelBase;

    @Schema(description = "数据集名称", example = "customer_faq_qa")
    @TableField("dataset_name")
    private String datasetName;

    @Schema(description = "数据集文件路径")
    @TableField("dataset_path")
    private String datasetPath;

    /**
     * QLoRA微调超参数
     */
    @Schema(description = "LoRA rank（低秩矩阵维度，默认64）", example = "64")
    @TableField("lora_rank")
    private Integer loraRank;

    @Schema(description = "LoRA alpha（缩放因子，默认16）", example = "16")
    @TableField("lora_alpha")
    private Integer loraAlpha;

    @Schema(description = "学习率", example = "2e-4")
    @TableField("learning_rate")
    private Double learningRate;

    @Schema(description = "训练轮数", example = "3")
    @TableField("num_epochs")
    private Integer numEpochs;

    @Schema(description = "batch size（微调受显存限制通常较小）", example = "4")
    @TableField("batch_size")
    private Integer batchSize;

    /**
     * Python服务交互字段
     */
    @Schema(description = "Python微调服务URL（默认从配置读取）")
    @TableField("python_service_url")
    private String pythonServiceUrl;

    @Schema(description = "Python端任务ID（Python服务返回）")
    @TableField("python_task_id")
    private String pythonTaskId;

    @Schema(description = "训练进度 0-100")
    private Integer progress;

    @Schema(description = "LoRA权重输出路径")
    @TableField("lora_weight_path")
    private String loraWeightPath;

    /**
     * 训练指标JSON
     * {"loss": 1.234, "eval_loss": 1.456, "train_runtime": 3600}
     */
    @Schema(description = "训练指标JSON")
    private String metrics;

    @Schema(description = "状态: PENDING, TRAINING, COMPLETED, FAILED")
    private String status;

    @Schema(description = "错误信息")
    @TableField("error_msg")
    private String errorMsg;

    @Schema(description = "创建时间")
    @TableField("create_time")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField("update_time")
    private LocalDateTime updateTime;
}
