package com.rbac.service.struct;

import com.rbac.common.BusinessException;
import com.rbac.common.ResultCode;
import com.rbac.common.enums.EventType;
import com.rbac.common.enums.UserStatus;
import com.rbac.dto.auth.LoginDTO;
import com.rbac.dto.auth.PasswordChangeDTO;
import com.rbac.entity.SysApplication;
import com.rbac.entity.SysDept;
import com.rbac.entity.SysSession;
import com.rbac.entity.SysUser;
import com.rbac.mapper.ApplicationMapper;
import com.rbac.mapper.DeptMapper;
import com.rbac.mapper.SecurityStateMapper;
import com.rbac.mapper.SessionMapper;
import com.rbac.mapper.UserMapper;
import com.rbac.security.CurrentUser;
import com.rbac.security.CurrentUserContext;
import com.rbac.security.LoginRateLimiter;
import com.rbac.security.PermissionEvaluator;
import com.rbac.security.PermissionEvaluator.EffectivePermissions;
import com.rbac.security.TokenService;
import com.rbac.service.AuditService;
import com.rbac.service.AuditService.AuditEvent;
import com.rbac.service.AuthService;
import com.rbac.util.AesGcmUtil;
import com.rbac.util.PasswordHashUtil;
import com.rbac.vo.application.ApplicationVO;
import com.rbac.vo.auth.CurrentPermissionsVO;
import com.rbac.vo.auth.LoginVO;
import com.rbac.vo.user.UserVO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * RBAC0 认证门户的结构化实现：A01–A06（设计文档 §5.1、§5.5、§5.7、§8.3）。
 *
 * <p>登录的三段式流程（§5.1）：
 * <ol>
 *   <li>锁外：工号归一、取用户、做一次密码校验（未知账号走固定的虚拟校验，避免时序泄露）；</li>
 *   <li>锁内：{@code security_state} 行锁 → 用户行锁 → 复核哈希未被并发修改、账号与组织状态、锁定时间；</li>
 *   <li>提交后：把结构化结果转成 HTTP。失败结果用返回值穿过事务，而不是抛异常——
 *       否则已提交的失败计数会被回滚（§5.1 明确要求）。</li>
 * </ol>
 *
 * <p>不变量：
 * <ul>
 *   <li>会话签发/撤销不递增 {@code policy_version}（§6.5）；</li>
 *   <li>登录失败计数与锁定时间属于内部维护，不递增业务 {@code version}（§6.5）；</li>
 *   <li>服务端只保存 Token 的 SHA-256 摘要，会话 UUID 不返回前端（§8.2）；</li>
 *   <li>登录失败统一 {@code LOGIN_FAILED}，不区分未知工号 / 错误密码 / 禁用状态（§5.1）。</li>
 * </ul>
 */
@Service
@ConditionalOnProperty(prefix = "rbac.service", name = "impl", havingValue = "struct", matchIfMissing = true)
public class StructAuthService implements AuthService {

    private static final Duration SESSION_ABSOLUTE = Duration.ofHours(2);
    private static final int MAX_FAILED_LOGINS = 5;
    private static final Duration LOGIN_LOCK = Duration.ofMinutes(15);
    private static final int PASSWORD_MIN = 12;
    private static final int PASSWORD_MAX = 64;
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Z0-9_]{1,50}$");

    private static final String LOGIN_FAILED_MESSAGE = "工号或密码不正确";
    private static final String LOGIN_LOCKED_MESSAGE = "账号已临时锁定，请稍后再试";
    private static final String SESSION_INVALID_MESSAGE = "登录状态已失效，请重新登录";

    /** 开发期弱口令样例；生产应改为版本化配置（§8.3 "拒绝配置的弱口令"）。 */
    private static final List<String> WEAK_PASSWORDS = List.of(
            "123456789012", "1234567890123", "password1234", "qwertyuiop12",
            "aaaaaaaaaaaa", "111111111111", "abcdefghijkl");

    private final UserMapper userMapper;
    private final DeptMapper deptMapper;
    private final SessionMapper sessionMapper;
    private final ApplicationMapper applicationMapper;
    private final SecurityStateMapper securityStateMapper;
    private final PermissionEvaluator permissionEvaluator;
    private final PasswordHashUtil passwordHashUtil;
    private final AesGcmUtil aesGcmUtil;
    private final TokenService tokenService;
    private final LoginRateLimiter loginRateLimiter;
    private final AuditService auditService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public StructAuthService(UserMapper userMapper,
                             DeptMapper deptMapper,
                             SessionMapper sessionMapper,
                             ApplicationMapper applicationMapper,
                             SecurityStateMapper securityStateMapper,
                             PermissionEvaluator permissionEvaluator,
                             PasswordHashUtil passwordHashUtil,
                             AesGcmUtil aesGcmUtil,
                             TokenService tokenService,
                             LoginRateLimiter loginRateLimiter,
                             AuditService auditService,
                             PlatformTransactionManager transactionManager,
                             Clock clock) {
        this.userMapper = userMapper;
        this.deptMapper = deptMapper;
        this.sessionMapper = sessionMapper;
        this.applicationMapper = applicationMapper;
        this.securityStateMapper = securityStateMapper;
        this.permissionEvaluator = permissionEvaluator;
        this.passwordHashUtil = passwordHashUtil;
        this.aesGcmUtil = aesGcmUtil;
        this.tokenService = tokenService;
        this.loginRateLimiter = loginRateLimiter;
        this.auditService = auditService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    // ==================================================================
    // A01 登录
    // ==================================================================

    @Override
    public LoginVO login(LoginDTO dto, String clientIp) {
        if (dto == null) {
            throw new BusinessException(ResultCode.INVALID_ARGUMENT, "请求正文不能为空");
        }
        String username = normalizeUsername(dto.username());
        String rawPassword = dto.password();
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new BusinessException(ResultCode.INVALID_ARGUMENT, "密码不能为空");
        }
        if (!loginRateLimiter.tryAcquire(clientIp)) {
            throw new BusinessException(ResultCode.RATE_LIMITED, "请求过于频繁，请稍后再试");
        }

        // 哈希可能在两次读取之间被改密/重置改动；最多重读一次，不沿用旧结论签发 Token。
        for (int attempt = 0; attempt < 2; attempt++) {
            SysUser preUser = findUserForLogin(username);
            String preHash = preUser == null ? null : preUser.getPasswordHash();
            boolean preVerified = verifyPassword(rawPassword, preHash);

            if (preUser == null) {
                // 未知工号：不创建用户、不改计数，只留匿名失败事件。
                auditService.writeFailureInNewTransaction(
                        new AuditEvent(EventType.LOGIN, "USER", null, null, null, false, "LOGIN_FAILED"));
                throw new BusinessException(ResultCode.LOGIN_FAILED, LOGIN_FAILED_MESSAGE);
            }

            AuthOutcome outcome = transactionTemplate.execute(status ->
                    authenticate(preUser, preHash, preVerified));

            if (outcome == null) {
                throw new BusinessException(ResultCode.INTERNAL_ERROR, "登录处理失败");
            }
            if (outcome.shouldRetry()) {
                continue;
            }
            if (outcome.failure() != null) {
                throw new BusinessException(outcome.failure(), outcome.message());
            }
            return new LoginVO(
                    outcome.token(),
                    LoginVO.BEARER,
                    outcome.expiresAt(),
                    outcome.restricted(),
                    Long.toString(outcome.userId()));
        }
        throw new BusinessException(ResultCode.LOGIN_FAILED, LOGIN_FAILED_MESSAGE);
    }

    /**
     * 认证结果事务。必须在事务内调用；第一条语句即 {@code security_state} 行锁，
     * 与所有管理写操作共用同一锁顺序（§6.1）。
     */
    private AuthOutcome authenticate(SysUser preUser, String preHash, boolean preVerified) {
        securityStateMapper.lockStateRow();

        SysUser user = userMapper.lockById(preUser.getId());
        if (user == null) {
            return AuthOutcome.fail(ResultCode.LOGIN_FAILED, LOGIN_FAILED_MESSAGE);
        }
        if (!Objects.equals(user.getPasswordHash(), preHash)) {
            return AuthOutcome.retry();
        }

        LocalDateTime now = utc(clock.instant());
        // 与后续受保护请求保持相同的组织资格要求，避免签发一经使用即被拒绝的会话。
        boolean eligible = isLoginEligible(user) && permissionEvaluator.isOrganizationChainEnabled(user);

        if (!preVerified) {
            // 已知账号的错误密码：计数与 LOGIN FAILURE 在同一事务提交，随后返回 401。
            if (eligible) {
                recordFailedAttempt(user, now);
            }
            auditService.writeInTransaction(
                    new AuditEvent(EventType.LOGIN, "USER", String.valueOf(user.getId()),
                            user.getId(), user.getUsername(), false, "LOGIN_FAILED"));
            return AuthOutcome.fail(ResultCode.LOGIN_FAILED, LOGIN_FAILED_MESSAGE);
        }

        if (!eligible) {
            auditService.writeInTransaction(
                    new AuditEvent(EventType.LOGIN, "USER", String.valueOf(user.getId()),
                            user.getId(), user.getUsername(), false, "LOGIN_FAILED"));
            return AuthOutcome.fail(ResultCode.LOGIN_FAILED, LOGIN_FAILED_MESSAGE);
        }

        if (isLocked(user, now)) {
            // 密码正确但仍处于锁定期：LOGIN_LOCKED 只在账号具备登录资格时返回，避免枚举账号。
            auditService.writeInTransaction(
                    new AuditEvent(EventType.LOGIN, "USER", String.valueOf(user.getId()),
                            user.getId(), user.getUsername(), false, "LOGIN_LOCKED"));
            return AuthOutcome.fail(ResultCode.LOGIN_LOCKED, LOGIN_LOCKED_MESSAGE);
        }

        boolean restricted = statusIs(user.getStatus(), UserStatus.PENDING_ACTIVATION);
        String token = tokenService.issue();
        byte[] tokenHash = tokenService.hash(token);
        LocalDateTime expiresAt = now.plus(SESSION_ABSOLUTE);
        String sessionId = UUID.randomUUID().toString();

        sessionMapper.insert(new SysSession(
                sessionId, user.getId(), tokenHash, restricted, now, expiresAt, now, null));
        userMapper.clearLoginFailure(user.getId());
        auditService.writeInTransaction(
                new AuditEvent(EventType.LOGIN, "SESSION", sessionId,
                        user.getId(), user.getUsername(), true, "OK"));

        return AuthOutcome.ok(token, expiresAt.toInstant(ZoneOffset.UTC), restricted, user.getId());
    }

    // ==================================================================
    // A02 退出
    // ==================================================================

    /**
     * 无 Token、格式无效、不存在、已过期、已撤销一律 200，且不读取主体资料（§5.7）。
     * 有效 Token 在事务内撤销并与 LOGOUT SUCCESS 一起提交；撤销失败不得谎称成功。
     */
    @Override
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        byte[] tokenHash;
        try {
            tokenHash = tokenService.hash(rawToken);
        } catch (RuntimeException ex) {
            return;
        }

        transactionTemplate.executeWithoutResult(status -> {
            securityStateMapper.lockStateRow();
            SysSession session = sessionMapper.lockByTokenHash(tokenHash);
            LocalDateTime now = utc(clock.instant());
            if (session == null || session.getRevokedAt() != null) {
                return;
            }
            if (session.getExpiresAt() != null && !session.getExpiresAt().isAfter(now)) {
                return;
            }
            sessionMapper.revoke(session.getId(), now);
            auditService.writeInTransaction(
                    new AuditEvent(EventType.LOGOUT, "SESSION", session.getId(),
                            session.getUserId(), null, true, "OK"));
        });
    }

    // ==================================================================
    // A03 当前用户
    // ==================================================================

    @Override
    public UserVO getCurrentUser() {
        CurrentUser current = requireCurrentUser();
        SysUser user = userMapper.findById(current.userId());
        if (user == null) {
            throw new BusinessException(ResultCode.SESSION_INVALID, SESSION_INVALID_MESSAGE);
        }
        SysDept dept = deptMapper.findById(user.getDeptId());
        return toUserVO(user, dept);
    }

    // ==================================================================
    // A04 本人改密
    // ==================================================================

    /**
     * 首次改密即激活：PENDING_ACTIVATION → ENABLED、must_change_password=0；
     * 普通会话改密只更换密码。两种情况都撤销该用户全部会话，用新密码重新登录（§8.3）。
     * A04 不要求 If-Match，但必须在锁定事务内再次验证会话类型、账号状态与当前密码。
     */
    @Override
    public void changePassword(PasswordChangeDTO dto) {
        if (dto == null) {
            throw new BusinessException(ResultCode.INVALID_ARGUMENT, "请求正文不能为空");
        }
        CurrentUser current = requireCurrentUser();
        String newPassword = dto.newPassword();
        validateNewPassword(newPassword);

        SysUser snapshot = userMapper.findById(current.userId());
        if (snapshot == null || snapshot.getPasswordHash() == null || snapshot.getPasswordHash().isBlank()) {
            throw new BusinessException(ResultCode.SESSION_INVALID, SESSION_INVALID_MESSAGE);
        }
        if (passwordHashUtil.matches(newPassword, snapshot.getPasswordHash())) {
            throw new BusinessException(ResultCode.WEAK_PASSWORD, "新密码不能与当前密码相同");
        }
        // 哈希计算耗时，放在锁外完成；锁内只比对旧密码与状态是否仍然有效。
        String newHash = passwordHashUtil.encode(newPassword);

        transactionTemplate.executeWithoutResult(status -> {
            securityStateMapper.lockStateRow();
            SysUser user = userMapper.lockById(current.userId());
            if (user == null || user.getPasswordHash() == null) {
                throw new BusinessException(ResultCode.SESSION_INVALID, SESSION_INVALID_MESSAGE);
            }
            if (!isSessionConsistentWithAccount(user, current.restricted())) {
                throw new BusinessException(ResultCode.SESSION_INVALID, SESSION_INVALID_MESSAGE);
            }
            if (dto.currentPassword() == null
                    || !passwordHashUtil.matches(dto.currentPassword(), user.getPasswordHash())) {
                throw new BusinessException(ResultCode.CURRENT_PASSWORD_INVALID, "当前密码不正确");
            }

            LocalDateTime now = utc(clock.instant());
            userMapper.updatePasswordHash(user.getId(), newHash, now);
            sessionMapper.revokeAllByUserId(user.getId(), now);
            auditService.writeInTransaction(
                    new AuditEvent(EventType.PASSWORD_CHANGE, "USER", String.valueOf(user.getId()),
                            user.getId(), user.getUsername(), true, "OK"));
        });
    }

    // ==================================================================
    // A05 门户应用卡片
    // ==================================================================

    /**
     * 身份只取会话主体，不接受客户端指定 userId（§5.5）。
     * 至少拥有一个有效权限点的启用应用才返回。
     */
    @Override
    public List<ApplicationVO> getApps() {
        CurrentUser current = requireCurrentUser();
        EffectivePermissions permissions = permissionEvaluator.evaluate(current.userId());
        if (!permissions.accountUsable() || permissions.applicationIds().isEmpty()) {
            return List.of();
        }
        List<SysApplication> apps = applicationMapper.selectEnabledByIds(permissions.applicationIds());
        List<ApplicationVO> result = new ArrayList<>();
        if (apps != null) {
            for (SysApplication app : apps) {
                result.add(toApplicationVO(app));
            }
        }
        return result;
    }

    // ==================================================================
    // A06 本人权限
    // ==================================================================

    /**
     * 未指定 applicationId 返回全部有效权限；指定时按应用过滤。
     * 未知应用 404；应用存在但无权限返回空数组（§10.1 A06）。
     */
    @Override
    public CurrentPermissionsVO getPermissions(Long applicationId) {
        CurrentUser current = requireCurrentUser();
        EffectivePermissions permissions = permissionEvaluator.evaluate(current.userId());
        List<String> keys;
        if (applicationId == null) {
            keys = permissions.permissionKeys();
        } else {
            SysApplication app = applicationMapper.findById(applicationId);
            if (app == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "应用不存在");
            }
            keys = permissions.permissionKeysOf(applicationId);
        }
        List<String> roleIds = new ArrayList<>();
        for (Long roleId : permissions.roleIds()) {
            roleIds.add(Long.toString(roleId));
        }
        return new CurrentPermissionsVO(keys, roleIds, Long.toString(permissions.policyVersion()));
    }

    // ==================================================================
    // 内部方法
    // ==================================================================

    private SysUser findUserForLogin(String username) {
        try {
            return userMapper.findByUsername(username);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            // 认证存储不可用时默认拒绝，不降级放行（§9.4、§8.4）。
            throw new BusinessException(ResultCode.AUTH_UNAVAILABLE, "认证服务暂时不可用");
        }
    }

    /**
     * 校验密码。未知账号或空哈希也执行一次固定的虚拟校验，
     * 使响应时间不因工号是否存在而变化（§5.1）。
     */
    private boolean verifyPassword(String rawPassword, String storedHash) {
        if (storedHash == null || storedHash.isBlank()) {
            passwordHashUtil.verifyAgainstDummy(rawPassword);
            return false;
        }
        return passwordHashUtil.matches(rawPassword, storedHash);
    }

    private String normalizeUsername(String username) {
        if (username == null) {
            throw new BusinessException(ResultCode.INVALID_ARGUMENT, "工号不能为空");
        }
        String normalized = username.trim().toUpperCase(Locale.ROOT);
        if (!USERNAME_PATTERN.matcher(normalized).matches()) {
            throw new BusinessException(ResultCode.INVALID_ARGUMENT, "工号格式不正确");
        }
        return normalized;
    }

    /** 账号是否具备登录资格（不含锁定判断）：有密码，且状态与首次改密标记匹配（§5.1 表格）。 */
    private boolean isLoginEligible(SysUser user) {
        String hash = user.getPasswordHash();
        if (hash == null || hash.isBlank()) {
            return false;
        }
        if (statusIs(user.getStatus(), UserStatus.PENDING_ACTIVATION)) {
            return flag(user.getMustChangePassword());
        }
        if (statusIs(user.getStatus(), UserStatus.ENABLED)) {
            return !flag(user.getMustChangePassword());
        }
        return false;
    }

    private boolean isSessionConsistentWithAccount(SysUser user, boolean restricted) {
        if (restricted) {
            return statusIs(user.getStatus(), UserStatus.PENDING_ACTIVATION) && flag(user.getMustChangePassword());
        }
        return statusIs(user.getStatus(), UserStatus.ENABLED) && !flag(user.getMustChangePassword());
    }

    private boolean isLocked(SysUser user, LocalDateTime now) {
        return user.getLockedUntil() != null && user.getLockedUntil().isAfter(now);
    }

    /**
     * 连续 5 次错误锁定 15 分钟；锁定期间不累加、不延长；到期后先清零再重新计数（§5.1）。
     */
    private void recordFailedAttempt(SysUser user, LocalDateTime now) {
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            return;
        }
        int priorCount = user.getLockedUntil() != null ? 0 : user.getFailedLoginCount();
        int nextCount = priorCount + 1;
        LocalDateTime lockedUntil = nextCount >= MAX_FAILED_LOGINS ? now.plus(LOGIN_LOCK) : null;
        userMapper.updateLoginFailure(user.getId(), nextCount, lockedUntil);
    }

    private void validateNewPassword(String newPassword) {
        if (newPassword == null) {
            throw new BusinessException(ResultCode.INVALID_ARGUMENT, "新密码不能为空");
        }
        int length = newPassword.codePointCount(0, newPassword.length());
        if (length < PASSWORD_MIN || length > PASSWORD_MAX) {
            throw new BusinessException(ResultCode.INVALID_ARGUMENT,
                    "密码长度必须为 " + PASSWORD_MIN + "–" + PASSWORD_MAX + " 个字符");
        }
        if (WEAK_PASSWORDS.contains(newPassword.toLowerCase(Locale.ROOT)) || isSingleRepeatedChar(newPassword)) {
            throw new BusinessException(ResultCode.WEAK_PASSWORD, "密码强度不足，请更换更复杂的密码");
        }
    }

    private static boolean isSingleRepeatedChar(String value) {
        if (value.isEmpty()) {
            return true;
        }
        char first = value.charAt(0);
        for (int i = 1; i < value.length(); i++) {
            if (value.charAt(i) != first) {
                return false;
            }
        }
        return true;
    }

    private CurrentUser requireCurrentUser() {
        CurrentUser current = CurrentUserContext.get();
        if (current == null) {
            throw new BusinessException(ResultCode.SESSION_INVALID, SESSION_INVALID_MESSAGE);
        }
        return current;
    }

    private UserVO toUserVO(SysUser user, SysDept dept) {
        return new UserVO(
                Long.toString(user.getId()),
                user.getUsername(),
                user.getRealName(),
                Long.toString(user.getCompanyId()),
                Long.toString(user.getDeptId()),
                dept == null ? null : dept.getDeptName(),
                user.getPositionName(),
                maskPhone(user.getPhoneCipher()),
                String.valueOf(user.getStatus()),
                flag(user.getMustChangePassword()),
                user.getLockedUntil(),
                Long.toString(user.getVersion()),
                user.getCreateTime(),
                user.getUpdateTime());
    }

    private ApplicationVO toApplicationVO(SysApplication app) {
        return new ApplicationVO(
                Long.toString(app.getId()),
                app.getAppName(),
                app.getAppKey(),
                app.getStatus(),
                Long.toString(app.getVersion()),
                app.getCreateTime(),
                app.getUpdateTime());
    }

    /** 电话只出掩码；解密失败时不回显任何原文（§8.4）。 */
    private String maskPhone(byte[] cipher) {
        if (cipher == null || cipher.length == 0) {
            return null;
        }
        try {
            String plain = aesGcmUtil.decrypt(cipher);
            if (plain == null || plain.length() < 7) {
                return null;
            }
            return plain.substring(0, 3) + "****" + plain.substring(plain.length() - 4);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static boolean statusIs(Object rawStatus, UserStatus expected) {
        return rawStatus != null && expected.name().equals(rawStatus.toString());
    }

    private static boolean flag(Object rawFlag) {
        return rawFlag instanceof Number number && number.intValue() == 1;
    }

    /** Clock 产出 Instant，数据库列与实体统一按 UTC LocalDateTime 存储，在此集中转换。 */
    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    /** 事务内的认证结果：重试 / 失败 / 成功三态，避免用异常穿过已提交的计数更新。 */
    private record AuthOutcome(boolean shouldRetry,
                               String failure,
                               String message,
                               String token,
                               Instant expiresAt,
                               boolean restricted,
                               long userId) {

        static AuthOutcome retry() {
            return new AuthOutcome(true, null, null, null, null, false, 0L);
        }

        static AuthOutcome fail(String code, String message) {
            return new AuthOutcome(false, code, message, null, null, false, 0L);
        }

        static AuthOutcome ok(String token, Instant expiresAt, boolean restricted, long userId) {
            return new AuthOutcome(false, null, null, token, expiresAt, restricted, userId);
        }
    }
}
