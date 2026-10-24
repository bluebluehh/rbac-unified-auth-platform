package com.rbac.util;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * 不透明 Token 生成：至少 256 位安全随机（32 字节 → Base64URL 43 字符），
 * 身份信息不编码在 Token 内，服务端仅存 SHA-256 摘要。
 */
public final class TokenGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private TokenGenerator() {
    }

    /** 生成 32 字节随机 Token（Base64URL 无填充）。 */
    public static String generate() {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}
