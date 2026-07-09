package com.test.qa.dto;

import lombok.Data;

import java.util.List;

/**
 * RAG 问答结果 DTO
 */
@Data
public class QAResult {
    private String answer;
    private String sessionId;
    private List<String> sources;
    private long latencyMs;
}
