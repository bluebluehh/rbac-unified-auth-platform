package com.rbac.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 安全相关配置项（application.yml 前缀 rbac.security）。
 * 默认值与 SystemConstants 中的开发默认值保持一致，可在配置文件覆盖。
 */
@Component
@ConfigurationProperties(prefix = "rbac.security")
public class RbacSecurityProperties {

    /** 会话绝对有效期（分钟），从创建时刻起算，到期强制失效。 */
    private long sessionAbsoluteMinutes = 120;

    /** 会话空闲超时（分钟），lastSeenAt 早于该阈值即失效。 */
    private long sessionIdleMinutes = 30;

    /** 连续登录失败锁定阈值（次）。 */
    private int loginLockThreshold = 5;

    /** 登录锁定时长（分钟）。 */
    private int loginLockMinutes = 15;

    /** 单 IP 每分钟登录尝试上限（次）。 */
    private int ipRateLimitPerMinute = 20;

    /** 手机号 AES-GCM 密钥（Base64 编码的 32 字节），生产环境必须外部注入。 */
    private String phoneAesKey;

    /** 手机号密文版本号，随密文首字节落库，支持换钥轮转。 */
    private int phoneKeyVersion = 1;

    /** CORS 允许的来源列表（精确匹配，不允许 * 携带凭据的场景）。 */
    private List<String> corsAllowedOrigins = new ArrayList<>();

    public long getSessionAbsoluteMinutes() {
        return sessionAbsoluteMinutes;
    }

    public void setSessionAbsoluteMinutes(long sessionAbsoluteMinutes) {
        this.sessionAbsoluteMinutes = sessionAbsoluteMinutes;
    }

    public long getSessionIdleMinutes() {
        return sessionIdleMinutes;
    }

    public void setSessionIdleMinutes(long sessionIdleMinutes) {
        this.sessionIdleMinutes = sessionIdleMinutes;
    }

    public int getLoginLockThreshold() {
        return loginLockThreshold;
    }

    public void setLoginLockThreshold(int loginLockThreshold) {
        this.loginLockThreshold = loginLockThreshold;
    }

    public int getLoginLockMinutes() {
        return loginLockMinutes;
    }

    public void setLoginLockMinutes(int loginLockMinutes) {
        this.loginLockMinutes = loginLockMinutes;
    }

    public int getIpRateLimitPerMinute() {
        return ipRateLimitPerMinute;
    }

    public void setIpRateLimitPerMinute(int ipRateLimitPerMinute) {
        this.ipRateLimitPerMinute = ipRateLimitPerMinute;
    }

    public String getPhoneAesKey() {
        return phoneAesKey;
    }

    public void setPhoneAesKey(String phoneAesKey) {
        this.phoneAesKey = phoneAesKey;
    }

    public int getPhoneKeyVersion() {
        return phoneKeyVersion;
    }

    public void setPhoneKeyVersion(int phoneKeyVersion) {
        this.phoneKeyVersion = phoneKeyVersion;
    }

    public List<String> getCorsAllowedOrigins() {
        return corsAllowedOrigins;
    }

    public void setCorsAllowedOrigins(List<String> corsAllowedOrigins) {
        this.corsAllowedOrigins = corsAllowedOrigins == null ? new ArrayList<>() : corsAllowedOrigins;
    }
}
