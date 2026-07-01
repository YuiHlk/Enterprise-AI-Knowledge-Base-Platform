package com.test.qa.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.test.qa.domain.TrainTask;

import java.util.Map;

/**
 * Python微调任务服务接口
 */
public interface TrainTaskService extends IService<TrainTask> {

    /**
     * 分页查询训练任务
     */
    Page<TrainTask> pageQuery(int page, int size, String status);

    /**
     * 创建并启动微调任务
     * 1. 保存任务到MySQL
     * 2. 通过WebClient调用Python服务发起训练
     * 3. 更新Python返回的task_id和状态
     */
    TrainTask createAndStart(TrainTask task);

    /**
     * 轮询Python服务获取训练状态
     * 更新本地数据库中的progress和metrics
     */
    TrainTask pollStatus(Long taskId);

    /**
     * 获取任务详情（含最新指标）
     */
    TrainTask getTaskDetail(Long id);

    /**
     * 删除训练任务
     */
    void deleteTask(Long id);
}
