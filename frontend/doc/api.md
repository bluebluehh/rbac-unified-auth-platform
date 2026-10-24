# 前端后端接口对接清单

本清单对应设计文档 A01-A40。接口前缀统一为 `/api/v1`。

## 通用约定

请求头：

```http
Authorization: Bearer <opaque-token>
X-Request-Id: <string, max 64>
If-Match: "<version>"
Content-Type: application/json
```

- `Authorization` 用于除登录、退出和字典外的受保护接口。
- `X-Request-Id` 由前端生成并回传；响应 JSON 的 `requestId` 与响应头 `X-Request-Id` 一致。
- 所有写操作使用版本号；A04 本人改密除外。
- 前端统一解包成功响应中的 `data`。
- 401 `SESSION_INVALID` 清空内存 Token 并回登录。
- 400 `fieldErrors` 显示在对应表单项。
- 403 显示无权限并刷新 A06。
- 404 关闭或刷新已删除对象。
- 412 刷新目标并保留未提交选择，要求用户再次确认。
- 429 显示 `Retry-After`。

## 认证与门户

| 编号 | 方法与路径 | 权限 | 前端调用 |
| --- | --- | --- | --- |
| A01 | `POST /auth/login` | PUBLIC | `login` |
| A02 | `POST /auth/logout` | PUBLIC，可携带 Token | `logout` |
| A03 | `GET /auth/me` | SELF | `getMe` |
| A04 | `PUT /auth/password` | SELF，允许受限会话 | `changePassword` |
| A05 | `GET /auth/apps` | SELF | `getMyApps` |
| A06 | `GET /auth/permissions` | SELF | `getMyPermissions` |

## 员工

| 编号 | 方法与路径 | 固定权限 |
| --- | --- | --- |
| A07 | `GET /users` | `platform:user:account:view` |
| A08 | `GET /users/{id}` | `platform:user:account:view` |
| A09 | `POST /users` | `platform:user:account:create` |
| A10 | `PATCH /users/{id}` | `platform:user:account:edit` |
| A11 | `PATCH /users/{id}/status` | `platform:user:account:status` |
| A12 | `POST /users/{id}/password-reset` | `platform:user:account:reset_password` |
| A13 | `GET /users/{id}/roles` | `platform:user:account:view` |
| A14 | `PUT /users/{id}/roles` | `platform:user:account:assign_role` |

## 部门与公司

| 编号 | 方法与路径 | 固定权限 |
| --- | --- | --- |
| A15 | `GET /depts/tree` | `platform:org:department:view` |
| A16 | `GET /depts/{id}` | `platform:org:department:view` |
| A17 | `POST /depts` | `platform:org:department:create` |
| A18 | `PATCH /depts/{id}` | `platform:org:department:edit` |
| A19 | `PATCH /depts/{id}/status` | `platform:org:department:status` |

公司没有独立 Controller，统一使用 `sys_dept` 的 ROOT/COMPANY 节点和 A15-A19。

## 角色

| 编号 | 方法与路径 | 固定权限 |
| --- | --- | --- |
| A20 | `GET /roles` | `platform:role:role:view` |
| A21 | `GET /roles/{id}` | `platform:role:role:view` |
| A22 | `POST /roles` | `platform:role:role:create` |
| A23 | `PATCH /roles/{id}` | `platform:role:role:edit` |
| A24 | `PATCH /roles/{id}/status` | `platform:role:role:status` |
| A25 | `GET /roles/{id}/permissions` | `platform:role:role:view` |
| A26 | `PUT /roles/{id}/permissions` | `platform:role:role:grant` |

## 应用与权限点

| 编号 | 方法与路径 | 固定权限 |
| --- | --- | --- |
| A27 | `GET /applications` | `platform:app:application:view` |
| A28 | `GET /applications/{id}` | `platform:app:application:view` |
| A29 | `POST /applications` | `platform:app:application:create` |
| A30 | `PATCH /applications/{id}` | `platform:app:application:edit` |
| A31 | `PATCH /applications/{id}/status` | `platform:app:application:status` |
| A32 | `GET /permissions` | `platform:resource:permission:view` |
| A33 | `GET /permissions/{id}` | `platform:resource:permission:view` |
| A34 | `POST /permissions` | `platform:resource:permission:create` |
| A35 | `PATCH /permissions/{id}/status` | `platform:resource:permission:status` |

## 日志、演示与字典

| 编号 | 方法与路径 | 权限 |
| --- | --- | --- |
| A36 | `GET /logs` | `platform:audit:log:view` |
| A37 | `GET /logs/{id}` | `platform:audit:log:view` |
| A38 | `GET /demo/projects` | `engineering:demo:project:view` |
| A39 | `POST /demo/projects/submit` | `engineering:demo:project:submit` |
| A40 | `GET /meta/dictionaries` | PUBLIC |

## 分页与查询

分页响应：

```json
{
  "records": [],
  "total": 0,
  "page": 1,
  "size": 20
}
```

分页大小上限为 100。列表查询必须支持搜索、状态筛选和分页；写操作使用 `If-Match` 最新版本。

## 前端封装位置

- 请求层：`src/api/request.ts`
- 认证：`src/api/auth.ts`
- 用户：`src/api/user.ts`
- 部门：`src/api/dept.ts`
- 角色：`src/api/role.ts`
- 应用：`src/api/application.ts`
- 权限点：`src/api/permission.ts`
- 日志：`src/api/log.ts`
- 字典：`src/api/meta.ts`
- 演示：`src/api/demo.ts`

## 后端联调验收

- 40 个端点的 HTTP 状态、字段名和枚举必须与设计文档一致。
- ID、`version` 和分页计数使用兼容字符串/安全整数，不丢精度。
- 所有响应提供 `requestId` 和 `X-Request-Id`。
- 可编辑详情和写操作返回 ETag。
- 后端始终重新鉴权，不信任前端隐藏按钮或客户端传入的身份。
- 数据库变化能被 A05/A06/A07/A15/A20/A27/A32/A36 等查询实时反映。
- 新增业务应用页面仍需前端增加路由；数据库单条应用记录不能自动生成业务 UI。
