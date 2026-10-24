package com.rbac.security;

import com.rbac.common.BusinessException;

/**
 * ThreadLocal 主体上下文存取。由 AuthenticationFilter 建立，请求链结束 finally 清理。
 * SELF 端点统一从此读取身份，不信任客户端传入 userId。
 */
public final class CurrentUserContext {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    private CurrentUserContext() {
    }

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    public static CurrentUser get() {
        return HOLDER.get();
    }

    /** 要求已认证；未认证抛 401 SESSION_INVALID。 */
    public static CurrentUser getRequired() {
        CurrentUser user = HOLDER.get();
        if (user == null) {
            throw BusinessException.sessionInvalid();
        }
        return user;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
