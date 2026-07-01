package com.test.qa.controller;

import com.test.qa.domain.QaEvaluationRecord;
import com.test.qa.domain.QaTestSet;
import com.test.qa.domain.Result;
import com.test.qa.mapper.QaEvaluationRecordMapper;
import com.test.qa.service.EvaluationService;
import com.test.qa.service.TestSetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 自动化评测 Controller
 * 提供评测问题集管理和评测任务执行接口
 */
@Slf4j
@RestController
@RequestMapping("/api/evaluation")
@RequiredArgsConstructor
@Tag(name = "自动化评测", description = "评测问题集管理、批量评测任务、结果查询")
public class EvaluationController {

    private final TestSetService testSetService;
    private final EvaluationService evaluationService;
    private final QaEvaluationRecordMapper recordMapper;

    // ================================================================
    // 评测问题集管理
    // ================================================================

    @GetMapping("/test-sets")
    @Operation(summary = "获取所有评测集名称列表")
    public Result<List<String>> listTestSets() {
        return Result.success(testSetService.listSetNames());
    }

    @GetMapping("/test-sets/{setName}/questions")
    @Operation(summary = "获取指定评测集的问题列表")
    public Result<List<QaTestSet>> getQuestions(
            @Parameter(description = "评测集名称") @PathVariable String setName) {
        return Result.success(testSetService.getQuestionsBySetName(setName));
    }

    @PostMapping("/test-sets")
    @Operation(summary = "创建评测问题（支持批量）",
            description = "请求体为QaTestSet数组，setName相同的为同一批次")
    public Result<List<QaTestSet>> createQuestions(
            @Parameter(description = "评测问题列表") @RequestBody List<QaTestSet> questions) {
        if (questions == null || questions.isEmpty()) {
            return Result.error(400, "问题列表不能为空");
        }
        return Result.success(testSetService.batchCreate(questions));
    }

    @DeleteMapping("/test-sets/{id}")
    @Operation(summary = "删除评测问题")
    public Result<Void> deleteQuestion(
            @Parameter(description = "问题ID") @PathVariable Long id) {
        testSetService.deleteQuestion(id);
        return Result.success(null);
    }

    // ================================================================
    // 评测任务管理
    // ================================================================

    @PostMapping("/run")
    @Operation(summary = "启动批量评测任务（异步执行）",
            description = "提交评测任务后立即返回taskId，评测在后台异步执行。可通过 GET /tasks/{taskId} 查询进度和结果。")
    public Result<Map<String, String>> runEvaluation(
            @Parameter(description = "评测集名称") @RequestParam String setName,
            @Parameter(description = "提示词模板ID") @RequestParam Long promptTemplateId,
            @Parameter(description = "TopK检索数量（默认5）") @RequestParam(defaultValue = "5") int topK,
            @Parameter(description = "分块大小覆盖（可选）") @RequestParam(required = false) Integer chunkSize) {

        String taskId = UUID.randomUUID().toString().substring(0, 8);

        Map<String, Object> ragConfig = new LinkedHashMap<>();
        ragConfig.put("topK", topK);
        if (chunkSize != null) {
            ragConfig.put("chunkSize", chunkSize);
        }

        evaluationService.runBatchEvaluation(taskId, setName, promptTemplateId, ragConfig);

        return Result.success(Map.of("taskId", taskId, "status", "STARTED"));
    }

    @GetMapping("/tasks")
    @Operation(summary = "获取所有评测任务列表（按taskId分组）")
    public Result<List<Map<String, Object>>> listTasks() {
        return Result.success(evaluationService.listTasks());
    }

    @GetMapping("/tasks/{taskId}")
    @Operation(summary = "获取评测任务详情与汇总统计",
            description = "返回任务汇总指标（平均分）+ 所有评测记录详情")
    public Result<Map<String, Object>> getTaskDetail(
            @Parameter(description = "任务ID") @PathVariable String taskId) {
        Map<String, Object> summary = evaluationService.getTaskSummary(taskId);
        return Result.success(summary);
    }

    @GetMapping("/records/{id}")
    @Operation(summary = "获取单条评测记录详情")
    public Result<QaEvaluationRecord> getRecord(
            @Parameter(description = "记录ID") @PathVariable Long id) {
        QaEvaluationRecord record = recordMapper.selectById(id);
        if (record == null) {
            return Result.error(404, "评测记录不存在");
        }
        return Result.success(record);
    }
}
