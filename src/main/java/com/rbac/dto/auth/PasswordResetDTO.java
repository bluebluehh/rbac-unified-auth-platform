package com.rbac.dto.auth;

/**
 * A12 管理员重置密码请求（§10.2）。
 * 由 D 的 StructUserService 使用，归属 E 维护 dto/auth 子包。
 */
public record PasswordResetDTO(String temporaryPassword) {
}
