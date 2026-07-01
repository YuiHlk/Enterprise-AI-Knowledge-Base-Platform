package com.test.qa.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.test.qa.domain.PromptTemplate;
import com.test.qa.domain.Result;
import com.test.qa.service.PromptTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 提示词模板管理 Controller
 * 提供提示词的 CRUD、版本管理、场景查询等 RESTful 接口
 */
@RestController
@RequestMapping("/api/prompt-templates")
@RequiredArgsConstructor
@Tag(name = "提示词模板管理", description = "提示词版本库CRUD、场景版本查询、归档/激活")
public class PromptTemplateController {

    private final PromptTemplateService promptTemplateService;

    @GetMapping
    @Operation(summary = "分页查询提示词模板", description = "支持按场景、状态筛选")
    public Result<Page<PromptTemplate>> page(
            @Parameter(description = "页码", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小", example = "10")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "场景筛选（模糊匹配）")
            @RequestParam(required = false) String scene,
            @Parameter(description = "状态筛选: ACTIVE/ARCHIVED/DRAFT")
            @RequestParam(required = false) String status) {
        return Result.success(promptTemplateService.pageQuery(page, size, scene, status));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取提示词模板详情")
    public Result<PromptTemplate> getById(
            @Parameter(description = "模板ID") @PathVariable Long id) {
        PromptTemplate template = promptTemplateService.getById(id);
        if (template == null) {
            return Result.error(404, "提示词模板不存在: id=" + id);
        }
        return Result.success(template);
    }

    @PostMapping
    @Operation(summary = "新增提示词模板", description = "自动分配版本号（同场景递增）")
    public Result<PromptTemplate> create(
            @Parameter(description = "提示词模板（ID和版本号无需填写，自动生成）")
            @RequestBody PromptTemplate template) {
        return Result.success(promptTemplateService.create(template));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新提示词模板")
    public Result<PromptTemplate> update(
            @Parameter(description = "模板ID") @PathVariable Long id,
            @RequestBody PromptTemplate template) {
        template.setId(id);
        boolean updated = promptTemplateService.updateById(template);
        if (!updated) {
            return Result.error(404, "提示词模板不存在: id=" + id);
        }
        return Result.success(promptTemplateService.getById(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除提示词模板")
    public Result<Void> delete(
            @Parameter(description = "模板ID") @PathVariable Long id) {
        boolean removed = promptTemplateService.removeById(id);
        if (!removed) {
            return Result.error(404, "提示词模板不存在: id=" + id);
        }
        return Result.success(null);
    }

    @GetMapping("/scene/{scene}/versions")
    @Operation(summary = "获取指定场景的所有历史版本")
    public Result<List<PromptTemplate>> getSceneVersions(
            @Parameter(description = "场景名称") @PathVariable String scene) {
        return Result.success(promptTemplateService.getSceneVersions(scene));
    }

    @PutMapping("/{id}/archive")
    @Operation(summary = "归档提示词版本")
    public Result<Void> archive(
            @Parameter(description = "模板ID") @PathVariable Long id) {
        promptTemplateService.archive(id);
        return Result.success(null);
    }

    @PutMapping("/{id}/activate")
    @Operation(summary = "激活提示词版本", description = "将指定版本设为ACTIVE，同时归档同场景其他ACTIVE版本")
    public Result<Void> activate(
            @Parameter(description = "模板ID") @PathVariable Long id) {
        promptTemplateService.activate(id);
        return Result.success(null);
    }
}
