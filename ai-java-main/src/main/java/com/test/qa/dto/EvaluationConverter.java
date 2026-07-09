package com.test.qa.dto;

import com.test.qa.domain.QaEvaluationRecord;
import com.test.qa.domain.QaTestSet;

import java.util.List;

/**
 * 评测相关 Entity ↔ DTO 转换工具
 */
public final class EvaluationConverter {

    private EvaluationConverter() {
    }

    // ================================================================
    // TestSet
    // ================================================================

    public static QaTestSet toEntity(TestSetRequest request) {
        QaTestSet entity = new QaTestSet();
        entity.setSetName(request.getSetName());
        entity.setQuestion(request.getQuestion());
        entity.setReferenceAnswer(request.getReferenceAnswer());
        entity.setExpectedKeywords(request.getExpectedKeywords());
        entity.setCategory(request.getCategory());
        entity.setDifficulty(request.getDifficulty());
        entity.setStatus("ACTIVE");
        return entity;
    }

    public static TestSetResponse toResponse(QaTestSet entity) {
        TestSetResponse response = new TestSetResponse();
        response.setId(entity.getId());
        response.setSetName(entity.getSetName());
        response.setQuestion(entity.getQuestion());
        response.setReferenceAnswer(entity.getReferenceAnswer());
        response.setExpectedKeywords(entity.getExpectedKeywords());
        response.setCategory(entity.getCategory());
        response.setDifficulty(entity.getDifficulty());
        response.setStatus(entity.getStatus());
        response.setCreateTime(entity.getCreateTime());
        response.setUpdateTime(entity.getUpdateTime());
        return response;
    }

    public static List<TestSetResponse> toTestSetResponseList(List<QaTestSet> entities) {
        return entities.stream().map(EvaluationConverter::toResponse).toList();
    }

    public static List<QaTestSet> toTestSetEntityList(List<TestSetRequest> requests) {
        return requests.stream().map(EvaluationConverter::toEntity).toList();
    }

    // ================================================================
    // EvaluationRecord
    // ================================================================

    public static EvaluationRecordResponse toResponse(QaEvaluationRecord entity) {
        EvaluationRecordResponse response = new EvaluationRecordResponse();
        response.setId(entity.getId());
        response.setTaskId(entity.getTaskId());
        response.setTestQuestionId(entity.getTestQuestionId());
        response.setPromptTemplateId(entity.getPromptTemplateId());
        response.setRagConfigSnapshot(entity.getRagConfigSnapshot());
        response.setModelResponse(entity.getModelResponse());
        response.setRetrievalPrecision(entity.getRetrievalPrecision());
        response.setContextRecall(entity.getContextRecall());
        response.setAnswerRelevance(entity.getAnswerRelevance());
        response.setContextFaithfulness(entity.getContextFaithfulness());
        response.setHallucinationScore(entity.getHallucinationScore());
        response.setJudgeRawResponse(entity.getJudgeRawResponse());
        response.setLatencyMs(entity.getLatencyMs());
        response.setStatus(entity.getStatus());
        response.setErrorMsg(entity.getErrorMsg());
        response.setCreateTime(entity.getCreateTime());
        return response;
    }

    public static List<EvaluationRecordResponse> toRecordResponseList(List<QaEvaluationRecord> entities) {
        return entities.stream().map(EvaluationConverter::toResponse).toList();
    }
}
