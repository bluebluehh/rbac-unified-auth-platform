# 市政公司统一权限管理平台前端

基于 Vue 3、TypeScript、Vite、Pinia、Vue Router 和 Element Plus 的 RBAC0 前端。

前端只通过后端 `/api/v1` 接口读取业务数据，不包含员工、部门、角色、应用、权限或日志数据库，也不在前端仓库保存源 Excel 和完整员工种子数据。

## 功能范围

- 平台欢迎页和工号密码登录
- 首次改密、自主改密和退出
- 员工门户与按有效权限生成的应用卡片
- 工程业务演示页
- 员工、部门和公司、角色、角色授权、应用、功能权限、操作日志
- 菜单、按钮和路由权限显隐
- 版本校验、完整集合替换、统一错误处理

当前阶段不实现角色继承、SSD/DSD、部门数据范围过滤以及 OA、财务等真实业务系统页面。

## 启动

先启动小组的 Spring Boot 后端和 MySQL，再运行前端：

```bash
npm install
npm run dev
```

默认前端地址：

```text
http://localhost:5173
```

Vite 将 `/api/v1` 代理到：

```text
http://localhost:8080
```

如果后端端口不同，请修改 `vite.config.ts` 的代理目标。

检查与构建：

```bash
npm run typecheck
npm run build
npm run preview
```

## 数据来源

运行数据全部来自后端接口：

- 员工和账号：A03、A07、A08
- 公司和部门树：A15
- 用户角色：A13
- 角色：A20、A21
- 角色权限：A25
- 应用：A05、A27、A28
- 功能权限：A06、A32、A33
- 操作日志：A36、A37
- 状态和动作字典：A40

前端不读取数据库，不读取 Excel，不根据岗位自动推导角色。

## 路由清单

| 路由 | 页面 | 权限 |
| --- | --- | --- |
| `/welcome` | 平台欢迎页 | 公开 |
| `/login` | 登录页 | 公开 |
| `/change-password` | 首次或自主改密 | 已登录，允许受限会话 |
| `/portal` | 应用门户 | 已登录且非受限 |
| `/portal/demo/projects` | 工程业务演示 | 任一 `engineering:*` 权限 |
| `/admin` | 管理后台入口 | 自动跳转首个可访问管理页 |
| `/admin/users` | 员工管理 | `platform:user:account:view` |
| `/admin/users/:id` | 员工详情 | `platform:user:account:view` |
| `/admin/depts` | 公司与部门树 | `platform:org:department:view` |
| `/admin/roles` | 角色管理 | `platform:role:role:view` |
| `/admin/roles/:id/permissions` | 角色授权 | `platform:role:role:grant` |
| `/admin/applications` | 应用管理 | `platform:app:application:view` |
| `/admin/permissions` | 功能权限管理 | `platform:resource:permission:view` |
| `/admin/logs` | 操作日志 | `platform:audit:log:view` |
| `/403` | 无权限 | 公开错误页 |
| 其他路径 | 404 | 公开错误页 |

平台卡片会根据 A06 权限进入第一个可访问的管理页面。工程卡片只对拥有任一 `engineering:*` 权限的账号显示。

没有注册业务页面的应用仍可依据 A05 显示卡片，但必须标记为“建设中”，不得拼接 URL 或进入不存在的页面。

## 接口契约

统一 API 前缀为 `/api/v1`。请求层自动添加：

```http
Authorization: Bearer <opaque-token>
X-Request-Id: <request-id>
If-Match: "<version>"
```

统一响应结构：

```json
{
  "code": "OK",
  "message": "success",
  "data": {},
  "requestId": "req-001"
}
```

A01–A40 接口明细见 [doc/api.md](doc/api.md)。

## 权限与数据规则

- Token 只保存在内存，不写入 Local Storage、URL 或 Cookie。
- 新增权限不会自动授予角色。
- “全部权限”不是通配符，新权限必须通过角色授权明确添加。
- 相同角色在所有公司和部门共享相同功能权限。
- `查看本人`、`查看本部门`属于后续数据范围，RBAC0 不实现。
- 前端隐藏按钮不能代替后端鉴权。
- 后端数据库是员工归属、岗位、角色和授权的唯一权威来源。

## 测试

完整人工测试流程见 [doc/frontend-test-guide.md](doc/frontend-test-guide.md)。

测试账号由后端测试数据库或部署环境提供，前端仓库不保存测试密码和真实员工账号。

## 已知限制

- 当前独立前端目录不包含 Spring Boot 和 MySQL，联调时需使用小组仓库后端。
- OA、人事、财务等 14 个源业务应用没有真实业务页面。
- 角色继承和数据范围过滤属于后续阶段。
- Element Plus 主包当前超过 Vite 的 500 kB 提示阈值，不影响构建。
