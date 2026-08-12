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

    /** 测试设计方法要求（模块2） */
    private static final String DESIGN_METHOD_REQUIREMENT = """

            【测试设计方法要求】
            请综合运用以下测试设计方法生成用例，保证覆盖充分且类型均衡（不能全是同一种）：
            1. 等价类划分：对每个输入字段，识别有效/无效等价类，各至少1条
            2. 边界值分析：对数值范围/长度限制，取 min、max、min-1、max+1 生成用例
            3. 判定表：存在多条件组合逻辑时，覆盖条件组合
            4. 场景法：识别主流程、备选流程、异常流程，生成端到端场景用例
            5. 错误推测法：覆盖空值、超长、特殊字符、SQL注入特征等常见错误
            每条用例的标题开头用【】标注其主要设计方法，如【等价类】【边界值】【判定表】【场景法】【错误推测法】。
            """;

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

                输出示例（每行10个字段用|分隔）：
                EMPLOYEE|功能验证|/admin/employee/login|POST|Content-Type: application/json|{"username":"admin","password":"123456"}|用户admin已注册|调用登录接口|200+{"code":0}|P0
                EMPLOYEE|参数校验|/admin/employee/login|POST|Content-Type: application/json|{"username":"","password":"123456"}|无|username为空调用|400+{"code":400}|P0
                EMPLOYEE|边界值|/admin/employee/login|POST|Content-Type: application/json|{"username":"a","password":"123456"}|存在1字符用户名|调用登录接口|200+{"code":0}|P1
                EMPLOYEE|异常场景|/admin/employee/logout|POST|Authorization: Bearer xxx|无|已登录获取token|调用登出接口|200+{"code":0}|P0

                接口文档：
                {INTERFACE_DOC}
                """;
        }

        if (promptGenerateTestCasesFromRequirement == null) {
            log.info("使用默认 generate-testcases-from-requirement Prompt");
            promptGenerateTestCasesFromRequirement = """
                你是一位资深测试工程师。请根据以下需求文档，生成【UI业务功能用例】。
                
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
                - 测试大模块：顶层业务域（如"系统管理"、"业务功能"、"数据管理"）
                - 子模块：具体功能模块（如"用户管理"、"订单管理"、"商品管理"）
                - 测试点分类：必须从枚举值中选择（正向/参数校验/边界/异常/UI校验/权限校验）
                - 用例标题：清晰描述单一验证目标，含场景关键词
                - 前置条件：必须包含系统状态（如"系统已登录"）、页面路径，禁止只写"系统已部署"
                - 预置测试数据：必须包含具体账号（如"管理员账号admin/123456"）、预置数据（如"已存在用户test001"），禁止空白
                - 操作步骤：必须包含完整页面路径（菜单入口/URL），每步以数字开头，步骤清晰可复现
                - 量化预期结果：必须包含明确判定标准（如"页面展示≥10条数据"、"状态变为'已支付'"），禁止模糊文字
                
                优先级评估标准：
                - P0：核心业务流程、主功能验证、严重安全漏洞、导致系统崩溃的场景、认证授权失败
                - P1：边界值、一般异常、普通数据校验、UI适配、权限越权
                - P2：次要功能、非核心场景、兼容性细节、文案校验
                
                输出格式（每个用例一行，字段用|分隔，不要输出用例编号！编号由系统自动生成）：
                测试大模块|子模块|测试点分类|用例标题|前置条件|预置测试数据|操作步骤|量化预期结果|优先级|测试环境|版本|实际结果|缺陷单号|测试日期
                
                输出示例（正确格式）：
                系统管理|用户管理|正向|管理员登录成功|测试环境已启动，浏览器已打开|管理员账号admin/123456已注册|1.访问http://test.example.com/login 2.输入用户名admin 3.输入密码123456 4.点击"登录"按钮|成功跳转到系统首页，右上角显示"admin"|P0|测试环境|V1.0.0|||
                系统管理|用户管理|参数校验|用户名为空登录失败|测试环境已启动，登录页面已打开|无|1.用户名输入框留空 2.密码输入123456 3.点击"登录"按钮|提示"用户名不能为空"|P0|测试环境|V1.0.0|||
                系统管理|用户管理|UI校验|登录页面刷新后数据不消失|测试环境已启动，登录页面已打开|无|1.在用户名输入框输入test 2.按F5刷新页面|用户名输入框保留"test"|P1|测试环境|V1.0.0|||
                系统管理|用户管理|权限校验|普通用户访问管理员页面被拒绝|测试环境已启动，普通用户已登录|普通用户user001账号|1.在地址栏输入http://test.example.com/admin 2.按回车|跳转到403禁止访问页面|P0|测试环境|V1.0.0|||

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

    public static void initModel() {
        if (model != null) {
            return;
        }
        
        String apiKey = getApiKey();
        String modelProvider = getModelProvider();

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
     * 切换模型（模块5：多模型切换）
     * 支持 deepseek-chat / qwen-vl-max / doubao-vision / gpt-4o / glm-4v 等
     */
    public static synchronized boolean switchModel(String modelName, String apiKey) {
        if (modelName == null || modelName.trim().isEmpty()) return false;
        String key = (apiKey != null && !apiKey.trim().isEmpty()) ? apiKey.trim() : API_KEY;
        if (key == null || key.isEmpty()) {
            log.warn("切换模型失败：未提供API Key");
            return false;
        }
        String name = modelName.trim();
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
            log.info("✅ 已切换模型: {}", name);
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

        String[] envNames = {"DASHSCOPE_API_KEY", "DEEPSEEK_API_KEY", "AI_API_KEY"};
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
        log.warn("⚠️ API Key未找到！请配置环境变量：DASHSCOPE_API_KEY / DEEPSEEK_API_KEY / AI_API_KEY");
        return API_KEY;
    }

    private static void ensureModelReady() {
        if (getModel() == null) {
            throw new AIServiceException("AI_MODEL_NOT_INITIALIZED",
                    "AI模型未初始化，请配置 DASHSCOPE_API_KEY / DEEPSEEK_API_KEY / AI_API_KEY 环境变量");
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

        List<String> uniqueInterfaces = new ArrayList<>();
        for (String api : allInterfaces) {
            if (!uniqueInterfaces.stream().anyMatch(api::contains)) {
                uniqueInterfaces.add(api);
            }
        }

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
        // 生成数量要求（模块7）
        prompt = prompt + "\n请生成约 " + currentGenerateCount + " 条测试用例。";
        // Few-shot：注入人工修正过的优秀用例，提升生成质量
        String fewShot = FeedbackSystem.getFewShotExamples(5);
        if (!fewShot.isEmpty()) {
            prompt = fewShot + "\n\n" + prompt;
        }
        // 自定义模板：强制替换 prompt 中的输出格式，AI只输出用户指定的列
        if (currentTemplate != null) {
            String outputFormat = currentTemplate.getOutputFormat();
            int colCount = currentTemplate.getFields().size();
            // 移除 prompt 中所有输出格式/示例行，替换为自定义格式
            prompt = prompt.replaceAll("(?m)^.*输出格式.*$", "")
                          .replaceAll("(?m)^.*\\d+列.*$", "")
                          .replaceAll("(?m)^.*字段用.*分隔.*$", "");
            prompt = prompt + "\n\n【特别注意】输出格式改为以下" + colCount + "列，字段用|分隔：\n" + outputFormat
                    + "\n请严格遵守：每行输出这" + colCount + "个字段，不要输出多余内容！";
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
                    // 过滤表头行和Markdown分隔行：检查前几个字段是否包含字段名或:---
                    if (parts.length >= 3) {
                        String p0 = safeGet(parts, 0), p1 = safeGet(parts, 1), p2 = safeGet(parts, 2);
                        if (p0.isEmpty() || p0.equals("测试模块") || p0.startsWith(":---") || p0.contains("---")
                            || p1.equals("测试点") || p1.startsWith(":---")
                            || p2.equals("用例标题") || p2.equals("接口路径") || p2.startsWith(":---")) {
                            continue;
                        }
                    }
                    // 自定义模板：用模板列数匹配，否则用默认23列解析
                    int minCols = currentTemplate != null ? currentTemplate.getFields().size() : 23;
                    if (parts.length >= minCols) {
                        TestCase tc = new TestCase();

                        if (currentTemplate != null) {
                            // 自定义模板：按位置映射到模板字段key
                            for (int i = 0; i < currentTemplate.getFields().size() && i < parts.length; i++) {
                                String key = currentTemplate.getFields().get(i).getFieldName();
                                String val = safeGet(parts, i);
                                tc.setField(key, val);
                            }
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
                            tc.setField("testEnvironment", safeGet(parts, 14));
                            tc.setField("version", safeGet(parts, 15));
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
                        // 生成编号
                        String moduleForId = (tc.getField("testModule") != null && !tc.getField("testModule").isEmpty())
                                ? tc.getField("testModule") : "API";
                        String modulePrefix = getApiModulePrefix(moduleForId, tc.getField("interfacePath"));
                        int count = globalModuleCounter.getOrDefault(modulePrefix, 0) + 1;
                        globalModuleCounter.put(modulePrefix, count);
                        tc.setId(modulePrefix + "-" + String.format("%04d", count));
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

        return testCases;
    }

    /**
     * 根据需求文档生成测试用例
     */
    public static List<TestCase> generateTestCasesFromRequirement(String requirementDoc) {
        List<TestCase> testCases = new ArrayList<>();
        ensureModelReady();

        String prompt = promptGenerateTestCasesFromRequirement.replace("{REQUIREMENT_DOC}", requirementDoc);

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
                        tc.setField("testEnvironment", testEnvironment.isEmpty() ? "测试环境" : testEnvironment);
                        tc.setField("version", version.isEmpty() ? "V1.0.0" : version);
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
        Map<String, Integer> moduleCounter = new HashMap<>();

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("---") || line.startsWith(":---") || line.startsWith("```") || line.startsWith("测试模块") || line.startsWith("用例编号") || line.contains("以下是为") || line.startsWith("请注意")) {
                continue;
            }

            TestCase tc = new TestCase();
            tc.setTestCaseType("API");
            tc.setField("testEnvironment", "测试环境");
            tc.setField("version", "V1.0.0");
            tc.setField("priority", "中");

            String[] parts = null;
            if (line.contains("|")) {
                parts = line.split("\\|", -1);
            } else if (line.contains("	")) {
                parts = line.split("\t", -1);
            }

            if (parts != null && parts.length >= 10) {
                tc.setField("testModule", safeGet(parts, 0));
                tc.setField("testPoint", safeGet(parts, 1));
                tc.setField("title", safeGet(parts, 2));
                tc.setField("interfacePath", safeGet(parts, 3));
                tc.setField("method", safeGet(parts, 4));
                tc.setField("precondition", safeGet(parts, 5));
                tc.setField("steps", safeGet(parts, 6));
                tc.setField("params", safeGet(parts, 7));
                if (isNumeric(safeGet(parts, 8))) {
                    tc.setField("expectedStatusCode", safeGet(parts, 8));
                }
                if (!safeGet(parts, 9).isEmpty() && isNumeric(safeGet(parts, 9))) {
                    tc.setField("expectedBusinessCode", safeGet(parts, 9));
                }
                if (parts.length > 10) {
                    tc.setField("expectedResult", safeGet(parts, 10));
                }

                String module = tc.getField("testModule") != null && !tc.getField("testModule").isEmpty() ? tc.getField("testModule") : "API";
                int cnt = moduleCounter.getOrDefault(module, 0) + 1;
                moduleCounter.put(module, cnt);
                tc.setId(module + "-" + String.format("%04d", cnt));
                tc.setField("testModuleCode", getModuleCode(module));
            } else if (parts != null && parts.length >= 5) {
                tc.setField("title", safeGet(parts, 1));
                if (parts.length >= 3) tc.setField("interfacePath", safeGet(parts, 2));
                if (parts.length >= 4) tc.setField("method", safeGet(parts, 3));
                if (parts.length >= 5) tc.setField("params", safeGet(parts, 4));
                if (parts.length >= 6) {
                    String statusCode = safeGet(parts, 5);
                    if (isNumeric(statusCode)) tc.setField("expectedStatusCode", statusCode);
                }
                if (parts.length >= 7) {
                    String businessCode = safeGet(parts, 6);
                    if (!businessCode.isEmpty() && isNumeric(businessCode)) tc.setField("expectedBusinessCode", businessCode);
                }
                if (parts.length >= 8) tc.setField("expectedResult", safeGet(parts, 7));
                else if (parts.length >= 5) tc.setField("expectedResult", safeGet(parts, 5));

                tc.setId("TC-F" + String.format("%04d", caseIndex++));
                tc.setField("testModule", "功能");
                tc.setField("interfacePath", "/api/unknown");
            } else {
                if (line.length() > 15 || !line.contains("/")) continue;
                tc.setField("title", line);
                tc.setField("method", "POST");
                tc.setField("interfacePath", "/api/unknown");
                tc.setId("TC-F" + String.format("%04d", caseIndex++));
            }

            testCases.add(tc);
        }

        return testCases;
    }

    /**
     * 根据需求文档按章节分段生成测试用例
     * @param sections 章节列表（包含标题和内容）
     * @return 测试用例列表
     */
    public static List<TestCase> generateTestCasesFromRequirementBySections(List<WordParser.SectionContent> sections) {
        List<TestCase> allTestCases = new ArrayList<>();
        ensureModelReady();

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

        for (WordParser.SectionContent section : sections) {
            log.info("处理章节 [{}/{}]: {}", sectionIndex, sections.size(), section.getTitle());

            String sectionPrompt = buildRequirementSectionPrompt(section.getTitle(), section.getContent());
            sectionPrompt = sectionPrompt + DESIGN_METHOD_REQUIREMENT;
            if (!fewShot.isEmpty()) sectionPrompt = fewShot + "\n\n" + sectionPrompt;
            if (!customFormatNote.isEmpty()) sectionPrompt = sectionPrompt.replaceAll("(?m)^.*输出格式.*$", "")
                    .replaceAll("(?m)^.*\\d+列.*$", "") + customFormatNote;
            try {
                String result = generateWithRetry(sectionPrompt);
                log.debug("章节 {} AI返回内容长度: {}", section.getTitle(), result != null ? result.length() : 0);

                if (result == null || result.trim().isEmpty()) {
                    log.warn("章节 {} AI返回空响应，跳过", section.getTitle());
                    sectionIndex++;
                    continue;
                }

                List<TestCase> sectionTestCases = parseRequirementTestCases(result, section.getTitle());
                allTestCases.addAll(sectionTestCases);
                
                log.info("章节 {} 生成了 {} 条测试用例", section.getTitle(), sectionTestCases.size());

            } catch (Exception e) {
                log.error("处理章节 {} 失败: {}", section.getTitle(), e.getMessage());
            }

            sectionIndex++;
        }

        log.info("所有章节处理完成，共生成 {} 条测试用例", allTestCases.size());
        return allTestCases;
    }

    /**
     * 构建章节测试用例生成的Prompt（UI业务功能用例）
     */
    private static String buildRequirementSectionPrompt(String sectionTitle, String sectionContent) {
        return """
            你是一位资深测试工程师。请根据以下需求章节，生成【UI业务功能用例】。
            
            ⚠️ 红线规则（必须严格遵守）：
            1. 这是需求文档的功能描述，不是API接口文档，不要生成接口测试用例
            2. 1个独立测试点 = 1条用例，一条用例只能验证单一目标，禁止多业务流程合并
            3. 禁止过粗：不能把多环节业务塞进1条用例（失败无法定位）
            4. 禁止过细：不要同质化参数校验过度拆分，避免大量重复冗余用例
            5. 编号由系统自动生成，不要输出用例编号
            6. 测试点分类必须严格使用枚举值：正向/参数校验/边界/异常/UI校验/权限校验
            7. 优先级必须使用：P0/P1/P2
            8. 所有预期结果必须量化，包含明确判定标准（数字、具体值、状态），禁止模糊描述
            9. **输出必须严格14个字段用|分隔**，末尾字段为空也必须保留分隔符（如`xxx|xxx|P0|测试环境|V1.0.0||||`）

            当前章节标题：%s

            章节内容（请仔细分析以下内容，识别出所有可测试的功能点）：
            %s

            分析指南（请按以下步骤识别测试点）：
            步骤1 - 提取核心业务功能：从文档中找出所有独立的功能点（如"用户登录"、"创建订单"、"数据导出"等）
            步骤2 - 分析每个功能点的输入：找出所有输入字段、约束条件、格式要求
            步骤3 - 分析每个功能点的操作：找出用户操作路径、页面流转、按钮点击等
            步骤4 - 分析每个功能点的输出：找出预期结果、数据变化、页面跳转等
            步骤5 - 对每个功能点，按以下场景清单生成对应类型的测试用例

            必须覆盖的场景清单（至少覆盖以下类型）：
            1. 正向：核心业务流程正常走通
            2. 参数校验：必填项、格式校验、数据类型校验
            3. 边界：字段长度边界、数量边界、日期边界
            4. 异常：空值、非法值、重复提交、数据不存在
            5. UI校验：页面刷新持久化、多端适配（PC/平板/手机）、文案固定性、布局正确性
            6. 权限校验：越权访问、无权限操作、角色权限边界

            字段填写规范：
            - 测试大模块：顶层业务域（如"系统管理"、"业务功能"、"数据管理"）
            - 子模块：具体功能模块（如"用户管理"、"订单管理"、"商品管理"）
            - 测试点分类：必须从枚举值中选择（正向/参数校验/边界/异常/UI校验/权限校验）
            - 用例标题：清晰描述单一验证目标，含场景关键词
            - 前置条件：必须包含系统状态（如"系统已登录"）、页面路径，禁止只写"系统已部署"
            - 预置测试数据：必须包含具体账号（如"管理员账号admin/123456"）、预置数据（如"已存在用户test001"、"订单ORD001状态为待支付"），禁止空白
            - 操作步骤：必须包含完整页面路径（菜单入口/URL），每步以数字开头，步骤清晰可复现
            - 量化预期结果：必须包含明确判定标准（如"页面展示≥10条数据"、"状态变为'已支付'"、"刷新后数据不消失"），禁止模糊文字

            优先级评估标准：
            - P0：核心业务流程、主功能验证、严重安全漏洞、导致系统崩溃的场景、认证授权失败
            - P1：边界值、一般异常、普通数据校验、UI适配、权限越权
            - P2：次要功能、非核心场景、兼容性细节、文案校验

            输出格式（每个用例一行，字段用|分隔，不要输出用例编号！编号由系统自动生成）：
            测试大模块|子模块|测试点分类|用例标题|前置条件|预置测试数据|操作步骤|量化预期结果|优先级|测试环境|版本|实际结果|缺陷单号|测试日期

            输出示例（正确格式 — 注意末尾保持|||占位）：
            系统管理|用户管理|正向|管理员登录成功|测试环境已启动，浏览器已打开|管理员账号admin/123456已注册|1.访问http://test.example.com/login 2.输入用户名admin 3.输入密码123456 4.点击"登录"按钮|成功跳转到系统首页，右上角显示"admin"，登录时间显示正确|P0|测试环境|V1.0.0|||
            系统管理|用户管理|参数校验|用户名为空登录失败|测试环境已启动，登录页面已打开|无|1.用户名输入框留空 2.密码输入123456 3.点击"登录"按钮|提示"用户名不能为空"，登录按钮保持可点击状态|P0|测试环境|V1.0.0|||
            系统管理|用户管理|边界|用户名达到最大长度登录成功|测试环境已启动，登录页面已打开|用户名为20位字母的账号已注册|1.用户名输入20位字母 2.密码输入正确密码 3.点击"登录"按钮|登录成功，首页正常展示|P1|测试环境|V1.0.0|||
            系统管理|用户管理|异常|输入不存在的用户名登录失败|测试环境已启动，登录页面已打开|无预置数据|1.用户名输入nonexist 2.密码输入123456 3.点击"登录"按钮|提示"用户名或密码错误"，连续失败3次后账号锁定5分钟|P0|测试环境|V1.0.0|||
            系统管理|用户管理|UI校验|登录页面刷新后数据不消失|测试环境已启动，登录页面已打开|无|1.在用户名输入框输入test 2.在密码输入框输入123 3.按F5刷新页面|用户名输入框保留"test"，密码输入框清空|P1|测试环境|V1.0.0|||
            系统管理|用户管理|权限校验|普通用户访问管理员页面被拒绝|测试环境已启动，普通用户已登录|普通用户user001账号，管理员页面URL：/admin|1.在地址栏输入http://test.example.com/admin 2.按回车|跳转到403禁止访问页面，显示"您无权限访问此页面"|P0|测试环境|V1.0.0|||
            业务功能|订单管理|正向|创建订单成功|测试环境已启动，商品详情页已打开|用户user001已登录，商品ID1001库存≥10|1.进入商品列表页→点击商品ID1001→点击"立即购买" 2.选择数量1 3.点击"提交订单" 4.确认订单信息|订单创建成功，页面显示订单号ORD-YYYYMMDD-XXXX，商品库存减少1，订单状态为"待支付"|P0|测试环境|V1.0.0|||
            业务功能|订单管理|UI校验|订单列表页面多端适配正常|测试环境已启动，订单列表页已打开|存在≥15条订单数据|1.使用浏览器开发者工具切换到PC模式 2.切换到平板模式(768px) 3.切换到手机模式(375px)|三种模式下表格列完整展示，无横向滚动条，按钮可点击|P1|测试环境|V1.0.0|||
            """.formatted(sectionTitle, sectionContent);
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
                TestCase tc = new TestCase();
                tc.setTestCaseType("UI");
                if (currentTemplate != null) {
                    // 自定义模板：按位置映射到模板字段key
                    for (int i = 0; i < currentTemplate.getFields().size() && i < parts.length; i++) {
                        String key = currentTemplate.getFields().get(i).getFieldName();
                        tc.setField(key, safeGet(parts, i));
                    }
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
