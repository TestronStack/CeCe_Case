package com.example.demo.controller;

import com.example.demo.auth.UserContext;
import com.example.demo.common.ApiResponse;
import com.example.demo.entity.DocumentEntity;
import com.example.demo.service.DocumentService;
import com.example.demo.util.DownloadHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @GetMapping
    public ApiResponse<List<DocumentEntity>> list(@RequestParam(required = false) Long projectId) {
        return ApiResponse.ok(documentService.list(UserContext.userId(), projectId));
    }

    @PostMapping
    public ApiResponse<DocumentEntity> upload(@RequestParam("file") MultipartFile file,
                                              @RequestParam(required = false) Long projectId) {
        return ApiResponse.ok("文档已上传", documentService.save(UserContext.userId(), projectId, file));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id) throws Exception {
        DocumentEntity doc = documentService.get(UserContext.userId(), id);
        byte[] data = Files.readAllBytes(documentService.pathOf(doc));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, DownloadHelper.buildContentDisposition(doc.getOriginalName()));
        return ResponseEntity.ok().headers(headers).body(data);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        documentService.delete(UserContext.userId(), id);
        return ApiResponse.ok("文档已删除", null);
    }
}
