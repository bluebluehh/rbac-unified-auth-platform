package com.rbac.security;

/**
 * 认证主体上下文（由 AuthenticationFilter 在 Token 校验通过后建立）。
 */
public class CurrentUser {

    private final Long userId;
    private final String username;
    private final String sessionId;
    private final boolean restricted;

    public CurrentUser(Long userId, String username, String sessionId, boolean restricted) {
        this.userId = userId;
        this.username = username;
        this.sessionId = sessionId;
        this.restricted = restricted;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getSessionId() {
        return sessionId;
    }

    /** 受限会话：仅可访问改密（A04）与退出（A02）。 */
    public boolean isRestricted() {
        return restricted;
    }

    // ===== record 风格访问器（组内 struct 实现统一使用）=====

    public Long userId() {
        return userId;
    }

    public String username() {
        return username;
    }

    public boolean restricted() {
        return restricted;
    }
}
