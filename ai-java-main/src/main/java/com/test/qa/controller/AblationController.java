package com.test.qa.controller;

import com.test.qa.domain.AblationExperiment;
import com.test.qa.domain.Result;
import com.test.qa.service.AblationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 消融实验 Controller
 * 多维度自动对比不同RAG参数对问答质量的影响
 */
@Slf4j
@RestController
@RequestMapping("/api/ablation")
@RequiredArgsConstructor
@Tag(name = "消融实验", description = "多维度RAG参数自动对比实验")
public class AblationController {

    private final AblationService ablationService;

    @PostMapping("/experiments")
    @Operation(summary = "创建消融实验",
            description = "创建实验后需调用 POST /experiments/{id}/run 启动执行。" +
                    "baseConfig示例: {\"promptTemplateId\":1,\"chunkSize\":512,\"topK\":5}，" +
                    "variableConfigs示例: [{\"variable\":\"chunkSize\",\"values\":[256,512,1024]}]")
    public Result<AblationExperiment> createExperiment(
            @Parameter(description = "实验名称") @RequestParam String name,
            @Parameter(description = "评测集名称") @RequestParam String testSetName,
            @Parameter(description = "基准配置（JSON）") @RequestParam String baseConfig,
            @Parameter(description = "变量配置（JSON数组）") @RequestParam String variableConfigs) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> bc = new com.fasterxml.jackson.databind.ObjectMapper().readValue(baseConfig, Map.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> vc = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(variableConfigs, List.class);
            return Result.success(ablationService.createExperiment(name, testSetName, bc, vc));
        } catch (Exception e) {
            return Result.error(400, "JSON解析失败: " + e.getMessage());
        }
    }

    @GetMapping("/experiments")
    @Operation(summary = "获取所有消融实验列表")
    public Result<List<AblationExperiment>> listExperiments() {
        return Result.success(ablationService.listExperiments());
    }

    @GetMapping("/experiments/{id}")
    @Operation(summary = "获取实验详情与对比报告")
    public Result<Map<String, Object>> getExperiment(
            @Parameter(description = "实验ID") @PathVariable Long id) {
        AblationExperiment exp = ablationService.getExperiment(id);
        if (exp == null) {
            return Result.error(404, "实验不存在");
        }
        Map<String, Object> report = ablationService.getComparisonReport(id);
        report.put("experiment", exp);
        return Result.success(report);
    }

    @PostMapping("/experiments/{id}/run")
    @Operation(summary = "启动消融实验（异步执行）",
            description = "实验在后台异步执行，通过 GET /experiments/{id} 查询进度和结果")
    public Result<Map<String, String>> runExperiment(
            @Parameter(description = "实验ID") @PathVariable Long id) {
        AblationExperiment exp = ablationService.getExperiment(id);
        if (exp == null) {
            return Result.error(404, "实验不存在");
        }
        if ("RUNNING".equals(exp.getStatus())) {
            return Result.error(400, "实验已在运行中");
        }
        ablationService.runExperiment(id);
        return Result.success(Map.of("status", "STARTED", "experimentId", String.valueOf(id)));
    }

    @DeleteMapping("/experiments/{id}")
    @Operation(summary = "删除消融实验")
    public Result<Void> deleteExperiment(
            @Parameter(description = "实验ID") @PathVariable Long id) {
        ablationService.deleteExperiment(id);
        return Result.success(null);
    }
}
