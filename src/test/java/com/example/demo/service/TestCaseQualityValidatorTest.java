package com.example.demo.service;

import com.example.demo.TestCase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestCaseQualityValidatorTest {

    @Test
    void acceptsMatchingNumberedStepsAndExpectedResults() {
        TestCase tc = uiCase("1.点击按钮\n2.打开Debug日志\n3.筛选日志",
                "①页面发生需求描述的跳转\n②Debug日志页面可查看\n③日志记录参数按需求定义完整");

        TestCaseQualityValidator.ValidationResult result =
                TestCaseQualityValidator.validateUiRequirement(tc, "点击按钮并查看埋点日志");

        assertThat(result.isValid()).isTrue();
        assertThat(result.getStepCount()).isEqualTo(3);
        assertThat(result.getExpectedResultCount()).isEqualTo(3);
        assertThat(result.getStepNumbers()).containsExactly(1, 2, 3);
    }

    @Test
    void rejectsMissingExpectedNumber() {
        TestCase tc = uiCase("1.点击按钮\n2.打开日志\n3.筛选日志", "①页面跳转\n②日志可查看");

        TestCaseQualityValidator.ValidationResult result =
                TestCaseQualityValidator.validateUiRequirement(tc, "点击按钮并查看日志");

        assertThat(result.isValid()).isFalse();
        assertThat(result.getIssues()).extracting(TestCaseQualityValidator.ValidationIssue::getCode)
                .contains("STEP_EXPECTED_NOT_ONE_TO_ONE");
    }

    @Test
    void acceptsSingleUnnumberedStepAndExpected() {
        TestCase tc = uiCase("点击提交按钮", "页面显示需求规定的提交结果");

        assertThat(TestCaseQualityValidator.validateUiRequirement(tc, "提交按钮").isValid()).isTrue();
    }

    @Test
    void rejectsVagueExpectedResult() {
        TestCase tc = uiCase("1.点击按钮", "①操作成功");

        TestCaseQualityValidator.ValidationResult result =
                TestCaseQualityValidator.validateUiRequirement(tc, "点击按钮");

        assertThat(result.isValid()).isFalse();
        assertThat(result.getIssues()).extracting(TestCaseQualityValidator.ValidationIssue::getCode)
                .contains("EXPECTED_RESULT_TOO_VAGUE");
    }

    @Test
    void rejectsConcreteFactAbsentFromRequirement() {
        TestCase tc = uiCase("1.访问https://example.invalid/login", "①页面打开");

        TestCaseQualityValidator.ValidationResult result =
                TestCaseQualityValidator.validateUiRequirement(tc, "用户进入登录页面");

        assertThat(result.isValid()).isFalse();
        assertThat(result.getIssues()).extracting(TestCaseQualityValidator.ValidationIssue::getCode)
                .contains("UNDOCUMENTED_URL_OR_PATH");
    }

    @Test
    void keepsGenericBusinessLanguageValid() {
        TestCase tc = uiCase("1.使用有效用户进入页面", "①页面展示需求描述的内容");

        assertThat(TestCaseQualityValidator.validateUiRequirement(tc, "用户登录后进入页面").isValid()).isTrue();
    }

    @Test
    void doesNotModifyCase() {
        TestCase tc = uiCase("1.点击按钮", "①页面跳转");
        String title = tc.getField("title");

        TestCaseQualityValidator.validateUiRequirement(tc, "点击按钮");

        assertThat(tc.getField("title")).isEqualTo(title);
        assertThat(tc.getField("steps")).isEqualTo("1.点击按钮");
    }

    private static TestCase uiCase(String steps, String expected) {
        TestCase tc = new TestCase();
        tc.setField("title", "验证功能");
        tc.setField("steps", steps);
        tc.setField("expectedResult", expected);
        return tc;
    }
}
