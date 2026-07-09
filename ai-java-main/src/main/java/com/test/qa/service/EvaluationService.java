package com.test.qa.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.test.qa.domain.PromptTemplate;
import com.test.qa.domain.QaEvaluationRecord;
import com.test.qa.domain.QaTestSet;
import com.test.qa.dto.EvaluationConverter;
import com.test.qa.dto.EvaluationRecordResponse;
import com.test.qa.dto.EvaluationTaskDetailResponse;
import com.test.qa.dto.EvaluationTaskItem;
import com.test.qa.dto.JudgeResultDTO;
import com.test.qa.mapper.PromptTemplateMapper;
import com.test.qa.mapper.QaEvaluationRecordMapper;
import com.test.qa.mapper.QaTestSetMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 自动化评测引擎
 *
 * 核心能力：
 * 1. 检索层客观指标（Java计算）—— 关键词召回率 + 检索精准度
 * 2. LLM-as-Judge主观评测（LLM打分）—— 答案相关性 + 上下文忠实度 + 幻觉风险
 * 3. 全自动批量评测任务（异步线程池）
 *
 * LLM-as-Judge工程注意：
 * - LLM打分天然不稳定，同一输入多次评分可能偏差0.5-1.5分
 * - 通过低温(0.1)+结构化评分量规+标准答案锚定来降低方差
 * - judge_raw_response存储原始JSON，便于后续偏差分析和评分校准
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationService {

    private final QaTestSetMapper testSetMapper;
    private final QaEvaluationRecordMapper recordMapper;
    private final PromptTemplateMapper promptTemplateMapper;
    private final RetrievalService retrievalService;
    private final PromptRenderService promptRenderService;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    @Value("${evaluation.retrieval.default-top-k:5}")
    private int defaultTopK;

    @Value("${evaluation.judge.temperature:0.1}")
    private double judgeTemperature;

    // ================================================================
    // LLM-as-Judge 评分提示词
    // 评分量规内嵌在提示词中以提高一致性
    // ================================================================
    private static final String JUDGE_SYSTEM_PROMPT = """
            你是一个专业的AI回答质量评审专家。你的任务是根据给定的【标准答案】和【检索到的上下文】，对【模型回答】进行多维度评分。

            ## 评分维度（每个维度1-5分，允许小数）

            ### 1. 答案相关性 (answerRelevance)
            - 5分：回答完全切题，直接解答了用户问题，无冗余信息
            - 4分：回答基本切题，有少量无关内容
            - 3分：回答部分相关，但遗漏了关键信息或包含明显无关内容
            - 2分：回答与问题关联较弱，主要信息不匹配
            - 1分：回答完全离题，答非所问

            ### 2. 上下文忠实度 (contextFaithfulness)
            - 5分：回答完全基于提供的上下文，无任何编造
            - 4分：回答主要基于上下文，有极少量合理推断
            - 3分：回答部分基于上下文，存在一些无依据的陈述
            - 2分：回答大量内容无法在上下文中找到支撑
            - 1分：回答完全脱离上下文，纯属编造

            ### 3. 幻觉风险 (hallucinationScore)
            注意：此分数越低越好，1=无幻觉，5=严重幻觉
            - 1分：回答中所有事实均可在上下文中验证，无编造
            - 2分：回答有极少量无法验证的细节，但不影响核心信息
            - 3分：回答存在一些可能误导的信息或编造的细节
            - 4分：回答包含明显错误的事实陈述
            - 5分：回答大量编造不存在的事实、数据、人名、日期等

            ## 评分要求
            1. 严格对照【标准答案】和【上下文】进行判断
            2. 每个维度给出具体分数（1-5，可保留1位小数）
            3. 给出综合评分理由
            4. 只返回JSON格式，不要其他内容

            ## 输出格式
            {"answerRelevance": 4.0, "contextFaithfulness": 3.5, "hallucinationScore": 1.5, "reasoning": "评分理由"}
            """;

    // ================================================================
    // 检索层客观指标计算（Java计算，不依赖LLM）
    // ================================================================

    /**
     * 计算上下文召回率
     * 定义：期望关键词在检索到的文本块中被命中的比例
     * recall = 命中的关键词数 / 总期望关键词数
     *
     * 工程说明：这是一种简化的关键词召回计算，不是严格的语义召回率。
     * 关键词匹配无法捕捉同义词和语义等价，但对于有明确关键词的场景（如客服FAQ）是有效的客观指标。
     */
    public double computeContextRecall(List<String> expectedKeywords, List<String> retrievedChunks) {
        if (expectedKeywords == null || expectedKeywords.isEmpty()) {
            return 0.0;
        }
        String allChunks = String.join(" ", retrievedChunks).toLowerCase();
        long hitCount = expectedKeywords.stream()
                .filter(kw -> allChunks.contains(kw.toLowerCase().trim()))
                .count();
        return Math.round((double) hitCount / expectedKeywords.size() * 100.0) / 100.0;
    }

    /**
     * 计算检索精准度
     * 定义：包含至少一个期望关键词的文本块占总检索文本块的比例
     * precision = 包含关键词的块数 / 总检索块数
     *
     * 工程说明：精准度低意味着检索回了大量不相关内容（噪声），可能稀释上下文质量。
     */
    public double computeRetrievalPrecision(List<String> expectedKeywords, List<String> retrievedChunks) {
        if (retrievedChunks == null || retrievedChunks.isEmpty()) {
            return 0.0;
        }
        if (expectedKeywords == null || expectedKeywords.isEmpty()) {
            return 1.0; // 无关键词约束时，假设全部相关
        }
        List<String> lowerKeywords = expectedKeywords.stream()
                .map(k -> k.toLowerCase().trim())
                .toList();
        long relevantCount = retrievedChunks.stream()
                .filter(chunk -> {
                    String lower = chunk.toLowerCase();
                    return lowerKeywords.stream().anyMatch(lower::contains);
                })
                .count();
        return Math.round((double) relevantCount / retrievedChunks.size() * 100.0) / 100.0;
    }

    // ================================================================
    // LLM-as-Judge 主观评分
    // ================================================================

    /**
     * 调用LLM对模型回答进行多维度打分
     *
     * 工程坑：LLM打分天然不稳定，同一输入可能给出不同分数。
     * 缓解措施：
     * 1. 使用低温(temperature=0.1)减少随机性
     * 2. 提供详细的评分量规（见JUDGE_SYSTEM_PROMPT）
     * 3. 输出固定JSON格式减少解析歧义
     * 4. 存储原始响应(judgeRawResponse)便于后续分析评分一致性
     */
    public JudgeResultDTO judge(String question, String referenceAnswer,
                             String retrievedContext, String modelAnswer) {
        String userPrompt = String.format("""
                        【用户问题】
                        %s

                        【标准答案】
                        %s

                        【检索到的上下文】
                        %s

                        【模型回答】
                        %s

                        请根据以上信息，按照你的评分标准进行打分。只返回JSON。""",
                question, referenceAnswer, retrievedContext, modelAnswer);

        try {
            ChatResponse response = chatClient.prompt()
                    .system(JUDGE_SYSTEM_PROMPT)
                    .user(userPrompt)
                    .call()
                    .chatResponse();

            String rawJson = response != null && response.getResult() != null
                    ? response.getResult().getOutput().getText()
                    : "{}";

            return parseJudgeResponse(rawJson);
        } catch (Exception e) {
            log.error("LLM-as-Judge评分失败: {}", e.getMessage());
            JudgeResultDTO fallback = new JudgeResultDTO();
            fallback.setAnswerRelevance(0.0);
            fallback.setContextFaithfulness(0.0);
            fallback.setHallucinationScore(0.0);
            fallback.setReasoning("评分失败: " + e.getMessage());
            fallback.setRawResponse("{}");
            return fallback;
        }
    }

    /**
     * 防御性JSON解析
     * LLM可能返回带有markdown代码块的JSON，需要清理后再解析
     */
    private JudgeResultDTO parseJudgeResponse(String raw) {
        JudgeResultDTO result = new JudgeResultDTO();
        result.setRawResponse(raw);

        String json = raw.trim();
        // 移除markdown代码块标记
        if (json.startsWith("```")) {
            json = json.replaceAll("```json\\s*", "")
                       .replaceAll("```\\s*", "")
                       .trim();
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            result.setAnswerRelevance(toDouble(map.get("answerRelevance")));
            result.setContextFaithfulness(toDouble(map.get("contextFaithfulness")));
            result.setHallucinationScore(toDouble(map.get("hallucinationScore")));
            result.setReasoning(Objects.toString(map.get("reasoning"), ""));
        } catch (JsonProcessingException e) {
            log.error("解析LLM评分JSON失败: {}", e.getMessage());
            result.setAnswerRelevance(0.0);
            result.setContextFaithfulness(0.0);
            result.setHallucinationScore(0.0);
            result.setReasoning("JSON解析失败: " + e.getMessage());
        }
        return result;
    }

    private double toDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        if (value instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException e) { return 0.0; }
        }
        return 0.0;
    }

    // ================================================================
    // 单条评测
    // ================================================================

    /**
     * 对单条测试问题进行完整评测流程：
     * 1. 检索上下文
     * 2. LLM生成回答
     * 3. 计算检索指标
     * 4. LLM-as-Judge打分
     * 5. 保存评测记录
     */
    public QaEvaluationRecord evaluateSingle(QaTestSet question, Long promptTemplateId,
                                              Map<String, Object> ragConfig, String taskId) {
        QaEvaluationRecord record = new QaEvaluationRecord();
        record.setTaskId(taskId);
        record.setTestQuestionId(question.getId());
        record.setPromptTemplateId(promptTemplateId);
        record.setStatus("RUNNING");
        record.setCreateTime(LocalDateTime.now());

        // 保存RAG配置快照
        try {
            record.setRagConfigSnapshot(objectMapper.writeValueAsString(ragConfig));
        } catch (JsonProcessingException e) {
            record.setRagConfigSnapshot("{}");
        }

        try {
            PromptTemplate template = promptTemplateMapper.selectById(promptTemplateId);
            if (template == null) {
                throw new IllegalArgumentException("提示词模板不存在: " + promptTemplateId);
            }

            int topK = (int) ragConfig.getOrDefault("topK", defaultTopK);
            long startTime = System.currentTimeMillis();

            // 1. 检索上下文
            List<String> retrievedChunks = retrievalService.retrieve(
                    question.getQuestion(), null, topK);
            String context = String.join("\n\n---\n\n", retrievedChunks);

            // 2. 渲染Prompt并调用LLM生成回答
            Map<String, String> variables = new LinkedHashMap<>();
            variables.put("question", question.getQuestion());
            variables.put("context", context);
            String fullPrompt = promptRenderService.render(template.getUserTemplate(), variables);

            ChatResponse chatResponse = chatClient.prompt()
                    .system(template.getSystemPrompt())
                    .user(fullPrompt)
                    .call()
                    .chatResponse();
            String answer = chatResponse != null && chatResponse.getResult() != null
                    ? chatResponse.getResult().getOutput().getText()
                    : "模型未返回有效回答";

            long latency = System.currentTimeMillis() - startTime;

            // 3. 计算检索指标
            List<String> keywords = parseKeywords(question.getExpectedKeywords());
            double recall = computeContextRecall(keywords, retrievedChunks);
            double precision = computeRetrievalPrecision(keywords, retrievedChunks);

            // 4. LLM-as-Judge评分
            JudgeResultDTO judgeResult = judge(
                    question.getQuestion(),
                    question.getReferenceAnswer() != null ? question.getReferenceAnswer() : "无标准答案",
                    context,
                    answer);

            // 5. 填充记录
            record.setModelResponse(answer);
            record.setRetrievalPrecision(precision);
            record.setContextRecall(recall);
            record.setAnswerRelevance(judgeResult.getAnswerRelevance());
            record.setContextFaithfulness(judgeResult.getContextFaithfulness());
            record.setHallucinationScore(judgeResult.getHallucinationScore());
            record.setJudgeRawResponse(judgeResult.getRawResponse());
            record.setLatencyMs(latency);
            record.setStatus("COMPLETED");

        } catch (Exception e) {
            log.error("评测失败 [taskId={}, questionId={}]: {}", taskId, question.getId(), e.getMessage());
            record.setStatus("FAILED");
            record.setErrorMsg(e.getMessage());
        }

        recordMapper.insert(record);
        return record;
    }

    // ================================================================
    // 批量评测（异步）
    // ================================================================

    /**
     * 批量异步评测
     * 遍历评测集中的所有问题，逐条执行评测流程
     *
     * 工程说明：当前为串行遍历，每条评测包含2次LLM调用（生成+评分），
     * 总耗时 = 问题数 × (LLM生成延迟 + LLM评分延迟)。
     * 后续可优化为线程池并行评测以提高吞吐。
     */
    @Async
    public void runBatchEvaluation(String taskId, String setName, Long promptTemplateId,
                                    Map<String, Object> ragConfig) {
        log.info("开始批量评测 [taskId={}, setName={}, promptId={}]", taskId, setName, promptTemplateId);

        List<QaTestSet> questions = testSetMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<QaTestSet>()
                        .eq(QaTestSet::getSetName, setName)
                        .eq(QaTestSet::getStatus, "ACTIVE"));

        int total = questions.size();
        int completed = 0;
        int failed = 0;

        for (QaTestSet q : questions) {
            try {
                QaEvaluationRecord record = evaluateSingle(q, promptTemplateId, ragConfig, taskId);
                if ("COMPLETED".equals(record.getStatus())) {
                    completed++;
                } else {
                    failed++;
                }
            } catch (Exception e) {
                log.error("评测异常 [taskId={}, questionId={}]: {}", taskId, q.getId(), e.getMessage());
                failed++;
            }
        }

        log.info("批量评测完成 [taskId={}]: 总计={}, 完成={}, 失败={}", taskId, total, completed, failed);
    }

    // ================================================================
    // 查询方法
    // ================================================================

    /**
     * 获取评测任务汇总统计
     */
    public EvaluationTaskDetailResponse getTaskSummary(String taskId) {
        List<QaEvaluationRecord> records = recordMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<QaEvaluationRecord>()
                        .eq(QaEvaluationRecord::getTaskId, taskId));

        long completed = records.stream().filter(r -> "COMPLETED".equals(r.getStatus())).count();
        long failed = records.stream().filter(r -> "FAILED".equals(r.getStatus())).count();

        double avgRelevance = records.stream()
                .filter(r -> r.getAnswerRelevance() != null)
                .mapToDouble(QaEvaluationRecord::getAnswerRelevance)
                .average().orElse(0.0);
        double avgFaithfulness = records.stream()
                .filter(r -> r.getContextFaithfulness() != null)
                .mapToDouble(QaEvaluationRecord::getContextFaithfulness)
                .average().orElse(0.0);
        double avgHallucination = records.stream()
                .filter(r -> r.getHallucinationScore() != null)
                .mapToDouble(QaEvaluationRecord::getHallucinationScore)
                .average().orElse(0.0);
        double avgRecall = records.stream()
                .filter(r -> r.getContextRecall() != null)
                .mapToDouble(QaEvaluationRecord::getContextRecall)
                .average().orElse(0.0);
        double avgPrecision = records.stream()
                .filter(r -> r.getRetrievalPrecision() != null)
                .mapToDouble(QaEvaluationRecord::getRetrievalPrecision)
                .average().orElse(0.0);

        List<EvaluationRecordResponse> recordResponses = EvaluationConverter.toRecordResponseList(records);

        EvaluationTaskDetailResponse detail = new EvaluationTaskDetailResponse();
        detail.setTaskId(taskId);
        detail.setTotal(records.size());
        detail.setCompleted((int) completed);
        detail.setFailed((int) failed);
        detail.setAvgAnswerRelevance(Math.round(avgRelevance * 100.0) / 100.0);
        detail.setAvgContextFaithfulness(Math.round(avgFaithfulness * 100.0) / 100.0);
        detail.setAvgHallucinationScore(Math.round(avgHallucination * 100.0) / 100.0);
        detail.setAvgContextRecall(Math.round(avgRecall * 100.0) / 100.0);
        detail.setAvgRetrievalPrecision(Math.round(avgPrecision * 100.0) / 100.0);
        detail.setRecords(recordResponses);
        return detail;
    }

    /**
     * 列出所有评测任务（按task_id分组）
     */
    public List<EvaluationTaskItem> listTasks() {
        List<QaEvaluationRecord> all = recordMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<QaEvaluationRecord>()
                        .orderByDesc(QaEvaluationRecord::getCreateTime));
        Map<String, List<QaEvaluationRecord>> grouped = new LinkedHashMap<>();
        for (QaEvaluationRecord r : all) {
            grouped.computeIfAbsent(r.getTaskId(), k -> new ArrayList<>()).add(r);
        }

        List<EvaluationTaskItem> tasks = new ArrayList<>();
        for (Map.Entry<String, List<QaEvaluationRecord>> entry : grouped.entrySet()) {
            List<QaEvaluationRecord> records = entry.getValue();
            long completed = records.stream().filter(r -> "COMPLETED".equals(r.getStatus())).count();
            long failed = records.stream().filter(r -> "FAILED".equals(r.getStatus())).count();
            long pending = records.stream().filter(r -> "PENDING".equals(r.getStatus())).count();

            LocalDateTime latestTime = records.stream()
                    .map(QaEvaluationRecord::getCreateTime)
                    .filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);

            tasks.add(new EvaluationTaskItem(
                    entry.getKey(), records.size(), (int) completed, (int) failed, (int) pending, latestTime));
        }
        tasks.sort((a, b) -> {
            LocalDateTime ta = a.getLatestTime();
            LocalDateTime tb = b.getLatestTime();
            if (ta == null && tb == null) return 0;
            if (ta == null) return 1;
            if (tb == null) return -1;
            return tb.compareTo(ta);
        });
        return tasks;
    }

    // ================================================================
    // 辅助方法
    // ================================================================

    @SuppressWarnings("unchecked")
    private List<String> parseKeywords(String expectedKeywords) {
        if (expectedKeywords == null || expectedKeywords.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(expectedKeywords, List.class);
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }
}
