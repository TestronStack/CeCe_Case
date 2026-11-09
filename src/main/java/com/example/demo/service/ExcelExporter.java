package com.example.demo.service;

import com.example.demo.TestCase;
import com.example.demo.TestCaseTemplate;
import com.example.demo.TestCaseTemplate.TemplateField;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Excel导出器
 * 支持根据自定义模板动态导出测试用例
 */
@Slf4j
public class ExcelExporter {

    /** Excel 单元格内容最大长度（POI 限制），超长需截断否则抛异常 */
    private static final int MAX_CELL_LENGTH = 32767;
    private static final String TRUNCATE_SUFFIX = "…(内容过长已截断)";

    /**
     * 截断超长单元格内容，避免超过 POI 的 32767 字符限制导致导出失败
     */
    private static String truncateCell(String value) {
        if (value == null) return "";
        if (value.length() <= MAX_CELL_LENGTH) return value;
        int keep = MAX_CELL_LENGTH - TRUNCATE_SUFFIX.length();
        if (keep < 0) keep = 0;
        return value.substring(0, keep) + TRUNCATE_SUFFIX;
    }

    /**
     * 导出测试用例到Excel文件（使用自定义模板）
     */
    public static void exportToExcel(List<TestCase> testCases, String filePath, TestCaseTemplate template) {
        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            exportToExcel(testCases, fos, template);
        } catch (Exception e) {
            log.error("导出Excel失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 导出测试用例到输出流（使用自定义模板）
     */
    public static void exportToExcel(List<TestCase> testCases, OutputStream outputStream, TestCaseTemplate template) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("测试用例");

            // 根据模板动态创建表头
            Row headerRow = sheet.createRow(0);
            List<String> displayNames = template.getDisplayNames();
            for (int i = 0; i < displayNames.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(displayNames.get(i));
                sheet.setColumnWidth(i, 4500);
            }

            // 填充数据（根据模板字段顺序）
            int rowNum = 1;
            for (TestCase tc : testCases) {
                Row row = sheet.createRow(rowNum++);
                for (int i = 0; i < template.getFields().size(); i++) {
                    TemplateField field = template.getFields().get(i);
                    String value = tc.getField(field.getFieldName());
                    row.createCell(i).setCellValue(truncateCell(value));
                }
            }

            workbook.write(outputStream);
        } catch (Exception e) {
            log.error("导出Excel失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 导出测试用例到Excel文件（后端接口自动化用例默认模板）
     */
    public static void exportToExcel(List<TestCase> testCases, String filePath) {
        exportToExcel(testCases, filePath, TestCaseTemplate.createAPITemplate());
    }

    /**
     * 导出测试用例到输出流（后端接口自动化用例默认模板）
     */
    public static void exportToExcel(List<TestCase> testCases, OutputStream outputStream) {
        exportToExcel(testCases, outputStream, TestCaseTemplate.createAPITemplate());
    }

    /**
     * 导出需求文档测试用例到Excel文件（UI业务功能用例默认模板）
     */
    public static void exportRequirementTestCasesToExcel(List<TestCase> testCases, String filePath) {
        exportRequirementTestCasesToExcel(testCases, filePath, TestCaseTemplate.createUITemplate());
    }

    /**
     * 导出需求文档测试用例到Excel文件（使用自定义模板）
     */
    public static void exportRequirementTestCasesToExcel(List<TestCase> testCases, String filePath, TestCaseTemplate template) {
        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            exportRequirementTestCasesToExcel(testCases, fos, template);
        } catch (Exception e) {
            log.error("导出需求文档测试用例Excel失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 导出需求文档测试用例到输出流（UI业务功能用例默认模板）
     */
    public static void exportRequirementTestCasesToExcel(List<TestCase> testCases, OutputStream outputStream) {
        exportRequirementTestCasesToExcel(testCases, outputStream, TestCaseTemplate.createUITemplate());
    }

    /**
     * 导出需求文档测试用例到输出流（使用自定义模板）
     */
    public static void exportRequirementTestCasesToExcel(List<TestCase> testCases, OutputStream outputStream, TestCaseTemplate template) {
        exportToExcel(testCases, outputStream, template);
    }

    /**
     * 按指定格式导出（xlsx / csv / json / markdown）
     * 返回内容字符串（xlsx返回null，走工作簿）
     */
    public static byte[] exportByFormat(List<TestCase> testCases, TestCaseTemplate template, String format) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        String fmt = format == null ? "xlsx" : format.toLowerCase();
        switch (fmt) {
            case "csv": {
                StringBuilder sb = new StringBuilder();
                sb.append(String.join(",", template.getDisplayNames())).append("\n");
                for (TestCase tc : testCases) {
                    List<String> vals = new ArrayList<>();
                    for (TemplateField f : template.getFields()) {
                        String v = tc.getField(f.getFieldName());
                        vals.add("\"" + (v != null ? v.replace("\"", "\"\"") : "") + "\"");
                    }
                    sb.append(String.join(",", vals)).append("\n");
                }
                return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            }
            case "json": {
                StringBuilder sb = new StringBuilder("[\n");
                for (int i = 0; i < testCases.size(); i++) {
                    TestCase tc = testCases.get(i);
                    sb.append("  {");
                    for (int j = 0; j < template.getFields().size(); j++) {
                        TemplateField f = template.getFields().get(j);
                        String v = tc.getField(f.getFieldName());
                        if (j > 0) sb.append(", ");
                        sb.append("\"").append(f.getDisplayName()).append("\": \"")
                          .append(v != null ? v.replace("\"", "\\\"") : "").append("\"");
                    }
                    sb.append("}").append(i < testCases.size() - 1 ? "," : "").append("\n");
                }
                sb.append("]");
                return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            }
            case "markdown":
            case "md": {
                StringBuilder sb = new StringBuilder();
                sb.append("| ").append(String.join(" | ", template.getDisplayNames())).append(" |\n");
                sb.append("|").append(" --- |".repeat(template.getFields().size())).append("\n");
                for (TestCase tc : testCases) {
                    List<String> vals = new ArrayList<>();
                    for (TemplateField f : template.getFields()) {
                        String v = tc.getField(f.getFieldName());
                        vals.add((v != null ? v.replace("|", "\\|") : ""));
                    }
                    sb.append("| ").append(String.join(" | ", vals)).append(" |\n");
                }
                return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            }
            case "xlsx":
            default:
                try {
                    exportToExcel(testCases, bos, template);
                } catch (Exception e) { log.error("xlsx导出失败: {}", e.getMessage()); }
                return bos.toByteArray();
        }
    }

    public static void exportFailedMockToExcel(String filePath) {
        try (Workbook workbook = new XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(filePath)) {
            Sheet sheet = workbook.createSheet("测试用例");
            Row headerRow = sheet.createRow(0);
            String[] headers = {"用例编号", "测试模块", "测试点", "用例标题", "前置条件", "具体操作", "预期结果", "优先级"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
            }
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("FAILED-001");
            row.createCell(1).setCellValue("生成失败");
            row.createCell(2).setCellValue("模拟数据");
            row.createCell(3).setCellValue("AI生成失败模拟用例");
            row.createCell(4).setCellValue("系统异常");
            row.createCell(5).setCellValue("N/A");
            row.createCell(6).setCellValue("生成失败，请检查日志");
            row.createCell(7).setCellValue("P0");
            workbook.write(fos);
        } catch (Exception e) {
            log.error("导出失败模拟文件失败: {}", e.getMessage(), e);
        }
    }

    public static void saveToText(List<TestCase> testCases, String filePath) {
        try (PrintWriter writer = new PrintWriter(new FileOutputStream(filePath))) {
            for (TestCase tc : testCases) {
                writer.println("=== " + tc.getId() + " ===");
                writer.println("标题: " + tc.getField("title"));
                writer.println("模块: " + tc.getField("testModule"));
                writer.println("优先级: " + tc.getField("priority"));
                writer.println();
            }
        } catch (Exception e) {
            log.error("保存文本文件失败: {}", e.getMessage(), e);
        }
    }

    public static List<TestCase> importFromExcel(String filePath) {
        List<TestCase> testCases = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(fis)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row != null) {
                    TestCase tc = new TestCase();
                    tc.setId(getCellValueAsString(row.getCell(0)));
                    tc.setField("testModule", getCellValueAsString(row.getCell(1)));
                    tc.setField("testPoint", getCellValueAsString(row.getCell(2)));
                    tc.setField("title", getCellValueAsString(row.getCell(3)));
                    tc.setField("precondition", getCellValueAsString(row.getCell(4)));
                    tc.setField("steps", getCellValueAsString(row.getCell(5)));
                    tc.setField("expectedResult", getCellValueAsString(row.getCell(6)));
                    tc.setField("priority", getCellValueAsString(row.getCell(7)));
                    testCases.add(tc);
                }
            }
        } catch (Exception e) {
            log.error("导入Excel失败: {}", e.getMessage(), e);
        }
        return testCases;
    }

    public static void exportExecutionResult(Object result, String filePath) {
        try (Workbook workbook = new XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(filePath)) {
            Sheet sheet = workbook.createSheet("执行结果");
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("结果");
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue(result != null ? result.toString() : "null");
            workbook.write(fos);
        } catch (Exception e) {
            log.error("导出执行结果失败: {}", e.getMessage(), e);
        }
    }

    private static String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                return String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return "";
        }
    }
}
