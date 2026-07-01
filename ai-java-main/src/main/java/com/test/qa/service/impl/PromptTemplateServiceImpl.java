package com.test.qa.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.test.qa.domain.PromptTemplate;
import com.test.qa.mapper.PromptTemplateMapper;
import com.test.qa.service.PromptTemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 提示词模板 Service 实现
 */
@Slf4j
@Service
public class PromptTemplateServiceImpl
        extends ServiceImpl<PromptTemplateMapper, PromptTemplate>
        implements PromptTemplateService {

    @Override
    public Page<PromptTemplate> pageQuery(int page, int size, String scene, String status) {
        LambdaQueryWrapper<PromptTemplate> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(scene)) {
            wrapper.like(PromptTemplate::getScene, scene);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(PromptTemplate::getStatus, status);
        }
        wrapper.orderByDesc(PromptTemplate::getCreateTime);
        return page(new Page<>(page, size), wrapper);
    }

    @Override
    @Transactional
    public PromptTemplate create(PromptTemplate template) {
        // 查询同场景最大版本号，自动递增
        LambdaQueryWrapper<PromptTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PromptTemplate::getScene, template.getScene())
                .orderByDesc(PromptTemplate::getVersion)
                .last("LIMIT 1");
        PromptTemplate latest = getOne(wrapper, false);
        int nextVersion = (latest != null) ? latest.getVersion() + 1 : 1;
        template.setVersion(nextVersion);

        // 默认状态为草稿
        if (!StringUtils.hasText(template.getStatus())) {
            template.setStatus("DRAFT");
        }
        // 设置默认超参
        if (template.getTemperature() == null) {
            template.setTemperature(0.7);
        }
        if (template.getTopP() == null) {
            template.setTopP(1.0);
        }
        if (template.getMaxTokens() == null) {
            template.setMaxTokens(2048);
        }

        save(template);
        log.info("新增提示词模板: scene={}, version={}, id={}",
                template.getScene(), template.getVersion(), template.getId());
        return template;
    }

    @Override
    public List<PromptTemplate> getSceneVersions(String scene) {
        LambdaQueryWrapper<PromptTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PromptTemplate::getScene, scene)
                .orderByDesc(PromptTemplate::getVersion);
        return list(wrapper);
    }

    @Override
    @Transactional
    public void archive(Long id) {
        PromptTemplate template = getById(id);
        if (template == null) {
            throw new IllegalArgumentException("提示词模板不存在: id=" + id);
        }
        template.setStatus("ARCHIVED");
        updateById(template);
        log.info("归档提示词模板: id={}, scene={}, version={}",
                id, template.getScene(), template.getVersion());
    }

    @Override
    @Transactional
    public void activate(Long id) {
        PromptTemplate template = getById(id);
        if (template == null) {
            throw new IllegalArgumentException("提示词模板不存在: id=" + id);
        }
        // 将同场景其他ACTIVE版本归档（确保一个场景只有一个ACTIVE版本）
        LambdaQueryWrapper<PromptTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PromptTemplate::getScene, template.getScene())
                .eq(PromptTemplate::getStatus, "ACTIVE");
        for (PromptTemplate active : list(wrapper)) {
            active.setStatus("DRAFT");
            updateById(active);
        }
        // 激活目标版本
        template.setStatus("ACTIVE");
        updateById(template);
        log.info("激活提示词模板: id={}, scene={}, version={}",
                id, template.getScene(), template.getVersion());
    }
}
