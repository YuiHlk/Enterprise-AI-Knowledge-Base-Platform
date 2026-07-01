package com.test.qa.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.test.qa.domain.Result;
import com.test.qa.domain.TrainTask;
import com.test.qa.service.TrainTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 模型微调 Controller
 * Java通过WebClient调度Python QLoRA微调服务
 */
@Slf4j
@RestController
@RequestMapping("/api/train")
@RequiredArgsConstructor
@Tag(name = "模型微调", description = "Python QLoRA微调任务管理，Java+Python协同架构")
public class TrainController {

    private final TrainTaskService trainTaskService;

    @PostMapping("/tasks")
    @Operation(summary = "创建并启动微调任务",
            description = "创建训练任务后自动调用Python服务发起QLoRA微调。如Python服务不可用，任务保持PENDING状态。")
    public Result<TrainTask> createTask(
            @Parameter(description = "任务名称") @RequestParam String taskName,
            @Parameter(description = "基座模型（如 Qwen2-7B-Instruct）") @RequestParam String modelBase,
            @Parameter(description = "数据集名称") @RequestParam String datasetName,
            @Parameter(description = "数据集路径（可选）") @RequestParam(required = false) String datasetPath,
            @Parameter(description = "LoRA rank（默认64）") @RequestParam(defaultValue = "64") int loraRank,
            @Parameter(description = "LoRA alpha（默认16）") @RequestParam(defaultValue = "16") int loraAlpha,
            @Parameter(description = "学习率（默认2e-4）") @RequestParam(defaultValue = "2e-4") double learningRate,
            @Parameter(description = "训练轮数（默认3）") @RequestParam(defaultValue = "3") int numEpochs,
            @Parameter(description = "batch size（默认4）") @RequestParam(defaultValue = "4") int batchSize) {

        TrainTask task = new TrainTask();
        task.setTaskName(taskName);
        task.setModelBase(modelBase);
        task.setDatasetName(datasetName);
        task.setDatasetPath(datasetPath);
        task.setLoraRank(loraRank);
        task.setLoraAlpha(loraAlpha);
        task.setLearningRate(learningRate);
        task.setNumEpochs(numEpochs);
        task.setBatchSize(batchSize);

        return Result.success(trainTaskService.createAndStart(task));
    }

    @GetMapping("/tasks")
    @Operation(summary = "分页查询训练任务列表")
    public Result<Page<TrainTask>> listTasks(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "状态筛选") @RequestParam(required = false) String status) {
        return Result.success(trainTaskService.pageQuery(page, size, status));
    }

    @GetMapping("/tasks/{id}")
    @Operation(summary = "获取任务详情（自动轮询最新状态）")
    public Result<TrainTask> getTask(
            @Parameter(description = "任务ID") @PathVariable Long id) {
        TrainTask task = trainTaskService.getTaskDetail(id);
        if (task == null) {
            return Result.error(404, "任务不存在");
        }
        return Result.success(task);
    }

    @PostMapping("/tasks/{id}/poll")
    @Operation(summary = "手动轮询训练状态",
            description = "从Python服务拉取最新的训练进度和指标")
    public Result<TrainTask> pollStatus(
            @Parameter(description = "任务ID") @PathVariable Long id) {
        TrainTask task = trainTaskService.pollStatus(id);
        if (task == null) {
            return Result.error(404, "任务不存在");
        }
        return Result.success(task);
    }

    @DeleteMapping("/tasks/{id}")
    @Operation(summary = "删除训练任务")
    public Result<Void> deleteTask(
            @Parameter(description = "任务ID") @PathVariable Long id) {
        trainTaskService.deleteTask(id);
        return Result.success(null);
    }
}
