<div align="center">

# 🧪 测测 CeCe

**AI Testing Buddy** — 面向测试团队的 AI 用例生成与管理平台

[![JDK](https://img.shields.io/badge/JDK-17%2B-blue)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.0-brightgreen)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.4-42b883)](https://vuejs.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1)](https://www.mysql.com/)
[![LangChain4j](https://img.shields.io/badge/LangChain4j-0.35.0-orange)](https://github.com/langchain4j/)
[![License](https://img.shields.io/badge/License-MIT-yellow)](#-license)

> 注册登录后上传需求文档 / 接口文档，AI 异步生成标准化测试用例，按账号归档、在线评审、多格式导出，并可直接生成可运行的自动化测试脚本。

</div>

---

## 📸 功能总览

```
登录 / 注册（JWT）
└── 工作台        用例数量、生成趋势、优先级分布、最近任务
├── AI 生成
│   ├── 需求文档生成    .docx/.pdf/.md/.txt → 15 列 UI 功能用例
│   ├── 接口用例生成    接口文档 / Swagger → 24 列接口用例
│   ├── 自动化脚本      接口用例 + TestNG(RestAssured) / pytest 工程 ZIP
│   └── 自然语言生成    一段描述 → 测试用例
├── 资产
│   ├── 用例库        批次列表 → 用例详情，在线编辑 / 通过 / 驳回 / 删除 / 导出
│   ├── 文档库        上传的文档按账号归档，可下载、删除、复用于生成
│   ├── 任务记录      每次生成的实时进度与结果，失败可查看原因
│   └── 项目管理      按项目空间分组管理文档、任务与用例
└── 配置
    ├── 模板管理      内置 3 套模板 + 自定义列模板
    └── 设置          个人资料、密码、AI 模型与 API Key
```

---

## ✨ 核心特性

### 🔐 多用户与账号隔离
- 注册 / 登录（JWT，默认 7 天有效期），密码 BCrypt 加密存储
- 文档、任务、用例批次、模板、AI 配置全部按 `userId` 隔离
- 换台电脑或重新登录，历史任务与生成结果仍在

### ⚡ 真实异步任务
- 生成任务写入 `generation_task` 表，由线程池异步执行，接口立即返回任务 ID
- 前端通过 SSE（`/api/tasks/{id}/events`）实时接收进度与阶段文案
- 任务失败落库并保留错误信息，任务记录页可回溯

### 📋 需求文档 → UI 用例
| 功能 | 说明 |
|------|------|
| 读取方式 | 全部 / 指定页数 / 指定章节（多选）/ 指定关键词 |
| 章节识别 | 自动识别 `第X章`、`1.1`、`一、`、Markdown 标题 |
| 导出规格 | **15 列**（编号、测试大模块、子模块、测试点分类、标题、前置条件、预置数据、步骤、量化预期结果、优先级、环境、版本、实际结果、缺陷单号、测试日期） |
| 质量校验 | 步骤与预期必须编号一一对应、关键事实需来源于原文，过滤空标题与模糊预期 |
| 自定义列 | 输入 `|` 分隔列名，自动映射标准字段 |

### 🧪 接口文档 → 用例 / 脚本
- 支持 `.docx` `.pdf` `.json` `.yaml` `.yml` `.md` 与 Swagger URL
- 接口用例 **24 列**（路径、方法、请求头、参数、预期状态码/业务码/响应 JSON、断言规则、数据库校验等）
- 脚本语言：Java（TestNG + RestAssured + Allure）/ Python（pytest + requests + allure），输出完整工程 ZIP

### 🗂 用例资产化
- 按批次保存每次生成结果，列结构（`columnsJson`）一并持久化
- 在线编辑任意字段、评审（通过 / 驳回 + 驳回原因）、删除
- 导出 Excel / CSV / JSON / Markdown，导出内容为"当前修改后的版本"

### 🤖 多模型可切换
| 模型 | 标识 | 环境变量 |
|------|------|---------|
| DeepSeek Chat（默认） | `deepseek-chat` | `DEEPSEEK_API_KEY` |
| 通义千问 Qwen | `qwen-vl-max` | `DASHSCOPE_API_KEY` |
| 豆包 Vision | `doubao-seed-1-6-vision-250815` | `DOUBAO_API_KEY` / `ARK_API_KEY` |
| OpenAI GPT-4o | `gpt-4o` | `OPENAI_API_KEY` |
| 智谱 GLM-4V | `glm-4v` | `GLM_API_KEY` |

也可在「设置 → AI 模型」中为当前账号保存加密后的 API Key（AES 存储，展示时脱敏）。

---

## 🏗️ 技术栈

| 层 | 技术 |
|------|------|
| 后端 | Spring Boot 3.3.0（Undertow）、MyBatis-Plus 3.5.7 |
| 数据库 | MySQL 8（启动自动建表，`db/schema.sql`） |
| 鉴权 | JJWT 0.12.5（HS512）+ BCrypt + 自定义拦截器 |
| AI | LangChain4j 0.35.0（OpenAI 兼容协议） |
| 文档解析 | Apache POI（DOCX）/ PDFBox 3（PDF）/ Jackson + SnakeYAML（Swagger） |
| 导出 | Apache POI（Excel）/ CSV / JSON / Markdown |
| 前端 | Vue 3 + Vite 5 + Element Plus + Pinia + Vue Router + ECharts + Axios |
| 实时进度 | SseEmitter + `ScheduledExecutorService` 轮询任务表推送 |

---

## 🚀 快速开始

### 前置条件
- JDK 17+
- Maven 3.6+
- MySQL 8（本机 `localhost:3306`）
- Node.js 18+（仅修改前端时需要）
- 任一模型 API Key

### 1. 配置数据库账号

项目会自动创建 `cece_case` 库与全部表，只需提供账号密码。在项目根目录新建 `config/application-local.yml`：

```yaml
spring:
  datasource:
    username: root
    password: 你的MySQL密码
```

> 也可用环境变量：`MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_DB` / `MYSQL_USERNAME` / `MYSQL_PASSWORD`。
> 该文件已加入 `.gitignore`，不会提交到仓库。

### 2. 配置模型 API Key

```bash
# Windows PowerShell
$env:DEEPSEEK_API_KEY="sk-xxxx"
```

或直接在 `application.yml` 的 `ai.api-key` 填写。

### 3. 启动后端

```bash
mvn spring-boot:run
```

启动后访问 **http://localhost:8819**，演示账号：**admin / 123456**。

### 4. 修改前端（可选）

前端源码在 `frontend/`，构建产物会输出到 `src/main/resources/static`（后端直接托管）：

```bash
cd frontend
npm install
npm run dev      # 开发模式，代理到 8819
npm run build    # 生产构建，产物写入后端静态目录
```

> 本机若 Maven 镜像下载受限，可使用项目内配置：`mvn -s maven-settings.xml spring-boot:run`。

---

## 📂 项目结构

```
src/main/java/com/example/demo/
├── auth/                JwtUtil、AuthInterceptor、UserContext
├── common/              ApiResponse、ApiException、CryptoUtil
├── config/              WebConfig、AsyncConfig、DataInitializer、全局异常处理
├── controller/          Auth / Project / Document / Generation / Task / Case / Dashboard / Template / Setting
├── entity/ mapper/      8 张表的实体与 MyBatis-Plus Mapper
├── service/             User、Project、Document、Storage、Generation、Task、Case、Dashboard、
│                        Template、AiConfig，以及 AiService、WordParser、PdfParser、
│                        ExcelExporter、ScriptGenerator、CaseAssembler 等
├── TestCase / TestCaseTemplate
└── DemoApplication.java

src/main/resources/
├── application.yml      主配置
├── db/schema.sql        建表脚本（启动时自动执行）
├── prompts/             Prompt 模板
└── static/              Vue 构建产物

frontend/                Vue3 + Vite 源码
```

---

## 🗄️ 数据表

| 表 | 说明 |
|------|------|
| `sys_user` | 用户（用户名、BCrypt 密码、昵称、邮箱、头像） |
| `project` | 项目空间 |
| `document` | 上传文档（路径、大小、SHA-256） |
| `generation_task` | 生成任务（类型、状态、进度、阶段、参数、耗时） |
| `test_case_batch` | 用例批次（列结构、用例数、导出文件路径） |
| `test_case_item` | 单条用例（动态字段 JSON、评审状态与意见） |
| `user_template` | 用户自定义模板 |
| `user_ai_config` | 用户 AI 配置（加密 API Key） |

---

## 🔌 主要 API

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/auth/register` / `/api/auth/login` | 注册 / 登录，返回 JWT |
| `GET` | `/api/auth/me` | 当前用户信息 |
| `GET/POST/PUT/DELETE` | `/api/projects` | 项目空间增删改查 |
| `POST` | `/api/documents` | 上传文档 |
| `GET` | `/api/documents/{id}/download` | 下载文档 |
| `POST` | `/api/generation/requirement` | 需求文档 → UI 用例（异步） |
| `POST` | `/api/generation/api` | 接口文档 → 接口用例（异步） |
| `POST` | `/api/generation/script` | 接口文档 → 用例 + 脚本 ZIP（异步） |
| `POST` | `/api/generation/text` | 自然语言 → 用例（异步） |
| `POST` | `/api/generation/chapters` | 读取文档章节列表 |
| `GET` | `/api/tasks` / `/api/tasks/{id}` | 任务列表 / 详情 |
| `GET` | `/api/tasks/{id}/events` | SSE 实时进度 |
| `GET` | `/api/batches` / `/api/batches/{id}` | 用例批次列表 / 详情 |
| `PUT` | `/api/cases/{id}` | 编辑用例 / 评审 |
| `GET` | `/api/batches/{id}/export?format=xlsx` | 导出当前用例 |
| `GET` | `/api/dashboard/overview` | 看板统计 |
| `GET/POST/DELETE` | `/api/templates` | 模板管理 |
| `GET/PUT` | `/api/settings/ai` | AI 模型与 Key 设置 |

除登录、注册、健康检查外，所有 `/api/**` 均需 `Authorization: Bearer <token>`（SSE 与下载支持 `?token=`）。

---

## 🐛 常见问题

**Q：启动报数据库连接失败？**  
A：确认 MySQL 已启动、`config/application-local.yml` 中账号密码正确；库和表会自动创建。

**Q：生成用例为空？**  
A：检查对应模型 API Key 是否配置；文档是否包含可测试的功能/接口描述。自然语言输入不少于 20 字。

**Q：上传文件报 500？**  
A：确认磁盘可写（`cece.storage.root`，默认 `./data`）；单文件限制 100MB。

**Q：页码不准确？**  
A：DOCX 无物理页码，按字符密度估算（误差约 ±3 页）；需要精确页码请上传 PDF。

**Q：Maven 依赖下载失败？**  
A：本机若配置了 http 镜像会被拦截，改用 `mvn -s maven-settings.xml ...`（项目内 HTTPS 阿里云镜像）。

---

## 📜 License

MIT
