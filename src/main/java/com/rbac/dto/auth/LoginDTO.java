package com.rbac.dto.auth;

/**
 * A01 登录请求（§10.2）。
 * username：1–50，去首尾空白、统一大写、保留前导零，匹配 ^[A-Z0-9_]{1,50}$
 * password：12–64 个 Unicode 码点，不截断
 */
public record LoginDTO(String username, String password) {
}
