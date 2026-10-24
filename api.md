# 市政公司统一权限管理平台 API 文档（第一阶段骨架）

> 对齐《开发设计文档（第一阶段·完整修订版）》§10 数据契约。
> Base URL：`http://localhost:8080`，全部路径前缀 `/api/v1`。
> 本文与 [src/main/java/com/rbac/controller](src/main/java/com/rbac/controller) 下 10 个 Controller 的实现一一对应（共 40 个端点，A01–A40）。

## 1. 通用约定

### 1.1 认证

- 除 **A01 登录** 与 **A02 退出**（幂等）外，全部接口要求请求头 `Authorization: Bearer <token>`。
- Token 为不透明随机串（32 字节 SecureRandom Base64URL），服务端仅存 SHA-256 摘要。
- 会话双重过期：绝对有效期（默认 120 分钟）+ 空闲超时（默认 30 分钟）。
- **受限会话**（首次改密前）：仅允许 A04 改密与 A02 退出，访问其他接口返回 403 `PASSWORD_CHANGE_REQUIRED`。

### 1.2 统一响应包装

```json
{ "code": "OK", "message": "success", "data": <业务数据>, "requestId": "<追踪ID>" }
```

失败时 `data` 为 `null`；仅 HTTP 400 携带 `fieldErrors` 字段错误数组。

### 1.3 乐观锁（If-Match / ETag）

- 所有**写路径**（PATCH/PUT/POST 返回 201 的详情类除外）要求请求头 `If-Match: "<version>"`，缺失返回 428 `PRECONDITION_REQUIRED`，不匹配返回 412 `VERSION_CONFLICT`。
- 读详情接口在响应头返回 `ETag: "<version>"`，写回时原样放入 `If-Match`。
- A19 返回 `DeptStatusVO`（无 ETag 语义变化，`item` 内含新版本）。

### 1.4 公共分页参数 P

| 参数 | 说明 |
| --- | --- |
| `page` | 页码，默认 1 |
| `size` | 每页条数，默认 20，最大 100 |

分页响应：`{ "records": [...], "total": 0, "page": 1, "size": 20 }`

### 1.5 错误码

| code | HTTP | 场景 |
| --- | --- | --- |
| OK | 200/201 | 成功 |
| INVALID_ARGUMENT | 400 | 参数校验失败（含 fieldErrors） |
| CURRENT_PASSWORD_INVALID | 400 | 改密时当前密码不正确 |
| WEAK_PASSWORD | 400 | 弱口令/不符合密码策略 |
| LOGIN_FAILED | 401 | 工号或密码不匹配（统一提示，不区分原因） |
| SESSION_INVALID | 401 | 会话无效/过期/已撤销 |
| PERMISSION_DENIED | 403 | 无有效权限 |
| PASSWORD_CHANGE_REQUIRED | 403 | 受限会话访问非改密接口 |
| NOT_FOUND | 404 | 目标资源不存在 |
| USERNAME_EXISTS / ROLE_KEY_EXISTS / APP_KEY_EXISTS / PERMISSION_KEY_EXISTS / NODE_KEY_EXISTS | 409 | 唯一键冲突 |
| LAST_ADMIN_REQUIRED | 409 | 变更会使有效管理员为零 |
| RESERVED_RESOURCE | 409 | 保留资源禁止变更（PLATFORM_ADMIN / platform 应用等） |
| STATE_CONFLICT | 409 | 状态机冲突（如未改密先启用） |
| VERSION_CONFLICT | 412 | If-Match 版本不匹配 |
| PRECONDITION_REQUIRED | 428 | 缺少 If-Match 头 |
| RATE_LIMITED | 429 | IP 限流（默认 20 次/分钟） |
| LOGIN_LOCKED | 429 | 连续失败 5 次锁定 15 分钟 |
| AUTH_UNAVAILABLE | 503 | 鉴权依赖不可用，默认拒绝 |
| INTERNAL_ERROR | 500 | 系统内部错误 |

## 2. 端点目录（A01–A40）

**实现状态说明**：本阶段为 struct 骨架——读路径与登录链路真实可用；标注「骨架」的写路径返回 501，待下一阶段补全业务规则。`rbac.service-mode=oop` 时由 OOP 实现接管（当前开关为空壳，OOP 未开发）。

### 认证与会话（AuthController）

| 编号 | 方法与路径 | 访问分类/固定权限 | 请求 | 成功 data 与额外规则 | 状态 |
| --- | --- | --- | --- | --- | --- |
| A01 | POST /auth/login | PUBLIC | LoginDTO | 200 LoginVO；Token 仅返回一次；登录错误统一提示；锁定/限流 429 | 真实 |
| A02 | POST /auth/logout | PUBLIC（可携带 Token） | 无正文 | 200 null；撤销当前会话；无 Token/重复退出也 200 | 真实 |
| A03 | GET /auth/me | SELF | 无 | 200 UserVO；受限会话 403 | 真实 |
| A04 | PUT /auth/password | SELF（允许受限） | PasswordChangeDTO | 200 null；改密并激活，撤销全部会话；无 If-Match | 真实 |
| A05 | GET /auth/apps | SELF | 无 | 200 ApplicationVO[]；仅有有效权限的启用应用 | 真实 |
| A06 | GET /auth/permissions | SELF | Query `applicationId` 可选 | 200 CurrentPermissionsVO；未知应用 404 | 真实 |

### 用户与角色分配（UserController）

| 编号 | 方法与路径 | 固定权限 | 请求 | 成功 data 与额外规则 | 状态 |
| --- | --- | --- | --- | --- | --- |
| A07 | GET /users | platform:user:account:view | P；keyword≤80、deptId、status 可选 | 200 Page\<UserVO\>；keyword 匹配工号或姓名 | 真实 |
| A08 | GET /users/{id} | platform:user:account:view | Path id | 200 UserVO + ETag | 真实 |
| A09 | POST /users | platform:user:account:create | UserCreateDTO | 201 UserVO + ETag；初始无密码、PENDING | 骨架 501 |
| A10 | PATCH /users/{id} | platform:user:account:edit | UserPatchDTO；If-Match | 200 UserVO + 新 ETag；调动撤销本人会话 | 骨架 501 |
| A11 | PATCH /users/{id}/status | platform:user:account:status | UserStatusDTO；If-Match | 200 UserVO + 新 ETag；停用撤销会话 | 骨架 501 |
| A12 | POST /users/{id}/password-reset | platform:user:account:reset_password | PasswordResetDTO；If-Match | 200 UserVO + 新 ETag；不回显密码 | 骨架 501 |
| A13 | GET /users/{id}/roles | platform:user:account:view | Path id | 200 UserRolesVO + 用户 ETag | 骨架 501 |
| A14 | PUT /users/{id}/roles | platform:user:account:assign_role | UserRoleAssignDTO；If-Match | 200 UserRolesVO + 新 ETag；完整替换 ≤100 | 骨架 501 |

### 部门组织树（DeptController）

| 编号 | 方法与路径 | 固定权限 | 请求 | 成功 data 与额外规则 | 状态 |
| --- | --- | --- | --- | --- | --- |
| A15 | GET /depts/tree | platform:org:department:view | rootId、includeDisabled 可选 | 200 DeptTreeVO[]；父停用整支不返回 | 真实 |
| A16 | GET /depts/{id} | platform:org:department:view | Path id | 200 DeptVO + ETag | 真实 |
| A17 | POST /depts | platform:org:department:create | DeptCreateDTO | 201 DeptVO + ETag；父节点须启用；nodeKey 唯一 | 骨架 501 |
| A18 | PATCH /depts/{id} | platform:org:department:edit | DeptPatchDTO；If-Match | 200 DeptVO + 新 ETag；不改父节点 | 骨架 501 |
| A19 | PATCH /depts/{id}/status | platform:org:department:status | BinaryStatusDTO；If-Match | 200 DeptStatusVO；停用撤销子树会话并查管理员 | 骨架 501 |

### 角色管理（RoleController）

| 编号 | 方法与路径 | 固定权限 | 请求 | 成功 data 与额外规则 | 状态 |
| --- | --- | --- | --- | --- | --- |
| A20 | GET /roles | platform:role:role:view | P；keyword≤80、status 可选 | 200 Page\<RoleVO\> | 真实 |
| A21 | GET /roles/{id} | platform:role:role:view | Path id | 200 RoleVO + ETag | 真实 |
| A22 | POST /roles | platform:role:role:create | RoleCreateDTO | 201 RoleVO + ETag；初始无权限 | 骨架 501 |
| A23 | PATCH /roles/{id} | platform:role:role:edit | RolePatchDTO；If-Match | 200 RoleVO + 新 ETag；保留角色禁改 | 骨架 501 |
| A24 | PATCH /roles/{id}/status | platform:role:role:status | BinaryStatusDTO；If-Match | 200 RoleVO + 新 ETag | 骨架 501 |
| A25 | GET /roles/{id}/permissions | platform:role:role:view | Path id | 200 RolePermissionsVO + 角色 ETag | 真实 |
| A26 | PUT /roles/{id}/permissions | platform:role:role:grant | RolePermissionAssignDTO；If-Match | 200 RolePermissionsVO；完整替换 ≤5000；禁改 PLATFORM_ADMIN | 骨架 501 |

### 应用登记（ApplicationController）

| 编号 | 方法与路径 | 固定权限 | 请求 | 成功 data 与额外规则 | 状态 |
| --- | --- | --- | --- | --- | --- |
| A27 | GET /applications | platform:app:application:view | P；keyword≤100、status 可选 | 200 Page\<ApplicationVO\> | 真实 |
| A28 | GET /applications/{id} | platform:app:application:view | Path id | 200 ApplicationVO + ETag | 真实 |
| A29 | POST /applications | platform:app:application:create | ApplicationCreateDTO | 201 ApplicationVO + ETag；不自动授权限 | 骨架 501 |
| A30 | PATCH /applications/{id} | platform:app:application:edit | ApplicationPatchDTO；If-Match | 200 ApplicationVO + 新 ETag；appKey 不可变 | 骨架 501 |
| A31 | PATCH /applications/{id}/status | platform:app:application:status | BinaryStatusDTO；If-Match | 200 ApplicationVO + 新 ETag；停用使下属权限失效 | 骨架 501 |

### 权限点维护（PermissionController）

| 编号 | 方法与路径 | 固定权限 | 请求 | 成功 data 与额外规则 | 状态 |
| --- | --- | --- | --- | --- | --- |
| A32 | GET /permissions | platform:resource:permission:view | P；applicationId、keyword≤160、status 可选 | 200 Page\<PermissionVO\> | 真实 |
| A33 | GET /permissions/{id} | platform:resource:permission:view | Path id | 200 PermissionVO + ETag | 真实 |
| A34 | POST /permissions | platform:resource:permission:create | PermissionCreateDTO | 201 PermissionVO + ETag；四段码服务端生成 | 骨架 501 |
| A35 | PATCH /permissions/{id}/status | platform:resource:permission:status | BinaryStatusDTO；If-Match | 200 PermissionVO + 新 ETag；固定技术权限禁停 | 骨架 501 |

### 操作日志（LogController，只读）

| 编号 | 方法与路径 | 固定权限 | 请求 | 成功 data 与额外规则 | 状态 |
| --- | --- | --- | --- | --- | --- |
| A36 | GET /logs | platform:audit:log:view | from/to 必填（跨度≤31 日）；userId、eventType、result、requestId 可选 | 200 Page\<LogVO\>；时间/id 降序 | 真实 |
| A37 | GET /logs/{id} | platform:audit:log:view | Path id | 200 LogVO；无 ETag（不可修改） | 真实 |

### 固定权限演示（DemoController）

| 编号 | 方法与路径 | 固定权限 | 请求 | 成功 data 与额外规则 | 状态 |
| --- | --- | --- | --- | --- | --- |
| A38 | GET /demo/projects | engineering:demo:project:view | 无 | 200 DemoProjectVO[]；固定合成项目 | 真实 |
| A39 | POST /demo/projects/submit | engineering:demo:project:submit | Body `{}` | 200 DemoActionVO；独立短事务写 DEMO_SUBMIT 审计 | 真实 |

### 集中字典（MetaController）

| 编号 | 方法与路径 | 访问分类 | 请求 | 成功 data 与额外规则 | 状态 |
| --- | --- | --- | --- | --- | --- |
| A40 | GET /meta/dictionaries | AUTHENTICATED（非受限） | 无 | 200 DictionaryVO；只读版本化枚举与动作字典 | 真实 |

## 3. 输入契约（DTO）

| DTO | 字段与规则 |
| --- | --- |
| LoginDTO | username:string 1–50（自动去空白转大写）；password:string 12–64 |
| PasswordChangeDTO | currentPassword:string；newPassword:string 12–64；不得与旧密码相同 |
| PasswordResetDTO | temporaryPassword:string 12–64；不回显 |
| UserCreateDTO | username 1–50；realName 1–80；companyId:ID；deptId:ID；positionName 1–80；phone 可选（非空须 11 位数字） |
| UserPatchDTO | realName/companyId/deptId/positionName/phone 可选；phone=null 清除 |
| UserStatusDTO | status:ENABLED/DISABLED/PENDING_ACTIVATION；状态机校验见 A11 |
| UserRoleAssignDTO | roleIds:ID[]；去重 ≤100；[] 清空 |
| DeptCreateDTO | deptName 1–160；nodeKey 1–160；parentId:ID；nodeType:COMPANY/DEPARTMENT/PROJECT_DEPT；managerUserId 可选；status 默认 1；不能创建 ROOT |
| DeptPatchDTO | deptName/managerUserId 可选；nodeKey/parentId/nodeType 不可变 |
| BinaryStatusDTO | status:0 或 1（仅整数） |
| RoleCreateDTO | roleName 1–80；roleKey 1–50 `[A-Z][A-Z0-9_]*`；description≤300 可选；status 默认 1 |
| RolePatchDTO | roleName/description 可选；roleKey 不可变 |
| RolePermissionAssignDTO | permissionIds:ID[]；≤5000 去重；[] 清空 |
| ApplicationCreateDTO | appName 1–100；appKey 1–32 `[a-z][a-z0-9_]*`；status 默认 1 |
| ApplicationPatchDTO | appName 可选；appKey 不可变 |
| PermissionCreateDTO | applicationId:ID；moduleKey≤32；moduleName≤100；resourceKey≤48；resourceName≤100；resourceDescription≤500 可选；actionCode 须字典登记；不接收 sourceRow/permKey/permName |

## 4. 输出契约（VO）

公共字段 `VO_C = { version, createTime, updateTime }`，适用于 User/Dept/Role/Application/Permission。

| VO | 字段（+ VO_C） |
| --- | --- |
| UserVO | id, username, realName, companyId, deptId, deptName, positionName, phoneMasked(null 可), status, mustChangePassword, lockedUntil(null 可) |
| DeptVO | id, deptName, nodeKey, parentId(null 可), nodeType, managerUserId(null 可), status:0/1 |
| DeptTreeVO | DeptVO 全部字段 + children:DeptTreeVO[]（叶子 []） |
| RoleVO | id, roleName, roleKey, description(null 可), status:0/1 |
| ApplicationVO | id, appName, appKey, status:0/1 |
| PermissionVO | id, applicationId, moduleKey, moduleName, resourceKey, resourceName, resourceDescription(null 可), actionCode, permName, permKey, sourceRow(null 可), status:0/1 |
| LoginVO | token, tokenType:"Bearer", expiresAt, restricted:boolean, userId |
| UserRolesVO | userId, roleIds:ID[], roles:RoleVO[], version |
| RolePermissionsVO | roleId, permissionIds:ID[], permissions:PermissionVO[], version |
| CurrentPermissionsVO | permissionKeys:string[], roleIds:ID[], policyVersion |
| LogVO | id, userId(null 可), usernameSnapshot(null 可), eventType, targetType(null 可), targetId(null 可), httpMethod, requestPath, requestId, result, reasonCode, createTime, details(null 可) |
| DemoProjectVO | id, name |
| DemoActionVO | accepted:boolean, operation:string |
| DeptStatusVO | item:DeptVO, affectedUserCount:integer |
| DictionaryVO | dictionaryVersion, userStatuses/binaryStatuses/nodeTypes/auditResults/eventTypes(value+label), actions(code+label), limits, reservedRoleKeys, reservedAppKeys |

## 5. struct / oop 切换开关

`application.yml`：

```yaml
rbac:
  service-mode: struct   # struct | oop
```

- `struct`：当前唯一实现，登录链路真实可跑、写路径 501 骨架（`service/struct/*`）。
- `oop`：**空壳预留**。`ServiceModeConfig` 在启动期校验该值合法性；OOP 实现开发完成后再以 `@ConditionalOnProperty(name = "rbac.service-mode", havingValue = "oop")` 注册即可，无需改动 Controller。
- 非法值：启动失败（快速失败，避免演示时静默走错实现）。
