package com.rbac.entity;

import java.time.LocalDateTime;

/**
 * 会话实体，对应表 sys_session（主键为会话 ID 字符串，令牌仅存哈希）。
 */
public class SysSession {

    /** 会话 ID（字符串主键） */
    private String id;
    /** 用户 ID */
    private Long userId;
    /** 令牌哈希字节 */
    private byte[] tokenHash;
    /** 是否受限会话：1 是 / 0 否 */
    private Integer restricted;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 过期时间 */
    private LocalDateTime expiresAt;
    /** 最近活跃时间 */
    private LocalDateTime lastSeenAt;
    /** 撤销时间，NULL 表示未撤销 */
    private LocalDateTime revokedAt;

    public SysSession() {
    }

    /** 全参构造：restricted 用布尔表达，内部转 0/1 存储。 */
    public SysSession(String id, Long userId, byte[] tokenHash, boolean restricted,
                      LocalDateTime createdAt, LocalDateTime expiresAt,
                      LocalDateTime lastSeenAt, LocalDateTime revokedAt) {
        this.id = id;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.restricted = restricted ? 1 : 0;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.lastSeenAt = lastSeenAt;
        this.revokedAt = revokedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public byte[] getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(byte[] tokenHash) {
        this.tokenHash = tokenHash;
    }

    public Integer getRestricted() {
        return restricted;
    }

    public void setRestricted(Integer restricted) {
        this.restricted = restricted;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(LocalDateTime lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(LocalDateTime revokedAt) {
        this.revokedAt = revokedAt;
    }
}
