package com.rbac.util;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import com.rbac.common.constants.SystemConstants;
import org.springframework.stereotype.Component;

/**
 * 密码哈希工具：Argon2id（内存 19 MiB、迭代 2、并行度 1），PHC 编码存储；
 * 校验为常量时间比较；不截断输入。
 *
 * <p>静态方法保留给旧调用方；struct 认证实现以 Spring Bean 注入使用
 * {@link #encode}/{@link #matches}/{@link #verifyAgainstDummy}。
 */
@Component
public class PasswordHashUtil {

    /** 19 MiB（KiB 单位）。 */
    private static final int MEMORY_KIB = 19456;
    private static final int ITERATIONS = 2;
    private static final int PARALLELISM = 1;

    /**
     * 固定格式的虚拟 Argon2id PHC（盐值与摘要为占位字节）：
     * 对"未知工号"执行一次等价耗时的校验，抹平时序差异（§5.1）。
     */
    private static final String DUMMY_PHC =
            "$argon2id$v=19$m=19456,t=2,p=1$YWJjZGVmZ2hpamtsbW5vcA$abcabcabcabcabcabcabcabcabcabcabcabcabcabca";

    private static final Argon2 ARGON2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);

    public PasswordHashUtil() {
    }

    // ===== 实例方法（struct 认证实现的注入契约）=====

    /** 计算 Argon2id PHC 哈希。 */
    public String encode(String password) {
        return hash(password);
    }

    /** 校验明文与存储哈希是否匹配（参数顺序：明文在前、哈希在后）。 */
    public boolean matches(String rawPassword, String storedHash) {
        return verify(storedHash, rawPassword);
    }

    /** 虚拟校验：对固定 dummy 哈希执行一次等价耗时的 Argon2 校验，结果恒为不匹配。 */
    public void verifyAgainstDummy(String rawPassword) {
        verify(DUMMY_PHC, rawPassword);
    }

    // ===== 静态方法（保留给旧调用方）=====

    /** 计算Argon2id PHC 哈希。 */
    public static String hash(String password) {
        return ARGON2.hash(ITERATIONS, MEMORY_KIB, PARALLELISM, password.toCharArray());
    }

    /** 校验密码与 PHC 哈希是否匹配。 */
    public static boolean verify(String hash, String password) {
        if (hash == null || hash.isBlank() || password == null) {
            return false;
        }
        try {
            return ARGON2.verify(hash, password.toCharArray());
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** 密码长度 12-64 个 Unicode 码点，且不在弱口令黑名单。 */
    public static boolean isAcceptable(String password) {
        if (password == null) {
            return false;
        }
        int len = password.codePointCount(0, password.length());
        if (len < SystemConstants.PASSWORD_MIN || len > SystemConstants.PASSWORD_MAX) {
            return false;
        }
        return !SystemConstants.WEAK_PASSWORDS.contains(password.toLowerCase());
    }
}
