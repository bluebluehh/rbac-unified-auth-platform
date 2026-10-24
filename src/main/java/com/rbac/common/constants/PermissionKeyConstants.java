package com.rbac.common.constants;

/**
 * 全部固定权限 key 常量（四段式 应用:模块:资源:操作），与数据库 sys_permission.perm_key 完全一致。
 * 服务端固定配置，不能由客户端指定。清单来源：设计文档附录 B。
 */
public final class PermissionKeyConstants {

    // 用户管理（platform:user:account:*）
    public static final String USER_ACCOUNT_VIEW = "platform:user:account:view";
    public static final String USER_ACCOUNT_CREATE = "platform:user:account:create";
    public static final String USER_ACCOUNT_EDIT = "platform:user:account:edit";
    public static final String USER_ACCOUNT_STATUS = "platform:user:account:status";
    public static final String USER_ACCOUNT_RESET_PASSWORD = "platform:user:account:reset_password";
    public static final String USER_ACCOUNT_ASSIGN_ROLE = "platform:user:account:assign_role";

    // 部门管理（platform:org:department:*）
    public static final String DEPT_VIEW = "platform:org:department:view";
    public static final String DEPT_CREATE = "platform:org:department:create";
    public static final String DEPT_EDIT = "platform:org:department:edit";
    public static final String DEPT_STATUS = "platform:org:department:status";

    // 角色管理（platform:role:role:*）
    public static final String ROLE_VIEW = "platform:role:role:view";
    public static final String ROLE_CREATE = "platform:role:role:create";
    public static final String ROLE_EDIT = "platform:role:role:edit";
    public static final String ROLE_STATUS = "platform:role:role:status";
    public static final String ROLE_GRANT = "platform:role:role:grant";

    // 应用管理（platform:app:application:*）
    public static final String APP_VIEW = "platform:app:application:view";
    public static final String APP_CREATE = "platform:app:application:create";
    public static final String APP_EDIT = "platform:app:application:edit";
    public static final String APP_STATUS = "platform:app:application:status";

    // 权限点管理（platform:resource:permission:*）
    public static final String PERMISSION_VIEW = "platform:resource:permission:view";
    public static final String PERMISSION_CREATE = "platform:resource:permission:create";
    public static final String PERMISSION_STATUS = "platform:resource:permission:status";

    // 日志审计（platform:audit:log:*）
    public static final String LOG_VIEW = "platform:audit:log:view";

    // 演示端点（engineering:demo:project:*，额外测试权限，source_row=NULL）
    public static final String DEMO_PROJECT_VIEW = "engineering:demo:project:view";
    public static final String DEMO_PROJECT_SUBMIT = "engineering:demo:project:submit";

    /** 保留角色 key：控制面管理员。 */
    public static final String RESERVED_ROLE_KEY = "PLATFORM_ADMIN";
    /** 保留应用 key：管理后台平台应用。 */
    public static final String RESERVED_APP_KEY = "platform";

    private PermissionKeyConstants() {
    }
}
