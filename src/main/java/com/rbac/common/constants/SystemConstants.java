package com.rbac.common.constants;

import java.time.Duration;
import java.util.Set;

/**
 * 部署、会话与业务上限常量（对齐设计文档 §8/§9.3/§6.4）。
 */
public final class SystemConstants {

    // ===== 分页 =====
    public static final int PAGE_DEFAULT = 1;
    public static final int SIZE_DEFAULT = 20;
    public static final int SIZE_MAX = 100;

    // ===== 选择上限 =====
    public static final int ROLE_SELECTION_MAX = 100;        // 用户角色最多 100 个
    public static final int PERMISSION_SELECTION_MAX = 5000; // 角色权限最多 5000 个

    // ===== 关键字长度上限 =====
    public static final int KEYWORD_USER_MAX = 80;
    public static final int KEYWORD_ROLE_MAX = 80;
    public static final int KEYWORD_APP_MAX = 100;
    public static final int KEYWORD_PERMISSION_MAX = 160;

    // ===== 密码 =====
    public static final int PASSWORD_MIN = 12;
    public static final int PASSWORD_MAX = 64;

    // ===== 日志查询跨度 =====
    public static final int LOG_RANGE_DAYS_MAX = 31;

    // ===== 会话与锁定（开发默认，可由配置覆盖） =====
    public static final Duration SESSION_ABSOLUTE = Duration.ofMinutes(120);
    public static final Duration SESSION_IDLE = Duration.ofMinutes(30);
    public static final int LOGIN_LOCK_THRESHOLD = 5;
    public static final Duration LOGIN_LOCK = Duration.ofMinutes(15);
    public static final int IP_RATE_LIMIT_PER_MINUTE = 20;

    // ===== 字典版本 =====
    public static final String DICTIONARY_VERSION = "RBAC0-V2.1";

    // ===== 校验正则 =====
    /** ID：十进制正整数字符串。 */
    public static final String ID_PATTERN = "^[1-9][0-9]{0,18}$";
    /** 版本：允许 0。 */
    public static final String VERSION_PATTERN = "^(0|[1-9][0-9]{0,18})$";
    /** 工号：去首尾空白、统一大写、保留前导零。 */
    public static final String USERNAME_PATTERN = "^[A-Z0-9_]{1,50}$";
    /** 角色 key。 */
    public static final String ROLE_KEY_PATTERN = "^[A-Z][A-Z0-9_]*$";
    /** 应用 key。 */
    public static final String APP_KEY_PATTERN = "^[a-z][a-z0-9_]*$";
    /** 手机号：非空须 11 位数字。 */
    public static final String PHONE_PATTERN = "^1[0-9]{10}$";

    // ===== 弱口令黑名单（小写比较） =====
    public static final Set<String> WEAK_PASSWORDS = Set.of(
            "password", "password123", "12345678", "123456789", "1234567890",
            "qwertyuiop", "abcdefgh", "111111111111", "aaaaaaaaaaaa", "rbac123456789");

    private SystemConstants() {
    }
}
