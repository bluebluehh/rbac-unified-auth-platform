# 课程角色与员工授权导入

导入器通过现有40个API建立应用、权限、43个公共/职责角色，以及已确认的员工角色关系，不在登录时按岗位自动授权。

## 推荐：完整包一次导入

先按项目README启用course环境并启动后端。取得单独提供的员工完整导入包，解压后在项目根目录执行：

```powershell
node tools/course-import/import-all.mjs --source-root "C:\资料\员工完整导入包_20261025"
node tools/course-import/import-all.mjs --source-root "C:\资料\员工完整导入包_20261025" --apply
```

包内已有校验后的载荷，不需要Python或手工填写员工。工具会核对载荷及原始资料摘要，再按现有API导入组织、应用、权限、43个职责角色、10,000名员工及20,046条员工角色关系。新基础库最终为49角色、10,001账号（含ADMIN）。只预览时不修改配置和员工；--apply会取得并校验本次预览确认值，不使用旧预览。

只需要不含人员的配置：`node tools/course-import/initialize-catalog.mjs --apply`，读取仓库自带`db/course-catalog-v3.3.json`。这一步不会导入员工，可与完整包导入先后使用，已有相同授权会复用。

两个入口均读取`.env.local`，支持进程环境覆盖。修改过管理员密码时指定`COURSE_ADMIN_PASSWORD`；其他本地后端端口通过`COURSE_API_BASE`指定。原始资料目录支持Windows和Linux路径分隔符。若已运行旧migration-002，会复用可明确匹配的应用和权限；未知或多义映射停止。

导入中断后保留本机`.local/`日志，再用相同包重跑。不要删除日志后强行覆盖角色；已有人为撤权、改资料或改密码时以当前状态为准，冲突需核实。完整包与初始化目录使用独立日志目录，防止互相覆盖载荷。

## 本地资料

原始三表、V3.3基线和员工计划不在仓库中。准备以下本地目录，勿上传员工文件：

```text
<source-root>/
  市政公司员工信息表.xlsx
  市政公司部门信息表.xlsx
  市政公司应用软件功能权限清单.xlsx
  交付文档/部门岗位权限映射_V3.3/
    角色配置与编码基线.json
    员工角色卡片计划.csv
    部门岗位角色卡片完整对照.csv
    角色原子权限逐项对照.csv
```

## 执行

1. 安装Python 3及openpyxl；按主README启动独立课程环境。
2. `python tools/course-import/prepare.py --source-root <source-root>`，校验源文件摘要、唯一工号、岗位组合及授权来源，生成私有`.local/course-import/payload.json`。
3. 使用`COURSE_ADMIN_USERNAME`、`COURSE_ADMIN_PASSWORD`指定本地管理员，执行`node tools/course-import/import.mjs`，只预览，不改配置或员工。
4. 备份数据库，确认无冲突后，设置`RBAC_BUSINESS_MODE=PERMISSION_DISPLAY_ONLY`，执行`node tools/course-import/import.mjs --apply --confirm=<预览中的confirmation>`。
5. 完整导入后可执行`node tools/course-import/verify.mjs`。它读取`.env.local`中的数据库主机、端口、库名、账号与密码，也可通过进程环境覆盖，`COURSE_MYSQL`可指定客户端路径。默认核对资料、角色、卡片与加密状态，保留已经人工激活的账号；刚导入的新库可添加`--initial-state`，额外要求全部源员工待激活且无密码。

导入阶段通过环境变量`COURSE_SOURCE_ROOT`指定与prepare相同的资料目录；不设置时默认使用工程外层两级目录。换电脑或改变目录时应显式设置此变量，不需要修改代码。不要把源文件复制到前端或加入Git。

## 规则与限制

- 方案为DEPTJOB-20261024-V3.3，校验10,000名员工、54种岗位、869种组织岗位组合、43角色、651条可展示授权。
- 部门岗位连接是明确的课程配置规则，不代表原表已经给出逐人授权。
- 本人、本部门等依赖真实数据范围的单元格暂缓，不发布成完整业务访问授权；S15系统后台保持停用，职责角色不获得平台管理员权限。
- 显式角色替换通过现有事务和审计完成。角色和权限ID从实际数据对齐，不使用源表行号充当ID。
- 重复运行保留人工变更，冲突时停止；不恢复已撤回授权，不重置现有密码。
- 新员工无密码、待激活，由管理员逐人设置临时密码，首次改密后启用。
- 运行日志、计划、凭据和部署映射写入`.local/`，该目录始终不提交。

`demo-check.mjs`、`lifecycle-check.mjs`仅供独立验收环境；需显式设置`RBAC_ALLOW_TEST_FIXTURES=1`。演示密码随机生成并只保存在本机，运行完应清理临时对象。一次性本机清理与人员核验脚本不随仓库提供。
