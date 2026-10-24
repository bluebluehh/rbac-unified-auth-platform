# Spec：修复角色授权页只显示「统一权限管理平台」的 bug

## 1. 问题现象（已确认）

进入「角色管理 → 分配权限 / 角色授权」页（`角色授权 · 项目经理`），权限树只有一个
「统一权限管理平台」节点，13 个业务应用及其权限均未显示。数据本身已正确导入数据库。

## 2. 根因（已实测定位）

**后端 `page` / `size` 分页参数绑定失效。**

`dto/common/PageQuery.java` 中，`page`、`size` 字段为 `String`，setter 为
`setPage(String)` / `setSize(String)`，但 getter `getPage()` / `getSize()` 返回
`int`（解析后的值）。JavaBeans 属性要求 getter 与 setter 类型一致，此处不一致导致
Spring MVC 的 `@ModelAttribute` 数据绑定无法识别 `page` / `size` 属性，请求参数
`page`、`size` 被静默丢弃，永远走默认值 `page=1, size=20`。

实测证据（`http://localhost:8080/api/v1/permissions`，管理员 token）：

```
?page=2&size=5&status=1  -> pageField=1  sizeField=20  首条 id=1
?size=100                -> sizeField=20
```

`status`、`keyword` 等 `Integer`/`String` 属性正常绑定，仅 `page`/`size` 失效。

### 与症状的因果链

前端 `views/role/PermissionAssign.vue → loadAllPermissions()` 通过递增 `page` 循环
翻页拉全量权限：

```js
for (;;) {
  const result = await listPermissions({ page, size: 100, status: 1 })
  out.push(...result.records)
  if (out.length >= result.total || result.records.length === 0) break
  page += 1   // 后端忽略 page，每轮都返回同一页
}
```

- 后端 `page` 失效 → 每轮都返回 `ORDER BY id ASC LIMIT 0,20`，即第 1 页前 20 条。
- 权限按 id 升序，id 1~23 全为 platform（统一权限管理平台）权限。
- 循环靠 `out.length >= total(239)` 终止，傻跑 12 轮，`out` 塞入 240 条但全是重复的
  platform 权限，业务权限（id>23）永远在翻页之后，永远拿不到。
- `buildTree()` 中，业务应用有「应用」但匹配不到「权限」（`appPerms.length===0`），
  被 `continue` 跳过 → 只剩「统一权限管理平台」一个节点。

应用列表 `loadAllApplications()` 因为只有 16 个（<20），一次拿全，不受影响。

## 3. 修复方案

最小改动，让 `PageQuery` 的 `page` / `size` 成为标准 String bean（getter 与 setter 类型一致），
把「懒解析 + 校验」逻辑移到独立方法，供 service 调用。

### 3.1 `src/main/java/com/rbac/dto/common/PageQuery.java`

- `getPage()`：返回值 `int` → `String`（返回原始 `page`）。
- `getSize()`：返回值 `int` → `String`（返回原始 `size`）。
- 新增 `public int pageNumber()`：承载原 `getPage()` 的解析 + 校验 + 缓存逻辑。
- 新增 `public int sizeNumber()`：承载原 `getSize()` 的解析 + 校验（含 `SIZE_MAX`）+ 缓存逻辑。
- 删除 `getPageRaw()` / `getSizeRaw()`（已确认无外部引用）。
- 保留 `setPage(String)` / `setSize(String)`、`cachedPage` / `cachedSize`、`parseIntOrDefault`。

### 3.2 调用方（5 个 service），`getPage()`/`getSize()` → `pageNumber()`/`sizeNumber()`

- `service/struct/StructApplicationService.java`（62-63 行）
- `service/struct/StructLogService.java`（44-45 行）
- `service/struct/StructPermissionService.java`（68-69 行）
- `service/struct/StructRoleService.java`（78-79 行）
- `service/struct/StructUserService.java`（102-103 行）

每处：`int page = query.getPage();` → `int page = query.pageNumber();`，
`int size = query.getSize();` → `int size = query.sizeNumber();`。

## 4. 不改动

- 前端 `PermissionAssign.vue` 及其它前端文件无需改动。
- 数据库无需改动（已确认 16 应用 / 239 权限，`application_id` 正确）。
- 迁移 SQL `db/migration-002-business-permissions.sql` 保持不变。

## 5. 验证步骤

1. 后端重新编译并重启 IDEA 中的 SpringBoot 应用。
2. `curl` / PowerShell 调用
   `GET /api/v1/permissions?page=2&size=5&status=1`，应返回 `page=2, size=5`，且首页
   首条 id 不再是 1（分页生效）。
3. `GET /api/v1/applications?page=1&size=3`，应返回 `size=3`（3 条）。
4. 前端刷新「角色授权 · 项目经理」页面，权限树应出现全部 15 个启用应用（platform +
   14 个业务应用，engineering 状态 0 不显示）。

## 6. 需用户确认

- 修复范围仅限后端 `PageQuery` + 5 个 service 调用点，前端与数据库不动，符合预期请批准。