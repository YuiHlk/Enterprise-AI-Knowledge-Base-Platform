package com.test.qa.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.test.qa.domain.RagDocument;
import com.test.qa.domain.Result;
import com.test.qa.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档管理 Controller
 */
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Tag(name = "文档管理", description = "知识库文档上传、查询、删除")
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping("/upload")
    @Operation(summary = "上传文档", description = "上传PDF/MD/TXT文件，异步解析分块向量化")
    public Result<RagDocument> upload(
            @Parameter(description = "文件") @RequestParam("file") MultipartFile file,
            @Parameter(description = "分块大小（默认512）") @RequestParam(defaultValue = "512") int chunkSize,
            @Parameter(description = "分块重叠（默认64）") @RequestParam(defaultValue = "64") int chunkOverlap) {
        return Result.success(documentService.upload(file, chunkSize, chunkOverlap));
    }

    @GetMapping
    @Operation(summary = "分页查询文档列表")
    public Result<Page<RagDocument>> page(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "状态筛选") @RequestParam(required = false) String status) {
        return Result.success(documentService.pageQuery(page, size, status));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取文档详情")
    public Result<RagDocument> getById(@PathVariable Long id) {
        RagDocument doc = documentService.getById(id);
        if (doc == null) {
            return Result.error(404, "文档不存在");
        }
        return Result.success(doc);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除文档（同时删除关联分块）")
    public Result<Void> delete(@PathVariable Long id) {
        documentService.deleteDocument(id);
        return Result.success(null);
    }
}
