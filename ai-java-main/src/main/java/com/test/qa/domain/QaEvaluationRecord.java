package com.test.qa.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 自动化评测结果记录
 * 存储每次评测的完整数据：检索指标（Java计算）+ 主观打分（LLM-as-Judge）+ 原始响应（偏差分析）
 */
@Data
@TableName("qa_evaluation_record")
@Schema(description = "自动化评测结果记录")
public class QaEvaluationRecord {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "评测任务ID（批量评测关联）")
    @TableField("task_id")
    private String taskId;

    @Schema(description = "关联评测问题ID")
    @TableField("test_question_id")
    private Long testQuestionId;

    @Schema(description = "使用的提示词模板ID（消融实验用）")
    @TableField("prompt_template_id")
    private Long promptTemplateId;

    /**
     * RAG配置快照，JSON格式
     * {"chunkSize": 512, "topK": 5, "overlap": 64}
     */
    @Schema(description = "RAG配置快照（JSON）")
    @TableField("rag_config_snapshot")
    private String ragConfigSnapshot;

    @Schema(description = "模型生成答案")
    @TableField("model_response")
    private String modelResponse;

    @Schema(description = "检索精准度（客观指标，Java计算）")
    @TableField("retrieval_precision")
    private Double retrievalPrecision;

    @Schema(description = "上下文召回率（客观指标，Java计算）")
    @TableField("context_recall")
    private Double contextRecall;

    @Schema(description = "答案相关性（LLM-as-Judge打分 1-5）")
    @TableField("answer_relevance")
    private Double answerRelevance;

    @Schema(description = "上下文忠实度（LLM-as-Judge打分 1-5）")
    @TableField("context_faithfulness")
    private Double contextFaithfulness;

    /**
     * 幻觉风险评分，1-5分，越低越好
     * 1=完全基于上下文，5=严重幻觉（编造不存在的事实）
     */
    @Schema(description = "幻觉风险评分（LLM-as-Judge打分 1-5，越低越好）")
    @TableField("hallucination_score")
    private Double hallucinationScore;

    @Schema(description = "LLM-as-Judge原始返回（完整JSON，用于偏差分析）")
    @TableField("judge_raw_response")
    private String judgeRawResponse;

    @Schema(description = "请求耗时(ms)")
    @TableField("latency_ms")
    private Long latencyMs;

    @Schema(description = "状态: PENDING, RUNNING, COMPLETED, FAILED")
    private String status;

    @Schema(description = "错误信息")
    @TableField("error_msg")
    private String errorMsg;

    @Schema(description = "创建时间")
    @TableField("create_time")
    private LocalDateTime createTime;
}
