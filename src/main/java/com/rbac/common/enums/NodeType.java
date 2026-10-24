package com.rbac.common.enums;

/**
 * 组织节点类型。ROOT 不能由 API 创建。
 */
public enum NodeType {
    ROOT("总公司"),
    COMPANY("分公司"),
    DEPARTMENT("部门"),
    PROJECT_DEPT("项目部");

    private final String label;

    NodeType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
