package com.rbac.common;

/**
 * 请求追踪 ID 工具：从 RequestIdFilter 写入的请求属性读取；不可用时现场生成。
 */
public final class RequestIds {

    /** HttpServletRequest attribute 名称，同时作为响应头 X-Request-Id 的取值来源。 */
    public static final String ATTRIBUTE = "RBAC_REQUEST_ID";
    public static final String HEADER = "X-Request-Id";

    private RequestIds() {
    }

    public static String current() {
        try {
            Object v = org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes()
                    .getAttribute(ATTRIBUTE, org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST);
            if (v instanceof String s && !s.isBlank()) {
                return s;
            }
        } catch (IllegalStateException ignore) {
            // 非 MVC 上下文（如启动阶段）
        }
        return generate();
    }

    /** 服务生成合法追踪 ID：16 字节 hex，长度 ≤ 64。 */
    public static String generate() {
        byte[] b = new byte[16];
        new java.security.SecureRandom().nextBytes(b);
        StringBuilder sb = new StringBuilder(32);
        for (byte x : b) {
            sb.append(String.format("%02x", x));
        }
        return sb.toString();
    }

    /** 校验客户端传入的 requestId：长度 ≤ 64 且无控制字符。 */
    public static boolean isValid(String requestId) {
        if (requestId == null || requestId.isEmpty() || requestId.length() > 64) {
            return false;
        }
        for (int i = 0; i < requestId.length(); i++) {
            char c = requestId.charAt(i);
            if (Character.isISOControl(c)) {
                return false;
            }
        }
        return true;
    }
}
