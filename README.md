<div align="center">

# 🧪 测测 CeCe

**AI Testing Buddy** — 你的 AI 测试用例生成助手

[![JDK](https://img.shields.io/badge/JDK-17%2B-blue)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.0-brightgreen)](https://spring.io/projects/spring-boot)
[![LangChain4j](https://img.shields.io/badge/LangChain4j-0.35.0-orange)](https://github.com/langchain4j/)
[![License](https://img.shields.io/badge/License-MIT-yellow)](#-license)

> 上传接口文档或需求文档，AI 自动生成标准化测试用例，支持多格式导出与自动化测试脚本生成。

</div>

---

## 📸 快速预览

```
👋 欢迎使用 测测 CeCe
┌────────────────────────────────────────────────────┐
│  📋 需求文档  →  UI 功能测试用例                     │
│  🧪 接口文档  →  接口用例 + 自动化脚本（Java/Python） │
│  🔧 接口文档  →  接口测试用例（仅导出用例）            │
│  💬 自然语言  →  快速生成测试用例                     │
│  🤖 多模型切换：DeepSeek / Qwen / 豆包 / GPT / GLM    │
└────────────────────────────────────────────────────┘
```

---

## ✨ 功能特性

### 📋 需求文档 → UI 测试用例（Tab1）
| 功能 | 说明 |
|------|------|
| 支持格式 | `.docx` `.pdf` `.md` `.txt` |
| 读取方式 | 全部读取 / 指定页数 / 指定章节（可多选）/ 指定关键词 |
| 章节识别 | 自动识别 `第X章` / `1.1` / `一、` 等章节标题，前端下拉选择 |
| 导出规格 | **15 列** UI 用例模板（用例编号、测试大模块、子模块、测试点分类、用例标题、前置条件、预置测试数据、操作步骤、量化预期结果、优先级、测试环境、版本、实际结果、缺陷单号、测试日期） |
| 测试点分类 | 枚举值：正向 / 参数校验 / 边界 / 异常 / UI校验 / 权限校验 |
| 质量校验 | 确定性校验步骤与预期编号是否一一对应、关键事实是否来源于需求原文，拒绝空标题/空步骤/模糊预期 |
| 自定义列 | 支持输入自定义列名（`|` 分隔），自动映射标准字段并补齐默认值 |
| 输出格式 | Excel / CSV / JSON / Markdown，可指定导出路径 |

### 🧪 接口文档 → 自动化脚本（Tab2）
| 功能 | 说明 |
|------|------|
| 支持格式 | `.docx` `.pdf` `.json` `.yaml` `.yml` `.md`，亦支持 Swagger URL |
| 读取方式 | 全部读取 / 指定页数 |
| 用例生成 | 先提取接口定义并生成 **24 列**接口测试用例，再基于用例生成脚本 |
| 脚本语言 | **Java**（TestNG + RestAssured + Allure）/ **Python**（pytest + requests + allure） |
| 输出 | 完整可运行工程，打包为 `.zip` 下载，内含 `接口测试用例.xlsx` |
| 数量控制 | 单接口最多 8 条用例，整份文档按预算裁剪，优先保留 P0/P1 |

### 🔧 接口文档 → 接口测试用例（Tab4）
- 只生成并导出接口用例，不生成脚本
- **24 列**标准模板：用例编号、测试模块、测试点、用例标题、接口路径、请求方法、前置条件、请求头、请求参数、预期状态码、预期业务码、预期响应 JSON、断言规则、数据库校验、优先级、测试环境、版本、测试人、缺陷单号、测试日期等
- 支持 Excel / CSV / JSON / Markdown 导出

### 💬 自然语言 → 测试用例（Tab3）
- 支持「接口描述」或「需求描述」两种输入类型
- 可指定生成数量（1～50 条）
- 支持自定义列模板与多格式导出
- 内容少于 20 字时给出补充提示，避免无效生成

### 🤖 多模型切换
| 模型 | 模型标识 | API Key 环境变量 |
|------|---------|-----------------|
| DeepSeek Chat（默认） | `deepseek-chat` | `DEEPSEEK_API_KEY` |
| 通义千问 Qwen-VL | `qwen-vl-max` | `DASHSCOPE_API_KEY` |
| 豆包 Vision | `doubao-seed-1-6-vision-250815` | `DOUBAO_API_KEY`（或 `ARK_API_KEY`） |
| OpenAI GPT-4o | `gpt-4o` | `OPENAI_API_KEY` |
| 智谱 GLM-4V | `glm-4v` | `GLM_API_KEY` |
| 通用兜底 | — | `AI_API_KEY` |

页面顶部下拉框可直接切换模型，切换时自动读取对应提供商的 Key。

### 🧹 生成质量保障
- **Prompt 质量红线**：禁止 `xxx`/`test` 等占位参数、预期结果必须量化、断言需包含状态码 + 业务码 + 关键字段
- **确定性校验**：`TestCaseQualityValidator` 校验 UI 用例的步骤/预期编号对应关系及需求来源事实
- **去重与裁剪**：按模板全字段去重；`ai.case-budget`（默认 120 条）控制总量，超量优先裁剪 P2 用例
- **字段规范化**：模板转置时强制补齐类型、自动化等默认值

### 📦 其他能力
- 批量扫描文件夹内文档，统一生成 HTML 汇总报告
- 自定义模板保存 / 列表复用（`/api/template/save`、`/api/template/list`）
- 静态资源禁用浏览器缓存，前端更新后刷新即生效

---

## 🚀 快速开始

### 前置条件
- JDK 17+
- Maven 3.6+
- 任一模型 API Key（推荐 DeepSeek：[申请地址](https://platform.deepseek.com/)）

### 1. 配置 API Key

```bash
# Windows PowerShell
$env:DEEPSEEK_API_KEY="your-api-key"

# 或直接修改 application.yml
```

```yaml
ai:
  api-key: ${DEEPSEEK_API_KEY:}
  model:
    provider: deepseek
```

Key 只从环境变量读取，不会写入代码仓库。

### 2. 启动

```bash
# 方式一：打包后双击 run.bat
mvn clean package -DskipTests
run.bat

# 方式二：直接运行
mvn spring-boot:run
```

### 3. 访问

浏览器打开 **[http://localhost:8819](http://localhost:8819)**

---

## 🏗️ 技术栈

| 层 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3.3.0（Undertow，无 Tomcat） |
| AI 集成 | LangChain4j 0.35.0 + OpenAI 兼容接口 |
| AI 模型 | DeepSeek / Qwen / 豆包 / GPT / GLM（可切换） |
| 文档解析 | Apache POI (DOCX) / PDFBox 3.0.2 (PDF) / Jackson + SnakeYAML + Swagger Parser |
| 报表导出 | Apache POI (Excel) / CSV / JSON / Markdown |
| 脚本生成 | TestNG + RestAssured + Allure（Java）/ pytest + requests + allure（Python） |
| 前端 | 原生 HTML + CSS + JavaScript（暗色技术风，单页四 Tab） |
| 测试 | JUnit 5 + AssertJ + Spring Boot Test |

## 📂 项目结构

```
src/main/java/com/example/demo/
├── controller/
│   └── TestCaseGeneratorController.java  # Web 接口（4 个功能入口 + 模型/模板）
├── config/
│   ├── WebConfig.java                    # 静态资源禁用缓存
│   └── GlobalExceptionHandler.java       # 全局异常处理
├── service/
│   ├── AiService.java                    # AI 调用、Prompt、模型切换、用例预算
│   ├── WordParser.java                   # DOCX/MD/TXT 解析 + 章节/分页算法
│   ├── PdfParser.java                    # PDF 解析（精确页码）
│   ├── ExcelExporter.java                # 多格式导出（24列/15列/自定义）
│   ├── ScriptGenerator.java              # Java / Python 自动化脚本生成
│   ├── TestCaseQualityValidator.java     # UI 用例确定性质量校验
│   ├── TemplateManager.java              # 自定义模板保存与读取
│   ├── BatchScanService.java             # 批量扫描
│   ├── HtmlReportGenerator.java          # HTML 汇总报告
│   ├── FeedbackSystem.java               # 生成反馈
│   └── TestCaseExecutor.java             # 用例执行辅助
├── dto/ exception/ util/                 # 响应体、异常、下载工具
├── TestCaseTemplate.java                 # 15 列 UI / 24 列 API 模板定义
├── TestCase.java                         # 测试用例实体
├── DocumentProcessor.java                # 主逻辑入口 / 模型初始化
├── SwaggerParser.java                    # Swagger / OpenAPI 解析
└── DemoApplication.java                  # 启动类（含启动信息面板）

src/test/java/com/example/demo/
├── TestCaseTemplateTest.java            # 模板字段与转置测试
└── service/
    ├── ExcelExporterTest.java           # Excel 导出测试
    └── TestCaseQualityValidatorTest.java # 质量校验测试
```

---

## 🔌 主要 API

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/` | 重定向到前端页面 |
| `POST` | `/api/generate-ui` | 需求文档 → UI 测试用例 |
| `POST` | `/api/chapters` | 读取需求文档章节列表 |
| `POST` | `/api/generate-script` | 接口文档 → 用例 + 自动化脚本 ZIP |
| `POST` | `/api/generate-api` | 接口文档 → 接口测试用例 |
| `POST` | `/api/generate-text` | 自然语言 → 测试用例 |
| `POST` | `/api/generate-from-url` | Swagger URL → 测试用例 |
| `GET` | `/api/models` | 获取可用模型列表与当前模型 |
| `POST` | `/api/switch-model` | 切换模型（可临时传入 apiKey） |
| `POST` | `/api/template/save` | 保存自定义模板 |
| `GET` | `/api/template/list` | 获取模板列表 |

---

## ⚙️ 配置说明

### application.yml 关键配置

```yaml
server:
  port: 8819                       # 服务端口

spring:
  servlet:
    multipart:
      max-file-size: 100MB         # 上传文件大小限制
      max-request-size: 100MB
  mvc:
    async:
      request-timeout: 600000      # 长任务超时（毫秒）

ai:
  api-key: ${DEEPSEEK_API_KEY:}    # API Key（仅从环境变量读取）
  model:
    provider: deepseek             # deepseek / qwen / doubao / glm / openai
  case-budget: 120                 # 整份文档用例总量预算
  prompts:
    # 可在此覆盖接口提取、用例生成默认 Prompt
```

---

## 🧪 运行测试

```bash
mvn test
```

覆盖模板字段定义与转置、Excel 导出、UI 用例质量校验（步骤/预期编号一致性等）。

---

## 🐛 常见问题

**Q：生成用例为空？**  
A：检查对应模型的 API Key 是否已配置，或文档中是否包含接口定义关键词（`/api/`、`GET`、`POST` 等）。自然语言输入需不少于 20 字。

**Q：页码不准确？**  
A：DOCX 没有物理页码，系统采用字符密度估算，误差约 ±3 页。需要精确页码时请上传 PDF。

**Q：切换模型后报错？**  
A：每个模型使用独立环境变量（见上表），切换前请先配置对应 Key，或通过 `/api/switch-model` 临时传入 `apiKey`。

**Q：AI 生成的用例质量不高？**  
A：系统内置 Prompt 质量红线与确定性校验（过滤占位参数、模糊预期、步骤/预期不匹配），也可在 `application.yml` 的 `ai.prompts` 中调整 Prompt。

**Q：支持哪些导出格式？**  
A：Excel（`.xlsx`）、CSV、JSON、Markdown；自动化脚本以 `.zip` 工程包输出。

---

## 📜 License

MIT
