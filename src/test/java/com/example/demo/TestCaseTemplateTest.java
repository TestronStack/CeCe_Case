package com.example.demo;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestCaseTemplateTest {

    @Test
    void defaultTemplateKeepsExpectedColumnsAndReviewStatus() {
        TestCaseTemplate template = TestCaseTemplate.createDefaultTemplate();

        assertThat(template.getDisplayNames()).containsExactly(
                "用例标题", "所属分组", "创建人", "关联需求", "前置条件", "测试步骤",
                "预期结果", "状态", "等级", "类型", "自动化");
        assertThat(template.getFieldByName("status").getEnumValues()).contains("待评审");
    }

    @Test
    void mapsChineseColumnsWithoutAddingUndeclaredFields() {
        TestCaseTemplate template = TestCaseTemplate.fromColumns(
                "标题|所属分组|创建人|关联需求|前置条件|测试步骤|预期结果|状态|等级|类型|自动化");

        assertThat(template.getFieldNames()).containsExactly(
                "title", "testModule", "creator", "relatedRequirement", "precondition",
                "steps", "expectedResult", "status", "priority", "caseType", "isAutomated");
        assertThat(template.getFields()).hasSize(11);
        assertThat(template.getFieldNames()).doesNotContain("eventId", "params");
    }

    @Test
    void keepsUnknownColumnAsExportOnlyDynamicField() {
        TestCaseTemplate template = TestCaseTemplate.fromColumns("标题|自定义说明");

        assertThat(template.getFieldNames()).containsExactly("title", "col_1");
        assertThat(TestCaseTemplate.mapColumnToField("测试步骤")).isEqualTo("steps");
    }
}
