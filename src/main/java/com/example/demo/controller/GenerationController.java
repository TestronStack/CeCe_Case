package com.example.demo.controller;

import com.example.demo.auth.UserContext;
import com.example.demo.common.ApiResponse;
import com.example.demo.entity.DocumentEntity;
import com.example.demo.entity.GenerationTaskEntity;
import com.example.demo.service.DocumentService;
import com.example.demo.service.GenerationService;
import com.example.demo.service.TaskService;
import com.example.demo.service.WordParser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 生成入口：上传文档 → 创建异步任务 → 前端通过 SSE 查看进度 */
@RestController
@RequestMapping("/api/generation")
@RequiredArgsConstructor
public class GenerationController {

    private final DocumentService documentService;
    private final TaskService taskService;
    private final GenerationService generationService;

    @PostMapping("/requirement")
    public ApiResponse<GenerationTaskEntity> requirement(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "documentId", required = false) Long documentId,
            @RequestParam(required = false) Long projectId,
            @RequestParam(defaultValue = "all") String readRangeType,
            @RequestParam(defaultValue = "1") int startPage,
            @RequestParam(defaultValue = "50") int endPage,
            @RequestParam(required = false) String chapterTitles,
            @RequestParam(required = false) String keywords,
            @RequestParam(required = false) String customTemplate,
            @RequestParam(required = false) String creator,
            @RequestParam(required = false) String relatedRequirement,
            @RequestParam(defaultValue = "xlsx") String outputFormat) {

        DocumentEntity doc = resolveDocument(file, documentId, projectId);
        Map<String, Object> params = commonParams(projectId, readRangeType, startPage, endPage, outputFormat);
        params.put("chapterTitles", chapterTitles);
        params.put("keywords", keywords);
        params.put("customTemplate", customTemplate);
        params.put("creator", creator);
        params.put("relatedRequirement", relatedRequirement);

        return ApiResponse.ok("任务已创建", taskService.submit(UserContext.userId(), projectId,
                doc.getId(), "REQUIREMENT_UI", doc.getOriginalName() + " · UI用例", params));
    }

    @PostMapping("/api")
    public ApiResponse<GenerationTaskEntity> apiCases(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "documentId", required = false) Long documentId,
            @RequestParam(required = false) Long projectId,
            @RequestParam(defaultValue = "all") String readRangeType,
            @RequestParam(defaultValue = "1") int startPage,
            @RequestParam(defaultValue = "50") int endPage,
            @RequestParam(defaultValue = "xlsx") String outputFormat) {

        DocumentEntity doc = resolveDocument(file, documentId, projectId);
        Map<String, Object> params = commonParams(projectId, readRangeType, startPage, endPage, outputFormat);
        return ApiResponse.ok("任务已创建", taskService.submit(UserContext.userId(), projectId,
                doc.getId(), "API_CASE", doc.getOriginalName() + " · 接口用例", params));
    }

    @PostMapping("/script")
    public ApiResponse<GenerationTaskEntity> script(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "documentId", required = false) Long documentId,
            @RequestParam(required = false) Long projectId,
            @RequestParam(defaultValue = "http://localhost:8080") String baseUrl,
            @RequestParam(defaultValue = "java") String language,
            @RequestParam(defaultValue = "all") String readRangeType,
            @RequestParam(defaultValue = "1") int startPage,
            @RequestParam(defaultValue = "50") int endPage) {

        DocumentEntity doc = resolveDocument(file, documentId, projectId);
        Map<String, Object> params = commonParams(projectId, readRangeType, startPage, endPage, "xlsx");
        params.put("baseUrl", baseUrl);
        params.put("language", language);
        return ApiResponse.ok("任务已创建", taskService.submit(UserContext.userId(), projectId,
                doc.getId(), "API_SCRIPT", doc.getOriginalName() + " · 自动化脚本", params));
    }

    @PostMapping("/text")
    public ApiResponse<GenerationTaskEntity> text(@RequestBody Map<String, Object> body) {
        String content = str(body.get("content"));
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("content", content);
        params.put("type", str(body.getOrDefault("type", "interface")));
        params.put("generateCount", str(body.getOrDefault("generateCount", "10")));
        params.put("customTemplate", str(body.get("customTemplate")));
        params.put("creator", str(body.get("creator")));
        params.put("relatedRequirement", str(body.get("relatedRequirement")));
        params.put("outputFormat", str(body.getOrDefault("outputFormat", "xlsx")));
        params.put("projectId", str(body.get("projectId")));

        Long projectId = body.get("projectId") == null ? null : Long.valueOf(str(body.get("projectId")));
        return ApiResponse.ok("任务已创建", taskService.submit(UserContext.userId(), projectId,
                null, "TEXT", "自然语言用例 · " + content.substring(0, Math.min(content.length(), 16)), params));
    }

    /** 读取文档章节，供前端多选 */
    @PostMapping("/chapters")
    public ApiResponse<Map<String, Object>> chapters(@RequestParam Long documentId) {
        DocumentEntity doc = documentService.get(UserContext.userId(), documentId);
        List<WordParser.SectionContent> sections = generationService.readSections(
                doc.getStoragePath(), doc.getOriginalName().toLowerCase(), 0, 0);
        List<String> titles = new ArrayList<>();
        for (WordParser.SectionContent s : sections) titles.add(s.getTitle());
        return ApiResponse.ok(Map.of("chapters", titles));
    }

    private DocumentEntity resolveDocument(MultipartFile file, Long documentId, Long projectId) {
        if (documentId != null) return documentService.get(UserContext.userId(), documentId);
        if (file == null || file.isEmpty()) {
            throw new com.example.demo.common.ApiException("请上传文档或选择已上传的文档");
        }
        return documentService.save(UserContext.userId(), projectId, file);
    }

    private Map<String, Object> commonParams(Long projectId, String readRangeType, int startPage,
                                             int endPage, String outputFormat) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("projectId", str(projectId));
        params.put("readRangeType", readRangeType);
        params.put("startPage", String.valueOf(startPage));
        params.put("endPage", String.valueOf(endPage));
        params.put("outputFormat", outputFormat);
        return params;
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
