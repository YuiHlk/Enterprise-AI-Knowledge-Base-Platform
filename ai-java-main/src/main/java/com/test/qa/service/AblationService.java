package com.test.qa.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.test.qa.domain.AblationExperiment;
import com.test.qa.domain.QaEvaluationRecord;
import com.test.qa.domain.QaTestSet;
import com.test.qa.mapper.AblationExperimentMapper;
import com.test.qa.mapper.QaEvaluationRecordMapper;
import com.test.qa.mapper.QaTestSetMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 消融实验引擎
 *
 * 核心思路：对RAG参数进行受控变量实验，自动对比不同参数组合下的问答质量。
 * 每个参数组合称为一个"实验组"，每个实验组独立运行一轮评测，
 * 最终汇总所有组的评分结果，输出对比报告。
 *
 * 工程坑：
 * - 组合爆炸：N个变量 × M个值 = N^M种组合，需配置上限防止资源耗尽
 * - LLM评分方差：同一配置两次实验的LLM-as-Judge评分可能偏差0.5-1分，
 *   消融对比只看相对趋势，不纠结绝对值微小差异
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AblationService {

    private final AblationExperimentMapper experimentMapper;
    private final EvaluationService evaluationService;
    private final QaEvaluationRecordMapper recordMapper;
    private final QaTestSetMapper testSetMapper;
    private final ObjectMapper objectMapper;

    @Value("${ablation.max-variable-values:10}")
    private int maxVariableValues;

    @Value("${ablation.max-combinations:50}")
    private int maxCombinations;

    // ================================================================
    // 实验CRUD
    // ================================================================

    @Transactional
    public AblationExperiment createExperiment(String name, String testSetName,
                                                Map<String, Object> baseConfig,
                                                List<Map<String, Object>> variableConfigs) {
        AblationExperiment exp = new AblationExperiment();
        exp.setExperimentName(name);
        exp.setTestSetName(testSetName);
        exp.setStatus("PENDING");
        exp.setCreateTime(LocalDateTime.now());

        try {
            exp.setBaseConfig(objectMapper.writeValueAsString(baseConfig));
            exp.setVariableConfigs(objectMapper.writeValueAsString(variableConfigs));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("配置序列化失败: " + e.getMessage());
        }

        // 预计算组合数
        List<Map<String, Object>> combinations = generateCombinations(baseConfig, variableConfigs);
        exp.setTotalTasks(combinations.size());
        exp.setCompletedTasks(0);

        experimentMapper.insert(exp);
        log.info("消融实验创建成功 [id={}, name={}, combinations={}]", exp.getId(), name, combinations.size());
        return exp;
    }

    public List<AblationExperiment> listExperiments() {
        return experimentMapper.selectList(
                new LambdaQueryWrapper<AblationExperiment>()
                        .orderByDesc(AblationExperiment::getCreateTime));
    }

    public AblationExperiment getExperiment(Long id) {
        return experimentMapper.selectById(id);
    }

    @Transactional
    public void deleteExperiment(Long id) {
        experimentMapper.deleteById(id);
    }

    // ================================================================
    // 组合生成（笛卡尔积）
    // ================================================================

    /**
     * 根据基线和变量定义，生成所有参数组合
     *
     * 例：baseConfig={promptId:1, chunkSize:512}, variables=[{variable:topK, values:[3,5]}]
     * 产生：[{promptId:1, chunkSize:512, topK:3}, {promptId:1, chunkSize:512, topK:5}]
     *
     * 算法：递归笛卡尔积。从空列表开始，逐个变量追加其每个取值，
     * 每步产生一个新的组合分支。
     */
    public List<Map<String, Object>> generateCombinations(
            Map<String, Object> baseConfig,
            List<Map<String, Object>> variableConfigs) {

        if (variableConfigs == null || variableConfigs.isEmpty()) {
            Map<String, Object> combo = new LinkedHashMap<>(baseConfig);
            combo.put("_label", "基线配置");
            return List.of(combo);
        }

        // 提取变量名和值列表
        List<String> varNames = new ArrayList<>();
        List<List<Object>> varValues = new ArrayList<>();

        for (Map<String, Object> vc : variableConfigs) {
            String varName = (String) vc.get("variable");
            @SuppressWarnings("unchecked")
            List<Object> values = (List<Object>) vc.get("values");
            if (varName != null && values != null && !values.isEmpty()) {
                varNames.add(varName);
                varValues.add(values);
            }
        }

        // 安全检查：防止组合爆炸
        long totalCombos = varValues.stream().mapToLong(List::size).reduce(1, (a, b) -> a * b);
        if (totalCombos > maxCombinations) {
            throw new IllegalArgumentException(
                    String.format("组合数(%d)超过上限(%d)，请减少变量或取值范围", totalCombos, maxCombinations));
        }

        List<Map<String, Object>> results = new ArrayList<>();
        cartesianProduct(baseConfig, varNames, varValues, 0, new LinkedHashMap<>(), results);
        return results;
    }

    private void cartesianProduct(Map<String, Object> baseConfig,
                                   List<String> varNames,
                                   List<List<Object>> varValues,
                                   int depth,
                                   Map<String, Object> current,
                                   List<Map<String, Object>> results) {
        if (depth == varNames.size()) {
            Map<String, Object> combo = new LinkedHashMap<>(baseConfig);
            combo.putAll(current);

            // 生成人类可读标签
            List<String> labelParts = new ArrayList<>();
            for (Map.Entry<String, Object> e : current.entrySet()) {
                labelParts.add(e.getKey() + "=" + e.getValue());
            }
            combo.put("_label", String.join(", ", labelParts));
            results.add(combo);
            return;
        }

        String varName = varNames.get(depth);
        for (Object value : varValues.get(depth)) {
            current.put(varName, value);
            cartesianProduct(baseConfig, varNames, varValues, depth + 1, current, results);
        }
        current.remove(varName); // 回溯清理
    }

    // ================================================================
    // 实验执行（异步）
    // ================================================================

    /**
     * 异步执行消融实验
     *
     * 执行流程：
     * 1. 解析实验配置，生成所有参数组合
     * 2. 为每个组合生成唯一taskId
     * 3. 逐组合执行评测（串行，避免LLM API限流）
     * 4. 更新进度（completed_tasks）
     * 5. 全部完成后生成汇总报告
     */
    @Async
    public void runExperiment(Long experimentId) {
        AblationExperiment exp = experimentMapper.selectById(experimentId);
        if (exp == null) {
            log.error("消融实验不存在: {}", experimentId);
            return;
        }

        exp.setStatus("RUNNING");
        experimentMapper.updateById(exp);
        log.info("开始执行消融实验 [id={}, name={}]", experimentId, exp.getExperimentName());

        try {
            Map<String, Object> baseConfig = parseConfig(exp.getBaseConfig());
            List<Map<String, Object>> variableConfigs = parseVariableConfigs(exp.getVariableConfigs());
            List<Map<String, Object>> combinations = generateCombinations(baseConfig, variableConfigs);

            Long promptTemplateId = toLong(baseConfig.get("promptTemplateId"));
            if (promptTemplateId == null) {
                throw new IllegalArgumentException("baseConfig中缺少promptTemplateId");
            }

            // 获取评测问题
            List<QaTestSet> questions = getActiveQuestions(exp.getTestSetName());

            int completed = 0;
            for (Map<String, Object> combo : combinations) {
                String label = (String) combo.getOrDefault("_label", "group-" + completed);
                String groupTaskId = "abl-" + experimentId + "-" + UUID.randomUUID().toString().substring(0, 6);

                log.info("执行实验组 [expId={}, label={}, taskId={}]", experimentId, label, groupTaskId);

                // 对每个问题执行评测
                for (QaTestSet q : questions) {
                    try {
                        evaluationService.evaluateSingle(q, promptTemplateId, combo, groupTaskId);
                    } catch (Exception e) {
                        log.error("实验组评测异常 [taskId={}, questionId={}]: {}", groupTaskId, q.getId(), e.getMessage());
                    }
                }

                completed++;
                exp.setCompletedTasks(completed);
                experimentMapper.updateById(exp);
            }

            // 生成汇总报告
            String report = generateSummaryReport(combinations, experimentId, promptTemplateId);
            exp.setSummaryReport(report);
            exp.setStatus("COMPLETED");
            experimentMapper.updateById(exp);

            log.info("消融实验完成 [id={}, groups={}]", experimentId, combinations.size());

        } catch (Exception e) {
            log.error("消融实验失败 [id={}]: {}", experimentId, e.getMessage());
            exp.setStatus("FAILED");
            experimentMapper.updateById(exp);
        }
    }

    // ================================================================
    // 汇总报告
    // ================================================================

    /**
     * 生成消融实验对比报告
     *
     * 对每个实验组的评测结果计算平均分，输出结构化对比数据
     */
    public Map<String, Object> getComparisonReport(Long experimentId) {
        AblationExperiment exp = experimentMapper.selectById(experimentId);
        if (exp == null) {
            return Map.of("error", "实验不存在");
        }

        // 如果已有缓存的汇总报告，直接返回
        if (exp.getSummaryReport() != null && !exp.getSummaryReport().isEmpty()) {
            try {
                return objectMapper.readValue(exp.getSummaryReport(), new TypeReference<Map<String, Object>>() {});
            } catch (JsonProcessingException e) {
                log.warn("解析汇总报告失败，重新计算: {}", e.getMessage());
            }
        }

        // 动态计算
        Map<String, Object> baseConfig = parseConfig(exp.getBaseConfig());
        List<Map<String, Object>> variableConfigs = parseVariableConfigs(exp.getVariableConfigs());
        List<Map<String, Object>> combinations = generateCombinations(baseConfig, variableConfigs);
        Long promptTemplateId = toLong(baseConfig.get("promptTemplateId"));

        String report = generateSummaryReport(combinations, experimentId, promptTemplateId);
        try {
            return objectMapper.readValue(report, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            return Map.of("error", "报告解析失败: " + e.getMessage());
        }
    }

    /**
     * 生成汇总报告JSON
     * 对每个组合，查询其所有评测记录并计算平均分
     */
    private String generateSummaryReport(List<Map<String, Object>> combinations,
                                          Long experimentId, Long promptTemplateId) {
        // 查询该实验下所有评测记录
        List<QaEvaluationRecord> allRecords = recordMapper.selectList(
                new LambdaQueryWrapper<QaEvaluationRecord>()
                        .likeRight(QaEvaluationRecord::getTaskId, "abl-" + experimentId + "-"));

        // 按taskId分组
        Map<String, List<QaEvaluationRecord>> byTaskId = new LinkedHashMap<>();
        for (QaEvaluationRecord r : allRecords) {
            byTaskId.computeIfAbsent(r.getTaskId(), k -> new ArrayList<>()).add(r);
        }

        // 为每个组合查找对应的taskId组并计算平均分
        List<Map<String, Object>> groupResults = new ArrayList<>();
        // 建立 combo label 到 taskId 的映射：通过rag_config_snapshot匹配
        for (Map.Entry<String, List<QaEvaluationRecord>> entry : byTaskId.entrySet()) {
            List<QaEvaluationRecord> records = entry.getValue();
            if (records.isEmpty()) continue;

            String label = extractLabel(records.get(0).getRagConfigSnapshot());
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("label", label);
            group.put("taskId", entry.getKey());
            group.put("total", records.size());

            long completed = records.stream().filter(r -> "COMPLETED".equals(r.getStatus())).count();
            group.put("completed", (int) completed);

            double avgRelevance = avgOrZero(records, QaEvaluationRecord::getAnswerRelevance);
            double avgFaithfulness = avgOrZero(records, QaEvaluationRecord::getContextFaithfulness);
            double avgHallucination = avgOrZero(records, QaEvaluationRecord::getHallucinationScore);
            double avgRecall = avgOrZero(records, QaEvaluationRecord::getContextRecall);
            double avgPrecision = avgOrZero(records, QaEvaluationRecord::getRetrievalPrecision);

            group.put("avgAnswerRelevance", round2(avgRelevance));
            group.put("avgContextFaithfulness", round2(avgFaithfulness));
            group.put("avgHallucinationScore", round2(avgHallucination));
            group.put("avgContextRecall", round2(avgRecall));
            group.put("avgRetrievalPrecision", round2(avgPrecision));
            groupResults.add(group);
        }

        // 按label排序（保持组合生成的顺序）
        groupResults.sort(Comparator.comparing(g -> (String) g.get("label")));

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("experimentId", experimentId);
        report.put("totalGroups", groupResults.size());
        report.put("groups", groupResults);

        // 找出各项指标最优组
        if (!groupResults.isEmpty()) {
            report.put("bestRelevance", findBest(groupResults, "avgAnswerRelevance", true));
            report.put("bestFaithfulness", findBest(groupResults, "avgContextFaithfulness", true));
            report.put("bestHallucination", findBest(groupResults, "avgHallucinationScore", false)); // 越低越好
            report.put("bestRecall", findBest(groupResults, "avgContextRecall", true));
            report.put("bestPrecision", findBest(groupResults, "avgRetrievalPrecision", true));
        }

        try {
            return objectMapper.writeValueAsString(report);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    // ================================================================
    // 辅助方法
    // ================================================================

    /**
     * 从rag_config_snapshot中提取可读标签
     * snapshot示例: {"promptTemplateId":1,"chunkSize":512,"topK":5}
     */
    private String extractLabel(String ragConfigSnapshot) {
        if (ragConfigSnapshot == null) return "未知";
        try {
            Map<String, Object> config = objectMapper.readValue(ragConfigSnapshot,
                    new TypeReference<Map<String, Object>>() {});
            // 过滤掉promptTemplateId和_label，只保留变量参数
            List<String> parts = new ArrayList<>();
            for (Map.Entry<String, Object> e : config.entrySet()) {
                if ("promptTemplateId".equals(e.getKey()) || "_label".equals(e.getKey())) continue;
                parts.add(e.getKey() + "=" + e.getValue());
            }
            return parts.isEmpty() ? "基线" : String.join(", ", parts);
        } catch (Exception e) {
            return ragConfigSnapshot;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfig(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseVariableConfigs(String json) {
        try {
            return objectMapper.readValue(json, List.class);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private List<QaTestSet> getActiveQuestions(String setName) {
        return testSetMapper.selectList(
                new LambdaQueryWrapper<QaTestSet>()
                        .eq(QaTestSet::getSetName, setName)
                        .eq(QaTestSet::getStatus, "ACTIVE"));
    }

    private Long toLong(Object value) {
        if (value instanceof Number n) return n.longValue();
        if (value instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    private double avgOrZero(List<QaEvaluationRecord> records,
                             java.util.function.Function<QaEvaluationRecord, Double> metric) {
        return records.stream()
                .filter(r -> "COMPLETED".equals(r.getStatus()))
                .map(metric)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    @SuppressWarnings("unchecked")
    private String findBest(List<Map<String, Object>> groups, String metric, boolean higherIsBetter) {
        return groups.stream()
                .min((a, b) -> {
                    double va = ((Number) a.getOrDefault(metric, 0.0)).doubleValue();
                    double vb = ((Number) b.getOrDefault(metric, 0.0)).doubleValue();
                    return higherIsBetter ? Double.compare(vb, va) : Double.compare(va, vb);
                })
                .map(g -> (String) g.get("label"))
                .orElse("N/A");
    }
}
