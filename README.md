# 市政公司统一权限管理平台

基于 Spring Boot、Vue 3 和 MySQL 的 RBAC0 统一权限管理项目。员工通过显式分配的角色获得权限，后端实时鉴权，门户按有效权限展示应用卡片。

## 最近更新（2026-10-24）

- 员工管理按“公司 / 部门 → 职级”分层，显示实际人数，保留原始岗位。
- 隐藏内部组织树根、无人员组织及空职级。点击公司或部门查询下属组织；公司职级只统计本公司人员，不混入下属分公司。
- 修正总部领导漏查及重复分类，分公司管理人员显示为“分公司领导”；不再给每个部门固定添加所有职级。
- 角色管理按部门职责和职级分组，显示适用原始岗位。未配置分类的自定义角色进入“其他角色 / 待分类”。
- 员工可点击应用和操作卡片，查询权限编码、实际权限查询接口、调用代码和后端鉴权示例；打开详情前重新校验权限。
- 清理本机QA测试数据，创建测试对象的脚本默认关闭。员工资料、备份、凭据和验收截图不随代码上传。
- 数据库密码、手机号加密密钥外置，增加环境模板和Windows启动脚本。

## 功能与边界

| 模块 | 已实现内容 |
| --- | --- |
| 员工账号 | 资料维护、启停、临时密码重置、首次改密、显式角色分配 |
| 组织管理 | 公司、分公司、部门、项目部树，负责人信息，组织停用影响会话 |
| 角色与权限 | 多角色权限并集、角色权限配置、应用及权限启停、管理员保护 |
| 员工门户 | 按有效权限生成应用卡片，按模块展示操作及权限接口详情 |
| 认证安全 | Argon2id密码哈希、不透明Token、MySQL会话、失败锁定、登录限流 |
| 数据保护 | 手机号AES-GCM加密、If-Match/ETag版本控制、操作审计 |
| 课程工具 | 三表校验、43个公共/职责角色方案、授权预览与显式批量导入 |

当前有40个接口、10张表。组织和职级分类不自动授予权限；主管、主任工程师按当前课程规则归普通员工，主任、副主任、部长、副部长归部门负责人。

本阶段不实现角色继承、SSD/DSD或真实业务数据范围，也未开发财务、GIS、OA等外部业务系统。未接入的业务接口会明确说明，代码示例不代表真实业务接口已实现。

## 环境要求

Java 17、Maven 3.9+、MySQL 8.0/8.4；前端为Vue 3、TypeScript、Vite、Pinia、Element Plus。建议Node.js 22.12+和npm。

## 快速开始

### 1. 初始化新开发库

依次执行以下脚本，已有数据库不要重新初始化：

```bash
mysql -h 127.0.0.1 -P 3306 -u root -p < db/schema.sql
mysql -h 127.0.0.1 -P 3306 -u root -p < db/data.sql
```

Windows PowerShell不支持上述输入重定向，可在MySQL客户端执行：

```sql
SOURCE C:/path/to/project/db/schema.sql;
SOURCE C:/path/to/project/db/data.sql;
```

基础种子包含6个基础角色、25个权限点、示例组织和开发管理员：`ADMIN / Admin@2026rbac`。这是公开的开发示例，安装后应修改密码，不可直接用于对外部署。

### 2. 配置环境

复制`.env.example`为`.env.local`，填写自己的数据库密码和32字节Base64手机号加密密钥。生成密钥：

```bash
openssl rand -base64 32
```

Windows PowerShell也可生成：

```powershell
$phoneKeyBytes = New-Object byte[] 32
$phoneKeyRng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$phoneKeyRng.GetBytes($phoneKeyBytes)
[Convert]::ToBase64String($phoneKeyBytes)
$phoneKeyRng.Dispose()
```

密钥只保存在本机`.env.local`的`RBAC_PHONE_KEY`中。同一数据库需保留同一密钥，更换后已有手机号无法解密。`.env.local`已被Git忽略。

### 3. 启动后端

Windows PowerShell：

```powershell
.\scripts\start-backend.ps1
```

其他环境将`RBAC_DB_HOST`、`RBAC_DB_PORT`、`RBAC_DB_NAME`、`RBAC_DB_USER`、`RBAC_DB_PASSWORD`、`RBAC_PHONE_KEY`注入环境变量后运行：

```bash
mvn spring-boot:run
```

Spring Boot本身不自动读取dotenv文件，Windows脚本负责加载`.env.local`；也可使用IDE运行配置注入变量。

### 4. 启动前端

另开终端：

```bash
cd frontend
npm ci
npm run dev
```

| 地址 | 用途 |
| --- | --- |
| http://127.0.0.1:5173 | 前端 |
| http://127.0.0.1:8080/swagger-ui/index.html | 接口文档 |
| http://127.0.0.1:8080/v3/api-docs | OpenAPI JSON |

Vite将`/api/v1`代理到8080端口。默认允许`localhost:5173`和`127.0.0.1:5173`。

## 使用流程

管理员维护组织、应用与权限，为角色配置操作权限，再按工号给员工分配角色。新员工没有统一默认密码，管理员设置临时密码并告知本人，员工首次登录改密后启用账号。

员工点击门户应用卡片，再点击具体操作，查看当前有效权限、接口和示例。查询自身应用权限的接口：

```http
GET /api/v1/auth/permissions?applicationId=<应用ID>
Authorization: Bearer <当前会话Token>
```

此接口只能查询当前登录员工权限；前端按钮显隐不能代替业务系统后端鉴权。

## 课程数据和批量导入

仓库包含导入代码、非人员权限标签与角色分类，**不包含三张源Excel、10,000名员工资料、个人角色计划、手机号或数据库快照**。克隆后初始化的是基础库，不会自动恢复本机数据。

完整课程导入另需本地提供原始三表及已确认的V3.3方案，见[导入说明](tools/course-import/README.md)。先校验和预览，再使用确认值显式应用；不在登录时按部门或岗位自动授权。

已有课程角色的数据库需要在环境中设置：

```dotenv
SPRING_PROFILES_ACTIVE=course
RBAC_BUSINESS_MODE=PERMISSION_DISPLAY_ONLY
RBAC_COURSE_DATASOURCE_ID=jdbc:mysql://127.0.0.1:3306/rbac_platform
```

连接标识须与实际主机、端口、库名一致，不包含查询参数。课程配置拒绝生产profile。

## 构建与验证

```bash
mvn package
cd frontend
npm ci
npm run build
```

本次本地验证覆盖10,001个账号的分类、115个组织记录、242个组织职级组合的查询、分页及门户权限详情。Java源码目前没有自动化单元测试，Maven打包通过不代表完整业务验收。前端构建有现存的大包体积提示。

专项测试应在独立验收环境运行；仅在设置`RBAC_ALLOW_TEST_FIXTURES=1`后，相关脚本才会创建合成测试账号。

## 项目目录

```text
src/main/java/com/rbac/  后端业务、鉴权与接口
src/main/resources/     应用配置
frontend/src/           前端页面与权限展示
frontend/src/generated/ 非人员权限标签和角色分类
db/                     建表和基础开发种子
tools/course-import/    课程资料校验与导入工具
scripts/                本地启动脚本
.env.example            环境配置模板
```

本机验收记录、日志、数据库备份、IDE配置、编译产物及依赖目录均不上传。
