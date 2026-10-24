package com.rbac.common.enums;

/**
 * 用户状态。锁定单独显示（lockedUntil），不增加 LOCKED 状态。
 */
public enum UserStatus {
    ENABLED("正常"),
    DISABLED("停用"),
    PENDING_ACTIVATION("待激活");

    private final String label;

    UserStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
