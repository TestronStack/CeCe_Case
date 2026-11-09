package com.example.demo.service;

import com.example.demo.TestCase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 需求类 UI 用例的确定性质量校验器。
 *
 * <p>该类不调用模型，也不会修改传入的用例对象。它只校验能够由程序可靠判断的
 * 结构问题：标题、步骤和预期是否完整，步骤与预期编号是否一一对应，以及输出中的
 * 少量高置信度具体事实是否来自本次需求文本。</p>
 */
public final class TestCaseQualityValidator {

    private static final Pattern NUMBERED_ITEM = Pattern.compile(
            "(?<![\\dA-Za-z])(?:步骤\\s*|第\\s*)?(\\d{1,2})\\s*(?:[.．、)）:：]|步(?:[：:]?))");
    private static final Pattern CIRCLED_ITEM = Pattern.compile("[①②③④⑤⑥⑦⑧⑨⑩⑪⑫⑬⑭⑮⑯⑰⑱⑲⑳]");
    private static final Pattern URL = Pattern.compile("(?i)https?://[^\\s，。；;、)）]+|(?<![\\w-])/(?:[A-Za-z][A-Za-z0-9_-]*)(?:/[A-Za-z0-9_{}-]+)+");
    private static final Pattern EVENT_ID = Pattern.compile(
            "(?:事件\\s*ID|event[ _-]?id)\\s*(?:为|:|：|=)\\s*[`\"“”']?([A-Za-z][A-Za-z0-9_.-]{1,})");
    private static final Pattern JSON_KEY = Pattern.compile("[\"']([A-Za-z][A-Za-z0-9_-]{1,})[\"']\\s*:");
    private static final Pattern ACCOUNT = Pattern.compile(
            "(?:账号|用户名|账户)\\s*(?:为|:|：|=)\\s*([A-Za-z][A-Za-z0-9_]{2,}|1\\d{10})");
    private static final Pattern CREDENTIAL = Pattern.compile("[A-Za-z][A-Za-z0-9_]{2,}/\\d{4,}");
    private static final Pattern TIME_THRESHOLD = Pattern.compile(
            "(?:误差|延迟|耗时|时间)\\s*(?:<|≤|<=|小于|不超过)\\s*\\d+\\s*(?:毫秒|秒|分钟|小时)");
    private static final Pattern ONLY_VAGUE_RESULT = Pattern.compile(
            "^(?:(?:操作|执行|验证|页面|数据)?(?:成功|正常|正确|完成|通过)|(?:操作成功|页面正常|数据正确))[。！!；;，,]*$");

    private TestCaseQualityValidator() {
    }

    /**
     * 校验一条来自需求文档的 UI 用例。
     *
     * @param testCase 候选用例
     * @param requirementText 本次实际送入模型的需求文本；为空时跳过来源事实检查
     * @return 不可变的校验结果
     */
    public static ValidationResult validateUiRequirement(TestCase testCase, String requirementText) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (testCase == null) {
            issues.add(ValidationIssue.error("CASE_EMPTY", "用例不能为空"));
            return new ValidationResult(0, 0, Collections.emptySet(), Collections.emptySet(), issues);
        }

        requireText(testCase.getField("title"), "title", "TITLE_EMPTY", "用例标题不能为空", issues);
        String steps = testCase.getField("steps");
        String expected = testCase.getField("expectedResult");
        requireText(steps, "steps", "STEPS_EMPTY", "测试步骤不能为空", issues);
        requireText(expected, "expectedResult", "EXPECTED_RESULT_EMPTY", "预期结果不能为空", issues);

        NumberedItems stepItems = parseNumberedItems(steps);
        NumberedItems expectedItems = parseNumberedItems(expected);
        validateNumbering(stepItems, expectedItems, issues);
        validateExpectedResults(expectedItems, expected, issues);
        validateSourceFacts(testCase, requirementText, issues);

        return new ValidationResult(stepItems.size(), expectedItems.size(), stepItems.numbers,
                expectedItems.numbers, issues);
    }

    private static void requireText(String value, String field, String code, String message,
                                    List<ValidationIssue> issues) {
        if (value == null || value.trim().isEmpty()) {
            issues.add(ValidationIssue.error(code, field + "：" + message));
        }
    }

    private static void validateNumbering(NumberedItems steps, NumberedItems expected,
                                          List<ValidationIssue> issues) {
        if (steps.isEmpty() || expected.isEmpty()) {
            return;
        }
        if (steps.numbered != expected.numbered) {
            issues.add(ValidationIssue.error("NUMBERING_STYLE_MISMATCH",
                    "测试步骤与预期结果必须同时编号；仅单步文本可同时省略编号"));
            return;
        }
        if (!steps.numbered) {
            return;
        }
        if (!isContinuousFromOne(steps.numbers)) {
            issues.add(ValidationIssue.error("STEP_NUMBER_NOT_CONTINUOUS", "测试步骤编号必须从 1 连续开始"));
        }
        if (!isContinuousFromOne(expected.numbers)) {
            issues.add(ValidationIssue.error("EXPECTED_NUMBER_NOT_CONTINUOUS", "预期结果编号必须从 1 连续开始"));
        }
        if (!steps.numbers.equals(expected.numbers)) {
            issues.add(ValidationIssue.error("STEP_EXPECTED_NOT_ONE_TO_ONE",
                    "测试步骤与预期结果的编号集合不一致，不能逐步对应"));
        }
    }

    private static void validateExpectedResults(NumberedItems expectedItems, String expected,
                                                List<ValidationIssue> issues) {
        if (expected == null || expected.trim().isEmpty()) {
            return;
        }
        List<String> resultTexts = expectedItems.contents;
        if (resultTexts.isEmpty()) {
            resultTexts = List.of(expected.trim());
        }
        for (String resultText : resultTexts) {
            String normalized = normalizePlainText(resultText);
            if (!normalized.isEmpty() && ONLY_VAGUE_RESULT.matcher(normalized).matches()) {
                issues.add(ValidationIssue.error("EXPECTED_RESULT_TOO_VAGUE",
                        "预期结果不能只写“操作成功”“页面正常”等不可判定描述"));
                return;
            }
        }
    }

    /**
     * 只检查模型常见的、可明确识别的具体事实，避免将普通业务词和合理改写误判为编造。
     */
    private static void validateSourceFacts(TestCase testCase, String requirementText,
                                            List<ValidationIssue> issues) {
        if (requirementText == null || requirementText.trim().isEmpty()) {
            return;
        }
        String candidateText = joinCandidateFields(testCase);
        String normalizedSource = normalizeForEvidence(requirementText);

        checkMatches(URL, candidateText, normalizedSource, "UNDOCUMENTED_URL_OR_PATH",
                "输出包含需求文本中不存在的 URL 或路径：", false, issues);
        checkMatches(EVENT_ID, candidateText, normalizedSource, "UNDOCUMENTED_EVENT_ID",
                "输出包含需求文本中不存在的事件 ID：", true, issues);
        checkMatches(JSON_KEY, candidateText, normalizedSource, "UNDOCUMENTED_PARAMETER",
                "输出包含需求文本中不存在的参数名：", true, issues);
        checkMatches(ACCOUNT, candidateText, normalizedSource, "UNDOCUMENTED_ACCOUNT",
                "输出包含需求文本中不存在的账号：", true, issues);
        checkMatches(CREDENTIAL, candidateText, normalizedSource, "UNDOCUMENTED_CREDENTIAL",
                "输出包含需求文本中不存在的账号或凭据：", false, issues);
        checkMatches(TIME_THRESHOLD, candidateText, normalizedSource, "UNDOCUMENTED_TIME_THRESHOLD",
                "输出包含需求文本中不存在的时间阈值：", false, issues);
    }

    private static void checkMatches(Pattern pattern, String candidateText, String normalizedSource,
                                     String code, String message, boolean useFirstGroup,
                                     List<ValidationIssue> issues) {
        Matcher matcher = pattern.matcher(candidateText);
        Set<String> checked = new LinkedHashSet<>();
        while (matcher.find()) {
            String value = useFirstGroup ? matcher.group(1) : matcher.group();
            if (value == null || value.isBlank()) {
                continue;
            }
            String normalizedValue = normalizeForEvidence(value);
            if (normalizedValue.length() < 2 || !checked.add(normalizedValue)) {
                continue;
            }
            if (!normalizedSource.contains(normalizedValue)) {
                issues.add(ValidationIssue.error(code, message + value));
            }
        }
    }

    private static String joinCandidateFields(TestCase testCase) {
        StringBuilder text = new StringBuilder();
        append(text, testCase.getField("title"));
        append(text, testCase.getField("precondition"));
        append(text, testCase.getField("presetData"));
        append(text, testCase.getField("steps"));
        append(text, testCase.getField("expectedResult"));
        return text.toString();
    }

    private static void append(StringBuilder builder, String value) {
        if (value != null && !value.isBlank()) {
            builder.append(value).append('\n');
        }
    }

    private static NumberedItems parseNumberedItems(String text) {
        if (text == null || text.trim().isEmpty()) {
            return NumberedItems.empty();
        }
        List<Marker> markers = new ArrayList<>();
        Matcher numberMatcher = NUMBERED_ITEM.matcher(text);
        while (numberMatcher.find()) {
            markers.add(new Marker(numberMatcher.start(), numberMatcher.end(),
                    Integer.parseInt(numberMatcher.group(1))));
        }
        Matcher circledMatcher = CIRCLED_ITEM.matcher(text);
        while (circledMatcher.find()) {
            markers.add(new Marker(circledMatcher.start(), circledMatcher.end(),
                    circledMatcher.group().codePointAt(0) - '①' + 1));
        }
        if (markers.isEmpty()) {
            return NumberedItems.singleUnnumbered(text.trim());
        }
        markers.sort((left, right) -> Integer.compare(left.start, right.start));
        Set<Integer> numbers = new LinkedHashSet<>();
        List<String> contents = new ArrayList<>();
        for (int i = 0; i < markers.size(); i++) {
            Marker marker = markers.get(i);
            int contentEnd = i + 1 < markers.size() ? markers.get(i + 1).start : text.length();
            numbers.add(marker.number);
            contents.add(text.substring(marker.end, contentEnd).trim());
        }
        return new NumberedItems(true, numbers, contents);
    }

    private static boolean isContinuousFromOne(Set<Integer> numbers) {
        int expected = 1;
        for (Integer number : numbers) {
            if (number == null || number != expected++) {
                return false;
            }
        }
        return !numbers.isEmpty();
    }

    private static String normalizeForEvidence(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s`'\"“”‘’，,。；;：:（）()【】\\[\\]]", "");
    }

    private static String normalizePlainText(String text) {
        return text == null ? "" : text.trim().replaceAll("\\s+", "");
    }

    private static final class Marker {
        private final int start;
        private final int end;
        private final int number;

        private Marker(int start, int end, int number) {
            this.start = start;
            this.end = end;
            this.number = number;
        }
    }

    private static final class NumberedItems {
        private final boolean numbered;
        private final Set<Integer> numbers;
        private final List<String> contents;

        private NumberedItems(boolean numbered, Set<Integer> numbers, List<String> contents) {
            this.numbered = numbered;
            this.numbers = new LinkedHashSet<>(numbers);
            this.contents = new ArrayList<>(contents);
        }

        private static NumberedItems empty() {
            return new NumberedItems(false, Collections.emptySet(), Collections.emptyList());
        }

        private static NumberedItems singleUnnumbered(String content) {
            return new NumberedItems(false, Collections.singleton(1), Collections.singletonList(content));
        }

        private boolean isEmpty() {
            return contents.isEmpty() || (contents.size() == 1 && contents.get(0).isBlank());
        }

        private int size() {
            return contents.size();
        }
    }

    public static final class ValidationResult {
        private final int stepCount;
        private final int expectedResultCount;
        private final Set<Integer> stepNumbers;
        private final Set<Integer> expectedResultNumbers;
        private final List<ValidationIssue> issues;

        private ValidationResult(int stepCount, int expectedResultCount, Set<Integer> stepNumbers,
                                 Set<Integer> expectedResultNumbers, List<ValidationIssue> issues) {
            this.stepCount = stepCount;
            this.expectedResultCount = expectedResultCount;
            this.stepNumbers = Collections.unmodifiableSet(new LinkedHashSet<>(stepNumbers));
            this.expectedResultNumbers = Collections.unmodifiableSet(new LinkedHashSet<>(expectedResultNumbers));
            this.issues = Collections.unmodifiableList(new ArrayList<>(issues));
        }

        public boolean isValid() {
            return issues.stream().noneMatch(issue -> issue.getSeverity() == Severity.ERROR);
        }

        public int getStepCount() {
            return stepCount;
        }

        public int getExpectedResultCount() {
            return expectedResultCount;
        }

        public Set<Integer> getStepNumbers() {
            return stepNumbers;
        }

        public Set<Integer> getExpectedResultNumbers() {
            return expectedResultNumbers;
        }

        public List<ValidationIssue> getIssues() {
            return issues;
        }
    }

    public enum Severity {
        ERROR,
        WARNING
    }

    public static final class ValidationIssue {
        private final Severity severity;
        private final String code;
        private final String message;

        private ValidationIssue(Severity severity, String code, String message) {
            this.severity = severity;
            this.code = code;
            this.message = message;
        }

        private static ValidationIssue error(String code, String message) {
            return new ValidationIssue(Severity.ERROR, code, message);
        }

        public Severity getSeverity() {
            return severity;
        }

        public String getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }
    }
}
