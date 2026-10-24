package com.rbac.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 手机号 AES-256-GCM 认证加密。密文封装：[keyVersion(1B)][nonce(12B)][ciphertext+tag(16B)]，
 * 可按密钥版本解密；API 仅输出掩码，不返回明文与密钥。
 *
 * <p>静态方法保留给旧调用方；struct 认证实现以 Spring Bean 注入使用
 * {@link #decrypt(byte[])}（密钥取自部署配置 rbac.security.phone-aes-key）。
 */
@Component
public class AesGcmUtil {

    private static final int NONCE_LEN = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 部署配置注入的手机号加密密钥（32 字节）。 */
    private final byte[] configuredKey;

    public AesGcmUtil(@Value("${rbac.security.phone-aes-key}") String base64Key) {
        this.configuredKey = decodeKey(base64Key);
    }

    /** 解密（使用部署配置的密钥），供 Service 注入使用。 */
    public String decrypt(byte[] sealed) {
        return decrypt(configuredKey, sealed);
    }

    /** 加密（使用部署配置的密钥，密钥版本固定为 1），供 Service 注入使用。 */
    public byte[] encrypt(String plaintext) {
        return encrypt(configuredKey, 1, plaintext);
    }

    /** 加密：返回 keyVersion+nonce+密文 的封装字节。 */
    public static byte[] encrypt(byte[] aesKey, int keyVersion, String plaintext) {
        try {
            byte[] nonce = new byte[NONCE_LEN];
            RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
            byte[] cipherText = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return ByteBuffer.allocate(1 + NONCE_LEN + cipherText.length)
                    .put((byte) keyVersion)
                    .put(nonce)
                    .put(cipherText)
                    .array();
        } catch (Exception e) {
            throw new IllegalStateException("手机号加密失败", e);
        }
    }

    /** 解密：按封装首字节的密钥版本选择密钥（当前仅支持单版本）。 */
    public static String decrypt(byte[] aesKey, byte[] sealed) {
        try {
            ByteBuffer buf = ByteBuffer.wrap(sealed);
            buf.get(); // keyVersion
            byte[] nonce = new byte[NONCE_LEN];
            buf.get(nonce);
            byte[] cipherText = new byte[buf.remaining()];
            buf.get(cipherText);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
            return new String(cipher.doFinal(cipherText), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("手机号解密失败", e);
        }
    }

    /** 解密并掩码：保留前 3 位与后 4 位。 */
    public static String decryptMasked(byte[] aesKey, byte[] sealed) {
        String plain = decrypt(aesKey, sealed);
        if (plain.length() <= 7) {
            return "******";
        }
        return plain.substring(0, 3) + "****" + plain.substring(plain.length() - 4);
    }

    /** Base64 密钥解码（32 字节）。 */
    public static byte[] decodeKey(String base64Key) {
        byte[] key = Base64.getDecoder().decode(base64Key);
        if (key.length != 32) {
            throw new IllegalArgumentException("手机号 AES 密钥必须为 32 字节");
        }
        return key;
    }
}
