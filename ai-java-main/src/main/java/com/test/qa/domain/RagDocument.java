package com.test.qa.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * RAG文档上传记录
 */
@Data
@TableName("rag_document")
@Schema(description = "RAG文档")
public class RagDocument {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "原始文件名", example = "产品手册.pdf")
    private String fileName;

    @Schema(description = "文件类型: PDF, MD, TXT", example = "PDF")
    private String fileType;

    @Schema(description = "文件大小(bytes)")
    private Long fileSize;

    @Schema(description = "文件存储路径")
    private String filePath;

    @Schema(description = "分块大小", example = "512")
    private Integer chunkSize;

    @Schema(description = "分块重叠大小", example = "64")
    private int chunkOverlap;

    // 显式getter — 避免Maven编译器增量编译顺序问题导致Lombok生成的方法不可见
    public Integer getChunkOverlap() {
        return chunkOverlap;
    }

    @Schema(description = "分块总数")
    private Integer chunkCount;

    @Schema(description = "处理状态: PROCESSING, COMPLETED, FAILED")
    private String status;

    @Schema(description = "上传时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
