package com.rbac.service.struct;

import com.rbac.common.BusinessException;
import com.rbac.common.FieldError;
import com.rbac.common.PageResult;
import com.rbac.common.ResultCode;
import com.rbac.common.constants.PermissionKeyConstants;
import com.rbac.common.constants.SystemConstants;
import com.rbac.common.enums.EventType;
import com.rbac.common.enums.UserStatus;
import com.rbac.dto.auth.PasswordResetDTO;
import com.rbac.dto.user.UserCreateDTO;
import com.rbac.dto.user.UserPatchDTO;
import com.rbac.dto.user.UserQueryDTO;
import com.rbac.dto.user.UserRoleAssignDTO;
import com.rbac.dto.user.UserStatusDTO;
import com.rbac.entity.SysDept;
import com.rbac.entity.SysRole;
import com.rbac.entity.SysUser;
import com.rbac.mapper.DeptMapper;
import com.rbac.mapper.RoleMapper;
import com.rbac.mapper.SecurityStateMapper;
import com.rbac.mapper.SessionMapper;
import com.rbac.mapper.UserMapper;
import com.rbac.mapper.UserRoleMapper;
import com.rbac.security.CurrentUser;
import com.rbac.security.CurrentUserContext;
import com.rbac.security.PermissionEvaluator;
import com.rbac.service.AuditService;
import com.rbac.service.AuditService.AuditEvent;
import com.rbac.service.UserService;
import com.rbac.util.AesGcmUtil;
import com.rbac.util.PasswordHashUtil;
import com.rbac.vo.role.RoleVO;
import com.rbac.vo.user.UserRolesVO;
import com.rbac.vo.user.UserVO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 结构化用户/角色分配实现（A07-A14）。
 *
 * <p>写路径统一约定（§5.1/§6.1）：事务内先锁 security_state 行，再锁目标用户行；
 * 写库走乐观锁（version = expected），0 行更新按「已删除 → 404，版本不匹配 → 412」区分；
 * Argon2id 哈希计算放在锁外；管理员保护以「有效管理员」（持有启用 PLATFORM_ADMIN 且账号可用）为准。
 */
@Service
@ConditionalOnProperty(prefix = "rbac.service", name = "impl", havingValue = "struct", matchIfMissing = true)
public class StructUserService implements UserService {

    private static final Pattern USERNAME_PATTERN = Pattern.compile(SystemConstants.USERNAME_PATTERN);
    private static final Pattern PHONE_PATTERN = Pattern.compile(SystemConstants.PHONE_PATTERN);

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final DeptMapper deptMapper;
    private final SessionMapper sessionMapper;
    private final SecurityStateMapper securityStateMapper;
    private final PermissionEvaluator permissionEvaluator;
    private final PasswordHashUtil passwordHashUtil;
    private final AesGcmUtil aesGcmUtil;
    private final AuditService auditService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public StructUserService(UserMapper userMapper,
                             UserRoleMapper userRoleMapper,
                             RoleMapper roleMapper,
                             DeptMapper deptMapper,
                             SessionMapper sessionMapper,
                             SecurityStateMapper securityStateMapper,
                             PermissionEvaluator permissionEvaluator,
                             PasswordHashUtil passwordHashUtil,
                             AesGcmUtil aesGcmUtil,
                             AuditService auditService,
                             PlatformTransactionManager transactionManager,
                             Clock clock) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.deptMapper = deptMapper;
        this.sessionMapper = sessionMapper;
        this.securityStateMapper = securityStateMapper;
        this.permissionEvaluator = permissionEvaluator;
        this.passwordHashUtil = passwordHashUtil;
        this.aesGcmUtil = aesGcmUtil;
        this.auditService = auditService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Override
    public PageResult<UserVO> list(UserQueryDTO query) {
        int page = query.pageNumber();
        int size = query.sizeNumber();
        String status = query.getStatus();
        String grade = com.rbac.util.StaffGrade.validate(query.getGrade());
        Long deptId = parseLong(query.getDeptId());
        Long companyId = parseLong(query.getCompanyId());
        List<Long> deptIds = null;
        if (deptId != null) {
            deptIds = Boolean.TRUE.equals(query.getIncludeDescendants())
                    ? deptMapper.selectSelfAndDescendants(deptId).stream().map(SysDept::getId).toList()
                    : List.of(deptId);
            if (deptIds.isEmpty()) deptIds = List.of(deptId);
        }
        List<SysUser> records = userMapper.selectPage(
                blankToNull(query.getKeyword()), deptIds, companyId, blankToNull(status), grade,
                (page - 1) * size, size);
        long total = userMapper.countPage(blankToNull(query.getKeyword()), deptIds, companyId,
                blankToNull(status), grade);
        List<UserVO> vos = records.stream().map(this::toVO).toList();
        return PageResult.of(vos, total, page, size);
    }

    @Override
    public UserVO get(String id) {
        SysUser user = requireUser(id);
        return toVO(user);
    }

    // ==================================================================
    // A09 创建用户：初始无密码、无角色、PENDING_ACTIVATION
    // ==================================================================

    @Override
    public UserVO create(UserCreateDTO dto) {
        if (dto == null) {
            throw invalidArgument("请求正文不能为空");
        }
        String username = normalizeUsername(dto.getUsername());
        String realName = requireText(dto.getRealName(), "realName", 1, 80);
        String positionName = requireText(dto.getPositionName(), "positionName", 1, 80);
        Long companyId = requireId(dto.getCompanyId(), "companyId");
        Long deptId = requireId(dto.getDeptId(), "deptId");
        byte[] phoneCipher = validatePhone(dto.getPhone());

        LocalDateTime now = utcNow();
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setRealName(realName);
        user.setCompanyId(companyId);
        user.setDeptId(deptId);
        user.setPositionName(positionName);
        user.setPhoneCipher(phoneCipher);
        user.setStatus(UserStatus.PENDING_ACTIVATION.name());
        user.setMustChangePassword(0);
        user.setCreateTime(now);
        user.setUpdateTime(now);
        user.setVersion(0L);

        try {
            transactionTemplate.executeWithoutResult(status -> {
                securityStateMapper.lockStateRow();
                validateOrganization(companyId, deptId);
                if (userMapper.findByUsername(username) != null) {
                    throw new BusinessException(ResultCode.USERNAME_EXISTS, 409, "工号已存在");
                }
                userMapper.insert(user);
                auditService.writeInTransaction(successEvent(EventType.USER_CREATE, "USER", user.getId()));
            });
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.USERNAME_EXISTS, 409, "工号已存在");
        }
        return toVO(userMapper.selectById(user.getId()));
    }

    // ==================================================================
    // A10 部分更新：部门不可跨指定公司；调动撤销本人会话
    // ==================================================================

    @Override
    public UserVO update(String id, Long expectedVersion, UserPatchDTO dto) {
        Long userId = requireExistingId(id);
        if (dto == null || !dto.hasAnyField()) {
            throw invalidArgument("至少提供一个待更新字段");
        }
        String realName = dto.isRealNameProvided() ? requireText(dto.getRealName(), "realName", 1, 80) : null;
        String positionName = dto.isPositionNameProvided()
                ? requireText(dto.getPositionName(), "positionName", 1, 80) : null;
        Long companyId = dto.isCompanyIdProvided() ? requireId(dto.getCompanyId(), "companyId") : null;
        Long deptId = dto.isDeptIdProvided() ? requireId(dto.getDeptId(), "deptId") : null;
        byte[] phoneCipher = null;
        boolean clearPhone = false;
        if (dto.isPhoneProvided()) {
            if (dto.getPhone() == null) {
                clearPhone = true;
            } else {
                phoneCipher = validatePhone(dto.getPhone());
            }
        }
        requireVersion(expectedVersion);
        updateInTransaction(userId, expectedVersion, realName, companyId, deptId,
                positionName, phoneCipher, clearPhone);
        return toVO(userMapper.selectById(userId));
    }

    /** 事务内更新；发生组织调动时在同一事务撤销本人会话，保证撤销不丢失。 */
    private void updateInTransaction(Long userId, Long expectedVersion, String realName, Long companyId,
                                     Long deptId, String positionName, byte[] phoneCipher, boolean clearPhone) {
        transactionTemplate.executeWithoutResult(status -> {
            securityStateMapper.lockStateRow();
            SysUser user = requireLockedUser(userId, expectedVersion);
            boolean moved = false;
            if (companyId != null || deptId != null) {
                Long finalCompanyId = companyId != null ? companyId : user.getCompanyId();
                Long finalDeptId = deptId != null ? deptId : user.getDeptId();
                validateOrganization(finalCompanyId, finalDeptId);
                moved = (deptId != null && !deptId.equals(user.getDeptId()))
                        || (companyId != null && !companyId.equals(user.getCompanyId()));
            }
            moved = moved || (positionName != null && !positionName.equals(user.getPositionName()));
            if (moved) {
                CurrentUser operator = CurrentUserContext.get();
                if (operator == null) {
                    throw BusinessException.permissionDenied();
                }
                PermissionEvaluator.EffectivePermissions effective = permissionEvaluator.evaluate(operator.getUserId());
                if (!effective.has(PermissionKeyConstants.USER_ACCOUNT_EDIT)
                        || !effective.has(PermissionKeyConstants.USER_ACCOUNT_ASSIGN_ROLE)) {
                    throw BusinessException.permissionDenied();
                }
                if (isEffectiveAdmin(user) && countOtherEffectiveAdmins(userId) == 0) {
                    throw BusinessException.lastAdminRequired();
                }
            }
            int rows = userMapper.updateProfile(userId, expectedVersion, realName, companyId, deptId,
                    positionName, phoneCipher, clearPhone);
            requireUpdated(rows, userId, () -> userMapper.selectById(userId) != null);
            if (moved) {
                userRoleMapper.deleteByUserId(userId);
                sessionMapper.revokeAllByUserId(userId, utcNow());
            }
            auditService.writeInTransaction(successEvent(EventType.USER_UPDATE, "USER", userId));
        });
    }

    // ==================================================================
    // A11 状态变更：停用撤销会话；恢复 ENABLED 须已改首次密码
    // ==================================================================

    @Override
    public UserVO changeStatus(String id, Long expectedVersion, UserStatusDTO dto) {
        Long userId = requireExistingId(id);
        String target = normalizeUserStatus(dto);
        requireVersion(expectedVersion);

        transactionTemplate.executeWithoutResult(status -> {
            securityStateMapper.lockStateRow();
            SysUser user = requireLockedUser(userId, expectedVersion);

            boolean toEnabled = UserStatus.ENABLED.name().equals(target);
            if (toEnabled) {
                if (user.getPasswordHash() == null || flag(user.getMustChangePassword())) {
                    throw BusinessException.stateConflict("账号尚未完成首次改密，不能启用");
                }
            } else if (UserStatus.PENDING_ACTIVATION.name().equals(target)) {
                if (user.getPasswordHash() == null || !flag(user.getMustChangePassword())) {
                    throw BusinessException.stateConflict("账号不具备待激活条件（须已设临时密码且未改密）");
                }
            }
            if (!toEnabled && isEffectiveAdmin(user) && countOtherEffectiveAdmins(user.getId()) == 0) {
                throw BusinessException.lastAdminRequired();
            }

            Integer mustChangePassword = null;
            if (toEnabled) {
                mustChangePassword = 0;
            } else if (UserStatus.PENDING_ACTIVATION.name().equals(target)) {
                mustChangePassword = 1;
            }
            int rows = userMapper.updateStatus(userId, expectedVersion, target, mustChangePassword, toEnabled);
            requireUpdated(rows, userId, () -> userMapper.selectById(userId) != null);
            if (UserStatus.DISABLED.name().equals(target)) {
                sessionMapper.revokeAllByUserId(userId, utcNow());
            }
            auditService.writeInTransaction(successEvent(EventType.USER_STATUS, "USER", userId));
        });
        return toVO(userMapper.selectById(userId));
    }

    // ==================================================================
    // A12 重置临时密码：DISABLED 仍 DISABLED，其余 PENDING；管理员保护；不回显密码
    // ==================================================================

    @Override
    public UserVO resetPassword(String id, Long expectedVersion, PasswordResetDTO dto) {
        Long userId = requireExistingId(id);
        String temporaryPassword = dto == null ? null : dto.temporaryPassword();
        validatePasswordPolicy(temporaryPassword);
        // Argon2id 计算耗时，放锁外；锁内只做状态与管理员保护校验
        String newHash = passwordHashUtil.encode(temporaryPassword);
        requireVersion(expectedVersion);

        transactionTemplate.executeWithoutResult(status -> {
            securityStateMapper.lockStateRow();
            SysUser user = requireLockedUser(userId, expectedVersion);
            if (isPlatformAdminHolder(user.getId())) {
                throw BusinessException.reservedResource();
            }
            String newStatus = UserStatus.DISABLED.name().equals(user.getStatus())
                    ? UserStatus.DISABLED.name()
                    : UserStatus.PENDING_ACTIVATION.name();
            int rows = userMapper.updatePassword(userId, expectedVersion, newHash, newStatus, 1, true);
            requireUpdated(rows, userId, () -> userMapper.selectById(userId) != null);
            // 临时密码下发后旧会话一律作废
            sessionMapper.revokeAllByUserId(userId, utcNow());
            auditService.writeInTransaction(successEvent(EventType.PASSWORD_RESET, "USER", userId));
        });
        return toVO(userMapper.selectById(userId));
    }

    // ==================================================================
    // A13 当前已分配直接角色（含停用，不伪造继承角色）
    // ==================================================================

    @Override
    public UserRolesVO getUserRoles(String id) {
        SysUser user = requireUser(id);
        return toUserRolesVO(user);
    }

    // ==================================================================
    // A14 完整替换角色集合：重复/不存在/新增停用角色 400；事务和管理员保护
    // ==================================================================

    @Override
    public UserRolesVO assignRoles(String id, Long expectedVersion, UserRoleAssignDTO dto) {
        Long userId = requireExistingId(id);
        List<Long> roleIds = parseDistinctIds(dto == null ? null : dto.getRoleIds(),
                "roleIds", SystemConstants.ROLE_SELECTION_MAX);
        requireVersion(expectedVersion);

        transactionTemplate.executeWithoutResult(status -> {
            securityStateMapper.lockStateRow();
            SysUser user = requireLockedUser(userId, expectedVersion);
            Set<Long> current = new LinkedHashSet<>(userRoleMapper.selectRoleIdsByUserId(userId));
            for (Long roleId : roleIds) {
                SysRole role = roleMapper.selectById(roleId);
                if (role == null) {
                    throw invalidArgument(FieldError.referenceInvalid("roleIds"));
                }
                if (!enabled(role.getStatus()) && !current.contains(roleId)) {
                    throw invalidArgument("roleIds 包含停用角色，不能新增分配",
                            FieldError.referenceInvalid("roleIds"));
                }
            }
            Long adminRoleId = platformAdminRoleId();
            if (adminRoleId != null && current.contains(adminRoleId) && !roleIds.contains(adminRoleId)
                    && countOtherEffectiveAdmins(user.getId()) == 0) {
                throw BusinessException.lastAdminRequired();
            }
            userRoleMapper.deleteByUserId(userId);
            if (!roleIds.isEmpty()) {
                userRoleMapper.batchInsert(userId, roleIds);
            }
            if (userMapper.bumpVersion(userId, expectedVersion) != 1) {
                throw BusinessException.versionConflict();
            }
            auditService.writeInTransaction(successEvent(EventType.USER_ROLE_ASSIGN, "USER", userId));
        });
        return toUserRolesVO(userMapper.selectById(userId));
    }

    // ---------- 校验与归一 ----------

    private String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            throw invalidArgument(FieldError.required("username"));
        }
        String normalized = username.trim().toUpperCase(Locale.ROOT);
        if (!USERNAME_PATTERN.matcher(normalized).matches()) {
            throw invalidArgument(FieldError.format("username"));
        }
        return normalized;
    }

    private void validateOrganization(Long companyId, Long deptId) {
        SysDept company = deptMapper.selectById(companyId);
        if (company == null || !enabled(company.getStatus())) {
            throw invalidArgument(FieldError.referenceInvalid("companyId"));
        }
        if (!"COMPANY".equals(company.getNodeType())) {
            throw invalidArgument("companyId 必须指向公司节点", FieldError.format("companyId"));
        }
        SysDept dept = deptMapper.selectById(deptId);
        if (dept == null || !enabled(dept.getStatus())) {
            throw invalidArgument(FieldError.referenceInvalid("deptId"));
        }
        boolean within = deptMapper.selectSelfAndAncestors(deptId).stream()
                .anyMatch(node -> companyId.equals(node.getId()));
        if (!within) {
            throw invalidArgument("deptId 必须隶属于指定公司", FieldError.referenceInvalid("deptId"));
        }
    }

    /** 手机号可空；非空须 11 位数字，AES-GCM 加密后落库。 */
    private byte[] validatePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            if (phone != null) {
                throw invalidArgument(FieldError.format("phone"));
            }
            return null;
        }
        if (!PHONE_PATTERN.matcher(phone.trim()).matches()) {
            throw invalidArgument(FieldError.format("phone"));
        }
        return aesGcmUtil.encrypt(phone.trim());
    }

    private void validatePasswordPolicy(String password) {
        if (password == null || password.isBlank()) {
            throw invalidArgument(FieldError.required("temporaryPassword"));
        }
        int length = password.codePointCount(0, password.length());
        if (length < SystemConstants.PASSWORD_MIN || length > SystemConstants.PASSWORD_MAX) {
            throw new BusinessException(ResultCode.INVALID_ARGUMENT, 400,
                    "密码长度必须为 " + SystemConstants.PASSWORD_MIN + "–" + SystemConstants.PASSWORD_MAX + " 个字符");
        }
        if (SystemConstants.WEAK_PASSWORDS.contains(password.toLowerCase(Locale.ROOT))
                || isSingleRepeatedChar(password)) {
            throw new BusinessException(ResultCode.WEAK_PASSWORD, 400, "密码强度不足，请更换更复杂的密码");
        }
    }

    private String normalizeUserStatus(UserStatusDTO dto) {
        if (dto == null || dto.getStatus() == null || dto.getStatus().isBlank()) {
            throw invalidArgument(FieldError.required("status"));
        }
        String status = dto.getStatus().trim();
        for (UserStatus candidate : UserStatus.values()) {
            if (candidate.name().equals(status)) {
                return status;
            }
        }
        throw invalidArgument(FieldError.format("status"));
    }

    /** ID 列表归一：非空、去重、限量、逐项格式校验。 */
    private List<Long> parseDistinctIds(List<String> rawIds, String field, int maxSize) {
        if (rawIds == null) {
            throw invalidArgument(FieldError.required(field));
        }
        if (rawIds.size() > maxSize) {
            throw invalidArgument(field + " 数量超出上限 " + maxSize, FieldError.range(field));
        }
        Set<Long> unique = new LinkedHashSet<>();
        for (String raw : rawIds) {
            Long id = parseLong(raw);
            if (id == null) {
                throw invalidArgument(FieldError.format(field));
            }
            if (!unique.add(id)) {
                throw invalidArgument(FieldError.duplicate(field));
            }
        }
        return List.copyOf(unique);
    }

    // ---------- 管理员保护 ----------

    private Long platformAdminRoleId() {
        SysRole role = roleMapper.selectByRoleKey(PermissionKeyConstants.RESERVED_ROLE_KEY);
        return role == null ? null : role.getId();
    }

    private boolean isPlatformAdminHolder(Long userId) {
        Long adminRoleId = platformAdminRoleId();
        return adminRoleId != null && userRoleMapper.selectRoleIdsByUserId(userId).contains(adminRoleId);
    }

    /** 有效管理员：持有启用 PLATFORM_ADMIN 角色且账号可用（ENABLED + 组织链启用）。 */
    private boolean isEffectiveAdmin(SysUser user) {
        SysRole adminRole = roleMapper.selectByRoleKey(PermissionKeyConstants.RESERVED_ROLE_KEY);
        if (adminRole == null || !enabled(adminRole.getStatus())) {
            return false;
        }
        return permissionEvaluator.isAccountUsable(user)
                && userRoleMapper.selectRoleIdsByUserId(user.getId()).contains(adminRole.getId());
    }

    /** 除指定用户外剩余的有效管理员数（用于 LAST_ADMIN_REQUIRED 判定）。 */
    private long countOtherEffectiveAdmins(Long excludeUserId) {
        SysRole adminRole = roleMapper.selectByRoleKey(PermissionKeyConstants.RESERVED_ROLE_KEY);
        if (adminRole == null || !enabled(adminRole.getStatus())) {
            return 0;
        }
        return userMapper.selectByRoleKey(PermissionKeyConstants.RESERVED_ROLE_KEY).stream()
                .filter(u -> !u.getId().equals(excludeUserId))
                .filter(permissionEvaluator::isAccountUsable)
                .count();
    }

    // ---------- 内部 ----------

    private SysUser requireUser(String id) {
        Long userId = parseLong(id);
        SysUser user = userId == null ? null : userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.notFound();
        }
        return user;
    }

    private Long requireExistingId(String id) {
        Long userId = parseLong(id);
        if (userId == null || userMapper.selectById(userId) == null) {
            throw BusinessException.notFound();
        }
        return userId;
    }

    private SysUser requireLockedUser(Long userId, Long expectedVersion) {
        SysUser user = userMapper.lockById(userId);
        if (user == null) {
            throw BusinessException.notFound();
        }
        if (!Objects.equals(user.getVersion(), expectedVersion)) {
            throw BusinessException.versionConflict();
        }
        return user;
    }

    /** 乐观锁 0 行更新的统一判定：记录已删除 → 404，否则版本不匹配 → 412。 */
    private void requireUpdated(int rows, Long id, java.util.function.BooleanSupplier stillExists) {
        if (rows == 1) {
            return;
        }
        throw stillExists.getAsBoolean() ? BusinessException.versionConflict() : BusinessException.notFound();
    }

    /** 成功审计事件：主体取当前请求身份，仅标记均为纯文本白名单值。 */
    private static AuditEvent successEvent(EventType type, String targetType, Long targetId) {
        CurrentUser current = CurrentUserContext.get();
        return new AuditEvent(type, targetType, String.valueOf(targetId),
                current == null ? null : current.getUserId(),
                current == null ? null : current.getUsername(),
                true, "OK");
    }

    private UserRolesVO toUserRolesVO(SysUser user) {
        List<SysRole> roles = userRoleMapper.selectRolesByUserId(user.getId());
        UserRolesVO vo = new UserRolesVO();
        vo.setUserId(String.valueOf(user.getId()));
        List<String> roleIds = new ArrayList<>();
        List<RoleVO> roleVos = new ArrayList<>();
        for (SysRole role : roles) {
            roleIds.add(String.valueOf(role.getId()));
            RoleVO roleVo = new RoleVO();
            roleVo.setId(String.valueOf(role.getId()));
            roleVo.setRoleName(role.getRoleName());
            roleVo.setRoleKey(role.getRoleKey());
            roleVo.setDescription(role.getDescription());
            roleVo.setStatus(role.getStatus());
            roleVo.setVersion(String.valueOf(role.getVersion()));
            roleVo.setCreateTime(role.getCreateTime());
            roleVo.setUpdateTime(role.getUpdateTime());
            roleVos.add(roleVo);
        }
        vo.setRoleIds(roleIds);
        vo.setRoles(roleVos);
        vo.setVersion(String.valueOf(user.getVersion()));
        return vo;
    }

    private UserVO toVO(SysUser user) {
        UserVO vo = new UserVO();
        vo.setId(String.valueOf(user.getId()));
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setCompanyId(user.getCompanyId() == null ? null : String.valueOf(user.getCompanyId()));
        vo.setDeptId(user.getDeptId() == null ? null : String.valueOf(user.getDeptId()));
        SysDept department = user.getDeptId() == null ? null : deptMapper.selectById(user.getDeptId());
        vo.setDeptName(department == null ? null : department.getDeptName());
        vo.setPositionName(user.getPositionName());
        vo.setPhoneMasked(maskPhone(user.getPhoneCipher()));
        vo.setStatus(user.getStatus());
        vo.setMustChangePassword(user.getMustChangePassword() != null && user.getMustChangePassword() == 1);
        vo.setLockedUntil(user.getLockedUntil());
        vo.setVersion(String.valueOf(user.getVersion()));
        vo.setCreateTime(user.getCreateTime());
        vo.setUpdateTime(user.getUpdateTime());
        return vo;
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

    private static boolean flag(Object rawFlag) {
        return rawFlag instanceof Number number && number.intValue() == 1;
    }

    private static boolean enabled(Object rawStatus) {
        return rawStatus instanceof Number number && number.intValue() == 1;
    }

    private static boolean isSingleRepeatedChar(String value) {
        char first = value.charAt(0);
        for (int i = 1; i < value.length(); i++) {
            if (value.charAt(i) != first) {
                return false;
            }
        }
        return true;
    }

    private static void requireVersion(Long expectedVersion) {
        if (expectedVersion == null) {
            throw BusinessException.preconditionRequired();
        }
    }

    private static Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long requireId(String value, String field) {
        Long id = parseLong(value);
        if (id == null) {
            throw invalidArgument(FieldError.format(field));
        }
        return id;
    }

    private static String requireText(String value, String field, int min, int max) {
        if (value == null || value.isBlank()) {
            throw invalidArgument(FieldError.required(field));
        }
        String trimmed = value.trim();
        if (trimmed.length() < min || trimmed.length() > max) {
            throw invalidArgument(FieldError.length(field));
        }
        return trimmed;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static BusinessException invalidArgument(String message) {
        return new BusinessException(ResultCode.INVALID_ARGUMENT, 400, message);
    }

    private static BusinessException invalidArgument(String message, FieldError error) {
        return new BusinessException(ResultCode.INVALID_ARGUMENT, 400, message, List.of(error));
    }

    private static BusinessException invalidArgument(FieldError error) {
        return new BusinessException(ResultCode.INVALID_ARGUMENT, 400, "请求参数不合法", List.of(error));
    }

    /** Clock 产出 Instant，数据库列统一按 UTC LocalDateTime 存储。 */
    private LocalDateTime utcNow() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
