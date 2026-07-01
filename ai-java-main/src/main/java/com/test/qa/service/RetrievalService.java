package com.test.qa.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.test.qa.domain.RagChunk;
import com.test.qa.mapper.RagChunkMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 检索服务：向量检索 + 结果加载
 *
 * 负责从 ChromaDB 语义检索相似chunk，再回 MySQL 加载完整文本
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalService {

    private final EmbeddingService embeddingService;
    private final RagChunkMapper ragChunkMapper;

    @Value("${rag.retrieval.default-top-k:5}")
    private int defaultTopK;

    /**
     * 语义检索 + 加载完整文本
     *
     * @param queryText  查询文本
     * @param documentId 限定文档范围（null 表示全局搜索）
     * @param topK       返回数量
     * @return 相似文本块列表（按相关度排序）
     */
    public List<String> retrieve(String queryText, Long documentId, int topK) {
        if (topK <= 0) {
            topK = defaultTopK;
        }
        topK = Math.min(topK, 20); // 上限保护

        // 1. ChromaDB 语义检索 → 返回 embedding ID 列表
        List<String> embeddingIds = embeddingService.query(queryText, topK);
        if (embeddingIds.isEmpty()) {
            log.warn("ChromaDB检索为空: query='{}'", queryText);
            return List.of();
        }

        // 2. 回 MySQL 加载完整文本
        LambdaQueryWrapper<RagChunk> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(RagChunk::getChunkEmbeddingId, embeddingIds);
        if (documentId != null) {
            wrapper.eq(RagChunk::getDocumentId, documentId);
        }

        List<RagChunk> chunks = ragChunkMapper.selectList(wrapper);

        // 3. 按 ChromaDB 返回的顺序排序（保证相关度顺序）
        List<String> orderedTexts = new ArrayList<>();
        for (String eid : embeddingIds) {
            for (RagChunk chunk : chunks) {
                if (eid.equals(chunk.getChunkEmbeddingId())) {
                    orderedTexts.add(chunk.getChunkText());
                    break;
                }
            }
        }
        log.info("检索结果: query='{}', topK={}, returned={}", queryText, topK, orderedTexts.size());
        return orderedTexts;
    }

    /**
     * 获取检索到的chunk的JSON表示（用于日志记录）
     */
    public String chunksToJson(List<String> chunks) {
        return "[" + chunks.stream()
                .map(c -> "\"" + c.substring(0, Math.min(c.length(), 200)) + "\"")
                .collect(Collectors.joining(",")) + "]";
    }
}
