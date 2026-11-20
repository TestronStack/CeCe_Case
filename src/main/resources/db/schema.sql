-- ============================================================
--  CeCe 智能测试平台 数据库结构（MySQL 8.0）
--  启动时由 spring.sql.init 自动执行，全部为 IF NOT EXISTS，可重复执行
-- ============================================================

CREATE TABLE IF NOT EXISTS sys_user (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username      VARCHAR(50)  NOT NULL COMMENT '登录账号',
    password      VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码',
    nickname      VARCHAR(50)  DEFAULT NULL COMMENT '昵称',
    email         VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    role          VARCHAR(20)  DEFAULT 'USER' COMMENT '角色',
    status        TINYINT      DEFAULT 1 COMMENT '1 启用 0 停用',
    created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    last_login_at DATETIME     DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '用户表';

CREATE TABLE IF NOT EXISTS project (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL COMMENT '归属用户',
    name        VARCHAR(100) NOT NULL COMMENT '项目名称',
    description VARCHAR(500) DEFAULT NULL COMMENT '项目描述',
    color       VARCHAR(20)  DEFAULT '#2563eb' COMMENT '标识色',
    created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_project_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '项目空间';

CREATE TABLE IF NOT EXISTS document (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    project_id    BIGINT       DEFAULT NULL,
    name          VARCHAR(255) NOT NULL COMMENT '展示名称',
    original_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
    ext           VARCHAR(20)  DEFAULT NULL,
    size          BIGINT       DEFAULT 0,
    storage_path  VARCHAR(500) NOT NULL COMMENT '服务器存储路径',
    sha256        VARCHAR(64)  DEFAULT NULL COMMENT '文件指纹',
    created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_document_user (user_id),
    KEY idx_document_project (project_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '上传文档';

CREATE TABLE IF NOT EXISTS generation_task (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    project_id  BIGINT       DEFAULT NULL,
    document_id BIGINT       DEFAULT NULL,
    batch_id    BIGINT       DEFAULT NULL COMMENT '成功后关联的用例批次',
    task_type   VARCHAR(30)  NOT NULL COMMENT 'REQUIREMENT_UI/API_CASE/API_SCRIPT/TEXT',
    title       VARCHAR(255) DEFAULT NULL,
    status      VARCHAR(20)  DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCESS/FAILED',
    progress    INT          DEFAULT 0,
    stage       VARCHAR(100) DEFAULT NULL COMMENT '当前阶段描述',
    message     VARCHAR(1000) DEFAULT NULL COMMENT '成功或失败信息',
    params_json TEXT         COMMENT '本次生成参数快照',
    model       VARCHAR(80)  DEFAULT NULL COMMENT '使用的模型',
    duration_ms BIGINT       DEFAULT 0,
    created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    finished_at DATETIME     DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_task_user (user_id),
    KEY idx_task_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '生成任务';

CREATE TABLE IF NOT EXISTS test_case_batch (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    project_id      BIGINT       DEFAULT NULL,
    document_id     BIGINT       DEFAULT NULL,
    task_id         BIGINT       DEFAULT NULL,
    name            VARCHAR(255) NOT NULL COMMENT '批次名称',
    case_type       VARCHAR(20)  DEFAULT 'UI' COMMENT 'UI/API',
    columns_json    TEXT         COMMENT '列定义 [{fieldName,displayName}]',
    case_count      INT          DEFAULT 0,
    output_format   VARCHAR(20)  DEFAULT 'xlsx',
    file_path       VARCHAR(500) DEFAULT NULL COMMENT '导出文件路径',
    script_language VARCHAR(20)  DEFAULT NULL COMMENT 'java/python',
    script_path     VARCHAR(500) DEFAULT NULL COMMENT '自动化脚本压缩包路径',
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_batch_user (user_id),
    KEY idx_batch_project (project_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '用例批次';

CREATE TABLE IF NOT EXISTS test_case_item (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    batch_id      BIGINT       NOT NULL,
    user_id       BIGINT       NOT NULL,
    case_no       VARCHAR(50)  DEFAULT NULL,
    title         VARCHAR(500) DEFAULT NULL,
    module        VARCHAR(100) DEFAULT NULL,
    category      VARCHAR(50)  DEFAULT NULL COMMENT '测试点分类',
    priority      VARCHAR(10)  DEFAULT NULL,
    review_status VARCHAR(20)  DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
    review_remark VARCHAR(500) DEFAULT NULL,
    data_json     LONGTEXT     COMMENT '完整字段 {fieldName:value}',
    sort_no       INT          DEFAULT 0,
    created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_case_batch (batch_id),
    KEY idx_case_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '测试用例明细';

CREATE TABLE IF NOT EXISTS user_template (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT       NOT NULL,
    name         VARCHAR(100) NOT NULL,
    columns_text TEXT         NOT NULL COMMENT '列名，| 分隔',
    created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_tpl_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '自定义模板';

CREATE TABLE IF NOT EXISTS user_ai_config (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    provider   VARCHAR(30)  DEFAULT 'deepseek',
    model_name VARCHAR(80)  DEFAULT 'deepseek-chat',
    api_key    VARCHAR(500) DEFAULT NULL COMMENT 'AES 加密存储',
    updated_at DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '用户模型配置';
