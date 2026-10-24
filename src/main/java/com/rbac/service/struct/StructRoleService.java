package com.rbac.service.struct;

import com.rbac.common.BusinessException;
import com.rbac.common.FieldError;
import com.rbac.common.PageResult;
import com.rbac.common.ResultCode;
import com.rbac.common.constants.PermissionKeyConstants;
import com.rbac.common.constants.SystemConstants;
import com.rbac.common.enums.EventType;
import com.rbac.dto.common.BinaryStatusDTO;
import com.rbac.dto.role.RoleCreateDTO;
import com.rbac.dto.role.RolePatchDTO;
import com.rbac.dto.role.RolePermissionAssignDTO;
import com.rbac.dto.role.RoleQueryDTO;
import com.rbac.entity.SysPermission;
import com.rbac.entity.SysRole;
import com.rbac.mapper.PermissionMapper;
import com.rbac.mapper.RoleMapper;
import com.rbac.mapper.RolePermissionMapper;
import com.rbac.mapper.SecurityStateMapper;
import com.rbac.security.CurrentUser;
import com.rbac.security.CurrentUserContext;
import com.rbac.service.AuditService;
import com.rbac.service.AuditService.AuditEvent;
import com.rbac.service.RoleService;
import com.rbac.vo.permission.PermissionVO;
import com.rbac.vo.role.RolePermissionsVO;
import com.rbac.vo.role.RoleVO;
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
 * 结构化角色/权限分配实现（A20-A26）。
 *
 * <p>写路径约定：A22-A24 事务 + 乐观锁，PLATFORM_ADMIN 为保留角色禁改禁停；
 * A25 返回角色已绑定权限（含停用）；A26 全量替换（≤5000 去重、不存在 400、
 * 新增停用权限 400）并递增角色版本号。
 */
@Service
@ConditionalOnProperty(prefix = "rbac.service", name = "impl", havingValue = "struct", matchIfMissing = true)
public class StructRoleService implements RoleService {

    private static final Pattern ROLE_KEY_PATTERN = Pattern.compile(SystemConstants.ROLE_KEY_PATTERN);

    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final PermissionMapper permissionMapper;
    private final SecurityStateMapper securityStateMapper;
    private final AuditService auditService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public StructRoleService(RoleMapper roleMapper,
                             RolePermissionMapper rolePermissionMapper,
                             PermissionMapper permissionMapper,
                             SecurityStateMapper securityStateMapper,
                             AuditService auditService,
                             PlatformTransactionManager transactionManager,
                             Clock clock) {
        this.roleMapper = roleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.permissionMapper = permissionMapper;
        this.securityStateMapper = securityStateMapper;
        this.auditService = auditService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Override
    public PageResult<RoleVO> list(RoleQueryDTO query) {
        int page = query.pageNumber();
        int size = query.sizeNumber();
        List<SysRole> records = roleMapper.selectPage(blankToNull(query.getKeyword()), query.getStatus(),
                (page - 1) * size, size);
        long total = roleMapper.countPage(blankToNull(query.getKeyword()), query.getStatus());
        return PageResult.of(records.stream().map(this::toVO).toList(), total, page, size);
    }

    @Override
    public RoleVO get(String id) {
        return toVO(requireRole(id));
    }

    // ==================================================================
    // A22 创建角色：初始无权限
    // ==================================================================

    @Override
    public RoleVO create(RoleCreateDTO dto) {
        if (dto == null) {
            throw invalidArgument("请求正文不能为空");
        }
        String roleName = requireText(dto.getRoleName(), "roleName", 1, 80);
        String roleKey = requireText(dto.getRoleKey(), "roleKey", 1, 50);
        if (!ROLE_KEY_PATTERN.matcher(roleKey).matches()) {
            throw invalidArgument(FieldError.format("roleKey"));
        }
        String description = requireOptionalText(dto.getDescription(), "description", 300);
        int status = normalizeBinaryStatus(dto.getStatus(), "status", true);

        SysRole role = new SysRole();
        role.setRoleName(roleName);
        role.setRoleKey(roleKey);
        role.setDescription(description);
        role.setStatus(status);
        role.setCreateTime(utcNow());
        role.setUpdateTime(role.getCreateTime());
        role.setVersion(0L);

        try {
            transactionTemplate.executeWithoutResult(tx -> {
                securityStateMapper.lockStateRow();
                if (roleMapper.selectByRoleKey(roleKey) != null) {
                    throw new BusinessException(ResultCode.ROLE_KEY_EXISTS, 409, "角色键已存在");
                }
                roleMapper.insert(role);
                auditService.writeInTransaction(successEvent(EventType.ROLE_CREATE, "ROLE", role.getId()));
            });
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.ROLE_KEY_EXISTS, 409, "角色键已存在");
        }
        return toVO(roleMapper.selectById(role.getId()));
    }

    // ==================================================================
    // A23 部分更新：保留角色禁改；roleKey 不可变（DTO 不携带）
    // ==================================================================

    @Override
    public RoleVO update(String id, Long expectedVersion, RolePatchDTO dto) {
        Long roleId = requireExistingId(id);
        SysRole role = requireRole(id);
        requireNotReservedRole(role);
        if (dto == null || !dto.hasAnyField()) {
            throw invalidArgument("至少提供一个待更新字段");
        }
        String roleName = dto.isRoleNameProvided() ? requireText(dto.getRoleName(), "roleName", 1, 80) : null;
        String description = null;
        boolean clearDescription = false;
        if (dto.isDescriptionProvided()) {
            if (dto.getDescription() == null) {
                clearDescription = true;
            } else {
                description = requireOptionalText(dto.getDescription(), "description", 300);
            }
        }
        requireVersion(expectedVersion);

        final String descriptionFinal = description;
        final boolean clearDescriptionFinal = clearDescription;
        transactionTemplate.executeWithoutResult(tx -> {
            securityStateMapper.lockStateRow();
            requireRoleWithVersion(roleId, expectedVersion);
            int rows = roleMapper.updateProfile(roleId, expectedVersion, roleName, descriptionFinal, clearDescriptionFinal);
            requireUpdated(rows, roleId);
            auditService.writeInTransaction(successEvent(EventType.ROLE_UPDATE, "ROLE", roleId));
        });
        return toVO(roleMapper.selectById(roleId));
    }

    // ==================================================================
    // A24 角色启停：PLATFORM_ADMIN 禁停
    // ==================================================================

    @Override
    public RoleVO changeStatus(String id, Long expectedVersion, BinaryStatusDTO dto) {
        Long roleId = requireExistingId(id);
        SysRole role = requireRole(id);
        requireNotReservedRole(role);
        int target = normalizeBinaryStatus(dto == null ? null : dto.getStatus(), "status", false);
        requireVersion(expectedVersion);

        transactionTemplate.executeWithoutResult(tx -> {
            securityStateMapper.lockStateRow();
            requireRoleWithVersion(roleId, expectedVersion);
            requireUpdated(roleMapper.updateStatus(roleId, expectedVersion, target), roleId);
            auditService.writeInTransaction(successEvent(EventType.ROLE_STATUS, "ROLE", roleId));
        });
        return toVO(roleMapper.selectById(roleId));
    }

    // ==================================================================
    // A25 角色已绑定权限（含停用）
    // ==================================================================

    @Override
    public RolePermissionsVO getPermissions(String id) {
        SysRole role = requireRole(id);
        return toRolePermissionsVO(role);
    }

    // ==================================================================
    // A26 权限全量替换：≤5000 去重；禁改 PLATFORM_ADMIN；版本递增
    // ==================================================================

    @Override
    public RolePermissionsVO assignPermissions(String id, Long expectedVersion, RolePermissionAssignDTO dto) {
        Long roleId = requireExistingId(id);
        SysRole role = requireRole(id);
        requireNotReservedRole(role);
        List<Long> permissionIds = parseDistinctIds(dto == null ? null : dto.getPermissionIds(),
                "permissionIds", SystemConstants.PERMISSION_SELECTION_MAX);
        requireVersion(expectedVersion);

        Set<Long> current = new LinkedHashSet<>(rolePermissionMapper.selectPermissionIdsByRoleId(roleId));
        for (Long permissionId : permissionIds) {
            SysPermission permission = permissionMapper.selectById(permissionId);
            if (permission == null) {
                throw invalidArgument(FieldError.referenceInvalid("permissionIds"));
            }
            if (!enabled(permission.getStatus()) && !current.contains(permissionId)) {
                throw invalidArgument("permissionIds 包含停用权限，不能新增绑定",
                        FieldError.referenceInvalid("permissionIds"));
            }
        }

        transactionTemplate.executeWithoutResult(tx -> {
            securityStateMapper.lockStateRow();
            requireRoleWithVersion(roleId, expectedVersion);
            rolePermissionMapper.deleteByRoleId(roleId);
            if (!permissionIds.isEmpty()) {
                rolePermissionMapper.batchInsert(roleId, permissionIds);
            }
            if (roleMapper.bumpVersion(roleId, expectedVersion) != 1) {
                throw BusinessException.versionConflict();
            }
            auditService.writeInTransaction(successEvent(EventType.ROLE_PERMISSION_ASSIGN, "ROLE", roleId));
        });
        return toRolePermissionsVO(roleMapper.selectById(roleId));
    }

    // ---------- 内部 ----------

    private RoleVO toVO(SysRole role) {
        RoleVO vo = new RoleVO();
        vo.setId(String.valueOf(role.getId()));
        vo.setRoleName(role.getRoleName());
        vo.setRoleKey(role.getRoleKey());
        vo.setDescription(role.getDescription());
        vo.setStatus(role.getStatus());
        vo.setVersion(String.valueOf(role.getVersion()));
        vo.setCreateTime(role.getCreateTime());
        vo.setUpdateTime(role.getUpdateTime());
        return vo;
    }

    private void requireNotReservedRole(SysRole role) {
        if (PermissionKeyConstants.RESERVED_ROLE_KEY.equals(role.getRoleKey())) {
            throw BusinessException.reservedResource();
        }
    }

    private RolePermissionsVO toRolePermissionsVO(SysRole role) {
        List<SysPermission> permissions = rolePermissionMapper.selectPermissionsByRoleId(role.getId());
        RolePermissionsVO vo = new RolePermissionsVO();
        vo.setRoleId(String.valueOf(role.getId()));
        List<String> permissionIds = new ArrayList<>();
        List<PermissionVO> permissionVos = new ArrayList<>();
        for (SysPermission p : permissions) {
            permissionIds.add(String.valueOf(p.getId()));
            permissionVos.add(toPermissionVO(p));
        }
        vo.setPermissionIds(permissionIds);
        vo.setPermissions(permissionVos);
        vo.setVersion(String.valueOf(role.getVersion()));
        return vo;
    }

    private PermissionVO toPermissionVO(SysPermission p) {
        PermissionVO vo = new PermissionVO();
        vo.setId(String.valueOf(p.getId()));
        vo.setApplicationId(String.valueOf(p.getApplicationId()));
        vo.setModuleKey(p.getModuleKey());
        vo.setModuleName(p.getModuleName());
        vo.setResourceKey(p.getResourceKey());
        vo.setResourceName(p.getResourceName());
        vo.setResourceDescription(p.getResourceDescription());
        vo.setActionCode(p.getActionCode());
        vo.setPermName(p.getPermName());
        vo.setPermKey(p.getPermKey());
        vo.setSourceRow(p.getSourceRow());
        vo.setStatus(p.getStatus());
        vo.setVersion(String.valueOf(p.getVersion()));
        vo.setCreateTime(p.getCreateTime());
        vo.setUpdateTime(p.getUpdateTime());
        return vo;
    }

    private SysRole requireRole(String id) {
        SysRole role = null;
        try {
            role = roleMapper.selectById(Long.parseLong(id.trim()));
        } catch (NumberFormatException e) {
            // 非法 ID 走 NOT_FOUND
        }
        if (role == null) {
            throw BusinessException.notFound();
        }
        return role;
    }

    private Long requireExistingId(String id) {
        Long roleId = parseLong(id);
        if (roleId == null || roleMapper.selectById(roleId) == null) {
            throw BusinessException.notFound();
        }
        return roleId;
    }

    private SysRole requireRoleWithVersion(Long roleId, Long expectedVersion) {
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw BusinessException.notFound();
        }
        if (!Objects.equals(role.getVersion(), expectedVersion)) {
            throw BusinessException.versionConflict();
        }
        return role;
    }

    private void requireUpdated(int rows, Long roleId) {
        if (rows == 1) {
            return;
        }
        throw roleMapper.selectById(roleId) == null ? BusinessException.notFound() : BusinessException.versionConflict();
    }

    /** 成功审计事件：主体取当前请求身份。 */
    private static AuditEvent successEvent(EventType type, String targetType, Long targetId) {
        CurrentUser current = CurrentUserContext.get();
        return new AuditEvent(type, targetType, String.valueOf(targetId),
                current == null ? null : current.getUserId(),
                current == null ? null : current.getUsername(),
                true, "OK");
    }

    /** ID 列表归一：非空、去重、限量、逐项格式校验。 */
    private static List<Long> parseDistinctIds(List<String> rawIds, String field, int maxSize) {
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

    private static int normalizeBinaryStatus(Integer raw, String field, boolean allowNullDefault) {
        if (raw == null) {
            if (allowNullDefault) {
                return 1;
            }
            throw invalidArgument(FieldError.required(field));
        }
        if (raw != 0 && raw != 1) {
            throw invalidArgument(FieldError.range(field));
        }
        return raw;
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

    /** 可选文本：null 合法；非空时 trim 且 ≤ max。 */
    private static String requireOptionalText(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            return value == null ? null : "";
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw invalidArgument(FieldError.length(field));
        }
        return trimmed;
    }

    private static boolean enabled(Object rawStatus) {
        return rawStatus instanceof Number number && number.intValue() == 1;
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

    private LocalDateTime utcNow() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
