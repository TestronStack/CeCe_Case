package com.example.demo.service;

import com.example.demo.TestCase;
import com.example.demo.exception.AIServiceException;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.dashscope.QwenChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI服务类
 * 职责：调用AI模型进行接口提取和测试用例生成
 */
@Slf4j
public class AiService {

    private static ChatLanguageModel model;
    private static final int CHUNK_SIZE = 2000;

    private static String API_KEY;
    private static String MODEL_PROVIDER;

    // 全局模块计数器（跨接口调用共享，保证同一文档内编号不重置）
    private static Map<String, Integer> globalModuleCounter = new HashMap<>();
    private static int globalSequenceCounter = 1;

    // 当前请求的自定义模板（null = 使用默认24/15列格式）
    private static String currentCustomTemplate = null;
    // 解析好的模板对象（含列名→字段key映射）
    private static com.example.demo.TestCaseTemplate currentTemplate = null;

    // 自然语言生成期望的用例数量（默认10条）
    private static int currentGenerateCount = 10;

    public static void setGenerateCount(int count) { currentGenerateCount = count > 0 ? count : 10; }
    public static int getGenerateCount() { return currentGenerateCount; }

    /** 接口文档流程：每个接口最多生成的用例数（硬上限，超出裁剪保留 P0/P1）；0=不启用（走 currentGenerateCount） */
    private static int apiCaseCap = 0;
    /** 接口文档流程：整份文档用例总量上限 */
    private static final int API_TOTAL_BUDGET = 100;

    public static void setApiCaseCap(int cap) { apiCaseCap = cap > 0 ? cap : 0; }
    public static int getApiCaseCap() { return apiCaseCap; }
    public static int getApiTotalBudget() { return API_TOTAL_BUDGET; }

    /** 测试设计方法要求（模块2）：覆盖充分且类型均衡，同时遵循"1测试点=1用例"粒度 */
    private static final String DESIGN_METHOD_REQUIREMENT = """

            【测试设计方法要求】
            请综合运用以下测试设计方法设计用例，保证覆盖充分且类型均衡（不能全是同一种）。
            但必须遵循"1个测试点 = 1条用例"的粒度：同一类测试场景合并为一条代表性用例，
            并在用例标题或预期结果中注明覆盖范围，禁止把每个等价类/每个边界值拆成多条用例：
            1. 等价类划分：对每个输入字段识别有效/无效等价类，合并为1条代表性用例
            2. 边界值分析：将 min、max、min-1、max+1 等边界合并进1条边界用例
            3. 判定表：存在多条件组合逻辑时，覆盖关键条件组合
            4. 场景法：识别主流程、备选流程、异常流程，生成端到端场景用例
            5. 错误推测法：覆盖空值、超长、特殊字符、SQL注入特征等常见错误，同类错误合并为1条
            测试设计方法只用于内部组织测试点，不能写入用例标题、步骤或预期结果。
            标题只描述具体页面、功能或测试目标，不要添加【等价类】【边界值】【判定表】【场景法】【错误推测法】等方法标签。
            """;

    /** 用例生成约束（模块8）：粒度 / 优先级 / 非功能性过滤 */
    private static final String GENERATION_CONSTRAINTS = """

            【用例生成约束】
            1. 用例粒度：1个独立测试点 = 1条用例。同一类测试场景（如"用户名长度校验"）只生成1条代表性用例，
               并在用例标题或预期结果中注明覆盖范围（如"覆盖3/20/21位边界"），禁止把每个等价类/每个边界值拆成多条用例。
            2. 用例优先级（用于决定取舍）：
               - P0：核心主流程、正向场景 —— 必须生成
               - P1：常见异常场景、边界值 —— 建议生成
               - P2：组合场景、极端边界、兼容性细节 —— 可选生成，数量不超过总数1/3
               优先保证 P0/P1 的覆盖质量，P2 可被忽略或折叠。
            3. 忽略以下内容，不为其生成用例：
               - UI样式、布局、颜色、字体等前端展示细节
               - 文案提示信息的具体内容（只关注功能逻辑，不校验具体文案措辞）
               - 产品背景介绍、业务价值描述、架构设计等非功能性文字
               - 文档中的字段说明（仅作为设计参考，不作为独立测试点）
            """;

    /** 用例总量预算（模块8：数量管控），可通过 application.yml 的 ai.case-budget 覆盖 */
    private static int CASE_BUDGET = 120;
    private static final int CASE_BUDGET_MIN = 100;
    private static final int CASE_BUDGET_MAX = 150;

    /** 状态默认值：自定义模板中【状态】列默认填写「待评审」 */
    private static final String DEFAULT_STATUS = "待评审";
    private static final Pattern DESIGN_METHOD_TITLE_PREFIX = Pattern.compile(
            "^\\s*(?:【\\s*(?:等价类|边界值|判定表|场景法|错误推测法|测试设计方法)\\s*】\\s*)+");
    private static final Pattern EXPECTED_RESULT_MARKER = Pattern.compile(
            "^\\s*(?:[①②③④⑤⑥⑦⑧⑨⑩⑪⑫⑬⑭⑮⑯⑰⑱⑲⑳]|\\d{1,2}\\s*[.．、)）:：])\\s*(?:/\\s*\\d{1,2}\\s*[.．、)）:：]\\s*)?");
    private static final Pattern NUMBERED_TEXT_MARKER = Pattern.compile(
            "(?<![\\dA-Za-z])(?:(?:第\\s*)?(\\d{1,2})\\s*[.．、)）:：](?:\\s*/\\s*(?:\\d{1,2})\\s*[.．、)）:：])?|([①②③④⑤⑥⑦⑧⑨⑩⑪⑫⑬⑭⑮⑯⑰⑱⑲⑳])(?:\\s*/\\s*\\d{1,2}\\s*[.．、)）:：]?)?)");

    /** 清理模型可能添加的测试设计方法标签，标题只保留业务页面或测试目标。 */
    static String normalizeRequirementTitle(String title) {
        if (title == null) return "";
        String normalized = title.trim();
        while (true) {
            Matcher matcher = DESIGN_METHOD_TITLE_PREFIX.matcher(normalized);
            if (!matcher.find()) return normalized;
            normalized = normalized.substring(matcher.end()).trim();
        }
    }

    /**
     * 将模型输出的步骤/预期条目重建为统一格式，避免出现“①/1.”这样的双重编号。
     * 步骤统一使用阿拉伯数字，预期结果统一使用圆圈数字。
     */
    static String normalizeRequirementItems(String text, boolean expectedResult) {
        if (text == null || text.trim().isEmpty()) return "";
        String source = text.trim();
        Matcher matcher = NUMBERED_TEXT_MARKER.matcher(source);
        List<String> contents = new ArrayList<>();
        List<Integer> numbers = new ArrayList<>();
        List<Integer> starts = new ArrayList<>();
        List<Integer> ends = new ArrayList<>();
        while (matcher.find()) {
            int number = matcher.group(1) != null
                    ? Integer.parseInt(matcher.group(1))
                    : matcher.group(2).codePointAt(0) - '①' + 1;
            numbers.add(number);
            starts.add(matcher.start());
            ends.add(matcher.end());
        }
        if (numbers.isEmpty()) return source;
        for (int i = 0; i < numbers.size(); i++) {
            int contentStart = ends.get(i);
            int contentEnd = i + 1 < numbers.size() ? starts.get(i + 1) : source.length();
            contents.add(source.substring(contentStart, contentEnd).trim());
        }

        StringBuilder normalized = new StringBuilder();
        for (int i = 0; i < contents.size(); i++) {
            String content = contents.get(i);
            // 去掉另一种编号及斜杠，兼容模型输出“①/1. 内容”或“1./① 内容”。
            content = content.replaceFirst(
                    "^\\s*(?:/\\s*)?(?:\\d{1,2}\\s*[.．、)）:：]|[①②③④⑤⑥⑦⑧⑨⑩⑪⑫⑬⑭⑮⑯⑰⑱⑲⑳])\\s*", "");
            if (i > 0) normalized.append('\n');
            if (expectedResult) {
                int number = numbers.get(i);
                normalized.append(toCircledNumber(number)).append(' ').append(content);
            } else {
                normalized.append(numbers.get(i)).append(". ").append(content);
            }
        }
        return normalized.toString().trim();
    }

    private static String toCircledNumber(int number) {
        return number >= 1 && number <= 20
                ? String.valueOf((char) ('①' + number - 1))
                : number + ".";
    }


    /** 真实状态取值（用于识别 AI 是否把优先级/环境等非状态内容填进了状态列） */
    private static final Set<String> STATUS_VALUES = new HashSet<>(List.of(
            "待评审", "待执行", "未执行", "执行中", "已执行",
            "通过", "未通过", "已通过", "失败", "阻塞", "跳过", "已完成", "取消"));

    /** 测试类型取值（用于识别类型列是否被 AI 填错） */
    private static final Set<String> TYPE_VALUES = new HashSet<>(List.of(
            "功能测试", "接口测试", "性能测试", "安全测试", "兼容性测试",
            "UI测试", "自动化测试", "回归测试", "冒烟测试", "集成测试", "单元测试"));

    /** 判断是否为优先级取值（P0/P1/P2/P3 或 高/中/低） */
    private static boolean isPriorityValue(String v) {
        if (v == null) return false;
        String s = v.trim();
        if (s.matches("(?i)P[0-4]")) return true;
        return s.equals("高") || s.equals("中") || s.equals("低");
    }

    /** 判断是否为真实的状态取值 */
    private static boolean isStatusValue(String v) {
        return v != null && STATUS_VALUES.contains(v.trim());
    }

    /** 判断是否为测试类型取值 */
    private static boolean isTypeValue(String v) {
        return v != null && TYPE_VALUES.contains(v.trim());
    }

    /** 判断是否为 AI 回显的列头行（如 "标题|所属分组|创建人|..."），是则跳过该行 */
    private static boolean isTemplateHeaderRow(String[] parts, com.example.demo.TestCaseTemplate template) {
        if (parts == null || parts.length == 0) return false;
        String first = safeGet(parts, 0);
        if (first.isEmpty()) return false;
        if (template != null) {
            for (com.example.demo.TestCaseTemplate.TemplateField f : template.getFields()) {
                if (first.equals(f.getDisplayName().trim())) return true;
            }
        }
        // 常见列头兜底（兼容模板未覆盖的情况）
        return first.equals("标题") || first.equals("用例标题")
                || first.equals("测试大模块") || first.equals("测试模块")
                || first.equals("用例编号") || first.equals("字段");
    }

    /**
     * 自定义模板列内容修复：应对 AI 输出列错位
     * - 状态(status)：非真实状态值一律纠正为「待评审」
     * - 等级/优先级(priority)：列值不是优先级时，从整行找回 P0/P1/P2 填入
     * - 类型(caseType)：非测试类型值或为空 → 默认「功能测试」
     * - 自动化(isAutomated)：仅保留 是/否/部分，其余清空
     */
    private static void repairCustomTemplateRow(TestCase tc, com.example.demo.TestCaseTemplate template) {
        if (template == null || tc == null) return;
        // 1) 从整行找回优先级值（AI 可能把 P0/P1/P2 填到任意列，如"状态"列）
        String priorityValue = null;
        for (com.example.demo.TestCaseTemplate.TemplateField f : template.getFields()) {
            String v = tc.getField(f.getFieldName());
            if (v != null && isPriorityValue(v)) { priorityValue = v; break; }
        }
        // 2) 逐字段修复
        for (com.example.demo.TestCaseTemplate.TemplateField f : template.getFields()) {
            String name = f.getFieldName();
            String val = tc.getField(name);
            if ("status".equals(name)) {
                // 状态是系统评审字段，不采用模型生成的执行状态
                tc.setField(name, DEFAULT_STATUS);
            } else if ("priority".equals(name)) {
                if (priorityValue != null && !isPriorityValue(val)) tc.setField(name, priorityValue);
            } else if ("caseType".equals(name)) {
                if (val == null || !isTypeValue(val)) tc.setField(name, "功能测试");
            } else if ("isAutomated".equals(name)) {
                String sv = val == null ? "" : val.trim();
                if (!sv.isEmpty() && !sv.equals("是") && !sv.equals("否") && !sv.equals("部分")) {
                    tc.setField(name, "");
                }
            } else if ("expectedStatusCode".equals(name)) {
                // 数字字段规范化：非纯数字（如 "返回200+{...}"）提取数字，避免后续 parseInt 崩溃
                if (val != null && !val.trim().isEmpty() && !val.trim().matches("\\d+")) {
                    Integer code = TestCase.extractStatusCode(val);
                    tc.setField(name, code != null ? code.toString() : "");
                }
            } else if ("expectedBusinessCode".equals(name)) {
                if (val != null && !val.trim().isEmpty() && !val.trim().matches("\\d+")) {
                    Integer code = TestCase.extractInteger(val);
                    tc.setField(name, code != null ? code.toString() : "");
                }
            }
        }
    }

    /** Controller 调用前设置，生成后自动清空 */
    public static void setCustomTemplate(String t) {
        currentCustomTemplate = (t != null && !t.trim().isEmpty()) ? t.trim() : null;
        currentTemplate = currentCustomTemplate != null
                ? com.example.demo.TestCaseTemplate.fromColumns(currentCustomTemplate)
                : null;
    }

    public static String getCustomTemplate() { return currentCustomTemplate; }

    public static com.example.demo.TestCaseTemplate getCurrentTemplate() { return currentTemplate; }

    /**
     * 重置计数器（每次开始处理新文档时调用）
     */
    public static void resetCounter() {
        globalModuleCounter.clear();
        globalSequenceCounter = 1;
        apiCaseCap = 0; // 新请求默认关闭接口用例数量上限，避免影响 /generate-text
    }

    /**
     * 接口文档流程：裁剪整份文档用例总量（超出预算优先保留 P0/P1），供控制器调用
     */
    public static List<TestCase> enforceApiTotalBudget(List<TestCase> cases) {
        if (cases == null || cases.size() <= API_TOTAL_BUDGET) return cases;
        int before = cases.size();
        List<TestCase> trimmed = trimLowPriority(cases, API_TOTAL_BUDGET);
        log.info("接口用例总量 {} 条超过预算 {}，已裁剪保留 {} 条（优先 P0/P1）", before, API_TOTAL_BUDGET, trimmed.size());
        return trimmed;
    }

    private static String promptExtractInterfaces;
    private static String promptExtractInterfacesSimple;
    private static String promptGenerateTestCases;
    private static String promptGenerateTestCasesFromRequirement;

    static {
        loadConfig();
    }

    private static void loadConfig() {
        try {
            InputStream is = AiService.class.getClassLoader().getResourceAsStream("application.yml");
            if (is == null) {
                log.warn("未找到 application.yml 配置文件，使用默认配置");
                return;
            }

            log.info("开始加载 application.yml 配置文件");

            Yaml yaml = new Yaml();
            Map<String, Object> config = yaml.load(is);
            log.info("配置文件加载成功，顶层键: {}", config.keySet());

            if (!config.containsKey("ai")) {
                log.warn("配置文件中未找到 'ai' 节点，使用默认配置");
                return;
            }

            Object aiObj = config.get("ai");
            if (!(aiObj instanceof Map)) {
                log.warn("'ai' 节点不是 Map 类型，使用默认配置");
                return;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> aiConfig = (Map<String, Object>) aiObj;

            if (aiConfig.containsKey("api-key")) {
                String apiKeyFromConfig = (String) aiConfig.get("api-key");
                String resolvedKey = resolvePlaceholder(apiKeyFromConfig);
                if (resolvedKey != null && !resolvedKey.isEmpty()) {
                    API_KEY = resolvedKey;
                    log.info("从配置文件加载 API Key，长度: {} 字符", API_KEY.length());
                }
            }

            if (aiConfig.containsKey("model")) {
                Object modelObj = aiConfig.get("model");
                if (modelObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> modelConfig = (Map<String, Object>) modelObj;
                    if (modelConfig.containsKey("provider")) {
                        String providerFromConfig = (String) modelConfig.get("provider");
                        String resolvedProvider = resolvePlaceholder(providerFromConfig);
                        if (resolvedProvider != null && !resolvedProvider.isEmpty()) {
                            MODEL_PROVIDER = resolvedProvider;
                            log.info("从配置文件加载模型提供商: {}", MODEL_PROVIDER);
                        }
                    }
                }
            }

            if (!aiConfig.containsKey("prompts")) {
                log.warn("ai 配置中未找到 'prompts' 节点，使用默认Prompts");
                return;
            }

            if (aiConfig.containsKey("case-budget")) {
                try {
                    int budget = Integer.parseInt(String.valueOf(aiConfig.get("case-budget")));
                    if (budget >= 50) {
                        CASE_BUDGET = budget;
                        log.info("从配置文件加载用例总量预算: {} 条", CASE_BUDGET);
                    } else {
                        log.warn("case-budget 配置过小({})，使用默认 {}", budget, CASE_BUDGET);
                    }
                } catch (Exception e) {
                    log.warn("case-budget 配置无效，使用默认 {}", CASE_BUDGET);
                }
            }

            Object promptsObj = aiConfig.get("prompts");
            if (!(promptsObj instanceof Map)) {
                log.warn("'prompts' 节点不是 Map 类型，使用默认Prompts");
                return;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> promptsConfig = (Map<String, Object>) promptsObj;
            log.info("Prompts 配置节点键: {}", promptsConfig.keySet());

            if (promptsConfig.containsKey("extract-interfaces")) {
                promptExtractInterfaces = (String) promptsConfig.get("extract-interfaces");
                log.info("成功加载 extract-interfaces Prompt，长度: {} 字符", promptExtractInterfaces.length());
            }

            if (promptsConfig.containsKey("extract-interfaces-simple")) {
                promptExtractInterfacesSimple = (String) promptsConfig.get("extract-interfaces-simple");
                log.info("成功加载 extract-interfaces-simple Prompt，长度: {} 字符", promptExtractInterfacesSimple.length());
            }

            if (promptsConfig.containsKey("generate-testcases")) {
                promptGenerateTestCases = (String) promptsConfig.get("generate-testcases");
                log.info("成功加载 generate-testcases Prompt，长度: {} 字符", promptGenerateTestCases.length());
            }

            if (promptsConfig.containsKey("generate-testcases-from-requirement")) {
                promptGenerateTestCasesFromRequirement = (String) promptsConfig.get("generate-testcases-from-requirement");
                log.info("成功加载 generate-testcases-from-requirement Prompt，长度: {} 字符", promptGenerateTestCasesFromRequirement.length());
            }

            log.info("已从配置文件加载所有配置");

        } catch (Exception e) {
            log.warn("加载配置文件失败，使用默认配置: {}", e.getMessage(), e);
        }

        if (promptExtractInterfaces == null) {
            log.info("使用默认 extract-interfaces Prompt");
            promptExtractInterfaces = """
                请从以下需求文档中提取所有API接口定义。

                要求：
                1. 每个接口单独一行
                2. 格式：方法 路径\n参数列表（如果有）
                3. 参数格式：参数名:类型:约束:说明

                示例输出：
                POST /api/user/login
                参数：
                  username: 字符串:必填,最大20字符:用户名
                  password: 字符串:必填,最大50字符:密码

                GET /api/user/list
                参数：
                  page: 整数:可选,默认1:页码
                  size: 整数:可选,默认10:每页大小

                文档内容：
                {DOC_CONTENT}
                """;
        }

        if (promptExtractInterfacesSimple == null) {
            log.info("使用默认 extract-interfaces-simple Prompt");
            promptExtractInterfacesSimple = """
                请从以下需求文档中提取所有API接口定义。
                输出格式（每行一个接口）：
                方法 路径|参数名1:类型:说明|参数名2:类型:说明|...

                如果没有找到接口，输出"未找到接口定义"。

                文档内容：
                {DOC_CONTENT}
                """;
        }

        if (promptGenerateTestCases == null) {
            log.info("使用默认 generate-testcases Prompt");
            promptGenerateTestCases = """
                你是一位资深测试工程师。请为以下接口设计【后端接口自动化用例】。

                【事实边界】
                1. 请求路径、方法、参数名和值、账号、URL、事件ID、业务码、响应字段和数据库事实只能来自接口文档；文档没有提供时不要猜测，不要使用本提示词中的示例作为实际数据。
                2. 文档缺少具体值时，使用“文档未指定”或留空，不得写入虚构的可执行值。

                红线规则：
                1. 1个独立测试点 = 1条用例
                2. **输出必须严格10个字段用|分隔**：测试模块|测试点|接口URL|请求方法|请求头|请求参数/请求体|前置条件|具体操作|预期结果(含状态码+响应体)|优先级

                必须覆盖的场景：
                1. 功能验证：核心业务流程走通
                2. 参数校验：必填项、格式、数据类型
                3. 边界值：长度边界、数值边界
                4. 异常场景：空值、非法值、越权、重复提交

                字段规范：
                - 测试模块：从接口路径识别，如/admin/employee→EMPLOYEE，大写英文
                - 测试点：功能验证/参数校验/边界值/异常场景
                - 接口URL：完整路径
                - 请求方法：GET/POST/PUT/DELETE
                - 请求头：Content-Type等，没有填"无"
                - 请求参数/请求体：JSON或表单格式
                - 前置条件：简要描述前提
                - 具体操作：测试步骤
                - 预期结果：HTTP状态码 + 响应体关键字段
                - 优先级：P0/P1/P2

                输出示例（仅说明字段结构，禁止将示例中的值复制到实际用例）：
                [测试模块]|[测试点]|[接口URL]|[请求方法]|[请求头]|[请求参数/请求体]|[前置条件]|[具体操作]|[预期结果]|[优先级]

                接口文档：
                {INTERFACE_DOC}
                """;
        }

        if (promptGenerateTestCasesFromRequirement == null) {
            log.info("使用默认 generate-testcases-from-requirement Prompt");
            promptGenerateTestCasesFromRequirement = """
                你是一位资深测试工程师。请根据以下需求文档，生成【UI业务功能用例】。

                【事实边界与步骤对应规则】
                1. 只能使用需求文档中明确出现或能够直接概括的页面、操作、字段、状态和埋点信息；不得引用提示词示例、常识或其他文档中的账号、URL、事件ID、参数名、参数值、错误码、时间阈值、数量和固定文案。
                2. 原文没有具体值时，不要猜测，也不要添加“待补充”“参数待确认”等伪字段；在已有字段中写“文档未指定”或“由测试环境提供”，也可以留空。
                3. 测试步骤必须使用连续编号（1.、2.、3.）；预期结果必须使用相同编号，第N条预期只验证第N步动作产生的结果，禁止将多个步骤合并到一个预期编号。

                ⚠️ 红线规则（必须严格遵守）：
                1. 这是需求文档的功能描述，不是API接口文档，不要生成接口测试用例
                2. 1个独立测试点 = 1条用例，一条用例只能验证单一目标，禁止多业务流程合并
                3. 禁止过粗：不能把多环节业务塞进1条用例（失败无法定位）
                4. 禁止过细：不要同质化参数校验过度拆分，避免大量重复冗余用例
                5. 编号由系统自动生成，不要输出用例编号
                6. 测试点分类必须严格使用枚举值：正向/参数校验/边界/异常/UI校验/权限校验
                7. 优先级必须使用：P0/P1/P2
                8. 所有预期结果必须量化，包含明确判定标准（数字、具体值、状态），禁止模糊描述
                
                必须覆盖的场景清单（至少覆盖以下类型）：
                1. 正向：核心业务流程正常走通
                2. 参数校验：必填项、格式校验、数据类型校验
                3. 边界：字段长度边界、数量边界、日期边界
                4. 异常：空值、非法值、重复提交、数据不存在
                5. UI校验：页面刷新持久化、多端适配（PC/平板/手机）、文案固定性、布局正确性
                6. 权限校验：越权访问、无权限操作、角色权限边界
                
                字段填写规范：
                - 测试大模块和子模块只能根据需求文档填写
                - 前置条件、预置测试数据、环境和版本如果原文未提供，填写“文档未指定”或留空
                - 操作步骤必须包含连续数字编号；量化预期结果必须使用对应编号逐步描述
                - 不能使用任何示例中的账号、URL、订单号或其他具体值
                
                优先级评估标准：
                - P0：核心业务流程、主功能验证、严重安全漏洞、导致系统崩溃的场景、认证授权失败
                - P1：边界值、一般异常、普通数据校验、UI适配、权限越权
                - P2：次要功能、非核心场景、兼容性细节、文案校验
                
                输出格式（每个用例一行，字段用|分隔，不要输出用例编号！编号由系统自动生成）：
                测试大模块|子模块|测试点分类|用例标题|前置条件|预置测试数据|操作步骤|量化预期结果|优先级|测试环境|版本|实际结果|缺陷单号|测试日期
                只输出符合格式的用例行，不要输出解释或示例。

                需求文档：
                {REQUIREMENT_DOC}
                """;
        }
    }

    /**
     * 解析 ${ENV_VAR:default} 格式的占位符
     * 支持从环境变量或系统属性中读取值
     */
    private static String resolvePlaceholder(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }

        // 检查是否包含 ${...} 格式
        if (!value.contains("${") || !value.contains("}")) {
            return value; // 不是占位符，直接返回
        }

        // 解析 ${VAR_NAME:default} 格式
        int start = value.indexOf("${") + 2;
        int end = value.indexOf("}");
        String placeholderContent = value.substring(start, end);

        // 分割变量名和默认值
        String varName;
        String defaultValue = null;

        if (placeholderContent.contains(":")) {
            int colonIndex = placeholderContent.indexOf(":");
            varName = placeholderContent.substring(0, colonIndex);
            defaultValue = placeholderContent.substring(colonIndex + 1);
        } else {
            varName = placeholderContent;
        }

        log.debug("解析占位符: {} -> 变量名: {}, 默认值: {}", value, varName, defaultValue);

        // 先从系统属性读取
        String resolvedValue = System.getProperty(varName);

        // 再从环境变量读取
        if (resolvedValue == null || resolvedValue.isEmpty()) {
            resolvedValue = System.getenv(varName);
        }

        // 如果都没找到，使用默认值
        if (resolvedValue == null || resolvedValue.isEmpty()) {
            resolvedValue = defaultValue;
            if (defaultValue != null && !defaultValue.isEmpty()) {
                log.info("使用默认值: {} -> {}", varName, defaultValue);
            }
        } else {
            log.info("从环境变量/系统属性读取: {} (长度: {})", varName, resolvedValue.length());
        }

        return resolvedValue;
    }

    /** 启动时打印各提供商 API Key 是否已配置（只显示是否配置，不输出 Key 内容） */
    private static void logApiKeyConfigStatus() {
        String[][] providers = {
                {"DeepSeek", "DEEPSEEK_API_KEY"},
                {"通义千问", "DASHSCOPE_API_KEY"},
                {"豆包", "DOUBAO_API_KEY"},
                {"豆包(别名)", "ARK_API_KEY"},
                {"智谱GLM", "GLM_API_KEY"},
                {"OpenAI", "OPENAI_API_KEY"},
                {"通用兜底", "AI_API_KEY"}
        };
        StringBuilder sb = new StringBuilder("【API Key 配置状态】 ");
        for (String[] p : providers) {
            String v = readEnvVar(p[1]);
            sb.append(p[0]).append("[").append(p[1]).append("]=")
              .append(v != null ? "✅已配置" : "❌未配置").append(" | ");
        }
        log.info(sb.toString());
    }

    public static void initModel() {
        if (model != null) {
            return;
        }
        logApiKeyConfigStatus();

        String modelProvider = getModelProvider();
        // 按提供商读取对应环境变量 Key（如 DEEPSEEK_API_KEY）；未配置时兜底全局 Key
        String apiKey = resolveProviderApiKey(modelProvider);
        if (apiKey == null || apiKey.isEmpty()) {
            String expectedEnv = providerApiKeyEnvName(modelProvider);
            apiKey = getApiKey();
            log.warn("未找到默认提供商 {} 的 Key 环境变量{}，回退使用全局 API_KEY（可能与模型不匹配，建议配置后重启）",
                    modelProvider, expectedEnv != null ? " " + expectedEnv : "");
        }

        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("⚠️ API Key未配置，AI功能将不可用");
            return;
        }

        log.info("当前模型提供商: {}, API Key: {}", modelProvider, maskApiKey(apiKey));

        if ("deepseek".equalsIgnoreCase(modelProvider)) {
            model = OpenAiChatModel.builder()
                    .apiKey(apiKey)
                    .baseUrl("https://api.deepseek.com")
                    .modelName("deepseek-chat")
                    .build();
            log.info("✅ 使用 DeepSeek 模型");
        } else {
            model = QwenChatModel.builder()
                    .apiKey(apiKey)
                    .modelName("qwen-turbo")
                    .build();
            log.info("✅ 使用阿里云 Qwen 模型");
        }
    }

    private static String getModelProvider() {
        if (MODEL_PROVIDER != null && !MODEL_PROVIDER.isEmpty()) {
            return MODEL_PROVIDER;
        }
        String provider = System.getProperty("AI_MODEL_PROVIDER");
        if (provider != null && !provider.isEmpty()) {
            return provider;
        }
        provider = System.getenv("AI_MODEL_PROVIDER");
        if (provider != null && !provider.isEmpty()) {
            return provider;
        }
        return "qwen";
    }

    private static String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= 8) {
            return "******";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }

    public static ChatLanguageModel getModel() {
        if (model == null) {
            initModel();
        }
        return model;
    }

    /** 当前使用的模型标识（默认 deepseek-chat） */
    private static String currentModelName = "deepseek-chat";

    public static String getCurrentModelName() { return currentModelName; }

    /**
     * 模型名/提供商 → API Key 环境变量名
     * deepseek→DEEPSEEK_API_KEY，qwen→DASHSCOPE_API_KEY，doubao→DOUBAO_API_KEY，
     * glm→GLM_API_KEY，openai/gpt→OPENAI_API_KEY，未匹配返回 null
     */
    private static String providerApiKeyEnvName(String modelName) {
        if (modelName == null) return null;
        String lower = modelName.toLowerCase();
        if (lower.contains("deepseek")) return "DEEPSEEK_API_KEY";
        if (lower.contains("qwen")) return "DASHSCOPE_API_KEY";
        if (lower.contains("doubao") || lower.contains("豆包")) return "DOUBAO_API_KEY";
        if (lower.contains("glm")) return "GLM_API_KEY";
        if (lower.contains("openai") || lower.contains("gpt")) return "OPENAI_API_KEY";
        return null;
    }

    /** 读取环境变量/系统属性，多个候选名取第一个非空值 */
    private static String readEnvVar(String... names) {
        for (String name : names) {
            String v = System.getProperty(name);
            if (v == null || v.isEmpty()) v = System.getenv(name);
            if (v != null && !v.isEmpty()) return v;
        }
        return null;
    }

    /**
     * 按模型名/提供商解析对应的 API Key 环境变量
     * 豆包支持 DOUBAO_API_KEY / ARK_API_KEY 两个别名；未匹配或未配置返回 null
     */
    private static String resolveProviderApiKey(String modelName) {
        if (modelName == null) return null;
        String lower = modelName.toLowerCase();
        if (lower.contains("doubao") || lower.contains("豆包")) {
            return readEnvVar("DOUBAO_API_KEY", "ARK_API_KEY");
        }
        String envName = providerApiKeyEnvName(modelName);
        if (envName == null) return null;
        return readEnvVar(envName);
    }

    /**
     * 切换模型（模块5：多模型切换）
     * 支持 deepseek-chat / qwen-vl-max / doubao-vision / gpt-4o / glm-4v 等
     */
    public static synchronized boolean switchModel(String modelName, String apiKey) {
        if (modelName == null || modelName.trim().isEmpty()) return false;
        String name = modelName.trim();
        log.info("收到模型切换请求: model={}, 显式apiKey={}", name,
                (apiKey != null && !apiKey.isEmpty()) ? "已传" : "未传");
        // 显式传入的 Key 优先；其次按提供商自动读取对应环境变量 Key；最后兜底全局 API_KEY
        String key = (apiKey != null && !apiKey.trim().isEmpty()) ? apiKey.trim()
                : resolveProviderApiKey(name);
        if (key == null || key.isEmpty()) key = API_KEY;
        if (key == null || key.isEmpty()) {
            log.warn("切换模型失败：未提供API Key（{}）", name);
            return false;
        }
        String lower = name.toLowerCase();
        try {
            if (lower.contains("deepseek")) {
                model = OpenAiChatModel.builder().apiKey(key)
                        .baseUrl("https://api.deepseek.com").modelName("deepseek-chat").build();
            } else if (lower.contains("qwen")) {
                model = QwenChatModel.builder().apiKey(key).modelName(name).build();
            } else if (lower.contains("doubao") || lower.contains("豆包")) {
                // 豆包视觉：走 OpenAI 兼容接口
                model = OpenAiChatModel.builder().apiKey(key)
                        .baseUrl("https://ark.cn-beijing.volces.com/api/v3").modelName(name).build();
            } else if (lower.contains("gpt")) {
                model = OpenAiChatModel.builder().apiKey(key).modelName(name).build();
            } else if (lower.contains("glm")) {
                model = OpenAiChatModel.builder().apiKey(key)
                        .baseUrl("https://open.bigmodel.cn/api/paas/v4").modelName(name).build();
            } else {
                model = OpenAiChatModel.builder().apiKey(key).modelName(name).build();
            }
            currentModelName = name;
            String keyEnv = providerApiKeyEnvName(name);
            log.info("✅ 已切换模型: {}（Key来源: {}，key: {}）", name, keyEnv != null ? keyEnv : "全局API_KEY", maskApiKey(key));
            return true;
        } catch (Exception e) {
            log.error("切换模型失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 带重试的模型调用（API失败自动重试3次，间隔2秒）
     */
    public static String generateWithRetry(String prompt) {
        log.info("调用模型 {} 生成测试用例", currentModelName);
        Exception lastErr = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                String r = getModel().generate(prompt);
                if (r != null && !r.trim().isEmpty()) return r;
                log.warn("第 {} 次调用返回空，重试...", attempt);
            } catch (Exception e) {
                lastErr = e;
                log.warn("第 {} 次调用失败: {}", attempt, e.getMessage());
            }
            if (attempt < 3) {
                try { Thread.sleep(2000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }
        }
        log.error("模型调用3次均失败: {}", lastErr != null ? lastErr.getMessage() : "空响应");
        return null;
    }

    public static String getModelProviderName() {
        return getModelProvider();
    }

    public static String getApiKey() {
        if (API_KEY != null && !API_KEY.isEmpty()) {
            return API_KEY;
        }

        String[] envNames = {"DASHSCOPE_API_KEY", "DEEPSEEK_API_KEY", "DOUBAO_API_KEY", "ARK_API_KEY", "GLM_API_KEY", "OPENAI_API_KEY", "AI_API_KEY"};
        for (String envName : envNames) {
            String value = System.getProperty(envName);
            if (value == null || value.isEmpty()) {
                value = System.getenv(envName);
            }
            if (value != null && !value.isEmpty()) {
                API_KEY = value;
                log.info("从环境变量/系统属性读取 API Key: {} (长度: {})", envName, value.length());
                return API_KEY;
            }
        }

        API_KEY = "";
        log.warn("⚠️ API Key未找到！请配置环境变量：DEEPSEEK_API_KEY / DASHSCOPE_API_KEY / DOUBAO_API_KEY / GLM_API_KEY / OPENAI_API_KEY / AI_API_KEY");
        return API_KEY;
    }

    private static void ensureModelReady() {
        if (getModel() == null) {
            throw new AIServiceException("AI_MODEL_NOT_INITIALIZED",
                    "AI模型未初始化，请配置 DEEPSEEK_API_KEY / DASHSCOPE_API_KEY / DOUBAO_API_KEY / GLM_API_KEY / OPENAI_API_KEY / AI_API_KEY 环境变量");
        }
    }

    /**
     * 批量提取所有接口（处理大文档）
     */
    public static List<String> extractAllInterfaces(String docContent) {
        ensureModelReady();

        List<String> allInterfaces = new ArrayList<>();

        if (!containsInterfaceKeywords(docContent)) {
            log.warn("文档中未识别到接口定义关键词（如 /api/、GET、POST 等）");
            return allInterfaces;
        }

        String prompt = promptExtractInterfaces.replace("{DOC_CONTENT}", docContent);

        try {
            String result = generateWithRetry(prompt);
            String[] lines = result.split("\n");

            StringBuilder currentApi = new StringBuilder();
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) continue;

                if (line.matches("^(GET|POST|PUT|DELETE|PATCH)\\s+/.*")) {
                    if (currentApi.length() > 0) {
                        allInterfaces.add(currentApi.toString());
                    }
                    currentApi = new StringBuilder(line);
                } else {
                    currentApi.append("\n").append(line);
                }
            }

            if (currentApi.length() > 0) {
                allInterfaces.add(currentApi.toString());
            }

            allInterfaces.removeIf(api -> api.length() < 5 || !api.contains("/"));

        } catch (Exception e) {
            log.warn("批量提取失败，尝试分块提取: {}", e.getMessage());

            List<String> chunks = splitIntoChunks(docContent);
            for (String chunk : chunks) {
                allInterfaces.addAll(extractInterfaces(chunk));
            }
        }

        // 按「方法+规范化路径」去重：同一接口在文档多处出现（定义/调用顺序表/示例）只保留一次，并过滤 curl 命令行示例
        List<String> uniqueInterfaces = PdfParser.dedupeInterfaces(allInterfaces);

        log.info("共提取到 {} 个唯一接口", uniqueInterfaces.size());
        return uniqueInterfaces;
    }

    /**
     * 检查文档中是否包含接口关键词
     */
    private static boolean containsInterfaceKeywords(String docContent) {
        if (docContent == null || docContent.isEmpty()) {
            return false;
        }

        String[] keywords = {"/api/", "GET ", "POST ", "PUT ", "DELETE ", "PATCH ",
                "接口地址", "请求地址", "API地址", "endpoint"};

        for (String keyword : keywords) {
            if (docContent.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 按章节分块
     */
    private static List<String> splitIntoChunks(String text) {
        List<String> chunks = new ArrayList<>();
        String[] sections = text.split("(?m)^第[一二三四五六七八九十\\d]+章\\s*");

        for (String section : sections) {
            section = section.trim();
            if (section.isEmpty()) continue;

            if (section.length() > CHUNK_SIZE) {
                for (int i = 0; i < section.length(); i += CHUNK_SIZE) {
                    int end = Math.min(i + CHUNK_SIZE, section.length());
                    chunks.add(section.substring(i, end));
                }
            } else {
                chunks.add(section);
            }
        }

        return chunks;
    }

    /**
     * 提取接口定义（单块）
     */
    private static List<String> extractInterfaces(String text) {
        List<String> interfaces = new ArrayList<>();

        String prompt = promptExtractInterfacesSimple.replace("{DOC_CONTENT}", text);

        try {
            String result = generateWithRetry(prompt);
            for (String line : result.split("\n")) {
                line = line.trim();
                if (line.contains("/api/") || line.contains("GET ") ||
                        line.contains("POST ") || line.contains("PUT ") ||
                        line.contains("DELETE ")) {
                    interfaces.add(line);
                }
            }
        } catch (Exception e) {
            log.warn("接口提取失败: {}", e.getMessage());
        }

        return interfaces;
    }

    /**
     * 生成测试用例
     */
    public static List<TestCase> generateTestCases(String interfaceDoc) {
        return generateTestCases(interfaceDoc, "");
    }

    public static List<TestCase> generateTestCases(String interfaceDoc, String customRequirements) {
        List<TestCase> testCases = new ArrayList<>();
        ensureModelReady();

        String prompt = promptGenerateTestCases.replace("{INTERFACE_DOC}", interfaceDoc);
        if (customRequirements != null && !customRequirements.isEmpty()) {
            prompt = customRequirements + "\n\n" + prompt;
        }
        // 测试设计方法要求（模块2）
        prompt = prompt + DESIGN_METHOD_REQUIREMENT;
        // 用例生成约束（模块8：粒度/优先级/非功能性过滤）
        prompt = prompt + GENERATION_CONSTRAINTS;
        // 生成数量要求（模块7）：接口文档流程开启数量硬约束，避免一个接口爆出几十条用例
        if (apiCaseCap > 0) {
            prompt = prompt + "\n【数量硬约束】本接口只生成 " + apiCaseCap + " 条以内测试用例！"
                    + "（1个测试点=1条，用等价类/边界值/场景法合并同类场景，覆盖核心流程即可："
                    + "正向1-2条、参数校验2-3条、边界值1条、异常2-3条，同场景合并为1条代表性用例；"
                    + "禁止拆分等价类、禁止重复冗余、禁止超过" + apiCaseCap + "条，宁少勿滥，优先保证 P0/P1 质量）";
        } else {
            prompt = prompt + "\n请生成约 " + currentGenerateCount + " 条测试用例，优先保证 P0/P1 质量。";
        }
        if (currentTemplate == null) {
            prompt = prompt + "\n每行必须输出全部23列，某列无内容时留空占位（不要省略列、不要减少分隔符），禁止输出Markdown表格。";
        }
        // Few-shot：注入人工修正过的优秀用例，提升生成质量
        String fewShot = FeedbackSystem.getFewShotExamples(5);
        if (!fewShot.isEmpty()) {
            prompt = fewShot + "\n\n" + prompt;
        }
        // 自定义模板：完全替换输出格式定义，AI只输出用户指定的列
        if (currentTemplate != null) {
            String outputFormat = currentTemplate.getOutputFormat();
            int colCount = currentTemplate.getFields().size();
            // 清除 prompt 中默认的输出格式说明和示例行（防止AI混用格式）
            prompt = prompt.replaceAll("(?m)^.*输出格式.*$", "")
                          .replaceAll("(?m)^.*\\d+列.*$", "")
                          .replaceAll("(?m)^.*字段用.*分隔.*$", "")
                          .replaceAll("(?m)^.*输出示例.*$", "")
                          .replaceAll("(?m)^\\s*(系统管理|业务功能|用户管理|EMPLOYEE|ORDER|系统管理\\|).*$", "")
                          .replaceAll("(?m)^.*测试大模块\\|.*$", "")
                          .replaceAll("(?m)^.*测试模块\\|.*$", "");
            // 替换为自定义格式的强制指令
            prompt = prompt + "\n\n" +
                "【输出格式 - 严格遵守】\n" +
                "每行输出且仅输出以下" + colCount + "列，字段间用 | 分隔：\n" +
                outputFormat + "\n\n" +
                "⚠️ 规则：\n" +
                "1. 禁止输出Markdown表格、禁止输出字段名作为数据行、禁止输出任何额外说明文字\n" +
                "2. 每行必须正好" + colCount + "个字段，值直接填，不要带列名\n" +
                "3. 只输出这" + colCount + "列，其他任何格式都不要！";
        }

        try {
            String result = generateWithRetry(prompt);

            log.debug("AI 返回内容: {}", result);

            if (result == null || result.trim().isEmpty()) {
                log.error("AI 返回了空响应");
                return testCases;
            }

            String[] lines = result.split("\n");


            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("---") || line.startsWith(":---") || line.startsWith("```") || line.startsWith("测试模块") || line.startsWith("用例编号") || line.contains("以下是为") || line.startsWith("请注意")) {
                    continue;
                }

                if (line.contains("|") || line.contains("	")) {
                    // 使用 split("\\|", -1) 保留尾部空字符串
                    String[] parts = line.contains("|") ? line.split("\\|", -1) : line.split("\t", -1);
                    // 过滤表头行：前3个字段如果都是默认/自定义表头名则跳过
                    String[] headerNames = {"测试模块", "测试点", "用例标题", "前置条件", "接口路径", "请求方法",
                            "测试大模块", "子模块", "测试点分类", "预置测试数据", "操作步骤", "量化预期结果",
                            "标题", "所属分组", "创建人", "关联需求", "测试步骤", "预期结果", "状态", "等级", "类型"};
                    int headerHits = 0;
                    for (int hi = 0; hi < Math.min(3, parts.length); hi++) {
                        String p = safeGet(parts, hi);
                        for (String hn : headerNames) { if (p.contains(hn)) { headerHits++; break; } }
                    }
                    if (headerHits >= 2) continue;
                    // 过滤表头行和Markdown分隔行：检查前几个字段是否包含字段名或:---
                    if (parts.length >= 3) {
                        String p0 = safeGet(parts, 0), p1 = safeGet(parts, 1), p2 = safeGet(parts, 2);
                        if (p0.isEmpty() || p0.equals("测试模块") || p0.startsWith(":---") || p0.contains("---")
                            || p1.equals("测试点") || p1.startsWith(":---")
                            || p2.equals("用例标题") || p2.equals("接口路径") || p2.startsWith(":---")) {
                            continue;
                        }
                    }
                    // 判定AI输出格式：列数接近模板(±2)视为自定义格式，否则视为默认格式
                    int tplCols = currentTemplate != null ? currentTemplate.getFields().size() : 23;
                    // 默认API布局从左对齐，列数放宽到8：AI输出列不全时也能解析，缺失字段由 repairApiCase 兜底
                    int minCols = currentTemplate != null ? Math.min(tplCols, 23) : 8;
                    boolean isCustomFormat = currentTemplate != null && parts.length <= tplCols + 2;
                    if (parts.length >= minCols) {
                        TestCase tc = new TestCase();

                        if (isCustomFormat) {
                            // 自定义格式：按位置映射到模板字段key
                            for (int i = 0; i < currentTemplate.getFields().size() && i < parts.length; i++) {
                                String key = currentTemplate.getFields().get(i).getFieldName();
                                tc.setField(key, safeGet(parts, i));
                            }
                            // 兜底标准字段
                            tc.setField("testModule", safeGet(parts, 1));
                            tc.setField("title", safeGet(parts, 0));
                            // 列内容修复：状态默认"待评审"、等级取优先级、类型/自动化兜底
                            repairCustomTemplateRow(tc, currentTemplate);
                        } else {
                            // 默认24列API映射
                            tc.setField("testModule", safeGet(parts, 0));
                            tc.setField("testPoint", safeGet(parts, 1));
                            tc.setField("title", safeGet(parts, 2));
                            tc.setField("precondition", safeGet(parts, 3));
                            tc.setField("interfacePath", safeGet(parts, 4));
                            tc.setField("method", safeGet(parts, 5));
                            tc.setField("completeHeaders", safeGet(parts, 6));
                            tc.setField("params", safeGet(parts, 7));
                            tc.setField("expectedResponseJson", safeGet(parts, 10));
                            tc.setField("assertionRules", safeGet(parts, 11));
                            tc.setField("dbValidation", safeGet(parts, 12));
                            tc.setField("priority", priority(safeGet(parts, 13)));
                            // 测试环境/版本默认不填（接口用例由使用者统一填写）
                            tc.setField("testEnvironment", "");
                            tc.setField("version", "");
                            tc.setField("tester", safeGet(parts, 16));
                            tc.setField("defectId", safeGet(parts, 17));
                            tc.setField("testDate", safeGet(parts, 18));
                            tc.setField("apiRelatedUiCaseId", safeGet(parts, 19));
                            tc.setField("actualResult", safeGet(parts, 20));
                            tc.setField("testCaseType", safeGet(parts, 21));
                            tc.setField("testModuleCode", safeGet(parts, 22));

                            String sc = safeGet(parts, 8);
                            if (!sc.isEmpty() && sc.matches("\\d+")) tc.setField("expectedStatusCode", sc);
                            String bc = safeGet(parts, 9);
                            if (!bc.isEmpty() && bc.matches("\\d+")) tc.setField("expectedBusinessCode", bc);
                            // 字段兜底：方法/路径/模块缺失时自动补齐
                            repairApiCase(tc);
                        }

                        // 空模块/空标题行跳过
                        if ((tc.getField("testModule") == null || tc.getField("testModule").isEmpty())
                                && (tc.getField("title") == null || tc.getField("title").isEmpty())) {
                            continue;
                        }
                        // 修正标题/路径互换
                        String title = tc.getField("title");
                        String path = tc.getField("interfacePath");
                        if (title != null && title.contains("/") && (path == null || !path.contains("/"))) {
                            tc.setField("interfacePath", title);
                            tc.setField("title", path);
                        }
                        // 生成编号：测试模块首字母 + 序号（模块切换重新计数），如 EMPLOYEE → E-001
                        String moduleForId = (tc.getField("testModule") != null && !tc.getField("testModule").isEmpty())
                                ? tc.getField("testModule") : "API";
                        tc.setId(generateApiCaseId(moduleForId));
                        tc.setTestCaseType("API");

                        testCases.add(tc);
                    }
                }
            }

            if (testCases.isEmpty()) {
                log.warn("无法解析标准格式，尝试备用解析...");
                testCases.addAll(parseTestCasesFlexible(result));
            }

            if (testCases.isEmpty()) {
                log.error("AI 返回了空的测试用例列表，请检查接口文档格式或 AI 服务状态");
                log.error("接口文档内容: {}", interfaceDoc);
            }

        } catch (Exception e) {
            log.error("API调用失败: {}", e.getMessage(), e);
        }

        // 接口文档流程：数量硬上限，超出的优先保留 P0/P1 裁剪（等价类/边界值已合并，宁少勿滥）
        if (apiCaseCap > 0 && testCases.size() > apiCaseCap) {
            int before = testCases.size();
            testCases = trimLowPriority(testCases, apiCaseCap);
            log.info("接口用例生成 {} 条超过上限 {}，已裁剪保留 {} 条（优先 P0/P1）", before, apiCaseCap, testCases.size());
        }

        return testCases;
    }

    /**
     * 根据需求文档生成测试用例
     */
    public static List<TestCase> generateTestCasesFromRequirement(String requirementDoc) {
        List<TestCase> testCases = new ArrayList<>();
        ensureModelReady();

        String prompt = promptGenerateTestCasesFromRequirement.replace("{REQUIREMENT_DOC}", requirementDoc);
        // 测试设计方法 + 生成约束 + 总量控制（模块8）
        prompt = prompt + DESIGN_METHOD_REQUIREMENT + GENERATION_CONSTRAINTS;
        prompt = prompt + "\n【数量控制】总条数控制在 " + CASE_BUDGET_MIN + "-" + CASE_BUDGET_MAX
                + " 条之间；覆盖范围较大时优先覆盖核心功能，次要功能适度抽样，禁止为凑数拆分用例。";

        try {
            String result = generateWithRetry(prompt);

            log.debug("AI 返回内容: {}", result);

            if (result == null || result.trim().isEmpty()) {
                log.error("AI 返回了空响应");
                return testCases;
            }

            String[] lines = result.split("\n");

            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("---") || line.startsWith("测试大模块") || line.startsWith("用例编号")) {
                    continue;
                }

                if (line.contains("|")) {
                    String[] parts = line.split("\\|", -1);
                    if (parts.length >= 14) {
                        TestCase tc = new TestCase();

                        String parentModule = safeGet(parts, 0);
                        String subModule = safeGet(parts, 1);
                        String testCategory = safeGet(parts, 2);
                        String title = safeGet(parts, 3);
                        String precondition = safeGet(parts, 4);
                        String presetData = safeGet(parts, 5);
                        String steps = safeGet(parts, 6);
                        String expectedResult = safeGet(parts, 7);
                        String priority = safeGet(parts, 8);
                        String testEnvironment = safeGet(parts, 9);
                        String version = safeGet(parts, 10);
                        String actualResult = safeGet(parts, 11);
                        String defectId = safeGet(parts, 12);
                        String testDate = safeGet(parts, 13);

                        tc.setTestCaseType("UI");
                        tc.setField("parentModule", parentModule);
                        tc.setField("subModule", subModule);
                        tc.setField("testCategory", testCategory);
                        tc.setField("testModule", parentModule + "-" + subModule);
                        tc.setField("testModuleCode", getModuleCode(subModule));
                        tc.setField("title", title);
                        tc.setField("precondition", precondition);
                        tc.setField("presetData", presetData);
                        tc.setField("steps", steps);
                        tc.setField("expectedResult", expectedResult);
                        tc.setField("priority", priority.isEmpty() ? "P1" : priority);
                        tc.setField("testEnvironment", testEnvironment);
                        tc.setField("version", version);
                        tc.setField("actualResult", actualResult);
                        tc.setField("defectId", defectId);
                        tc.setField("testDate", testDate);

                        String modulePrefix = "DOC-" + getSubModuleCode(subModule);
                        int count = globalModuleCounter.getOrDefault(modulePrefix, 0) + 1;
                        globalModuleCounter.put(modulePrefix, count);
                        tc.setId(modulePrefix + "-" + String.format("%03d", count));

                        testCases.add(tc);
                    }
                }
            }

            if (testCases.isEmpty()) {
                log.warn("无法解析标准格式，尝试备用解析...");
                testCases.addAll(parseTestCasesFlexible(result));
            }

            if (testCases.isEmpty()) {
                log.error("AI 返回了空的测试用例列表，请检查需求文档格式或 AI 服务状态");
            }

        } catch (Exception e) {
            log.error("API调用失败: {}", e.getMessage(), e);
        }

        // 接口文档流程：数量硬上限，超出的优先保留 P0/P1 裁剪（等价类/边界值已合并，宁少勿滥）
        if (apiCaseCap > 0 && testCases.size() > apiCaseCap) {
            int before = testCases.size();
            testCases = trimLowPriority(testCases, apiCaseCap);
            log.info("接口用例生成 {} 条超过上限 {}，已裁剪保留 {} 条（优先 P0/P1）", before, apiCaseCap, testCases.size());
        }

        return testCases;
    }

    /**
     * 根据需求模块名生成模块前缀（旧方法，兼容用）
     */
    private static String generateModulePrefixFromRequirement(String testModule) {
        if (testModule == null || testModule.isEmpty()) {
            return "REQ";
        }

        if (testModule.contains("用户")) return "USER";
        if (testModule.contains("订单")) return "ORDER";
        if (testModule.contains("支付")) return "PAY";
        if (testModule.contains("商品")) return "PRODUCT";
        if (testModule.contains("登录")) return "LOGIN";
        if (testModule.contains("权限")) return "AUTH";
        if (testModule.contains("管理")) return "ADMIN";
        if (testModule.contains("配置")) return "CONFIG";
        if (testModule.contains("数据")) return "DATA";

        String[] chars = testModule.replaceAll("[\\s\\-\\_]", "").split("");
        StringBuilder prefix = new StringBuilder();
        for (String c : chars) {
            if (c.matches("[A-Za-z]")) {
                prefix.append(c.toUpperCase());
            } else if (c.matches("[\\u4e00-\\u9fa5]")) {
                prefix.append(c.charAt(0));
            }
        }

        return prefix.length() > 0 ? prefix.substring(0, Math.min(6, prefix.length())).toUpperCase() : "REQ";
    }

    private static List<TestCase> parseTestCasesFromRequirementFlexible(String result) {
        List<TestCase> testCases = new ArrayList<>();
        String[] lines = result.split("\n");

        TestCase currentTc = null;
        int caseNum = 1;

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            if (line.matches("^\\d+\\..*")) {
                if (currentTc != null) {
                    testCases.add(currentTc);
                }
                currentTc = new TestCase();
                currentTc.setField("title", line.replaceAll("^\\d+\\.", "").trim());
                currentTc.setId("REQ-" + String.format("%03d", caseNum++));
            } else if (line.startsWith("模块：") || line.startsWith("测试模块：")) {
                if (currentTc != null) {
                    currentTc.setField("testModule", line.replaceAll("^测试?模块[：:]", "").trim());
                }
            } else if (line.startsWith("测试点：")) {
                if (currentTc != null) {
                    currentTc.setField("testPoint", line.replaceAll("^测试点[：:]", "").trim());
                }
            } else if (line.startsWith("前置条件：")) {
                if (currentTc != null) {
                    currentTc.setField("precondition", line.replaceAll("^前置条件[：:]", "").trim());
                }
            } else if (line.startsWith("步骤：") || line.startsWith("操作步骤：")) {
                if (currentTc != null) {
                    currentTc.setField("steps", line.replaceAll("^操作?步骤[：:]", "").trim());
                }
            } else if (line.startsWith("预期：") || line.startsWith("预期结果：")) {
                if (currentTc != null) {
                    currentTc.setField("expectedResult", line.replaceAll("^预期(结果)?[：:]", "").trim());
                }
            } else if (line.startsWith("优先级：")) {
                if (currentTc != null) {
                    currentTc.setField("priority", line.replaceAll("^优先级[：:]", "").trim());
                }
            }
        }

        if (currentTc != null) {
            testCases.add(currentTc);
        }

        return testCases;
    }

    private static String generateModulePrefix(String testModule, String interfacePath) {
        if (testModule != null && !testModule.isEmpty() && testModule.matches("[A-Z]+")) {
            return testModule;
        }

        String path = interfacePath;
        if (path == null || path.isEmpty()) {
            return "TC";
        }

        if (path.contains("/employee")) return "EMPLOYEE";
        if (path.contains("/category")) return "CATEGORY";
        if (path.contains("/user")) return "USER";
        if (path.contains("/login")) return "LOGIN";
        if (path.contains("/logout")) return "LOGOUT";
        if (path.contains("/product")) return "PRODUCT";
        if (path.contains("/order")) return "ORDER";
        if (path.contains("/pay")) return "PAY";
        if (path.contains("/admin")) return "ADMIN";

        String[] segments = path.split("/");
        for (String segment : segments) {
            if (!segment.isEmpty() && segment.length() > 2) {
                return segment.toUpperCase();
            }
        }

        return "TC";
    }

    /**
     * 生成接口用例编号：取测试模块首字母 + 序号（模块切换重新计数），如 EMPLOYEE → E-001
     * 计数器按完整模块名隔离：模块不同则序号从 001 重新开始
     */
    private static String generateApiCaseId(String testModule) {
        String module = (testModule == null || testModule.trim().isEmpty()) ? "API" : testModule.trim();
        String moduleKey = module.toUpperCase();
        char prefix = 'U';
        char first = moduleKey.isEmpty() ? 'U' : moduleKey.charAt(0);
        if (first >= 'A' && first <= 'Z') {
            prefix = first;
        } else {
            // 中文等非英文模块：回退到模块代码首字母（如 员工管理→EMP→E），避免用中文字符作前缀
            String code = getModuleCode(module);
            if (code != null && !code.isEmpty() && code.charAt(0) >= 'A' && code.charAt(0) <= 'Z') {
                prefix = code.charAt(0);
            }
        }
        int count = globalModuleCounter.getOrDefault(moduleKey, 0) + 1;
        globalModuleCounter.put(moduleKey, count);
        return prefix + "-" + String.format("%03d", count);
    }

    /**
     * 判断是否是数字
     */
    private static boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        try {
            Integer.parseInt(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 安全获取数组元素，越界返回空字符串
     */
    private static String safeGet(String[] parts, int index) {
        if (parts == null || index < 0 || index >= parts.length) {
            return "";
        }
        return parts[index] != null ? parts[index].trim() : "";
    }

    /**
     * 质量校验：API用例是否合格
     * 检查：参数值是否真实（不含xxx/test等占位符）、预期结果是否量化
     */
    private static boolean isValidApiTestCase(TestCase tc) {
        if (tc == null) return false;
        String params = tc.getField("params");
        if (params != null && !params.isEmpty()) {
            String lower = params.toLowerCase();
            if (lower.contains("xxx") || lower.contains("\"test\"") || lower.contains("\"abc\"")
                || lower.contains("string") || lower.contains("请填写")) {
                return false;
            }
        }
        String expectedResult = tc.getField("expectedResult");
        if (expectedResult == null || expectedResult.isEmpty()) {
            return false;
        }
        if (expectedResult.length() < 5 || (!expectedResult.contains(" ") && !expectedResult.contains("{"))) {
            return false;
        }
        return true;
    }

    /**
     * 标准化优先级格式：将 AI 输出的各种格式转为 P0/P1/P2
     */
    private static String priority(String p) {
        if (p == null || p.isEmpty()) return "P1";
        p = p.trim();
        if (p.matches("P[012]")) return p;
        if (p.contains("P0") || p.contains("高") || p.contains("High")) return "P0";
        if (p.contains("P2") || p.contains("低") || p.contains("Low")) return "P2";
        return "P1";
    }

    /**
     * 备用解析方法
     */
    private static List<TestCase> parseTestCasesFlexible(String result) {
        List<TestCase> testCases = new ArrayList<>();
        String[] lines = result.split("\n");
        int caseIndex = 1;

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("---") || line.startsWith(":---") || line.startsWith("```") || line.startsWith("测试模块") || line.startsWith("用例编号") || line.contains("以下是为") || line.startsWith("请注意")) {
                continue;
            }

            TestCase tc = new TestCase();
            tc.setTestCaseType("API");
            // 测试环境/版本默认不填
            tc.setField("testEnvironment", "");
            tc.setField("version", "");
            tc.setField("priority", "中");

            String[] parts = null;
            if (line.contains("|")) {
                parts = line.split("\\|", -1);
            } else if (line.contains("	")) {
                parts = line.split("\t", -1);
            }

            // 自定义模板：按位置映射到模板字段key
            if (parts != null && currentTemplate != null && parts.length >= currentTemplate.getFields().size()) {
                // 跳过 AI 回显的列头垃圾行（如 "标题|所属分组|创建人|..."）
                if (isTemplateHeaderRow(parts, currentTemplate)) continue;
                for (int i = 0; i < currentTemplate.getFields().size() && i < parts.length; i++) {
                    String key = currentTemplate.getFields().get(i).getFieldName();
                    tc.setField(key, safeGet(parts, i));
                }
                // 列内容修复：状态默认"待评审"、等级取优先级、类型/自动化兜底
                repairCustomTemplateRow(tc, currentTemplate);
            } else if (parts != null && parts.length >= 5) {
                // 从左对齐标准API布局，兼容 AI 输出部分列（缺失字段由 repairApiCase 兜底）
                tc.setField("testModule", safeGet(parts, 0));
                tc.setField("testPoint", safeGet(parts, 1));
                tc.setField("title", safeGet(parts, 2));
                tc.setField("precondition", safeGet(parts, 3));
                tc.setField("interfacePath", safeGet(parts, 4));
                tc.setField("method", safeGet(parts, 5));
                tc.setField("completeHeaders", safeGet(parts, 6));
                tc.setField("params", safeGet(parts, 7));
                if (isNumeric(safeGet(parts, 8))) {
                    tc.setField("expectedStatusCode", safeGet(parts, 8));
                }
                if (parts.length > 9 && !safeGet(parts, 9).isEmpty() && isNumeric(safeGet(parts, 9))) {
                    tc.setField("expectedBusinessCode", safeGet(parts, 9));
                }
                if (parts.length > 10) tc.setField("expectedResponseJson", safeGet(parts, 10));
                if (parts.length > 11) tc.setField("assertionRules", safeGet(parts, 11));
                if (parts.length > 12) tc.setField("dbValidation", safeGet(parts, 12));
                if (parts.length > 13) tc.setField("priority", safeGet(parts, 13));

                repairApiCase(tc);

                String module = tc.getField("testModule") != null && !tc.getField("testModule").isEmpty() ? tc.getField("testModule") : "API";
                // 编号：测试模块首字母 + 序号（模块切换重新计数），如 EMPLOYEE → E-001
                tc.setId(generateApiCaseId(module));
                tc.setField("testModuleCode", getModuleCode(module));
            } else {
                // 无管道分隔的行：看起来像接口行（含HTTP方法或 /路径）才兜底，否则跳过
                if (!line.matches("(?i).*\\b(GET|POST|PUT|DELETE|PATCH)\\b.*") && !line.startsWith("/")) continue;
                tc.setField("title", line.length() > 50 ? line.substring(0, 50) : line);
                tc.setField("interfacePath", line);
                repairApiCase(tc);
                tc.setId("TC-F" + String.format("%04d", caseIndex++));
            }

            testCases.add(tc);
        }

        return testCases;
    }

    /**
     * API用例字段兜底修复：方法/接口路径/测试模块缺失时自动补齐，
     * 保证脚本生成所需的关键字段（method/interfacePath/params）始终可用。
     */
    private static void repairApiCase(TestCase tc) {
        if (tc == null) return;

        // 1) 方法兜底：缺失时从各字段中识别方法关键字，否则默认 POST
        String method = tc.getField("method");
        if (method == null || method.trim().isEmpty()) {
            String m = findMethodInFields(tc);
            tc.setField("method", m != null ? m : "POST");
        } else {
            tc.setField("method", method.trim().toUpperCase());
        }

        // 2) 接口路径兜底：为空或不像路径时，从字段中提取以 / 开头的路径
        String path = tc.getField("interfacePath");
        String cleaned = path != null ? cleanPath(path) : null;
        if (cleaned == null) {
            cleaned = extractPathFromFields(tc);
        }
        tc.setField("interfacePath", cleaned != null ? cleaned : "/api/unknown");

        // 3) 列错位修复：AI 省略"请求头"列时，参数 JSON 被放到 completeHeaders、状态码被当成 params
        String params = tc.getField("params");
        String status = tc.getField("expectedStatusCode");
        String headers = tc.getField("completeHeaders");
        if (params != null && !params.trim().isEmpty() && params.trim().matches("\\d+")
                && (status == null || status.trim().isEmpty())) {
            // params 是纯数字 → 是状态码，交换
            tc.setField("expectedStatusCode", params.trim());
            tc.setField("params", "");
        }
        if (headers != null && headers.trim().startsWith("{") && (tc.getField("params") == null || tc.getField("params").isEmpty())) {
            // completeHeaders 看起来是 JSON 参数体 → 挪到 params
            tc.setField("params", headers);
            tc.setField("completeHeaders", "");
        }

        // 4) 测试模块兜底
        if (tc.getField("testModule") == null || tc.getField("testModule").trim().isEmpty()) {
            tc.setField("testModule", "API");
        }
    }

    /**
     * 从用例各字段中识别 HTTP 方法关键字
     */
    private static String findMethodInFields(TestCase tc) {
        String[] keys = {"interfacePath", "title", "steps", "params", "precondition"};
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\b(GET|POST|PUT|DELETE|PATCH)\\b", java.util.regex.Pattern.CASE_INSENSITIVE);
        for (String key : keys) {
            String v = tc.getField(key);
            if (v == null) continue;
            java.util.regex.Matcher m = p.matcher(v);
            if (m.find()) return m.group(1).toUpperCase();
        }
        return null;
    }

    /**
     * 从用例各字段中提取以 / 开头的接口路径（兼容 "POST /api/login"、"http://host/api" 等写法）
     */
    private static String extractPathFromFields(TestCase tc) {
        String[] keys = {"interfacePath", "params", "title", "steps", "precondition"};
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("(?:https?://[^/\\s]+)?(/[\\w\\-{}.:$/?=&]+)");
        for (String key : keys) {
            String v = tc.getField(key);
            if (v == null) continue;
            java.util.regex.Matcher m = p.matcher(v);
            while (m.find()) {
                String path = m.group(1);
                if (path.length() > 1 && !path.equals("/")) return path;
            }
        }
        return null;
    }

    /**
     * 规范化路径：剥离前导 HTTP 方法/装饰字符、截断到第一个空白、去尾部标点
     */
    private static String cleanPath(String path) {
        if (path == null) return null;
        String p = path.trim();
        if (p.isEmpty()) return null;
        // 剥离前导 HTTP 方法（如 "POST /api/login"）
        p = p.replaceFirst("^(?i)(GET|POST|PUT|DELETE|PATCH)\\s+", "");
        // 剥离前导引号/反引号/括号/星号等装饰
        p = p.replaceFirst("^[`\"'()<>\\[\\]*\\s]+", "");
        // 取第一个空白前的内容
        int sp = p.indexOf(' ');
        if (sp > 0) p = p.substring(0, sp);
        // 去掉尾部常见标点
        p = p.replaceAll("[,;.，。;]$", "");
        return (p.startsWith("/") || p.startsWith("http")) ? p : null;
    }

    /**
     * 根据需求文档按章节分段生成测试用例
     * @param sections 章节列表（包含标题和内容）
     * @return 测试用例列表
     */
    public static List<TestCase> generateTestCasesFromRequirementBySections(List<WordParser.SectionContent> sections) {
        List<TestCase> allTestCases = new ArrayList<>();
        ensureModelReady();

        if (sections == null || sections.isEmpty()) {
            log.warn("需求章节为空，跳过生成");
            return allTestCases;
        }
        String requirementEvidence = sections.stream()
                .map(s -> (s.getTitle() == null ? "" : s.getTitle()) + "\n"
                        + (s.getContent() == null ? "" : s.getContent()))
                .reduce("", (left, right) -> left + "\n" + right);

        log.info("开始按章节生成测试用例，共 {} 个章节/段落", sections.size());
        int sectionIndex = 1;

        // Few-shot：注入人工修正用例提升质量
        String fewShot = FeedbackSystem.getFewShotExamples(5);
        // 自定义模板输出格式说明
        String customFormatNote = "";
        if (currentTemplate != null) {
            customFormatNote = "\n\n【特别注意】输出格式改为以下" + currentTemplate.getFields().size()
                    + "列，字段用|分隔：\n" + currentTemplate.getOutputFormat()
                    + "\n请严格遵守：每行输出这" + currentTemplate.getFields().size() + "个字段，不要输出多余内容！";
        }

        // 用例总量控制（模块8）：整份文档预算 CASE_BUDGET，按剩余章节均摊，动态调整
        int remainingBudget = CASE_BUDGET;
        int remainingSections = Math.max(1, sections.size());

        for (WordParser.SectionContent section : sections) {
            log.info("处理章节 [{}/{}]: {}", sectionIndex, sections.size(), section.getTitle());

            // 本节配额 = 剩余预算 / 剩余章节数（下限4条，避免过少）
            int quota = Math.max(4, Math.round(remainingBudget / (float) remainingSections));
            String sectionPrompt = buildRequirementSectionPrompt(section.getTitle(), section.getContent(), quota, remainingBudget);
            sectionPrompt = sectionPrompt + DESIGN_METHOD_REQUIREMENT;
            sectionPrompt = sectionPrompt + GENERATION_CONSTRAINTS;
            if (!fewShot.isEmpty()) sectionPrompt = fewShot + "\n\n" + sectionPrompt;
            if (!customFormatNote.isEmpty()) sectionPrompt = sectionPrompt.replaceAll("(?m)^.*输出格式.*$", "")
                    .replaceAll("(?m)^.*\\d+列.*$", "") + customFormatNote;
            try {
                String result = generateWithRetry(sectionPrompt);
                log.debug("章节 {} AI返回内容长度: {}", section.getTitle(), result != null ? result.length() : 0);

                if (result != null && !result.trim().isEmpty()) {
                    List<TestCase> sectionTestCases = parseRequirementTestCases(result, section.getTitle());
                    List<TestCase> validSectionCases = validateRequirementCases(sectionTestCases, requirementEvidence);
                    allTestCases.addAll(validSectionCases);
                    remainingBudget = Math.max(0, remainingBudget - validSectionCases.size());
                    log.info("章节 {} 生成 {} 条，质量校验通过 {} 条，过滤 {} 条，剩余预算 {} 条",
                            section.getTitle(), sectionTestCases.size(), validSectionCases.size(),
                            sectionTestCases.size() - validSectionCases.size(), remainingBudget);
                } else {
                    log.warn("章节 {} AI返回空响应，跳过", section.getTitle());
                }
            } catch (Exception e) {
                log.error("处理章节 {} 失败: {}", section.getTitle(), e.getMessage());
            }

            remainingSections = Math.max(1, remainingSections - 1);
            sectionIndex++;
        }

        // 若总量仍超预算，裁剪低优先级(P2)用例，保留 P0/P1（模块8：P2 可折叠忽略）
        if (allTestCases.size() > CASE_BUDGET) {
            int before = allTestCases.size();
            allTestCases = trimLowPriority(allTestCases, CASE_BUDGET);
            log.info("用例总量 {} 条超过预算 {} 条，已裁剪低优先级用例，保留 {} 条", before, CASE_BUDGET, allTestCases.size());
        }

        log.info("所有章节处理完成，共生成 {} 条测试用例", allTestCases.size());
        return allTestCases;
    }

    private static List<TestCase> validateRequirementCases(List<TestCase> candidates, String requirementText) {
        if (candidates == null || candidates.isEmpty()) return new ArrayList<>();
        List<TestCase> valid = new ArrayList<>();
        int filtered = 0;
        for (TestCase tc : candidates) {
            TestCaseQualityValidator.ValidationResult result =
                    TestCaseQualityValidator.validateUiRequirement(tc, requirementText);
            if (result.isValid()) {
                valid.add(tc);
            } else {
                filtered++;
                String codes = result.getIssues().stream()
                        .map(TestCaseQualityValidator.ValidationIssue::getCode)
                        .distinct()
                        .reduce((a, b) -> a + "," + b)
                        .orElse("UNKNOWN");
                log.warn("过滤需求用例 [{}]，质量问题: {}", tc != null ? tc.getField("title") : "空用例", codes);
            }
        }
        if (filtered > 0) {
            log.info("需求用例质量校验完成：通过 {} 条，过滤 {} 条", valid.size(), filtered);
        }
        return valid;
    }

    /**
     * 构建章节测试用例生成的Prompt（UI业务功能用例）
     */
    private static String buildRequirementSectionPrompt(String sectionTitle, String sectionContent, int quota, int remainingBudget) {
        return """
            你是一位资深测试工程师。请根据以下需求章节，生成【UI业务功能用例】。

            【事实边界与步骤对应规则】
            1. 只能使用“章节内容”中明确出现或能够直接概括的页面、操作、字段、状态和埋点信息；不得引用提示词示例、常识或其他章节之外的账号、URL、事件 ID、参数名、参数值、错误码、时间阈值、数量和固定文案。
            2. 原文没有给出具体值时，不要猜测具体值，也不要添加“待补充”“参数待确认”等伪字段；在允许的字段中写“文档未指定”或“由测试环境提供”这类中性说明即可。
            3. 测试步骤必须使用连续编号（1.、2.、3.）；预期结果必须使用相同编号（①/1.、②/2.、③/3.均可），第N条预期只验证第N步动作产生的结果。一个步骤可以包含多个断言，但禁止把多个步骤合并到一个预期编号。
            4. 埋点场景按“执行操作→打开Debug日志→筛选并查看日志”的顺序拆分步骤，并分别验证操作结果、日志页面可用性、日志记录及需求规定的参数完整性；事件 ID 与参数只按原文校验，原文未给出时不得编造。

            ⚠️ 红线规则（必须严格遵守）：
            1. 这是需求文档的功能描述，不是API接口文档，不要生成接口测试用例，更不要为字段说明/背景介绍生成用例
            2. 用例粒度：1个独立测试点 = 1条用例。同一类测试场景只生成1条代表性用例，禁止重复冗余
            3. 编号由系统自动生成，不要输出用例编号
            4. 测试点分类必须严格使用枚举值：正向/参数校验/边界/异常/UI校验/权限校验
            5. 优先级必须使用 P0/P1/P2
            6. 预期结果必须可观察、可判定，不得只写“操作成功”“页面正常”“数据正确”
            7. 输出必须严格14个字段用|分隔，顺序与下方格式一致，末尾空字段保留分隔符
            8. 数量按本节配额生成，功能点不足时宁可少生成，不得通过编造事实凑数

            当前章节标题：%s

            章节内容（事实来源仅限以下文本）：
            %s

            输出格式（每个用例一行，字段用|分隔，不要输出用例编号）：
            测试大模块|子模块|测试点分类|用例标题|前置条件|预置测试数据|操作步骤|量化预期结果|优先级|测试环境|版本|实际结果|缺陷单号|测试日期

            输出要求：前置条件、预置测试数据、测试环境、版本等字段如果原文没有提供具体内容，填写“文档未指定”或留空；不要使用任何示例中的具体账号、URL、订单号、事件ID或参数。
            """.formatted(sectionTitle, sectionContent);
    }

    /**
     * 裁剪低优先级用例：总量超预算时优先保留 P0/P1，P2 从尾部裁剪（模块8：P2 可选折叠）
     */
    private static List<TestCase> trimLowPriority(List<TestCase> cases, int budget) {
        if (cases == null || cases.size() <= budget) return cases;
        List<TestCase> p0p1 = new ArrayList<>();
        List<TestCase> p2 = new ArrayList<>();
        for (TestCase tc : cases) {
            if ("P2".equals(tc.getField("priority"))) {
                p2.add(tc);
            } else {
                p0p1.add(tc);
            }
        }
        List<TestCase> ordered = new ArrayList<>(p0p1);
        ordered.addAll(p2);
        return new ArrayList<>(ordered.subList(0, Math.min(budget, ordered.size())));
    }

    /**
     * 解析需求测试用例响应（UI业务功能用例）
     */
    private static List<TestCase> parseRequirementTestCases(String result, String sectionTitle) {
        List<TestCase> testCases = new ArrayList<>();
        String[] lines = result.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("---") || line.startsWith(":---")
                || line.startsWith("测试大模块") || line.startsWith("子模块")
                || line.startsWith("用例编号")) continue;
            String[] parts = null;
            if (line.contains("|")) parts = line.split("[|]", -1);
            else if (line.contains("	")) parts = line.split("\t", -1);
            int minCols = currentTemplate != null ? currentTemplate.getFields().size() : 14;
            if (parts != null && parts.length >= minCols) {
                // 跳过 AI 回显的列头垃圾行（如 "标题|所属分组|创建人|..."）
                if (isTemplateHeaderRow(parts, currentTemplate)) continue;
                // 跳过 Markdown 表格行（首字段为空，如 "| 系统管理 | 用户管理 |"）
                if (safeGet(parts, 0).isEmpty() && safeGet(parts, 1).isEmpty()) continue;
                TestCase tc = new TestCase();
                tc.setTestCaseType("UI");
                if (currentTemplate != null) {
                    // 自定义模板：按位置映射到模板字段key
                    for (int i = 0; i < currentTemplate.getFields().size() && i < parts.length; i++) {
                        String key = currentTemplate.getFields().get(i).getFieldName();
                        tc.setField(key, safeGet(parts, i));
                    }
                    // 列内容修复：状态默认"待评审"、等级取优先级、类型/自动化兜底
                    repairCustomTemplateRow(tc, currentTemplate);
                } else {
                    // 默认14列UI映射
                    tc.setField("parentModule", safeGet(parts, 0));
                    tc.setField("subModule", safeGet(parts, 1));
                    tc.setField("testCategory", safeGet(parts, 2));
                    tc.setField("title", safeGet(parts, 3));
                    tc.setField("precondition", safeGet(parts, 4));
                    tc.setField("presetData", safeGet(parts, 5));
                    tc.setField("steps", safeGet(parts, 6));
                    tc.setField("expectedResult", safeGet(parts, 7));
                    tc.setField("priority", priority(safeGet(parts, 8)));
                    tc.setField("testEnvironment", safeGet(parts, 9));
                    tc.setField("version", safeGet(parts, 10));
                    tc.setField("actualResult", safeGet(parts, 11));
                    tc.setField("defectId", safeGet(parts, 12));
                    tc.setField("testDate", safeGet(parts, 13));
                }
                String parentModule = tc.getField("parentModule");
                String subModule = tc.getField("subModule");
                if (parentModule != null && subModule != null) {
                    tc.setField("testModule", parentModule + "-" + subModule);
                    tc.setField("testModuleCode", getModuleCode(subModule));
                }
                // 生成编号（优先用子模块，否则用测试模块）
                String modForId = (subModule != null && !subModule.isEmpty()) ? subModule
                        : (tc.getField("testModule") != null ? tc.getField("testModule") : "UI");
                String pfx = "DOC-" + getSubModuleCode(modForId);
                int cnt = globalModuleCounter.getOrDefault(pfx, 0) + 1;
                globalModuleCounter.put(pfx, cnt);
                tc.setId(pfx + "-" + String.format("%03d", cnt));
                testCases.add(tc);
            }
        }
        return testCases;
    }

    /**
     * 从章节标题提取模块名
     */
    private static String extractModuleFromTitle(String title) {
        if (title == null || title.isEmpty()) {
            return "功能测试";
        }
        
        // 移除章节编号
        String cleanTitle = title.replaceAll("^(第[一二三四五六七八九十\\d]+[章节部]|\\d+\\.\\d+(\\.\\d+)*|\\d+[\\s\\.、]|[一二三四五六七八九十]、)", "").trim();
        
        // 常见的业务模块关键词
        String[] businessKeywords = {"管理", "系统", "功能", "模块", "服务", "平台", "中心", "接口", "订单", "用户", "商品", "支付", "库存", "日志", "数据", "报表", "审核", "审批", "流程", "权限", "配置"};
        
        // 智能提取：如果标题包含业务关键词，提取完整模块名
        for (String keyword : businessKeywords) {
            int index = cleanTitle.indexOf(keyword);
            if (index >= 0) {
                // 提取关键词前的完整模块名
                String moduleName = cleanTitle.substring(0, index + keyword.length());
                if (moduleName.length() > 2 && moduleName.length() <= 10) {
                    return moduleName;
                }
            }
        }
        
        // 如果没有匹配到关键词，取前10个字
        if (cleanTitle.length() > 10) {
            return cleanTitle.substring(0, 10);
        }
        return cleanTitle.isEmpty() ? "功能测试" : cleanTitle;
    }

    /**
     * 获取模块代码（用于统一编号）
     */
    private static String getModuleCode(String moduleName) {
        if (moduleName == null || moduleName.isEmpty()) {
            return "FUNC";
        }
        
        Map<String, String> moduleCodeMap = new HashMap<>();
        moduleCodeMap.put("用户管理", "USR");
        moduleCodeMap.put("订单系统", "ORD");
        moduleCodeMap.put("商品管理", "PRD");
        moduleCodeMap.put("支付功能", "PAY");
        moduleCodeMap.put("库存管理", "INV");
        moduleCodeMap.put("权限管理", "PERM");
        moduleCodeMap.put("数据管理", "DATA");
        moduleCodeMap.put("报表管理", "REPT");
        moduleCodeMap.put("配置管理", "CONF");
        moduleCodeMap.put("审核管理", "AUD");
        moduleCodeMap.put("审批管理", "APR");
        moduleCodeMap.put("流程管理", "PROC");
        moduleCodeMap.put("日志管理", "LOG");
        moduleCodeMap.put("系统管理", "SYS");
        moduleCodeMap.put("接口管理", "API");
        moduleCodeMap.put("登录认证", "AUTH");
        moduleCodeMap.put("角色管理", "ROLE");
        moduleCodeMap.put("菜单管理", "MENU");
        moduleCodeMap.put("文件管理", "FILE");
        moduleCodeMap.put("消息管理", "MSG");
        moduleCodeMap.put("通知管理", "NOTI");
        moduleCodeMap.put("数据统计", "STAT");
        moduleCodeMap.put("数据分析", "ANAL");
        moduleCodeMap.put("数据报表", "REPT");
        moduleCodeMap.put("功能测试", "FUNC");
        moduleCodeMap.put("合同管理", "CT");
        moduleCodeMap.put("合同详情", "CTD");
        moduleCodeMap.put("合同", "CT");
        moduleCodeMap.put("员工管理", "EMP");
        moduleCodeMap.put("员工", "EMP");
        moduleCodeMap.put("组织管理", "ORG");
        moduleCodeMap.put("组织", "ORG");
        moduleCodeMap.put("部门管理", "DEPT");
        moduleCodeMap.put("部门", "DEPT");
        moduleCodeMap.put("岗位管理", "POST");
        moduleCodeMap.put("岗位", "POST");
        moduleCodeMap.put("客户管理", "CRM");
        moduleCodeMap.put("客户", "CRM");
        moduleCodeMap.put("供应商管理", "SUPP");
        moduleCodeMap.put("供应商", "SUPP");
        moduleCodeMap.put("财务管理", "FIN");
        moduleCodeMap.put("财务", "FIN");
        moduleCodeMap.put("考勤管理", "ATT");
        moduleCodeMap.put("考勤", "ATT");
        moduleCodeMap.put("公告管理", "NOTI");
        moduleCodeMap.put("公告", "NOTI");
        moduleCodeMap.put("日程管理", "CAL");
        moduleCodeMap.put("日程", "CAL");
        moduleCodeMap.put("审批管理", "APR");
        moduleCodeMap.put("审批", "APR");
        moduleCodeMap.put("薪资管理", "SAL");
        moduleCodeMap.put("薪资", "SAL");
        moduleCodeMap.put("系统设置", "SYS");
        moduleCodeMap.put("登录", "AUTH");
        moduleCodeMap.put("注册", "REG");
        moduleCodeMap.put("首页", "HOME");
        moduleCodeMap.put("工作台", "WS");
        moduleCodeMap.put("个人信息", "PROF");
        moduleCodeMap.put("密码修改", "PWD");
        // API路径对应的模块代码（大写英文）
        moduleCodeMap.put("EMPLOYEE", "EMP");
        moduleCodeMap.put("CATEGORY", "CAT");
        moduleCodeMap.put("DISH", "DSH");
        moduleCodeMap.put("SETMEAL", "SET");
        moduleCodeMap.put("ORDER", "ORD");
        moduleCodeMap.put("SHOPPING", "SHOP");
        moduleCodeMap.put("ADDRESS", "ADDR");
        moduleCodeMap.put("USER", "USR");
        moduleCodeMap.put("ADMIN", "ADMIN");

        for (Map.Entry<String, String> entry : moduleCodeMap.entrySet()) {
            if (moduleName.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        // 中文或其他非ASCII字符的模块名，使用哈希码生成英文代码
        if (moduleName.codePointAt(0) > 127) {
            return "M" + String.format("%03d", Math.abs(moduleName.hashCode() % 1000));
        }

        StringBuilder code = new StringBuilder();
        String[] words = moduleName.split("[\\s\\-_\\|]+");
        for (String word : words) {
            if (!word.isEmpty()) {
                code.append(word.charAt(0));
            }
        }
        return code.length() > 0 ? code.toString().toUpperCase() : "FUNC";
    }

    /**
     * 获取子模块代码（用于DOC-子模块-XXX编号格式）
     */
    private static String getSubModuleCode(String subModule) {
        if (subModule == null || subModule.isEmpty()) {
            return "MOD";
        }

        Map<String, String> codeMap = new HashMap<>();
        codeMap.put("用户管理", "USR");
        codeMap.put("订单管理", "ORD");
        codeMap.put("商品管理", "PRD");
        codeMap.put("支付管理", "PAY");
        codeMap.put("库存管理", "INV");
        codeMap.put("权限管理", "PERM");
        codeMap.put("数据管理", "DATA");
        codeMap.put("报表管理", "REPT");
        codeMap.put("配置管理", "CONF");
        codeMap.put("审核管理", "AUD");
        codeMap.put("审批管理", "APR");
        codeMap.put("流程管理", "PROC");
        codeMap.put("日志管理", "LOG");
        codeMap.put("系统管理", "SYS");
        codeMap.put("接口管理", "API");
        codeMap.put("登录认证", "AUTH");
        codeMap.put("角色管理", "ROLE");
        codeMap.put("菜单管理", "MENU");
        codeMap.put("文件管理", "FILE");
        codeMap.put("消息管理", "MSG");
        codeMap.put("通知管理", "NOTI");
        codeMap.put("数据统计", "STAT");
        codeMap.put("数据分析", "ANAL");
        codeMap.put("订单系统", "ORD");
        codeMap.put("支付功能", "PAY");
        codeMap.put("功能测试", "FUNC");
        codeMap.put("合同管理", "CT");
        codeMap.put("合同详情", "CTD");
        codeMap.put("合同列表", "CTL");
        codeMap.put("合同", "CT");
        codeMap.put("员工管理", "EMP");
        codeMap.put("员工", "EMP");
        codeMap.put("组织管理", "ORG");
        codeMap.put("组织", "ORG");
        codeMap.put("部门管理", "DEPT");
        codeMap.put("部门", "DEPT");
        codeMap.put("岗位管理", "POST");
        codeMap.put("岗位", "POST");
        codeMap.put("客户管理", "CRM");
        codeMap.put("客户", "CRM");
        codeMap.put("供应商管理", "SUPP");
        codeMap.put("供应商", "SUPP");
        codeMap.put("财务管理", "FIN");
        codeMap.put("财务", "FIN");
        codeMap.put("考勤管理", "ATT");
        codeMap.put("考勤", "ATT");
        codeMap.put("公告管理", "NOTI");
        codeMap.put("公告", "NOTI");
        codeMap.put("日程管理", "CAL");
        codeMap.put("日程", "CAL");
        codeMap.put("薪资管理", "SAL");
        codeMap.put("薪资", "SAL");
        codeMap.put("系统设置", "SYS");
        codeMap.put("登录", "AUTH");
        codeMap.put("注册", "REG");
        codeMap.put("首页", "HOME");
        codeMap.put("工作台", "WS");
        codeMap.put("个人信息", "PROF");
        codeMap.put("密码修改", "PWD");

        for (Map.Entry<String, String> entry : codeMap.entrySet()) {
            if (subModule.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        // 中文文本用哈希码兜底
        if (subModule.codePointAt(0) > 127) {
            return "M" + String.format("%03d", Math.abs(subModule.hashCode() % 1000));
        }

        StringBuilder code = new StringBuilder();
        String[] words = subModule.split("[\\s\\-_\\|]+");
        for (String word : words) {
            if (!word.isEmpty()) {
                code.append(word.charAt(0));
            }
        }
        return code.length() > 0 ? code.toString().toUpperCase() : "MOD";
    }

    /**
     * 获取模块前缀（用于编号，格式：{模块代码}-UI）
     */
    private static String getModulePrefix(String moduleName) {
        String code = getModuleCode(moduleName);
        return code + "-UI";
    }

    /**
     * 获取API测试用例模块前缀（用于编号，格式：{模块代码}-API）
     */
    private static String getApiModulePrefix(String moduleName, String interfacePath) {
        String code = getModuleCode(moduleName);
        if ("FUNC".equals(code) && interfacePath != null && !interfacePath.isEmpty()) {
            code = extractCodeFromPath(interfacePath);
        }
        return code + "-API";
    }

    /**
     * 从接口路径提取模块代码
     */
    private static String extractCodeFromPath(String path) {
        if (path == null || path.isEmpty()) {
            return "API";
        }
        
        Map<String, String> pathCodeMap = new HashMap<>();
        pathCodeMap.put("/user", "USR");
        pathCodeMap.put("/login", "AUTH");
        pathCodeMap.put("/admin", "ADMIN");
        pathCodeMap.put("/order", "ORD");
        pathCodeMap.put("/product", "PRD");
        pathCodeMap.put("/pay", "PAY");
        pathCodeMap.put("/inventory", "INV");
        pathCodeMap.put("/permission", "PERM");
        pathCodeMap.put("/role", "ROLE");
        pathCodeMap.put("/menu", "MENU");
        pathCodeMap.put("/file", "FILE");
        pathCodeMap.put("/data", "DATA");
        pathCodeMap.put("/report", "REPT");
        pathCodeMap.put("/config", "CONF");
        pathCodeMap.put("/audit", "AUD");
        pathCodeMap.put("/log", "LOG");
        pathCodeMap.put("/system", "SYS");
        pathCodeMap.put("/api", "API");
        pathCodeMap.put("/token", "AUTH");
        pathCodeMap.put("/message", "MSG");
        pathCodeMap.put("/notification", "NOTI");
        pathCodeMap.put("/stat", "STAT");
        pathCodeMap.put("/analysis", "ANAL");
        
        for (Map.Entry<String, String> entry : pathCodeMap.entrySet()) {
            if (path.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        
        return "API";
    }

}
