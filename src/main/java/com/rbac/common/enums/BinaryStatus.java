package com.rbac.common.enums;

/**
 * 1/0 启停状态，用于部门、角色、应用和权限。JSON 正文只接受数字 0/1。
 */
public enum BinaryStatus {
    DISABLED(0, "停用"),
    ENABLED(1, "启用");

    private final int value;
    private final String label;

    BinaryStatus(int value, String label) {
        this.value = value;
        this.label = label;
    }

    public int getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }
}
