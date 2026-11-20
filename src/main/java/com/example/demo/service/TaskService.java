package com.example.demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.TestCase;
import com.example.demo.TestCaseTemplate;
import com.example.demo.common.ApiException;
import com.example.demo.common.CryptoUtil;
import com.example.demo.entity.CaseBatchEntity;
import com.example.demo.entity.CaseItemEntity;
import com.example.demo.entity.DocumentEntity;
import com.example.demo.entity.GenerationTaskEntity;
import com.example.demo.entity.UserAiConfigEntity;
import com.example.demo.mapper.CaseBatchMapper;
import com.example.demo.mapper.CaseItemMapper;
import com.example.demo.mapper.GenerationTaskMapper;
import com.example.demo.mapper.UserAiConfigMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 生成任务：落库、异步执行、进度更新、结果持久化 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    public static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final GenerationTaskMapper taskMapper;
    private final CaseBatchMapper batchMapper;
    private final CaseItemMapper itemMapper;
    private final DocumentService documentService;
    private final ProjectService projectService;
    private final StorageService storageService;
    private final GenerationService generationService;
    private final UserAiConfigMapper aiConfigMapper;
    private final ObjectMapper objectMapper;
    private final ThreadPoolTaskExecutor generationExecutor;

    public GenerationTaskEntity submit(Long userId, Long projectId, Long documentId,
                                       String type, String title, Map<String, Object> params) {
        GenerationTaskEntity task = new GenerationTaskEntity();
        task.setUserId(userId);
        task.setProjectId(projectId);
        task.setDocumentId(documentId);
        task.setTaskType(type);
        task.setTitle(title);
        task.setStatus("PENDING");
        task.setProgress(0);
        task.setStage("排队中");
        task.setModel(AiService.getCurrentModelName());
        task.setDurationMs(0L);
        task.setCreatedAt(LocalDateTime.now());
        try {
            task.setParamsJson(objectMapper.writeValueAsString(params));
        } catch (Exception e) {
            task.setParamsJson("{}");
        }
        taskMapper.insert(task);
        Long taskId = task.getId();
        generationExecutor.execute(() -> run(taskId));
        return task;
    }

    public List<GenerationTaskEntity> list(Long userId, Long projectId, String status, int limit) {
        LambdaQueryWrapper<GenerationTaskEntity> q = new LambdaQueryWrapper<GenerationTaskEntity>()
                .eq(GenerationTaskEntity::getUserId, userId)
                .orderByDesc(GenerationTaskEntity::getId);
        if (projectId != null) q.eq(GenerationTaskEntity::getProjectId, projectId);
        if (status != null && !status.isBlank()) q.eq(GenerationTaskEntity::getStatus, status);
        q.last("LIMIT " + Math.max(1, Math.min(limit, 200)));
        return taskMapper.selectList(q);
    }

    public GenerationTaskEntity get(Long userId, Long id) {
        GenerationTaskEntity task = taskMapper.selectById(id);
        if (task == null || !task.getUserId().equals(userId)) throw new ApiException(404, "任务不存在");
        return task;
    }

    public void delete(Long userId, Long id) {
        get(userId, id);
        taskMapper.deleteById(id);
    }

    // ==================== 异步执行 ====================
    private void run(Long taskId) {
        GenerationTaskEntity task = taskMapper.selectById(taskId);
        if (task == null) return;
        long start = System.currentTimeMillis();
        task.setStatus("RUNNING");
        task.setProgress(3);
        task.setStage("开始生成");
        taskMapper.updateById(task);

        GenerationService.Progress progress = (percent, stage) -> {
            task.setProgress(percent);
            task.setStage(stage);
            taskMapper.updateById(task);
        };

        try {
            Map<String, String> p = readParams(task.getParamsJson());
            DocumentEntity doc = task.getDocumentId() == null ? null
                    : documentService.get(task.getUserId(), task.getDocumentId());
            applyUserModel(task.getUserId());

            GenerationService.GenResult result;
            switch (task.getTaskType()) {
                case "REQUIREMENT_UI" -> result = generationService.requirement(doc,
                        p.getOrDefault("readRangeType", "all"),
                        num(p.get("startPage"), 1), num(p.get("endPage"), 50),
                        p.get("chapterTitles"), p.get("keywords"), p.get("customTemplate"),
                        p.get("creator"), p.get("relatedRequirement"), progress);
                case "API_CASE" -> result = generationService.apiCases(doc,
                        p.getOrDefault("readRangeType", "all"),
                        num(p.get("startPage"), 1), num(p.get("endPage"), 50), progress);
                case "API_SCRIPT" -> result = generationService.script(doc,
                        p.getOrDefault("baseUrl", "http://localhost:8080"),
                        p.getOrDefault("language", "java"),
                        p.getOrDefault("readRangeType", "all"),
                        num(p.get("startPage"), 1), num(p.get("endPage"), 50), progress);
                case "TEXT" -> result = generationService.text(p.get("content"),
                        p.getOrDefault("type", "interface"),
                        num(p.get("generateCount"), 10),
                        p.get("customTemplate"), p.get("creator"), p.get("relatedRequirement"), progress);
                default -> throw new ApiException("未知任务类型: " + task.getTaskType());
            }

            progress.report(92, "保存生成结果");
            CaseBatchEntity batch = saveBatch(task, doc, result);
            task.setBatchId(batch.getId());
            task.setStatus("SUCCESS");
            task.setProgress(100);
            task.setStage("已完成");
            task.setMessage("生成 " + result.cases().size() + " 条用例");
            task.setModel(AiService.getCurrentModelName());
        } catch (Exception e) {
            log.error("任务 {} 执行失败", taskId, e);
            task.setStatus("FAILED");
            task.setStage("生成失败");
            task.setMessage(e instanceof ApiException ? e.getMessage()
                    : "生成失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        } finally {
            task.setDurationMs(System.currentTimeMillis() - start);
            task.setFinishedAt(LocalDateTime.now());
            taskMapper.updateById(task);
        }
    }

    private CaseBatchEntity saveBatch(GenerationTaskEntity task, DocumentEntity doc,
                                      GenerationService.GenResult result) throws Exception {
        String baseName = doc != null
                ? doc.getOriginalName().replaceAll("\\.[^.]+$", "")
                : "自然语言用例";
        String caseType = resolveCaseType(task, result.template());
        String suffix = "API_SCRIPT".equals(task.getTaskType()) ? "自动化脚本"
                : "API".equals(caseType) ? "接口用例" : "UI用例";

        String stamp = LocalDateTime.now().format(STAMP);
        String ext = resolveExt(task);
        byte[] data = result.scriptZip() != null ? result.scriptZip()
                : ExcelExporter.exportByFormat(result.cases(), result.template(), ext);
        String fileName = baseName + "-" + suffix + "-" + stamp + "." + ext;
        Path saved = storageService.saveBytes(task.getUserId(), "files", fileName, data);

        CaseBatchEntity batch = new CaseBatchEntity();
        batch.setUserId(task.getUserId());
        batch.setProjectId(task.getProjectId());
        batch.setDocumentId(task.getDocumentId());
        batch.setTaskId(task.getId());
        batch.setName(baseName + " · " + suffix);
        batch.setCaseType(caseType);
        batch.setColumnsJson(columnsJson(result.template()));
        batch.setCaseCount(result.cases().size());
        batch.setOutputFormat(ext);
        batch.setFilePath(saved.toString());
        if (result.scriptZip() != null) {
            batch.setScriptLanguage(readParams(task.getParamsJson()).getOrDefault("language", "java"));
            batch.setScriptPath(saved.toString());
        }
        batch.setCreatedAt(LocalDateTime.now());
        batchMapper.insert(batch);
        task.setBatchId(batch.getId());

        int index = 1;
        for (TestCase tc : result.cases()) {
            CaseItemEntity item = new CaseItemEntity();
            item.setBatchId(batch.getId());
            item.setUserId(task.getUserId());
            String no = tc.getId();
            item.setCaseNo(no == null || no.isBlank() ? String.format("TC-%04d", index) : no);
            item.setTitle(firstNonEmpty(tc.getField("title"), "未命名用例"));
            item.setModule(firstNonEmpty(tc.getField("parentModule"), tc.getField("testModule"), ""));
            item.setCategory(firstNonEmpty(tc.getField("testCategory"), tc.getField("testPoint"), ""));
            item.setPriority(firstNonEmpty(tc.getField("priority"), "P1"));
            item.setReviewStatus("PENDING");
            item.setDataJson(objectMapper.writeValueAsString(tc.getDynamicFields()));
            item.setSortNo(index++);
            itemMapper.insert(item);
        }
        projectService.touch(task.getUserId(), task.getProjectId());
        return batch;
    }

    private String resolveCaseType(GenerationTaskEntity task, TestCaseTemplate tpl) {
        if ("REQUIREMENT_UI".equals(task.getTaskType())) return "UI";
        if ("API_CASE".equals(task.getTaskType()) || "API_SCRIPT".equals(task.getTaskType())) return "API";
        boolean ui = tpl.getFields().stream().anyMatch(f -> "testCategory".equals(f.getFieldName()));
        return ui ? "UI" : "API";
    }

    private String resolveExt(GenerationTaskEntity task) {
        Map<String, String> p = readParams(task.getParamsJson());
        if ("API_SCRIPT".equals(task.getTaskType())) return "zip";
        String format = p.getOrDefault("outputFormat", "xlsx");
        return switch (format.toLowerCase()) {
            case "csv" -> "csv";
            case "json" -> "json";
            case "md", "markdown" -> "md";
            default -> "xlsx";
        };
    }

    private String columnsJson(TestCaseTemplate tpl) throws Exception {
        List<Map<String, String>> cols = new ArrayList<>();
        for (TestCaseTemplate.TemplateField f : tpl.getFields()) {
            Map<String, String> col = new LinkedHashMap<>();
            col.put("fieldName", f.getFieldName());
            col.put("displayName", f.getDisplayName());
            cols.add(col);
        }
        return objectMapper.writeValueAsString(cols);
    }

    private void applyUserModel(Long userId) {
        UserAiConfigEntity config = aiConfigMapper.selectOne(new LambdaQueryWrapper<UserAiConfigEntity>()
                .eq(UserAiConfigEntity::getUserId, userId));
        if (config == null) return;
        String key = CryptoUtil.decrypt(config.getApiKey());
        if (key != null && !key.isEmpty()) {
            boolean ok = AiService.switchModel(config.getModelName(), key);
            if (!ok) log.warn("用户 {} 的模型配置不可用，回退全局模型", userId);
        }
    }

    private Map<String, String> readParams(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private int num(String v, int def) {
        try {
            return v == null ? def : Integer.parseInt(v.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private String firstNonEmpty(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return "";
    }
}
