package com.test.qa.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.test.qa.domain.RagChunk;
import com.test.qa.domain.RagDocument;
import com.test.qa.mapper.RagChunkMapper;
import com.test.qa.mapper.RagDocumentMapper;
import com.test.qa.service.DocumentService;
import com.test.qa.service.EmbeddingService;
import com.test.qa.service.TextChunkService;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 文档管理 Service 实现
 */
@Slf4j
@Service
public class DocumentServiceImpl
        extends ServiceImpl<RagDocumentMapper, RagDocument>
        implements DocumentService {

    private final RagChunkMapper ragChunkMapper;
    private final TextChunkService textChunkService;
    private final EmbeddingService embeddingService;
    private final ApplicationContext context;

    @Autowired
    public DocumentServiceImpl(RagChunkMapper ragChunkMapper, TextChunkService textChunkService,
                               EmbeddingService embeddingService, ApplicationContext context) {
        this.ragChunkMapper = ragChunkMapper;
        this.textChunkService = textChunkService;
        this.embeddingService = embeddingService;
        this.context = context;
    }

    @Value("${rag.chunk.default-size:512}")
    private int defaultChunkSize;

    @Value("${rag.chunk.default-overlap:64}")
    private int defaultOverlap;

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @Override
    @Transactional
    public RagDocument upload(MultipartFile file, int chunkSize, int chunkOverlap) {
        // 参数默认值
        if (chunkSize <= 0) chunkSize = defaultChunkSize;
        if (chunkOverlap < 0) chunkOverlap = defaultOverlap;

        // 1. 保存文件到磁盘
        String originalName = file.getOriginalFilename();
        String fileType = getFileType(originalName);
        String storedName = UUID.randomUUID().toString() + "_" + originalName;
        try {
            Path uploadPath = Paths.get(uploadDir);
            Files.createDirectories(uploadPath);
            Path filePath = uploadPath.resolve(storedName);
            file.transferTo(filePath.toFile());

            // 2. 创建文档记录
            RagDocument doc = new RagDocument();
            doc.setFileName(originalName);
            doc.setFileType(fileType);
            doc.setFileSize(file.getSize());
            doc.setFilePath(filePath.toString());
            doc.setChunkSize(chunkSize);
            doc.setChunkOverlap(chunkOverlap);
            doc.setStatus("PROCESSING");
            save(doc);

            // 3. 异步处理文档（通过代理调用，确保 @Async 生效）
            context.getBean(DocumentService.class).processDocument(doc.getId());
            log.info("文档上传成功: id={}, name={}, type={}, size={}", doc.getId(), originalName, fileType, file.getSize());
            return doc;
        } catch (IOException e) {
            log.error("文件保存失败: {}", originalName, e);
            throw new RuntimeException("文件保存失败: " + e.getMessage(), e);
        }
    }

    /**
     * 异步处理文档：解析文本 → 分块 → 向量化 → 入库
     * 使用 @Async 避免阻塞上传接口响应
     */
    @Override
    @Async
    @Transactional
    public void processDocument(Long documentId) {
        RagDocument doc = getById(documentId);
        if (doc == null) return;
        try {
            // 1. 解析文本
            String text = extractText(doc.getFilePath(), doc.getFileType());
            if (text == null || text.trim().isEmpty()) {
                throw new RuntimeException("文档内容为空");
            }

            // 2. 分块
            int overlap = doc.getChunkOverlap() != null ? doc.getChunkOverlap() : 64;
            List<String> chunks = textChunkService.chunk(text, doc.getChunkSize(), overlap);
            log.info("文档分块完成: docId={}, chunks={}, chunkSize={}, overlap={}",
                    documentId, chunks.size(), doc.getChunkSize(), overlap);

            // 3. 向量化 + 存入 ChromaDB
            List<String> embeddingIds = embeddingService.embedAndStore(chunks);

            // 4. 分块记录入库（MySQL）
            for (int i = 0; i < chunks.size(); i++) {
                RagChunk chunk = new RagChunk();
                chunk.setDocumentId(documentId);
                chunk.setChunkIndex(i);
                chunk.setChunkText(chunks.get(i));
                chunk.setChunkEmbeddingId(embeddingIds.get(i));
                chunk.setCharCount(chunks.get(i).length());
                ragChunkMapper.insert(chunk);
            }

            // 5. 更新文档状态
            doc.setChunkCount(chunks.size());
            doc.setStatus("COMPLETED");
            updateById(doc);
            log.info("文档处理完成: docId={}, chunks={}", documentId, chunks.size());
        } catch (Exception e) {
            log.error("文档处理失败: docId={}", documentId, e);
            doc.setStatus("FAILED");
            updateById(doc);
        }
    }

    @Override
    public Page<RagDocument> pageQuery(int page, int size, String status) {
        LambdaQueryWrapper<RagDocument> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) {
            wrapper.eq(RagDocument::getStatus, status);
        }
        wrapper.orderByDesc(RagDocument::getCreateTime);
        return page(new Page<>(page, size), wrapper);
    }

    @Override
    @Transactional
    public void deleteDocument(Long id) {
        // 删除关联分块
        LambdaQueryWrapper<RagChunk> chunkWrapper = new LambdaQueryWrapper<>();
        chunkWrapper.eq(RagChunk::getDocumentId, id);
        ragChunkMapper.delete(chunkWrapper);
        // 删除文档记录
        removeById(id);
        log.info("文档及关联分块已删除: docId={}", id);
    }

    /**
     * 从文件提取纯文本
     */
    private String extractText(String filePath, String fileType) throws IOException {
        File file = new File(filePath);
        if ("PDF".equalsIgnoreCase(fileType)) {
            try (PDDocument pdfDoc = Loader.loadPDF(file)) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setSortByPosition(true);
                return stripper.getText(pdfDoc);
            }
        }
        if ("MD".equalsIgnoreCase(fileType) || "TXT".equalsIgnoreCase(fileType)) {
            return Files.readString(Path.of(filePath));
        }
        throw new IllegalArgumentException("不支持的文件类型: " + fileType);
    }

    private String getFileType(String fileName) {
        if (fileName == null) return "TXT";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) return "PDF";
        if (lower.endsWith(".md")) return "MD";
        if (lower.endsWith(".txt")) return "TXT";
        return "TXT";
    }
}
