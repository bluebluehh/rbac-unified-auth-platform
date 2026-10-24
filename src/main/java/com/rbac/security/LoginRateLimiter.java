package com.rbac.security;

import com.rbac.common.constants.SystemConstants;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录 IP 限流器：滑动窗口，默认每 IP 每分钟 20 次（§5.1、§9.3）。
 * 进程内实现，适配单实例部署；集群部署应替换为 Redis 等集中存储版本。
 */
@Component
public class LoginRateLimiter {

    private static final long WINDOW_MILLIS = 60_000L;

    private final int limitPerMinute;
    private final Map<String, Deque<Long>> windows = new ConcurrentHashMap<>();

    public LoginRateLimiter() {
        this(SystemConstants.IP_RATE_LIMIT_PER_MINUTE);
    }

    public LoginRateLimiter(int limitPerMinute) {
        this.limitPerMinute = limitPerMinute;
    }

    /** 尝试为该 IP 获取一次登录配额；true=放行，false=窗口内超限应拒绝。 */
    public boolean tryAcquire(String clientIp) {
        String key = (clientIp == null || clientIp.isBlank()) ? "unknown" : clientIp;
        long now = System.currentTimeMillis();
        long windowStart = now - WINDOW_MILLIS;
        Deque<Long> hits = windows.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (hits) {
            while (!hits.isEmpty() && hits.peekFirst() < windowStart) {
                hits.pollFirst();
            }
            if (hits.size() >= limitPerMinute) {
                return false;
            }
            hits.addLast(now);
            return true;
        }
    }
}
