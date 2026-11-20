package com.example.demo.service;

import com.example.demo.DocumentProcessor;
import com.example.demo.SwaggerParser;
import com.example.demo.TestCase;
import com.example.demo.TestCaseTemplate;
import com.example.demo.common.ApiException;
import com.example.demo.entity.DocumentEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * AI 用例生成编排：文档解析 → 接口提取 → 分节生成 → 模板转置。
 * 生成进度通过 Progress 回调上报，供任务表与前端实时展示。
 * AiService 内部为全局静态模型，故生成方法串行执行，避免多任务互相覆盖模型与计数器。
 */
@Slf4j
@Service
public class GenerationService {

    public interface Progress {
        void report(int percent, String stage);
    }

    public record GenResult(List<TestCase> cases, TestCaseTemplate template, String summary, byte[] scriptZip) {
    }

    // ==================== 需求文档 → UI 用例 ====================
    public synchronized GenResult requirement(DocumentEntity doc, String readRangeType, int startPage, int endPage,
                                             String chapterTitles, String keywords, String customTemplate,
                                             String creator, String relatedRequirement, Progress p) {
        DocumentProcessor.initModel();
        if (AiService.getModel() == null) {
            throw new ApiException("AI 模型未初始化，请先在「模型设置」中配置 API Key");
        }
        String fp = doc.getStoragePath();
        String fname = doc.getOriginalName().toLowerCase();

        p.report(15, "解析需求文档");
        List<WordParser.SectionContent> sections;
        if ("chapter".equals(readRangeType) && chapterTitles != null && !chapterTitles.isEmpty()) {
            List<String> selected = new ArrayList<>(Arrays.asList(chapterTitles.split(",")));
            sections = readSections(fp, fname, 0, 0);
            sections.removeIf(s -> selected.stream().noneMatch(t ->
                    s.getTitle().contains(t.trim()) || t.trim().contains(s.getTitle())));
            if (sections.isEmpty()) throw new ApiException("指定章节不存在，请重新选择");
        } else if ("keyword".equals(readRangeType) && keywords != null && !keywords.isEmpty()) {
            List<String> kws = new ArrayList<>(Arrays.asList(keywords.split("[,，]")));
            sections = readSections(fp, fname, 0, 0);
            sections.removeIf(s -> kws.stream().noneMatch(k ->
                    s.getContent().contains(k.trim()) || s.getTitle().contains(k.trim())));
            if (sections.isEmpty()) throw new ApiException("未找到包含指定关键词的内容");
        } else {
            sections = readSections(fp, fname,
                    "page".equals(readRangeType) ? startPage : 0,
                    "page".equals(readRangeType) ? endPage : 0);
        }
        if (sections.isEmpty()) throw new ApiException("未能从文档中解析出有效内容");

        StringBuilder summary = new StringBuilder();
        for (int i = 0; i < Math.min(sections.size(), 10); i++) {
            summary.append(i + 1).append(". ").append(sections.get(i).getTitle()).append("\n");
        }

        p.report(30, "AI 生成用例（共 " + sections.size() + " 个章节）");
        AiService.resetCounter();
        AiService.setCustomTemplate(customTemplate);
        List<TestCase> cases = AiService.generateTestCasesFromRequirementBySections(sections);
        if (cases.isEmpty()) throw new ApiException("未生成用例，请检查文档是否包含可测试的功能描述");

        p.report(85, "字段映射与去重");
        TestCaseTemplate tpl = AiService.getCurrentTemplate() != null
                ? AiService.getCurrentTemplate() : TestCaseTemplate.createUITemplate();
        cases = CaseAssembler.transpose(cases, tpl, creator, relatedRequirement);
        if (cases.isEmpty()) throw new ApiException("生成结果为空，请尝试扩大读取范围");
        return new GenResult(cases, tpl, summary.toString(), null);
    }

    // ==================== 接口文档 → 接口用例 ====================
    public synchronized GenResult apiCases(DocumentEntity doc, String readRangeType, int startPage, int endPage,
                                           Progress p) {
        DocumentProcessor.initModel();
        if (AiService.getModel() == null) {
            throw new ApiException("AI 模型未初始化，请先在「模型设置」中配置 API Key");
        }
        p.report(15, "提取接口定义");
        List<String> interfaces = extractInterfaces(doc, readRangeType, startPage, endPage);
        if (interfaces.isEmpty()) throw new ApiException("文档中未识别到接口定义");

        List<TestCase> cases = generateByInterfaces(interfaces, p, 25, 80);
        p.report(85, "字段映射与去重");
        TestCaseTemplate tpl = TestCaseTemplate.createAPITemplate();
        cases = CaseAssembler.transpose(cases, tpl, null, null);
        if (cases.isEmpty()) throw new ApiException("AI 未能根据接口文档生成用例");
        return new GenResult(cases, tpl, "识别到 " + interfaces.size() + " 个接口", null);
    }

    // ==================== 接口文档 → 接口用例 + 自动化脚本 ====================
    public synchronized GenResult script(DocumentEntity doc, String baseUrl, String language,
                                         String readRangeType, int startPage, int endPage, Progress p) {
        GenResult base = apiCases(doc, readRangeType, startPage, endPage, p);
        p.report(90, "生成自动化脚本工程");
        byte[] caseExcel = ExcelExporter.exportByFormat(base.cases(), base.template(), "xlsx");
        byte[] zip;
        try {
            zip = "python".equalsIgnoreCase(language)
                    ? ScriptGenerator.generatePythonScriptAsZip(base.cases(), baseUrl, "接口测试用例.xlsx", caseExcel)
                    : ScriptGenerator.generateScriptAsZip(base.cases(), baseUrl, "接口测试用例.xlsx", caseExcel);
        } catch (java.io.IOException e) {
            throw new ApiException(500, "自动化脚本生成失败：" + e.getMessage());
        }
        return new GenResult(base.cases(), base.template(), base.summary(), zip);
    }

    // ==================== 自然语言 → 用例 ====================
    public synchronized GenResult text(String content, String type, int generateCount, String customTemplate,
                                       String creator, String relatedRequirement, Progress p) {
        DocumentProcessor.initModel();
        if (AiService.getModel() == null) {
            throw new ApiException("AI 模型未初始化，请先在「模型设置」中配置 API Key");
        }
        if (content == null || content.trim().length() < 20) {
            throw new ApiException("描述过于简略，请补充输入参数、约束条件或预期行为（至少 20 字）");
        }
        p.report(25, "AI 生成用例");
        AiService.resetCounter();
        AiService.setCustomTemplate(customTemplate);
        AiService.setGenerateCount(generateCount);

        List<TestCase> cases = "requirement".equals(type)
                ? AiService.generateTestCasesFromRequirementBySections(
                        List.of(new WordParser.SectionContent("自然语言需求", content)))
                : AiService.generateTestCases(content);
        if (cases.isEmpty()) throw new ApiException("AI 未能生成测试用例，请补充描述内容");

        p.report(85, "字段映射与去重");
        TestCaseTemplate tpl = AiService.getCurrentTemplate();
        if (tpl == null) {
            tpl = "requirement".equals(type)
                    ? TestCaseTemplate.createUITemplate() : TestCaseTemplate.createAPITemplate();
        }
        cases = CaseAssembler.transpose(cases, tpl, creator, relatedRequirement);
        if (cases.isEmpty()) throw new ApiException("AI 未能生成测试用例");
        return new GenResult(cases, tpl, "自然语言生成 " + cases.size() + " 条用例", null);
    }

    // ==================== 内部方法 ====================
    private List<TestCase> generateByInterfaces(List<String> interfaces, Progress p, int from, int to) {
        AiService.resetCounter();
        AiService.setApiCaseCap(8);
        List<TestCase> all = new ArrayList<>();
        for (int i = 0; i < interfaces.size(); i++) {
            p.report(from + (to - from) * i / Math.max(interfaces.size(), 1),
                    "AI 生成接口用例 " + (i + 1) + "/" + interfaces.size() + "：" + firstLine(interfaces.get(i)));
            all.addAll(AiService.generateTestCases(interfaces.get(i)));
        }
        return AiService.enforceApiTotalBudget(all);
    }

    private List<String> extractInterfaces(DocumentEntity doc, String range, int sp, int ep) {
        String fp = doc.getStoragePath();
        String name = doc.getOriginalName().toLowerCase();
        if (name.endsWith(".docx")) {
            return "page".equals(range) ? DocumentProcessor.extractFromDocx(fp, sp, ep)
                    : DocumentProcessor.extractFromDocx(fp);
        }
        if (name.endsWith(".pdf")) {
            String c = "page".equals(range) ? PdfParser.readPdf(fp, sp, ep) : PdfParser.readPdf(fp);
            return PdfParser.extractInterfaces(c);
        }
        if (name.endsWith(".json") || name.endsWith(".yaml") || name.endsWith(".yml")) {
            return SwaggerParser.parseSwagger(fp);
        }
        if (name.endsWith(".md") || name.endsWith(".txt")) {
            try {
                String text = Files.readString(Path.of(fp), StandardCharsets.UTF_8);
                return PdfParser.extractInterfaces(text);
            } catch (Exception e) {
                return Collections.emptyList();
            }
        }
        return Collections.emptyList();
    }

    /** 按文件类型读取章节：docx 走章节算法，pdf/md/txt 按整体或标题切分 */
    public List<WordParser.SectionContent> readSections(String fp, String fname, int sp, int ep) {
        if (fname.endsWith(".pdf")) {
            String text = sp > 0 ? PdfParser.readPdf(fp, sp, ep) : PdfParser.readPdf(fp);
            List<WordParser.SectionContent> list = new ArrayList<>();
            list.add(new WordParser.SectionContent("需求文档", text));
            return list;
        }
        if (fname.endsWith(".md") || fname.endsWith(".txt")) {
            List<WordParser.SectionContent> list = new ArrayList<>();
            try {
                String text = Files.readString(Path.of(fp), StandardCharsets.UTF_8);
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
        return WordParser.readDocxBySections(fp, sp, ep);
    }

    private String firstLine(String s) {
        if (s == null || s.isEmpty()) return "";
        for (String l : s.split("\n")) {
            if (!l.trim().isEmpty()) return l.trim();
        }
        return "";
    }
}
