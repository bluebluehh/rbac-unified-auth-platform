package com.rbac.common.enums;

/**
 * 审计事件类型白名单（与 sys_operation_log.event_type CHECK 约束一致，字典接口返回标签）。
 */
public enum EventType {
    LOGIN("登录"),
    LOGOUT("退出"),
    USER_CREATE("新增用户"),
    USER_UPDATE("修改用户"),
    USER_STATUS("用户启停"),
    PASSWORD_RESET("重置密码"),
    PASSWORD_CHANGE("修改密码"),
    USER_ROLE_ASSIGN("分配角色"),
    ROLE_CREATE("新增角色"),
    ROLE_UPDATE("修改角色"),
    ROLE_STATUS("角色启停"),
    ROLE_PERMISSION_ASSIGN("角色授权"),
    DEPT_CREATE("新增部门"),
    DEPT_UPDATE("修改部门"),
    DEPT_STATUS("部门启停"),
    APP_CREATE("新增应用"),
    APP_UPDATE("修改应用"),
    APP_STATUS("应用启停"),
    PERMISSION_CREATE("新增权限"),
    PERMISSION_STATUS("权限启停"),
    AUTHZ_DENY("鉴权拒绝"),
    DEMO_SUBMIT("演示提交");

    private final String label;

    EventType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
