package com.test.qa.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.test.qa.domain.PromptTemplate;

import java.util.List;

/**
 * 提示词模板 Service 接口
 */
public interface PromptTemplateService extends IService<PromptTemplate> {

    /**
     * 分页查询提示词模板
     *
     * @param page   页码
     * @param size   每页大小
     * @param scene  场景筛选（可选）
     * @param status 状态筛选（可选）
     * @return 分页结果
     */
    Page<PromptTemplate> pageQuery(int page, int size, String scene, String status);

    /**
     * 新增提示词模板（自动处理版本号：同场景版本+1）
     *
     * @param template 模板实体
     * @return 保存后的实体（含自动生成的版本号）
     */
    PromptTemplate create(PromptTemplate template);

    /**
     * 获取指定场景的所有版本
     *
     * @param scene 场景名称
     * @return 该场景的所有历史版本（按版本号降序）
     */
    List<PromptTemplate> getSceneVersions(String scene);

    /**
     * 归档指定提示词版本（同一场景只保留一个ACTIVE版本）
     *
     * @param id 模板ID
     */
    void archive(Long id);

    /**
     * 激活指定版本（将同场景其他版本归档）
     *
     * @param id 模板ID
     */
    void activate(Long id);
}
