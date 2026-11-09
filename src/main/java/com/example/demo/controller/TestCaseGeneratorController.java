package com.example.demo.controller;

import com.example.demo.DocumentProcessor;
import com.example.demo.SwaggerParser;
import com.example.demo.TestCase;
import com.example.demo.dto.ErrorResponse;
import com.example.demo.service.*;
import com.example.demo.util.DownloadHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.net.URI;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api")
public class TestCaseGeneratorController {

    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String TMP = System.getProperty("java.io.tmpdir");

    @GetMapping("/")
    public ResponseEntity<Void> index() {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create("/index.html")).build();
    }

    // ================================================================
    //  功能1：需求文档 → UI测试用例 + 业务流程分析
    // ================================================================
    @PostMapping("/generate-ui")
    public ResponseEntity<?> generateUI(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "readRangeType", defaultValue = "all") String readRangeType,
            @RequestParam(value = "startPage", defaultValue = "1") int startPage,
            @RequestParam(value = "endPage", defaultValue = "50") int endPage,
            @RequestParam(value = "chapterTitles", required = false) String chapterTitles,
            @RequestParam(value = "keywords", required = false) String keywords,
            @RequestParam(value = "customTemplate", required = false) String customTemplate,
            @RequestParam(value = "creator", required = false) String creator,
            @RequestParam(value = "relatedRequirement", required = false) String relatedRequirement,
            @RequestParam(value = "outputFormat", defaultValue = "xlsx") String outputFormat,
            @RequestParam(value = "outputPath", required = false) String outputPath) {

        try {
            initAi();
            if (AiService.getModel() == null) return err("AI模型未初始化", "请配置 DEEPSEEK_API_KEY / DASHSCOPE_API_KEY / DOUBAO_API_KEY / GLM_API_KEY / OPENAI_API_KEY / AI_API_KEY 环境变量");

            String fp = saveTempFile(file);
            String fname = file.getOriginalFilename().toLowerCase();

            // 章节模式：通过章节标题选择
            List<WordParser.SectionContent> sections;
            if ("chapter".equals(readRangeType) && chapterTitles != null && !chapterTitles.isEmpty()) {
                List<String> selected = new ArrayList<>(Arrays.asList(chapterTitles.split(",")));
                sections = readDocSections(fp, fname, 0, 0);
                sections.removeIf(s -> !selected.stream().anyMatch(t ->
                        s.getTitle().contains(t.trim()) || t.trim().contains(s.getTitle())));
                if (sections.isEmpty()) {
                    List<String> actual = new ArrayList<>();
                    for (WordParser.SectionContent s : readDocSections(fp, fname, 0, 0)) actual.add(s.getTitle());
                    return err("指定章节不存在", "文档实际章节：\n" + String.join("\n", actual));
                }
            } else if ("keyword".equals(readRangeType) && keywords != null && !keywords.isEmpty()) {
                // 关键词模式：只保留包含关键词的段落
                List<String> kws = new ArrayList<>(Arrays.asList(keywords.split("[,，]")));
                sections = readDocSections(fp, fname, 0, 0);
                sections.removeIf(s -> kws.stream().noneMatch(k ->
                        s.getContent().contains(k.trim()) || s.getTitle().contains(k.trim())));
                if (sections.isEmpty()) return err("未找到匹配内容", "未找到包含指定关键词的内容，请扩大关键词范围");
            } else {
                // 全部/页码模式
                sections = readDocSections(fp, fname,
                    "page".equals(readRangeType) ? startPage : 0,
                    "page".equals(readRangeType) ? endPage : 0);
            }

            // 业务流程摘要
            StringBuilder flowSummary = new StringBuilder();
            if (!sections.isEmpty()) {
                for (int i = 0; i < Math.min(sections.size(), 10); i++) {
                    flowSummary.append(i + 1).append(". ").append(sections.get(i).getTitle()).append("\n");
                }
            }

            AiService.resetCounter();
            AiService.setCustomTemplate(customTemplate);
            List<TestCase> allTestCases = AiService.generateTestCasesFromRequirementBySections(sections);

            if (allTestCases.isEmpty()) return err("未生成测试用例", "请检查文档内容是否包含可测试的功能描述");

            // 选择模板
            com.example.demo.TestCaseTemplate tpl = AiService.getCurrentTemplate();
            if (tpl == null) tpl = com.example.demo.TestCaseTemplate.createUITemplate();
            // 转置标准字段到模板 + 用户字段 + 默认值 + 去重
            allTestCases = applyTemplateToCases(allTestCases, tpl, creator, relatedRequirement);
            if (allTestCases.isEmpty()) return err("未生成测试用例", "请检查文档内容是否包含可测试的功能描述");

            // 按格式导出（xlsx/csv/json/markdown）
            byte[] data = ExcelExporter.exportByFormat(allTestCases, tpl, outputFormat);

            Files.walk(Paths.get(fp).getParent()).sorted(java.util.Comparator.reverseOrder())
                    .map(Path::toFile).forEach(File::delete);

            String ext = "xlsx";
            if ("csv".equalsIgnoreCase(outputFormat)) ext = "csv";
            else if ("json".equalsIgnoreCase(outputFormat)) ext = "json";
            else if ("md".equalsIgnoreCase(outputFormat) || "markdown".equalsIgnoreCase(outputFormat)) ext = "md";
            String outFile = DownloadHelper.toAscii("testcases_" + LocalDateTime.now().format(DF) + "." + ext);

            if (outputPath != null && !outputPath.trim().isEmpty()) {
                Path p = Paths.get(outputPath);
                if (Files.isDirectory(p)) p = p.resolve(outFile);
                else if (!outputPath.toLowerCase().endsWith("." + ext)) p = Paths.get(outputPath + "." + ext);
                Files.createDirectories(p.getParent());
                Files.write(p, data);
                Map<String, Object> r = Map.of("message", "测试用例已生成", "path", p.toString(),
                        "count", allTestCases.size(), "flowSummary", flowSummary.toString());
                return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(r);
            }

            MediaType mt = "xlsx".equals(ext) ? MediaType.APPLICATION_OCTET_STREAM
                    : "csv".equals(ext) ? MediaType.parseMediaType("text/csv")
                    : "json".equals(ext) ? MediaType.APPLICATION_JSON
                    : MediaType.parseMediaType("text/markdown");
            return DownloadHelper.fileDownload(data, outFile, mt);

        } catch (IOException e) {
            return err500("文件操作失败", e.getMessage());
        } catch (Exception e) {
            log.error("生成UI用例失败", e);
            return err500("生成失败", e.getMessage());
        }
    }

    /**
     * 获取需求文档的章节列表（前端下拉选择用）
     */
    @PostMapping("/chapters")
    public ResponseEntity<?> getChapters(@RequestParam("file") MultipartFile file) {
        try {
            String fp = saveTempFile(file);
            String fn = file.getOriginalFilename().toLowerCase();
            List<WordParser.SectionContent> sections = readDocSections(fp, fn, 0, 0);
            List<String> titles = new ArrayList<>();
            for (WordParser.SectionContent s : sections) titles.add(s.getTitle());
            Files.walk(Paths.get(fp).getParent()).sorted(java.util.Comparator.reverseOrder())
                    .map(Path::toFile).forEach(File::delete);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("chapters", titles));
        } catch (Exception e) {
            return err500("读取章节失败", e.getMessage());
        }
    }

    // ================================================================
    //  功能2：接口文档 → 自动化测试脚本 (Java + Python)
    // ================================================================
    @PostMapping("/generate-script")
    public ResponseEntity<?> generateScript(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "baseUrl", defaultValue = "http://localhost:8080") String baseUrl,
            @RequestParam(value = "language", defaultValue = "java") String language,
            @RequestParam(value = "readRangeType", defaultValue = "all") String readRangeType,
            @RequestParam(value = "startPage", defaultValue = "1") int startPage,
            @RequestParam(value = "endPage", defaultValue = "50") int endPage,
            @RequestParam(value = "outputPath", required = false) String outputPath) {

        try {
            initAi();
            if (AiService.getModel() == null) return err("AI模型未初始化", "请配置 API Key");

            String fp = saveTempFile(file);
            List<String> interfaces = extractInterfaces(fp, file.getOriginalFilename(), readRangeType, startPage, endPage);
            if (interfaces.isEmpty()) return err("未提取到接口", "文档中未找到API定义");

            // 接口用例数量控制：每个接口最多 8 条（等价类/边界值/场景法合并，宁少勿滥）
            AiService.resetCounter();
            AiService.setApiCaseCap(8);
            List<TestCase> allTestCases = new ArrayList<>();
            int totalIfaces = interfaces.size();
            for (int i = 0; i < totalIfaces; i++) {
                String api = interfaces.get(i);
                log.info("生成接口用例 [{}/{}]: {}", i + 1, totalIfaces, firstLine(api));
                List<TestCase> cases = AiService.generateTestCases(api);
                log.info("接口 [{}/{}] 生成 {} 条用例，累计 {} 条", i + 1, totalIfaces, cases.size(), allTestCases.size() + cases.size());
                allTestCases.addAll(cases);
            }
            if (allTestCases.isEmpty()) return err("未生成测试用例", "AI 未能根据接口文档生成测试用例");
            // 整份文档总量上限裁剪（优先保留 P0/P1）
            allTestCases = AiService.enforceApiTotalBudget(allTestCases);

            // 接口测试用例：转置到 24 列 API 模板 + 全字段去重（自动化脚本严格基于这些用例生成）
            com.example.demo.TestCaseTemplate tpl = com.example.demo.TestCaseTemplate.createAPITemplate();
            allTestCases = applyTemplateToCases(allTestCases, tpl, null, null);
            byte[] caseExcel = ExcelExporter.exportByFormat(allTestCases, tpl, "xlsx");

            byte[] zipBytes = "python".equalsIgnoreCase(language)
                    ? ScriptGenerator.generatePythonScriptAsZip(allTestCases, baseUrl, "接口测试用例.xlsx", caseExcel)
                    : ScriptGenerator.generateScriptAsZip(allTestCases, baseUrl, "接口测试用例.xlsx", caseExcel);

            String fname = "api-automation-" + LocalDateTime.now().format(DF) + ".zip";

            if (outputPath != null && !outputPath.trim().isEmpty()) {
                Path p = Paths.get(outputPath);
                if (Files.isDirectory(p)) p = p.resolve(fname);
                else if (!outputPath.toLowerCase().endsWith(".zip")) p = Paths.get(outputPath + ".zip");
                Files.createDirectories(p.getParent());
                Files.write(p, zipBytes);
                return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("message", "自动化脚本已生成（含接口测试用例 " + allTestCases.size() + " 条）",
                                "path", p.toString(), "count", allTestCases.size()));
            }

            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            h.set(HttpHeaders.CONTENT_DISPOSITION, DownloadHelper.buildContentDisposition(fname));
            h.setContentLength(zipBytes.length);
            return ResponseEntity.ok().headers(h).body(zipBytes);

        } catch (IOException e) {
            return err500("文件操作失败", e.getMessage());
        } catch (Exception e) {
            log.error("生成脚本失败", e);
            return err500("生成失败", e.getMessage());
        }
    }

    // ================================================================
    //  功能2.1：接口文档 → 接口测试用例（只导用例，不生成脚本）
    // ================================================================
    @PostMapping("/generate-api")
    public ResponseEntity<?> generateApi(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "readRangeType", defaultValue = "all") String readRangeType,
            @RequestParam(value = "startPage", defaultValue = "1") int startPage,
            @RequestParam(value = "endPage", defaultValue = "50") int endPage,
            @RequestParam(value = "outputFormat", defaultValue = "xlsx") String outputFormat,
            @RequestParam(value = "outputPath", required = false) String outputPath) {

        try {
            initAi();
            if (AiService.getModel() == null) return err("AI模型未初始化", "请配置 API Key");

            String fp = saveTempFile(file);
            List<String> interfaces = extractInterfaces(fp, file.getOriginalFilename(), readRangeType, startPage, endPage);
            if (interfaces.isEmpty()) return err("未提取到接口", "文档中未找到API定义");

            // 接口用例数量控制：每个接口最多 8 条（等价类/边界值/场景法合并，宁少勿滥）
            AiService.resetCounter();
            AiService.setApiCaseCap(8);
            List<TestCase> allTestCases = new ArrayList<>();
            int totalIfaces = interfaces.size();
            for (int i = 0; i < totalIfaces; i++) {
                String api = interfaces.get(i);
                log.info("生成接口用例 [{}/{}]: {}", i + 1, totalIfaces, firstLine(api));
                List<TestCase> cases = AiService.generateTestCases(api);
                log.info("接口 [{}/{}] 生成 {} 条用例，累计 {} 条", i + 1, totalIfaces, cases.size(), allTestCases.size() + cases.size());
                allTestCases.addAll(cases);
            }
            if (allTestCases.isEmpty()) return err("未生成测试用例", "AI 未能根据接口文档生成测试用例");
            // 整份文档总量上限裁剪（优先保留 P0/P1）
            allTestCases = AiService.enforceApiTotalBudget(allTestCases);

            // 接口测试用例：转置到 24 列 API 模板 + 全字段去重 + 数字字段规范化
            com.example.demo.TestCaseTemplate tpl = com.example.demo.TestCaseTemplate.createAPITemplate();
            allTestCases = applyTemplateToCases(allTestCases, tpl, null, null);
            if (allTestCases.isEmpty()) return err("未生成测试用例", "AI 未能根据接口文档生成测试用例");

            byte[] data = ExcelExporter.exportByFormat(allTestCases, tpl, outputFormat);

            Files.walk(Paths.get(fp).getParent()).sorted(java.util.Comparator.reverseOrder())
                    .map(Path::toFile).forEach(File::delete);

            String ext = "xlsx";
            if ("csv".equalsIgnoreCase(outputFormat)) ext = "csv";
            else if ("json".equalsIgnoreCase(outputFormat)) ext = "json";
            else if ("md".equalsIgnoreCase(outputFormat) || "markdown".equalsIgnoreCase(outputFormat)) ext = "md";
            String fname = DownloadHelper.toAscii("api_testcases_" + LocalDateTime.now().format(DF) + "." + ext);

            if (outputPath != null && !outputPath.trim().isEmpty()) {
                Path p = Paths.get(outputPath);
                if (Files.isDirectory(p)) p = p.resolve(fname);
                else if (!outputPath.toLowerCase().endsWith("." + ext)) p = Paths.get(outputPath + "." + ext);
                Files.createDirectories(p.getParent());
                Files.write(p, data);
                return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("message", "接口测试用例已生成", "path", p.toString(), "count", allTestCases.size()));
            }

            MediaType mt = "xlsx".equals(ext) ? MediaType.APPLICATION_OCTET_STREAM
                    : "csv".equals(ext) ? MediaType.parseMediaType("text/csv")
                    : "json".equals(ext) ? MediaType.APPLICATION_JSON
                    : MediaType.parseMediaType("text/markdown");
            return DownloadHelper.fileDownload(data, fname, mt);

        } catch (IOException e) {
            return err500("文件操作失败", e.getMessage());
        } catch (Exception e) {
            log.error("生成接口用例失败", e);
            return err500("生成失败", e.getMessage());
        }
    }

    // ================================================================
    //  功能3：自然语言 → 测试用例
    // ================================================================
    @PostMapping("/generate-text")
    public ResponseEntity<?> generateText(
            @RequestParam("content") String content,
            @RequestParam(value = "type", defaultValue = "interface") String type,
            @RequestParam(value = "generateCount", defaultValue = "10") int generateCount,
            @RequestParam(value = "customTemplate", required = false) String customTemplate,
            @RequestParam(value = "creator", required = false) String creator,
            @RequestParam(value = "relatedRequirement", required = false) String relatedRequirement,
            @RequestParam(value = "outputFormat", defaultValue = "xlsx") String outputFormat,
            @RequestParam(value = "outputPath", required = false) String outputPath) {

        try {
            initAi();
            if (AiService.getModel() == null) return err("AI模型未初始化", "请配置 API Key");

            // 功能描述过简提示
            if (content == null || content.trim().length() < 20) {
                return err("功能描述过于简略", "请补充输入参数、约束条件或预期行为（至少20字）");
            }

            AiService.resetCounter();
            AiService.setCustomTemplate(customTemplate);
            AiService.setGenerateCount(generateCount);

            List<TestCase> allTestCases;
            if ("requirement".equals(type)) {
                WordParser.SectionContent sc = new WordParser.SectionContent("自然语言需求", content);
                allTestCases = AiService.generateTestCasesFromRequirementBySections(List.of(sc));
            } else {
                allTestCases = AiService.generateTestCases(content);
            }

            if (allTestCases.isEmpty()) return err("未生成用例", "AI未能生成测试用例，请检查输入内容");

            // 选择模板
            com.example.demo.TestCaseTemplate tpl = AiService.getCurrentTemplate();
            if (tpl == null) tpl = "requirement".equals(type)
                    ? com.example.demo.TestCaseTemplate.createUITemplate()
                    : com.example.demo.TestCaseTemplate.createAPITemplate();
            // 转置标准字段到模板 + 用户字段 + 默认值 + 去重
            allTestCases = applyTemplateToCases(allTestCases, tpl, creator, relatedRequirement);
            if (allTestCases.isEmpty()) return err("未生成用例", "AI未能生成测试用例");

            byte[] data = ExcelExporter.exportByFormat(allTestCases, tpl, outputFormat);

            String ext = "xlsx";
            if ("csv".equalsIgnoreCase(outputFormat)) ext = "csv";
            else if ("json".equalsIgnoreCase(outputFormat)) ext = "json";
            else if ("md".equalsIgnoreCase(outputFormat) || "markdown".equalsIgnoreCase(outputFormat)) ext = "md";
            String fname = DownloadHelper.toAscii("testcases_" + LocalDateTime.now().format(DF) + "." + ext);

            if (outputPath != null && !outputPath.trim().isEmpty()) {
                Path p = Paths.get(outputPath);
                if (Files.isDirectory(p)) p = p.resolve(fname);
                else if (!outputPath.toLowerCase().endsWith("." + ext)) p = Paths.get(outputPath + "." + ext);
                Files.createDirectories(p.getParent());
                Files.write(p, data);
                return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("message", "测试用例已生成", "path", p.toString(), "count", allTestCases.size()));
            }

            MediaType mt = "xlsx".equals(ext) ? MediaType.APPLICATION_OCTET_STREAM
                    : "csv".equals(ext) ? MediaType.parseMediaType("text/csv")
                    : "json".equals(ext) ? MediaType.APPLICATION_JSON
                    : MediaType.parseMediaType("text/markdown");
            return DownloadHelper.fileDownload(data, fname, mt);

        } catch (IOException e) {
            return err500("文件操作失败", e.getMessage());
        } catch (Exception e) {
            log.error("自然语言生成失败", e);
            return err500("生成失败", e.getMessage());
        }
    }

    // 旧端点兼容
    @PostMapping("/generate") public ResponseEntity<?> generate0(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "readRangeType", defaultValue = "all") String rt,
            @RequestParam(value = "startPage", defaultValue = "1") int sp,
            @RequestParam(value = "endPage", defaultValue = "50") int ep,
            @RequestParam(value = "customTemplate", required = false) String ct,
            @RequestParam(value = "outputPath", required = false) String op) {
        return generateUI(file, rt, sp, ep, null, null, ct, null, null, "xlsx", op);
    }

    @PostMapping("/generate-from-text") public ResponseEntity<?> generateText0(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "interfaceDoc", required = false) String doc,
            @RequestParam(value = "docType", defaultValue = "interface") String type,
            @RequestParam(value = "customTemplate", required = false) String ct,
            @RequestParam(value = "outputPath", required = false) String op,
            @RequestParam(value = "readRangeType", defaultValue = "all") String rt,
            @RequestParam(value = "startPage", defaultValue = "1") int sp,
            @RequestParam(value = "endPage", defaultValue = "50") int ep) {
        if (file != null && !file.isEmpty()) return generateUI(file, rt, sp, ep, null, null, ct, null, null, "xlsx", op);
        String content = doc != null ? doc : "";
        return generateText(content, type, 10, ct, null, null, "xlsx", op);
    }

    @PostMapping("/generate-from-url") public ResponseEntity<?> generateFromUrl(@RequestParam("url") String url) {
        try {
            initAi();
            if (AiService.getModel() == null) return err("AI模型未初始化", "请配置 API Key");
            List<String> interfaces = SwaggerParser.parseSwagger(url);
            if (interfaces.isEmpty()) return err("未提取到接口", "无法从URL解析出API");
            AiService.resetCounter();
            List<TestCase> all = new ArrayList<>();
            for (String api : interfaces) all.addAll(AiService.generateTestCases(api));
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ExcelExporter.exportToExcel(all, bos);
            String fn = DownloadHelper.toAscii("TestCase_" + LocalDateTime.now().format(DF) + ".xlsx");
            return DownloadHelper.fileDownload(bos.toByteArray(), fn, MediaType.APPLICATION_OCTET_STREAM);
        } catch (Exception e) { return err500("URL生成失败", e.getMessage()); }
    }

    // ================================================================
    //  模块5：多模型列表 + 切换
    // ================================================================
    @GetMapping("/models")
    public ResponseEntity<?> listModels() {
        List<Map<String, String>> models = new ArrayList<>();
        models.add(Map.of("id", "deepseek-chat", "name", "DeepSeek Chat", "multimodal", "false", "desc", "性价比高，默认"));
        models.add(Map.of("id", "qwen-vl-max", "name", "通义千问 Qwen-VL", "multimodal", "true", "desc", "支持图像理解"));
        models.add(Map.of("id", "doubao-seed-1-6-vision-250815", "name", "豆包 Vision（doubao-seed-1-6-vision）", "multimodal", "true", "desc", "视觉理解"));
        models.add(Map.of("id", "gpt-4o", "name", "OpenAI GPT-4o", "multimodal", "true", "desc", "多模态旗舰"));
        models.add(Map.of("id", "glm-4v", "name", "智谱 GLM-4V", "multimodal", "true", "desc", "视觉理解"));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("models", models, "current", AiService.getCurrentModelName()));
    }

    @PostMapping("/switch-model")
    public ResponseEntity<?> switchModel(
            @RequestParam("model") String model,
            @RequestParam(value = "apiKey", required = false) String apiKey) {
        boolean ok = AiService.switchModel(model, apiKey);
        if (!ok) return err("模型切换失败", "请检查模型标识和API Key");
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("message", "已切换到 " + model, "current", AiService.getCurrentModelName()));
    }

    // ================================================================
    //  模块4：模板保存 / 列表 / 调用
    // ================================================================
    @PostMapping("/template/save")
    public ResponseEntity<?> saveTemplate(
            @RequestParam("name") String name,
            @RequestParam("columns") String columns) {
        try {
            TemplateManager.save(name, columns);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("message", "模板已保存", "name", name));
        } catch (IllegalArgumentException e) {
            return err("保存失败", e.getMessage());
        }
    }

    @GetMapping("/template/list")
    public ResponseEntity<?> listTemplates() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("templates", TemplateManager.listNames()));
    }

    // helpers
    private void initAi() { DocumentProcessor.initModel(); }
    private String saveTempFile(MultipartFile file) throws IOException {
        String dir = TMP + File.separator + "cece_" + System.currentTimeMillis();
        Files.createDirectories(Paths.get(dir));
        String fp = dir + File.separator + file.getOriginalFilename();
        file.transferTo(new File(fp));
        return fp;
    }
    /** 取接口块首行（方法 + 路径），用于进度日志展示 */
    private String firstLine(String s) {
        if (s == null || s.isEmpty()) return "";
        for (String l : s.split("\n")) {
            if (!l.trim().isEmpty()) return l.trim();
        }
        return "";
    }
    private List<String> extractInterfaces(String fp, String name, String range, int sp, int ep) {
        name = name.toLowerCase();
        if (name.endsWith(".docx")) return "page".equals(range) ? DocumentProcessor.extractFromDocx(fp, sp, ep) : DocumentProcessor.extractFromDocx(fp);
        if (name.endsWith(".pdf")) {
            String c = "page".equals(range) ? PdfParser.readPdf(fp, sp, ep) : PdfParser.readPdf(fp);
            return PdfParser.extractInterfaces(c);
        }
        if (name.endsWith(".json") || name.endsWith(".yaml") || name.endsWith(".yml")) return SwaggerParser.parseSwagger(fp);
        if (name.endsWith(".md") || name.endsWith(".txt")) {
            try {
                String text = new String(Files.readAllBytes(Paths.get(fp)), java.nio.charset.StandardCharsets.UTF_8);
                return com.example.demo.service.PdfParser.extractInterfaces(text);
            } catch (Exception e) { return Collections.emptyList(); }
        }
        return Collections.emptyList();
    }
    private ResponseEntity<?> err(String m, String d) { return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(ErrorResponse.badRequest(m, d)); }
    private ResponseEntity<?> err500(String m, String d) { return ResponseEntity.status(500).contentType(MediaType.APPLICATION_JSON).body(ErrorResponse.serverError(m, d)); }

    /**
     * 将标准字段转置到自定义模板字段（按名称映射，跨格式兼容）
     * 处理用户自定义字段(创建人/关联需求) + 默认值(自动化/类型) + 全字段去重
     */
    private List<TestCase> applyTemplateToCases(List<TestCase> cases, com.example.demo.TestCaseTemplate tpl,
            String creator, String relatedRequirement) {
        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
        List<TestCase> result = new ArrayList<>();
        for (TestCase tc : cases) {
            Map<String, String> std = new HashMap<>(tc.getDynamicFields());
            Map<String, String> transposed = new java.util.LinkedHashMap<>();
            for (com.example.demo.TestCaseTemplate.TemplateField f : tpl.getFields()) {
                String key = f.getFieldName();
                String val = std.get(key);
                // 用例编号存在独立字段（setId 不写入 dynamicFields），转置时需显式带入
                if (val == null && "id".equals(key)) val = tc.getId();
                // 别名匹配（如 API 的 expectedResponseJson → 预期结果）
                if (val == null || val.isEmpty()) {
                    val = findStandardValue(std, f.getDisplayName());
                }
                transposed.put(key, val == null ? "" : val);
            }
            tc.setDynamicFields(transposed);
            // 数字字段规范化：预期状态码/业务码只保留数字，避免自然语言描述混入导致脚本生成崩溃
            normalizeNumericField(tc, "expectedStatusCode", true);
            normalizeNumericField(tc, "expectedBusinessCode", false);
            // 创建人 / 关联需求：仅用用户定义值，否则清空（不采用AI猜测）
            tc.setField("creator", (creator != null && !creator.isEmpty()) ? creator : "");
            tc.setField("relatedRequirement", (relatedRequirement != null && !relatedRequirement.isEmpty()) ? relatedRequirement : "");
            // 自动化：默认不填（强制清空）；类型：默认功能测试（强制覆盖）
            tc.setField("isAutomated", "");
            tc.setField("caseType", "功能测试");
            // 全字段指纹去重
            StringBuilder fingerprint = new StringBuilder();
            for (Map.Entry<String, String> e : tc.getDynamicFields().entrySet()) {
                if ("id".equals(e.getKey()) || "customRowData".equals(e.getKey())) continue;
                fingerprint.append(e.getKey()).append("=")
                          .append(e.getValue() == null ? "" : e.getValue().replaceAll("【[^】]*】", "").replaceAll("\\s+", "")).append("|");
            }
            if (seen.add(fingerprint.toString())) result.add(tc);
        }
        if (!result.isEmpty() && log.isDebugEnabled()) {
            log.debug("转置后首条用例字段: {}", result.get(0).getDynamicFields());
        }
        return result;
    }

    private String findStandardValue(Map<String, String> std, String displayName) {
        if (displayName == null) return null;
        // 预期状态码/业务码是数字字段，不可用整段自然语言预期结果填充（否则脚本生成 parseInt 会崩溃）
        if (displayName.contains("状态码") || displayName.contains("业务码")) return null;
        if (displayName.contains("预期结果") || displayName.contains("预期")) {
            String v = std.get("expectedResult");
            if (v == null || v.isEmpty()) v = std.get("expectedResponseJson");
            return v;
        }
        if (displayName.contains("步骤") || displayName.contains("操作")) return std.get("steps");
        if (displayName.contains("分组") || displayName.contains("模块")) return std.get("testModule");
        if (displayName.contains("等级") || displayName.contains("优先级")) return std.get("priority");
        if (displayName.contains("状态")) return std.get("status");
        return null;
    }

    /**
     * 数字字段规范化：非纯数字的字段值（如 AI 生成的 "返回200+{...}"）提取出数字，
     * 提取不到则清空，保证导出/脚本生成阶段不会因 Integer.parseInt 崩溃。
     */
    private void normalizeNumericField(TestCase tc, String field, boolean statusCode) {
        String v = tc.getField(field);
        if (v == null || v.isEmpty() || v.trim().matches("\\d+")) return;
        Integer code = statusCode ? TestCase.extractStatusCode(v) : TestCase.extractInteger(v);
        tc.setField(field, code != null ? code.toString() : "");
    }

    /**
     * 按文件类型读取章节（支持 docx/pdf/md/txt）
     */
    private List<WordParser.SectionContent> readDocSections(String fp, String fname, int sp, int ep) {
        if (fname.endsWith(".pdf")) {
            String text = sp > 0 ? PdfParser.readPdf(fp, sp, ep) : PdfParser.readPdf(fp);
            List<WordParser.SectionContent> list = new ArrayList<>();
            list.add(new WordParser.SectionContent("需求文档", text));
            return list;
        }
        if (fname.endsWith(".md") || fname.endsWith(".txt")) {
            // 纯文本：按 Markdown 标题 / 章节分割
            List<WordParser.SectionContent> list = new ArrayList<>();
            try {
                String text = new String(Files.readAllBytes(Paths.get(fp)), java.nio.charset.StandardCharsets.UTF_8);
                String[] lines = text.split("\n");
                StringBuilder cur = new StringBuilder();
                String title = "文档开头";
                for (String line : lines) {
                    String t = line.trim();
                    if (t.startsWith("#") || t.matches("^第[一二三四五六七八九十\\d]+[章节部].*")) {
                        if (cur.length() > 30) list.add(new WordParser.SectionContent(title, cur.toString()));
                        title = t.replaceAll("^#+\\s*", "");
                        cur = new StringBuilder();
                    } else {
                        cur.append(t).append("\n");
                    }
                }
                if (cur.length() > 30) list.add(new WordParser.SectionContent(title, cur.toString()));
                if (list.isEmpty()) list.add(new WordParser.SectionContent("需求文档", text));
            } catch (Exception e) {
                list.add(new WordParser.SectionContent("需求文档", "读取失败"));
            }
            return list;
        }
        // docx
        return WordParser.readDocxBySections(fp, sp, ep);
    }
}
