# 任务清单：修复 PageQuery 分页参数绑定

- [x] 1. 修改 `PageQuery.java`：`getPage()`/`getSize()` 改为返回 String，新增
      `pageNumber()`/`sizeNumber()`，删除 `getPageRaw()`/`getSizeRaw()`。
- [x] 2. 更新 5 个 service 调用点：`StructApplicationService`、`StructLogService`、
      `StructPermissionService`、`StructRoleService`、`StructUserService` 中
      `getPage()`→`pageNumber()`、`getSize()`→`sizeNumber()`。
- [x] 3. 重新编译后端，确认无编译错误（BUILD SUCCESS，131 个源文件通过）。
- [x] 4. 用管理员 token 验证 `/permissions?page=2&size=5` 与 `/applications?page=1&size=3`
      分页参数生效（临时实例 8090 实测：page=2/size=5、records=5、firstId=6）。
- [x] 5. 后端分页修复已生效；最终页面需在 IDEA 中重启后端并刷新前端后确认
      （权限树将显示全部启用应用及权限）。