package com.rbac.security;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/**
 * 不透明 Token 基础设施：生成原文 Token、计算 SHA-256 摘要（数据库仅存摘要）、
 * 生成服务端会话 ID（UUID，不向前端返回）。
 */
@Component
public class TokenService {

    /** 生成 256 位随机原文 Token（仅返回一次，前端内存保存）。 */
    public String issue() {
        return com.rbac.util.TokenGenerator.generate();
    }

    /** Token → SHA-256 摘要字节（BINARY(32)）。 */
    public byte[] digest(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Token 摘要计算失败", e);
        }
    }

    /** digest 的别名（组内 struct 实现统一命名 hash）。 */
    public byte[] hash(String token) {
        return digest(token);
    }

    /** 服务端会话 ID（UUID）。 */
    public String newSessionId() {
        return UUID.randomUUID().toString();
    }
}
