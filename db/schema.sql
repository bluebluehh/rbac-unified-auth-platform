-- ============================================================================
-- RBAC 统一权限管理平台 — 建库迁移脚本（设计文档 §4.13 基准 DDL）
-- ----------------------------------------------------------------------------
-- 执行方式（首次安装执行一次，后续改动通过新迁移完成，不要重复执行掩盖差异）：
--   mysql -h localhost -P 3308 -u root -p < db/schema.sql
--   mysql -h localhost -P 3308 -u root -p < db/data.sql
-- 字符集：库/表 utf8mb4 / utf8mb4_unicode_ci；编码与用户名列 utf8mb4_bin
-- 时间基准：全部 UTC（DATETIME(3) / UTC_TIMESTAMP(3)）
-- ============================================================================

CREATE DATABASE IF NOT EXISTS rbac_platform
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE rbac_platform;

SET NAMES utf8mb4;
SET time_zone = "+00:00";

-- ----------------------------------------------------------------------------
-- 组织表：树形结构，root_guard 生成列保证唯一 ROOT
-- ----------------------------------------------------------------------------
CREATE TABLE sys_dept (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  dept_name VARCHAR(160) NOT NULL,
  node_key VARCHAR(160) COLLATE utf8mb4_bin NOT NULL,
  parent_id BIGINT NULL,
  node_type VARCHAR(24) COLLATE utf8mb4_bin NOT NULL,
  manager_user_id BIGINT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME(3) NOT NULL,
  update_time DATETIME(3) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CHECK (version >= 0),
  root_guard TINYINT GENERATED ALWAYS AS (CASE WHEN node_type = 'ROOT' THEN 1 ELSE NULL END) STORED,
  UNIQUE KEY uk_dept_node_key (node_key),
  UNIQUE KEY uk_dept_single_root (root_guard),
  KEY idx_dept_parent_status (parent_id,status),
  FOREIGN KEY (parent_id) REFERENCES sys_dept(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CHECK (node_type IN ('ROOT','COMPANY','DEPARTMENT','PROJECT_DEPT')),
  CHECK (status IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- 用户表：Argon2id PHC 哈希；手机号为 AES-GCM 密文（VARBINARY）
-- CHECK：ENABLED 账号必须有密码且 must_change_password=0
-- ----------------------------------------------------------------------------
CREATE TABLE sys_user (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(50) COLLATE utf8mb4_bin NOT NULL,
  password_hash VARCHAR(255) NULL,
  real_name VARCHAR(80) NOT NULL,
  company_id BIGINT NOT NULL,
  dept_id BIGINT NOT NULL,
  position_name VARCHAR(80) NOT NULL,
  phone_cipher VARBINARY(256) NULL,
  status VARCHAR(24) COLLATE utf8mb4_bin NOT NULL DEFAULT 'PENDING_ACTIVATION',
  must_change_password TINYINT NOT NULL DEFAULT 1,
  failed_login_count INT NOT NULL DEFAULT 0,
  locked_until DATETIME(3) NULL,
  create_time DATETIME(3) NOT NULL,
  update_time DATETIME(3) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CHECK (version >= 0),
  UNIQUE KEY uk_user_username (username),
  KEY idx_user_company_dept (company_id,dept_id),
  KEY idx_user_dept_status (dept_id,status),
  FOREIGN KEY (company_id) REFERENCES sys_dept(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  FOREIGN KEY (dept_id) REFERENCES sys_dept(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CHECK (status IN ('ENABLED','DISABLED','PENDING_ACTIVATION')),
  CHECK (must_change_password IN (0,1)),
  CHECK (failed_login_count >= 0),
  CHECK (status <> 'ENABLED' OR (password_hash IS NOT NULL AND must_change_password = 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 负责人外键依赖 sys_user，故在用户表之后补加（文档 §4.13 原文如此）
ALTER TABLE sys_dept ADD CONSTRAINT fk_dept_manager FOREIGN KEY (manager_user_id) REFERENCES sys_user(id) ON DELETE RESTRICT ON UPDATE RESTRICT;

-- ----------------------------------------------------------------------------
-- 角色表：role_key 唯一且不可改；PLATFORM_ADMIN 为保留角色（种子固定）
-- ----------------------------------------------------------------------------
CREATE TABLE sys_role (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  role_name VARCHAR(80) NOT NULL,
  role_key VARCHAR(50) COLLATE utf8mb4_bin NOT NULL,
  description VARCHAR(300) NULL,
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME(3) NOT NULL,
  update_time DATETIME(3) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CHECK (version >= 0),
  UNIQUE KEY uk_role_key (role_key),
  CHECK (status IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- 应用表：app_key 唯一；platform 为保留应用（技术权限挂载点）
-- ----------------------------------------------------------------------------
CREATE TABLE sys_application (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  app_name VARCHAR(100) NOT NULL,
  app_key VARCHAR(32) COLLATE utf8mb4_bin NOT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME(3) NOT NULL,
  update_time DATETIME(3) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CHECK (version >= 0),
  UNIQUE KEY uk_app_key (app_key),
  CHECK (status IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- 权限表：四段式 perm_key（应用:模块:功能:操作）；四元组唯一
-- ----------------------------------------------------------------------------
CREATE TABLE sys_permission (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  application_id BIGINT NOT NULL,
  module_key VARCHAR(32) COLLATE utf8mb4_bin NOT NULL,
  module_name VARCHAR(100) NOT NULL,
  resource_key VARCHAR(48) COLLATE utf8mb4_bin NOT NULL,
  resource_name VARCHAR(100) NOT NULL,
  resource_description VARCHAR(500) NULL,
  action_code VARCHAR(32) COLLATE utf8mb4_bin NOT NULL,
  perm_name VARCHAR(160) NOT NULL,
  perm_key VARCHAR(160) COLLATE utf8mb4_bin NOT NULL,
  source_row INT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME(3) NOT NULL,
  update_time DATETIME(3) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CHECK (version >= 0),
  UNIQUE KEY uk_permission_key (perm_key),
  UNIQUE KEY uk_permission_tuple (application_id,module_key,resource_key,action_code),
  KEY idx_permission_app_status (application_id,status),
  FOREIGN KEY (application_id) REFERENCES sys_application(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CHECK (status IN (0,1)),
  CHECK (source_row IS NULL OR source_row >= 2)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- 用户-角色 / 角色-权限 关系表
-- ----------------------------------------------------------------------------
CREATE TABLE sys_user_role (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  UNIQUE KEY uk_user_role (user_id,role_id),
  KEY idx_user_role_reverse (role_id),
  FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  FOREIGN KEY (role_id) REFERENCES sys_role(id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sys_role_permission (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  role_id BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  UNIQUE KEY uk_role_permission (role_id,permission_id),
  KEY idx_role_permission_reverse (permission_id),
  FOREIGN KEY (role_id) REFERENCES sys_role(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  FOREIGN KEY (permission_id) REFERENCES sys_permission(id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- 会话表：仅存 Token SHA-256 摘要（BINARY(32)），身份不编码在 Token 内
-- ----------------------------------------------------------------------------
CREATE TABLE sys_session (
  id CHAR(36) COLLATE utf8mb4_bin NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  token_hash BINARY(32) NOT NULL,
  restricted TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL,
  expires_at DATETIME(3) NOT NULL,
  last_seen_at DATETIME(3) NOT NULL,
  revoked_at DATETIME(3) NULL,
  UNIQUE KEY uk_session_token_hash (token_hash),
  KEY idx_session_user_expiry (user_id,expires_at),
  FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CHECK (restricted IN (0,1)),
  CHECK (expires_at > created_at),
  CHECK (last_seen_at >= created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- 操作审计表：event_type 白名单 22 值；审计主体为快照不画外键
-- ----------------------------------------------------------------------------
CREATE TABLE sys_operation_log (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NULL,
  username_snapshot VARCHAR(50) NULL,
  event_type VARCHAR(50) COLLATE utf8mb4_bin NOT NULL,
  target_type VARCHAR(32) COLLATE utf8mb4_bin NULL,
  target_id VARCHAR(64) NULL,
  http_method VARCHAR(8) COLLATE utf8mb4_bin NOT NULL,
  request_path VARCHAR(200) NOT NULL,
  request_id VARCHAR(64) NOT NULL,
  result VARCHAR(16) COLLATE utf8mb4_bin NOT NULL,
  reason_code VARCHAR(48) NOT NULL,
  source_ip VARCHAR(45) NULL,
  details JSON NULL,
  create_time DATETIME(3) NOT NULL,
  KEY idx_log_time_id (create_time,id),
  KEY idx_log_user_time (user_id,create_time),
  KEY idx_log_request (request_id),
  CHECK (result IN ('SUCCESS','FAILURE')),
  CHECK (event_type IN ('LOGIN','LOGOUT','USER_CREATE','USER_UPDATE','USER_STATUS','PASSWORD_RESET','PASSWORD_CHANGE','USER_ROLE_ASSIGN','ROLE_CREATE','ROLE_UPDATE','ROLE_STATUS','ROLE_PERMISSION_ASSIGN','DEPT_CREATE','DEPT_UPDATE','DEPT_STATUS','APP_CREATE','APP_UPDATE','APP_STATUS','PERMISSION_CREATE','PERMISSION_STATUS','AUTHZ_DENY','DEMO_SUBMIT')),
  CHECK (target_type IS NULL OR target_type IN ('USER','ROLE','DEPT','APP','PERMISSION','SESSION')),
  CHECK (http_method IN ('GET','POST','PUT','PATCH','DELETE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- 安全状态表：独立单行锁（id 恒为 1），与所有管理写操作共用同一锁顺序
-- ----------------------------------------------------------------------------
CREATE TABLE sys_security_state (
  id SMALLINT NOT NULL DEFAULT 1 PRIMARY KEY,
  policy_version BIGINT NOT NULL DEFAULT 1,
  update_time DATETIME(3) NOT NULL,
  CHECK (id = 1),
  CHECK (policy_version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO sys_security_state (id,policy_version,update_time) VALUES (1,1,UTC_TIMESTAMP(3));
