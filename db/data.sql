-- ============================================================================
-- RBAC 统一权限管理平台 — 幂等种子数据（设计文档 §7.5、§8.1）
-- ----------------------------------------------------------------------------
-- 执行前提：先执行 db/schema.sql（本脚本依赖其表结构与外键）
--   mysql -h localhost -P 3308 -u root -p < db/data.sql
--
-- 幂等语义（§7.5"重跑不静默恢复或覆盖人工授权"）：
--   全部使用固定主键/唯一键 + INSERT IGNORE——已存在的记录原样保留，
--   管理员后来调整的状态、密码、授权不会被重置。
--
-- 内容分两类：
--   A. 固定配置：platform/engineering 应用、25 个固定权限点、
--      五类业务角色 + PLATFORM_ADMIN 保留角色、PLATFORM_ADMIN 权限集合
--   B. 初始示例：最小组织树（5 节点）、bootstrap 管理员账号 ADMIN
--
-- bootstrap 管理员（§8.1 首次安装闭环）：
--   工号 ADMIN，密码 Admin@2026rbac（与 application.yml 本地默认一致）；
--   Argon2id PHC 由工程内 PasswordHashUtil 生成并自校验通过；
--   ENABLED + must_change_password=0 属安装显式激活例外。
--   !! 生产部署：安装密码由部署配置交付，不入库不入文档；安装后立即改密。
-- ============================================================================

USE rbac_platform;

SET NAMES utf8mb4;
SET time_zone = "+00:00";

-- ============================================================================
-- 1. 组织树（§7.5：负责人留空，后续由管理员回填 manager_user_id）
--    结构：ROOT → 市政总公司 → 2 部门 + 1 项目部（示例数据，可按真实导入替换）
-- ============================================================================
INSERT IGNORE INTO sys_dept
  (id, dept_name, node_key, parent_id, node_type, manager_user_id, status, create_time, update_time, version)
VALUES
  (1, '组织树根',     'ROOT',      NULL, 'ROOT',        NULL, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0),
  (2, '市政总公司',   'ORG-ROOT',  1,    'COMPANY',     NULL, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0),
  (3, '综合管理部',   'ORG-HR',    2,    'DEPARTMENT',  NULL, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0),
  (4, '工程管理部',   'ORG-ENG',   2,    'DEPARTMENT',  NULL, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0),
  (5, '示范项目部',   'ORG-DEMO',  2,    'PROJECT_DEPT',NULL, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0);

-- ============================================================================
-- 2. 应用（§8.1：platform 为保留应用；engineering 承载演示端点权限）
-- ============================================================================
INSERT IGNORE INTO sys_application
  (id, app_name, app_key, status, create_time, update_time, version)
VALUES
  (1, '统一权限管理平台', 'platform',    1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0),
  (2, '工程演示应用',     'engineering', 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0);

-- ============================================================================
-- 3. 权限点（perm_key 与 PermissionKeyConstants.java 完全一致；附录 B 目录）
--    platform 技术权限 source_row 依目录顺序 2..24；演示权限 source_row=NULL
-- ============================================================================
INSERT IGNORE INTO sys_permission
  (id, application_id, module_key, module_name, resource_key, resource_name, resource_description, action_code, perm_name, perm_key, source_row, status, create_time, update_time, version)
VALUES
  -- 用户管理（platform:user:account:*）
  ( 1, 1, 'user', '用户管理', 'account', '员工账号', '员工账号的查询与维护', 'view',           '查看员工账号', 'platform:user:account:view',           2,  1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0),
  ( 2, 1, 'user', '用户管理', 'account', '员工账号', '员工账号的查询与维护', 'create',         '新增员工',     'platform:user:account:create',         3,  1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0),
  ( 3, 1, 'user', '用户管理', 'account', '员工账号', '员工账号的查询与维护', 'edit',           '编辑员工',     'platform:user:account:edit',           4,  1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0),
  ( 4, 1, 'user', '用户管理', 'account', '员工账号', '员工账号的查询与维护', 'status',         '员工启停',     'platform:user:account:status',         5,  1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0),
  ( 5, 1, 'user', '用户管理', 'account', '员工账号', '员工账号的查询与维护', 'reset_password', '重置密码',     'platform:user:account:reset_password', 6,  1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0),
  ( 6, 1, 'user', '用户管理', 'account', '员工账号', '员工账号的查询与维护', 'assign_role',    '分配角色',     'platform:user:account:assign_role',    7,  1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0),
  -- 部门管理（platform:org:department:*）
  ( 7, 1, 'org', '组织管理', 'department', '部门', '组织架构的查询与维护', 'view',   '查看部门', 'platform:org:department:view',   8,  1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0),
  ( 8, 1, 'org', '组织管理', 'department', '部门', '组织架构的查询与维护', 'create', '新增部门', 'platform:org:department:create', 9,  1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0),
  ( 9, 1, 'org', '组织管理', 'department', '部门', '组织架构的查询与维护', 'edit',   '编辑部门', 'platform:org:department:edit',   10, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0),
  (10, 1, 'org', '组织管理', 'department', '部门', '组织架构的查询与维护', 'status', '部门启停', 'platform:org:department:status', 11, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0),
  -- 角色管理（platform:role:role:*）
  (11, 1, 'role', '角色管理', 'role', '角色', '角色定义与授权', 'view',   '查看角色',     'platform:role:role:view',   12, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0),
  (12, 1, 'role', '角色管理', 'role', '角色', '角色定义与授权', 'create', '新增角色',     'platform:role:role:create', 13, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0),
  (13, 1, 'role', '角色管理', 'role', '角色', '角色定义与授权', 'edit',   '编辑角色',     'platform:role:role:edit',   14, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0),
  (14, 1, 'role', '角色管理', 'role', '角色', '角色定义与授权', 'status', '角色启停',     'platform:role:role:status', 15, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0),
  (15, 1, 'role', '角色管理', 'role', '角色', '角色定义与授权', 'grant',  '分配角色权限', 'platform:role:role:grant',  16, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0),
  -- 应用管理（platform:app:application:*）
  (16, 1, 'app', '应用管理', 'application', '接入应用', '应用接入管理', 'view',   '查看应用', 'platform:app:application:view',   17, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0),
  (17, 1, 'app', '应用管理', 'application', '接入应用', '应用接入管理', 'create', '新增应用', 'platform:app:application:create', 18, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0),
  (18, 1, 'app', '应用管理', 'application', '接入应用', '应用接入管理', 'edit',   '编辑应用', 'platform:app:application:edit',   19, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0),
  (19, 1, 'app', '应用管理', 'application', '接入应用', '应用接入管理', 'status', '应用启停', 'platform:app:application:status', 20, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0),
  -- 权限点管理（platform:resource:permission:*）
  (20, 1, 'resource', '资源管理', 'permission', '权限点', '权限点目录维护', 'view',   '查看权限点', 'platform:resource:permission:view',   21, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0),
  (21, 1, 'resource', '资源管理', 'permission', '权限点', '权限点目录维护', 'create', '新增权限点', 'platform:resource:permission:create', 22, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0),
  (22, 1, 'resource', '资源管理', 'permission', '权限点', '权限点目录维护', 'status', '权限点启停', 'platform:resource:permission:status', 23, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0),
  -- 日志审计（platform:audit:log:*）
  (23, 1, 'audit', '审计管理', 'log', '操作日志', '操作日志查询', 'view', '查看操作日志', 'platform:audit:log:view', 24, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0),
  -- 演示端点（engineering:demo:project:*，source_row=NULL 额外测试权限）
  (24, 2, 'demo', '演示模块', 'project', '演示项目', '演示场景数据提交（A38/A39）', 'view',   '查看演示项目', 'engineering:demo:project:view',   NULL, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0),
  (25, 2, 'demo', '演示模块', 'project', '演示项目', '演示场景数据提交（A38/A39）', 'submit', '演示提交',     'engineering:demo:project:submit', NULL, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0);

-- ============================================================================
-- 4. 角色：PLATFORM_ADMIN 保留角色（§8.1）+ 原五类业务角色（§7.4，均启用、
--    不预授权——"初次仅发布明确确认的授权"，业务授权由管理员经 A26 配置）
-- ============================================================================
INSERT IGNORE INTO sys_role
  (id, role_name, role_key, description, status, create_time, update_time, version)
VALUES
  (1, '控制面管理员', 'PLATFORM_ADMIN',  '保留角色：固定拥有全部 platform 技术权限，禁止 API 修改与停用', 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0),
  (2, '系统管理员',   'SYS_ADMIN',       '原矩阵业务角色（与 PLATFORM_ADMIN 不混同）',                     1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0),
  (3, '公司领导',     'COMPANY_LEADER',  '原矩阵业务角色',                                                 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0),
  (4, '部门经理',     'DEPT_MANAGER',    '原矩阵业务角色',                                                 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0),
  (5, '项目经理',     'PROJECT_MANAGER', '原矩阵业务角色',                                                 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0),
  (6, '普通员工',     'EMPLOYEE',        '原矩阵业务角色',                                                 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0);

-- ============================================================================
-- 5. PLATFORM_ADMIN 权限集合（§8.1：仅全部 platform:* 去重集合，版本化种子固定；
--    演示权限 24/25（engineering:demo:*）不授予保留角色，业务授权由管理员经 A26 配置）
-- ============================================================================
INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id) VALUES
  ( 1, 1,  1), ( 2, 1,  2), ( 3, 1,  3), ( 4, 1,  4), ( 5, 1,  5),
  ( 6, 1,  6), ( 7, 1,  7), ( 8, 1,  8), ( 9, 1,  9), (10, 1, 10),
  (11, 1, 11), (12, 1, 12), (13, 1, 13), (14, 1, 14), (15, 1, 15),
  (16, 1, 16), (17, 1, 17), (18, 1, 18), (19, 1, 19), (20, 1, 20),
  (21, 1, 21), (22, 1, 22), (23, 1, 23);

-- ============================================================================
-- 6. bootstrap 管理员（§8.1 首次安装闭环：唯一初始管理员）
--    ADMIN / Admin@2026rbac（本地默认；生产由部署配置交付，安装后立即改密）
--    phone_cipher 留 NULL——真实手机号须经理 AES-GCM 加密后写入
-- ============================================================================
INSERT IGNORE INTO sys_user
  (id, username, password_hash, real_name, company_id, dept_id, position_name,
   phone_cipher, status, must_change_password, failed_login_count, locked_until,
   create_time, update_time, version)
VALUES
  (1, 'ADMIN',
   '$argon2id$v=19$m=19456,t=2,p=1$iTWXD4ufH7Q1fzcafUCMzw$m9APxpXsNqYNqKMieRlRHtSb60Z5UOURJyW4t3dwqZQ',
   '控制面管理员', 2, 3, '控制面管理员',
   NULL, 'ENABLED', 0, 0, NULL,
   UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0);

-- 管理员 → PLATFORM_ADMIN（直接分配，管理员不变量的初始有效管理员）
INSERT IGNORE INTO sys_user_role (id, user_id, role_id) VALUES
  (1, 1, 1);
