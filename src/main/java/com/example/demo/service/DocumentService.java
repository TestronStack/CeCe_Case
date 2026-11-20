package com.example.demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.common.ApiException;
import com.example.demo.entity.DocumentEntity;
import com.example.demo.mapper.DocumentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentMapper documentMapper;
    private final StorageService storageService;

    public DocumentEntity save(Long userId, Long projectId, MultipartFile file) {
        if (file == null || file.isEmpty()) throw new ApiException("请选择要上传的文档");
        String original = file.getOriginalFilename() == null ? "document" : file.getOriginalFilename();
        Path stored = storageService.saveUpload(file, userId, original);
        String ext = original.contains(".")
                ? original.substring(original.lastIndexOf('.') + 1).toLowerCase() : "";
        DocumentEntity doc = new DocumentEntity();
        doc.setUserId(userId);
        doc.setProjectId(projectId);
        doc.setName(original);
        doc.setOriginalName(original);
        doc.setExt(ext);
        // 不要使用 file.getSize()：Undertow 解析完请求后已清理临时文件，再次读取 Part 会抛 NoSuchFileException
        try {
            doc.setSize(Files.size(stored));
        } catch (IOException e) {
            doc.setSize(0L);
        }
        doc.setStoragePath(stored.toString());
        doc.setSha256(StorageService.sha256(stored));
        doc.setCreatedAt(LocalDateTime.now());
        documentMapper.insert(doc);
        return doc;
    }

    public List<DocumentEntity> list(Long userId, Long projectId) {
        LambdaQueryWrapper<DocumentEntity> q = new LambdaQueryWrapper<DocumentEntity>()
                .eq(DocumentEntity::getUserId, userId)
                .orderByDesc(DocumentEntity::getCreatedAt);
        if (projectId != null) q.eq(DocumentEntity::getProjectId, projectId);
        return documentMapper.selectList(q);
    }

    public DocumentEntity get(Long userId, Long id) {
        DocumentEntity doc = documentMapper.selectById(id);
        if (doc == null || !doc.getUserId().equals(userId)) throw new ApiException(404, "文档不存在");
        return doc;
    }

    public Path pathOf(DocumentEntity doc) {
        return storageService.resolve(doc.getStoragePath());
    }

    public void delete(Long userId, Long id) {
        DocumentEntity doc = get(userId, id);
        storageService.deleteQuietly(doc.getStoragePath());
        documentMapper.deleteById(id);
    }

    public long count(Long userId) {
        Long c = documentMapper.selectCount(new LambdaQueryWrapper<DocumentEntity>()
                .eq(DocumentEntity::getUserId, userId));
        return c == null ? 0 : c;
    }
}
