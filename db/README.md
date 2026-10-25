# 数据库初始化与完整课程数据

代码合并不会同步其他电脑的数据库。只有6个角色，说明只导入了基础开发种子。

| 文件或工具 | 内容 |
| --- | --- |
| `schema.sql` | 10张基础表，仅新库执行 |
| `data.sql` | 6个基础角色、开发管理员和最小组织，不包含43个职责角色或员工 |
| `migration-002-business-permissions.sql` | 旧版应用及权限定义，不负责员工和43个职责角色授权 |
| `course-catalog-v3.3.json` | 不含人员的15个应用定义、220个权限、43个职责角色及651条授权模板 |
| `initialize-catalog.mjs` | 通过已有API导入公开配置，基础6角色加职责43角色后共49角色 |
| `import-all.mjs` | 从另行提供的本地员工包导入111个组织、10,000名员工、角色和20,046条员工角色关系 |

新库运行schema和data后，先配置并启动course模式后端。完整员工导入可直接执行，不需要先单独初始化角色目录：

```powershell
node tools/course-import/import-all.mjs --source-root "C:\资料\员工完整导入包"
node tools/course-import/import-all.mjs --source-root "C:\资料\员工完整导入包" --apply
```

只需要角色、应用和权限配置时：

```powershell
node tools/course-import/initialize-catalog.mjs
node tools/course-import/initialize-catalog.mjs --apply
```

这两个入口会读取本机`.env.local`，默认为预览，添加`--apply`才写入；已有授权不同会停止，不静默覆盖。管理员密码已修改时，通过`COURSE_ADMIN_USERNAME`及`COURSE_ADMIN_PASSWORD`指定本机账号。后端端口不为8080时设置`COURSE_API_BASE`为对应的本地`/api/v1`地址。

员工包含姓名、手机号及个人角色计划，须单独取得，不在Git中。导入后新员工无密码、待激活，管理员设置临时密码后由员工首次改密。已有员工密码不被重置。不要反复执行schema重建或用旧备份覆盖已使用的数据库。

若已运行migration-002，导入器会按应用名称和权限完整语义核对并复用；只对旧engineering演示与project业务应用的明确兼容情况特殊处理，其他冲突仍需人工核实。生成的前端权限标签会按实际应用编码更新；部署静态前端应在导入后重新构建。
