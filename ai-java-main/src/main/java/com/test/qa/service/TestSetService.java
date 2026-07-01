package com.test.qa.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.test.qa.domain.QaTestSet;

import java.util.List;

/**
 * 标准评测问题集服务接口
 */
public interface TestSetService extends IService<QaTestSet> {

    /**
     * 分页查询问题集
     * @param page 页码
     * @param size 每页大小
     * @param setName 评测集名称（可选筛选）
     * @param difficulty 难度筛选（可选）
     */
    Page<QaTestSet> pageQuery(int page, int size, String setName, String difficulty);

    /**
     * 获取所有评测集名称（去重）
     */
    List<String> listSetNames();

    /**
     * 根据评测集名称查询问题列表
     */
    List<QaTestSet> getQuestionsBySetName(String setName);

    /**
     * 批量创建评测问题
     */
    List<QaTestSet> batchCreate(List<QaTestSet> questions);

    /**
     * 删除问题
     */
    void deleteQuestion(Long id);
}
