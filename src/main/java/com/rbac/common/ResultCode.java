package com.rbac.common;

/**
 * 语义错误码常量（对齐设计文档 §9.2 状态码与错误码表）。
 */
public final class ResultCode {

    public static final String OK = "OK";
    public static final String INVALID_ARGUMENT = "INVALID_ARGUMENT";
    public static final String CURRENT_PASSWORD_INVALID = "CURRENT_PASSWORD_INVALID";
    public static final String WEAK_PASSWORD = "WEAK_PASSWORD";
    public static final String LOGIN_FAILED = "LOGIN_FAILED";
    public static final String SESSION_INVALID = "SESSION_INVALID";
    public static final String PERMISSION_DENIED = "PERMISSION_DENIED";
    public static final String PASSWORD_CHANGE_REQUIRED = "PASSWORD_CHANGE_REQUIRED";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String USERNAME_EXISTS = "USERNAME_EXISTS";
    public static final String ROLE_KEY_EXISTS = "ROLE_KEY_EXISTS";
    public static final String APP_KEY_EXISTS = "APP_KEY_EXISTS";
    public static final String PERMISSION_KEY_EXISTS = "PERMISSION_KEY_EXISTS";
    public static final String NODE_KEY_EXISTS = "NODE_KEY_EXISTS";
    public static final String LAST_ADMIN_REQUIRED = "LAST_ADMIN_REQUIRED";
    public static final String RESERVED_RESOURCE = "RESERVED_RESOURCE";
    public static final String STATE_CONFLICT = "STATE_CONFLICT";
    public static final String TRANSACTION_RETRY = "TRANSACTION_RETRY";
    public static final String VERSION_CONFLICT = "VERSION_CONFLICT";
    public static final String PRECONDITION_REQUIRED = "PRECONDITION_REQUIRED";
    public static final String RATE_LIMITED = "RATE_LIMITED";
    public static final String LOGIN_LOCKED = "LOGIN_LOCKED";
    public static final String AUTH_UNAVAILABLE = "AUTH_UNAVAILABLE";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private ResultCode() {
    }
}
