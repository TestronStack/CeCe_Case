package com.example.demo.service;

import com.example.demo.TestCase;
import com.example.demo.TestCaseTemplate;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelExporterTest {

    @Test
    void exportsTemplateColumnsAndChineseMultilineValues() throws Exception {
        TestCase tc = new TestCase();
        tc.setField("title", "验证埋点");
        tc.setField("steps", "1.点击按钮\n2.查看日志");
        tc.setField("expectedResult", "①页面跳转\n②日志记录完整");
        TestCaseTemplate template = TestCaseTemplate.fromColumns("标题|测试步骤|预期结果");

        byte[] bytes = ExcelExporter.exportByFormat(List.of(tc), template, "xlsx");

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue()).isEqualTo("标题");
            assertThat(workbook.getSheetAt(0).getRow(0).getCell(1).getStringCellValue()).isEqualTo("测试步骤");
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(1).getStringCellValue())
                    .isEqualTo("1.点击按钮\n2.查看日志");
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(2).getStringCellValue())
                    .contains("日志记录完整");
        }
    }

    @Test
    void exportsEmptyListWithHeader() throws Exception {
        TestCaseTemplate template = TestCaseTemplate.createDefaultTemplate();

        byte[] bytes = ExcelExporter.exportByFormat(List.of(), template, "xlsx");

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getSheetAt(0).getLastRowNum()).isZero();
            assertThat(workbook.getSheetAt(0).getRow(0).getPhysicalNumberOfCells()).isEqualTo(11);
        }
    }

    @Test
    void truncatesOversizedCellWithoutFailingExport() throws Exception {
        TestCase tc = new TestCase();
        tc.setField("title", "标题");
        tc.setField("expectedResult", "x".repeat(40000));
        TestCaseTemplate template = TestCaseTemplate.fromColumns("标题|预期结果");

        byte[] bytes = ExcelExporter.exportByFormat(List.of(tc), template, "xlsx");

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            String value = workbook.getSheetAt(0).getRow(1).getCell(1).getStringCellValue();
            assertThat(value.length()).isLessThanOrEqualTo(32767);
            assertThat(value).endsWith("…(内容过长已截断)");
        }
    }
}
