package com.rbac.dto.auth;

/**
 * A04 本人改密请求（§10.2）。
 * 新密码 12–64 个 Unicode 码点，且不得与当前密码相同；A04 不要求 If-Match。
 */
public record PasswordChangeDTO(String currentPassword, String newPassword) {
}
