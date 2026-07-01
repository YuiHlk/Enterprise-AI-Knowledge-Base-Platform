package com.test.qa.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本分块服务
 *
 * 按可配置的 chunkSize 和 overlap 将长文本切分为小块。
 * 采用滑动窗口策略：每块向前 overlap 个字符，确保上下文衔接。
 *
 * 工程注意：
 * - 分块过大 → 检索精准度下降、一次召回噪声多
 * - 分块过小 → 语义碎片化、上下文断裂
 */
@Service
public class TextChunkService {

    @Value("${rag.chunk.default-size:512}")
    private int defaultChunkSize;

    @Value("${rag.chunk.default-overlap:64}")
    private int defaultOverlap;

    @Value("${rag.chunk.min-size:128}")
    private int minChunkSize;

    @Value("${rag.chunk.max-size:4096}")
    private int maxChunkSize;

    /**
     * 使用默认参数分块
     */
    public List<String> chunk(String text) {
        return chunk(text, defaultChunkSize, defaultOverlap);
    }

    /**
     * 按指定参数分块
     *
     * @param text      原始文本
     * @param chunkSize 每块字符数
     * @param overlap   块间重叠字符数
     * @return 分块文本列表
     */
    public List<String> chunk(String text, int chunkSize, int overlap) {
        // 参数校验，防止配置异常导致切片失效
        chunkSize = Math.max(minChunkSize, Math.min(chunkSize, maxChunkSize));
        overlap = Math.max(0, Math.min(overlap, chunkSize / 2));

        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return chunks;
        }

        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            // 滑动窗口：下一块从 (end - overlap) 开始
            start = end - overlap;
            // 防止死循环：最后一块不足 overlap 时直接退出
            if (end >= text.length()) {
                break;
            }
        }
        return chunks;
    }
}
