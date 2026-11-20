package com.example.demo.controller;

import com.example.demo.auth.UserContext;
import com.example.demo.common.ApiResponse;
import com.example.demo.entity.CaseBatchEntity;
import com.example.demo.entity.CaseItemEntity;
import com.example.demo.service.CaseService;
import com.example.demo.util.DownloadHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CaseController {

    private final CaseService caseService;

    @GetMapping("/batches")
    public ApiResponse<List<CaseBatchEntity>> listBatches(@RequestParam(required = false) Long projectId,
                                                          @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(caseService.listBatches(UserContext.userId(), projectId, keyword));
    }

    @GetMapping("/batches/{id}")
    public ApiResponse<Map<String, Object>> batchDetail(@PathVariable Long id) {
        return ApiResponse.ok(caseService.batchDetail(UserContext.userId(), id));
    }

    @DeleteMapping("/batches/{id}")
    public ApiResponse<Void> deleteBatch(@PathVariable Long id) {
        caseService.deleteBatch(UserContext.userId(), id);
        return ApiResponse.ok("批次已删除", null);
    }

    @GetMapping("/batches/{id}/cases")
    public ApiResponse<List<CaseItemEntity>> listCases(@PathVariable Long id,
                                                       @RequestParam(required = false) String priority,
                                                       @RequestParam(required = false) String reviewStatus,
                                                       @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(caseService.listCases(UserContext.userId(), id, priority, reviewStatus, keyword));
    }

    /** 导出当前批次（含在线修改后的用例内容） */
    @GetMapping("/batches/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable Long id,
                                         @RequestParam(defaultValue = "xlsx") String format) {
        CaseBatchEntity batch = caseService.getBatch(UserContext.userId(), id);
        byte[] data = caseService.exportBatch(UserContext.userId(), id, format);
        String ext = format == null || format.isBlank() ? "xlsx" : format;
        if ("markdown".equalsIgnoreCase(ext)) ext = "md";
        String name = batch.getName().replaceAll("[\\\\/:*?\"<>|]", "_") + "." + ext;
        MediaType mt = switch (ext.toLowerCase()) {
            case "csv" -> MediaType.parseMediaType("text/csv");
            case "json" -> MediaType.APPLICATION_JSON;
            case "md" -> MediaType.parseMediaType("text/markdown");
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
        return DownloadHelper.fileDownload(data, name, mt);
    }

    @GetMapping("/batches/{id}/download")
    public ResponseEntity<byte[]> downloadFile(@PathVariable Long id) throws Exception {
        CaseBatchEntity batch = caseService.getBatch(UserContext.userId(), id);
        java.nio.file.Path path = java.nio.file.Paths.get(batch.getFilePath());
        byte[] data = java.nio.file.Files.readAllBytes(path);
        String fileName = path.getFileName().toString();
        MediaType mt = fileName.endsWith(".zip") ? MediaType.APPLICATION_OCTET_STREAM
                : fileName.endsWith(".xlsx") ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.APPLICATION_OCTET_STREAM;
        return DownloadHelper.fileDownload(data, fileName, mt);
    }

    @PutMapping("/cases/{id}")
    public ApiResponse<CaseItemEntity> updateCase(@PathVariable Long id,
                                                  @RequestBody Map<String, Object> body) {
        return ApiResponse.ok("已保存", caseService.updateCase(UserContext.userId(), id, body));
    }

    @DeleteMapping("/cases/{id}")
    public ApiResponse<Void> deleteCase(@PathVariable Long id) {
        caseService.deleteCase(UserContext.userId(), id);
        return ApiResponse.ok("用例已删除", null);
    }
}
