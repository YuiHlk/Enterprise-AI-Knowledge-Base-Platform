package com.test.qa.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.test.qa.domain.TrainTask;
import com.test.qa.mapper.TrainTaskMapper;
import com.test.qa.service.TrainTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Python微调任务服务实现
 *
 * 架构关键：Java不写训练代码，仅做HTTP调度。
 * 通过WebClient调用Python FastAPI服务，将训练结果持久化到MySQL。
 */
@Slf4j
@Service
public class TrainTaskServiceImpl extends ServiceImpl<TrainTaskMapper, TrainTask> implements TrainTaskService {

    private final WebClient pythonTrainWebClient;
    private final ObjectMapper objectMapper;

    public TrainTaskServiceImpl(@Qualifier("pythonTrainWebClient") WebClient pythonTrainWebClient,
                                 ObjectMapper objectMapper) {
        this.pythonTrainWebClient = pythonTrainWebClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Page<TrainTask> pageQuery(int page, int size, String status) {
        LambdaQueryWrapper<TrainTask> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) {
            wrapper.eq(TrainTask::getStatus, status);
        }
        wrapper.orderByDesc(TrainTask::getCreateTime);
        return page(new Page<>(page, size), wrapper);
    }

    @Override
    @Transactional
    public TrainTask createAndStart(TrainTask task) {
        // 默认值
        if (task.getLoraRank() == null) task.setLoraRank(64);
        if (task.getLoraAlpha() == null) task.setLoraAlpha(16);
        if (task.getLearningRate() == null) task.setLearningRate(2e-4);
        if (task.getNumEpochs() == null) task.setNumEpochs(3);
        if (task.getBatchSize() == null) task.setBatchSize(4);
        if (task.getStatus() == null) task.setStatus("PENDING");
        task.setProgress(0);
        task.setCreateTime(LocalDateTime.now());

        // 保存到数据库
        save(task);
        log.info("训练任务已创建 [id={}, name={}]", task.getId(), task.getTaskName());

        // 通过HTTP调用Python服务启动训练
        try {
            Map<String, Object> requestBody = Map.of(
                    "task_id", String.valueOf(task.getId()),
                    "model_base", task.getModelBase(),
                    "dataset_name", task.getDatasetName(),
                    "dataset_path", task.getDatasetPath() != null ? task.getDatasetPath() : "",
                    "lora_rank", task.getLoraRank(),
                    "lora_alpha", task.getLoraAlpha(),
                    "learning_rate", task.getLearningRate(),
                    "num_epochs", task.getNumEpochs(),
                    "batch_size", task.getBatchSize()
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> response = pythonTrainWebClient.post()
                    .uri("/train")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null && response.containsKey("python_task_id")) {
                task.setPythonTaskId((String) response.get("python_task_id"));
            }
            task.setStatus("TRAINING");
            log.info("Python训练任务已启动 [id={}, pythonTaskId={}]", task.getId(), task.getPythonTaskId());

        } catch (Exception e) {
            // Python服务不可用时，任务保持PENDING状态，等待后续手动重试或定时轮询
            log.error("无法连接Python训练服务 [id={}]: {}", task.getId(), e.getMessage());
            task.setStatus("PENDING");
            task.setErrorMsg("无法连接Python训练服务: " + e.getMessage());
        }

        updateById(task);
        return task;
    }

    @Override
    @Transactional
    public TrainTask pollStatus(Long taskId) {
        TrainTask task = getById(taskId);
        if (task == null || task.getPythonTaskId() == null) {
            return task;
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = pythonTrainWebClient.get()
                    .uri("/train/{task_id}/status", task.getPythonTaskId())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null) {
                String status = (String) response.get("status");
                Integer progress = response.get("progress") != null
                        ? ((Number) response.get("progress")).intValue() : task.getProgress();
                @SuppressWarnings("unchecked")
                Map<String, Object> metrics = (Map<String, Object>) response.get("metrics");

                task.setStatus(mapStatus(status));
                task.setProgress(progress);

                if (metrics != null) {
                    task.setMetrics(objectMapper.writeValueAsString(metrics));
                }
                if (response.get("lora_weight_path") != null) {
                    task.setLoraWeightPath((String) response.get("lora_weight_path"));
                }
                if (response.get("error") != null) {
                    task.setErrorMsg((String) response.get("error"));
                }

                updateById(task);
            }
        } catch (Exception e) {
            log.error("轮询训练状态失败 [id={}]: {}", taskId, e.getMessage());
        }

        return task;
    }

    @Override
    public TrainTask getTaskDetail(Long id) {
        // 如果任务正在训练中，先轮询最新状态
        TrainTask task = getById(id);
        if (task != null && "TRAINING".equals(task.getStatus())) {
            return pollStatus(id);
        }
        return task;
    }

    @Override
    @Transactional
    public void deleteTask(Long id) {
        removeById(id);
    }

    /**
     * 将Python服务的状态映射为本地状态
     */
    private String mapStatus(String pythonStatus) {
        if (pythonStatus == null) return "TRAINING";
        return switch (pythonStatus.toLowerCase()) {
            case "training", "running" -> "TRAINING";
            case "completed", "done", "finished" -> "COMPLETED";
            case "failed", "error" -> "FAILED";
            default -> "TRAINING";
        };
    }
}
