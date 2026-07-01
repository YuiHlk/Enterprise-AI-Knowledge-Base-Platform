package com.test.qa.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * RAG向量分块记录
 * chunk_embedding_id 关联 ChromaDB 中的向量
 */
@Data
@TableName("rag_chunk")
@Schema(description = "RAG分块")
public class RagChunk {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "关联文档ID")
    private Long documentId;

    @Schema(description = "分块序号（从0开始）")
    private Integer chunkIndex;

    @Schema(description = "分块文本内容")
    private String chunkText;

    /**
     * ChromaDB 返回的向量ID，用于后续检索时定位
     */
    @Schema(description = "ChromaDB中的向量ID")
    private String chunkEmbeddingId;

    @Schema(description = "字符数")
    private Integer charCount;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
