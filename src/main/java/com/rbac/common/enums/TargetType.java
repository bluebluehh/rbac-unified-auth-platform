package com.rbac.common.enums;

/**
 * 审计目标类型（sys_operation_log.target_type 白名单）。
 */
public enum TargetType {
    USER,
    ROLE,
    DEPT,
    APP,
    PERMISSION,
    SESSION
}
