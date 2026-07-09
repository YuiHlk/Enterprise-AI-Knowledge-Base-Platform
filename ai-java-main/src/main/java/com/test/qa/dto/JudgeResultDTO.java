package com.test.qa.dto;

import lombok.Data;

/**
 * LLM-as-Judge 评分结果 DTO
 * 封装多维度主观评分，由 EvaluationService 内部生成后对外透出
 */
@Data
public class JudgeResultDTO {
    /** 答案相关性评分 1-5 */
    private double answerRelevance;
    /** 上下文忠实度评分 1-5 */
    private double contextFaithfulness;
    /** 幻觉风险评分 1-5（越低越好） */
    private double hallucinationScore;
    /** 评分理由 */
    private String reasoning;
    /** LLM原始返回（完整JSON，用于偏差分析） */
    private String rawResponse;
}
