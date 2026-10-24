package com.rbac.vo.auth;

import java.time.Instant;

/**
 * A01 登录成功响应（§10.3）。
 * 服务端会话 UUID 不返回；token 原文只在这一次响应中出现，前端仅内存保存。
 */
public record LoginVO(String token, String tokenType, Instant expiresAt, boolean restricted, String userId) {

    public static final String BEARER = "Bearer";
}
