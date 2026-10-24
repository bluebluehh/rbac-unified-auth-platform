package com.rbac.service.struct;

import com.rbac.common.BusinessException;
import com.rbac.common.FieldError;
import com.rbac.common.PageResult;
import com.rbac.common.ResultCode;
import com.rbac.common.enums.ActionCode;
import com.rbac.common.enums.EventType;
import com.rbac.dto.common.BinaryStatusDTO;
import com.rbac.dto.permission.PermissionCreateDTO;
import com.rbac.dto.permission.PermissionQueryDTO;
import com.rbac.entity.SysApplication;
import com.rbac.entity.SysPermission;
import com.rbac.mapper.ApplicationMapper;
import com.rbac.mapper.PermissionMapper;
import com.rbac.mapper.SecurityStateMapper;
import com.rbac.security.CurrentUser;
import com.rbac.security.CurrentUserContext;
import com.rbac.service.AuditService;
import com.rbac.service.AuditService.AuditEvent;
import com.rbac.service.PermissionService;
import com.rbac.vo.permission.PermissionVO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 结构化权限点维护实现（A32-A35）。
 *
 * <p>写路径约定：A34 四段码 perm_key = appKey:moduleKey:resourceKey:actionCode 由服务端生成
 * （perm_name = 动作标签 + 资源名，source_row = NULL 表示业务自建权限），同元组/同键 409；
 * A35 platform:* 固定技术权限禁停。
 */
@Service
@ConditionalOnProperty(prefix = "rbac.service", name = "impl", havingValue = "struct", matchIfMissing = true)
public class StructPermissionService implements PermissionService {

    /** 小写码规则（与 APP_KEY_PATTERN 同形）：moduleKey/resourceKey/actionCode 共用。 */
    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9_]*$");
    /** platform 应用固定技术权限的键前缀。 */
    private static final String PLATFORM_PERM_KEY_PREFIX = "platform:";

    private final PermissionMapper permissionMapper;
    private final ApplicationMapper applicationMapper;
    private final SecurityStateMapper securityStateMapper;
    private final AuditService auditService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public StructPermissionService(PermissionMapper permissionMapper,
                                   ApplicationMapper applicationMapper,
                                   SecurityStateMapper securityStateMapper,
                                   AuditService auditService,
                                   PlatformTransactionManager transactionManager,
                                   Clock clock) {
        this.permissionMapper = permissionMapper;
        this.applicationMapper = applicationMapper;
        this.securityStateMapper = securityStateMapper;
        this.auditService = auditService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Override
    public PageResult<PermissionVO> list(PermissionQueryDTO query) {
        int page = query.pageNumber();
        int size = query.sizeNumber();
        List<SysPermission> records = permissionMapper.selectPage(parseLong(query.getApplicationId()),
                blankToNull(query.getModuleKey()), blankToNull(query.getKeyword()), query.getStatus(),
                (page - 1) * size, size);
        long total = permissionMapper.countPage(parseLong(query.getApplicationId()),
                blankToNull(query.getModuleKey()), blankToNull(query.getKeyword()), query.getStatus());
        return PageResult.of(records.stream().map(this::toVO).toList(), total, page, size);
    }

    @Override
    public PermissionVO get(String id) {
        return toVO(requirePermission(id));
    }

    // ==================================================================
    // A34 创建权限点：四段码服务端生成；sourceRow = NULL；同元组 409
    // ==================================================================

    @Override
    public PermissionVO create(PermissionCreateDTO dto) {
        if (dto == null) {
            throw invalidArgument("请求正文不能为空");
        }
        Long applicationId = parseId(dto.getApplicationId(), "applicationId");
        String moduleKey = requireCode(dto.getModuleKey(), "moduleKey", 32);
        String moduleName = requireText(dto.getModuleName(), "moduleName", 1, 100);
        String resourceKey = requireCode(dto.getResourceKey(), "resourceKey", 48);
        String resourceName = requireText(dto.getResourceName(), "resourceName", 1, 100);
        String resourceDescription = requireOptionalText(dto.getResourceDescription(), "resourceDescription", 500);
        String actionCode = requireCode(dto.getActionCode(), "actionCode", 32);
        ActionCode action = ActionCode.of(actionCode);
        if (action == null) {
            throw invalidArgument("actionCode 未在动作字典登记", FieldError.format("actionCode"));
        }

        SysPermission permission = new SysPermission();
        permission.setApplicationId(applicationId);
        permission.setModuleKey(moduleKey);
        permission.setModuleName(moduleName);
        permission.setResourceKey(resourceKey);
        permission.setResourceName(resourceName);
        permission.setResourceDescription(resourceDescription);
        permission.setActionCode(actionCode);
        permission.setPermKey(null);
        permission.setPermName(null);
        permission.setSourceRow(null);
        permission.setStatus(1);
        permission.setCreateTime(utcNow());
        permission.setUpdateTime(permission.getCreateTime());
        permission.setVersion(0L);

        try {
            transactionTemplate.executeWithoutResult(tx -> {
                securityStateMapper.lockStateRow();
                SysApplication app = applicationMapper.selectById(applicationId);
                if (app == null) {
                    throw invalidArgument(FieldError.referenceInvalid("applicationId"));
                }
                // 四段码服务端生成：appKey:moduleKey:resourceKey:actionCode
                permission.setPermKey(app.getAppKey() + ":" + moduleKey + ":" + resourceKey + ":" + actionCode);
                permission.setPermName(action.getLabel() + resourceName);
                if (permissionMapper.selectByPermKey(permission.getPermKey()) != null
                        || permissionMapper.selectByTuple(applicationId, moduleKey, resourceKey, actionCode) != null) {
                    throw new BusinessException(ResultCode.PERMISSION_KEY_EXISTS, 409, "权限键或四元组已存在");
                }
                permissionMapper.insert(permission);
                auditService.writeInTransaction(successEvent(EventType.PERMISSION_CREATE, "PERMISSION", permission.getId()));
            });
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.PERMISSION_KEY_EXISTS, 409, "权限键或四元组已存在");
        }
        return toVO(permissionMapper.selectById(permission.getId()));
    }

    // ==================================================================
    // A35 权限启停：platform:* 固定技术权限禁停
    // ==================================================================

    @Override
    public PermissionVO changeStatus(String id, Long expectedVersion, BinaryStatusDTO dto) {
        Long permissionId = requireExistingId(id);
        SysPermission permission = requirePermission(id);
        int target = normalizeBinaryStatus(dto == null ? null : dto.getStatus(), "status", false);
        if (target == 0 && permission.getPermKey() != null
                && permission.getPermKey().startsWith(PLATFORM_PERM_KEY_PREFIX)) {
            throw BusinessException.reservedResource();
        }
        requireVersion(expectedVersion);

        transactionTemplate.executeWithoutResult(tx -> {
            securityStateMapper.lockStateRow();
            requirePermissionWithVersion(permissionId, expectedVersion);
            requireUpdated(permissionMapper.updateStatus(permissionId, expectedVersion, target), permissionId);
            auditService.writeInTransaction(successEvent(EventType.PERMISSION_STATUS, "PERMISSION", permissionId));
        });
        return toVO(permissionMapper.selectById(permissionId));
    }

    // ---------- 内部 ----------

    private PermissionVO toVO(SysPermission p) {
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

    private SysPermission requirePermission(String id) {
        SysPermission p = null;
        try {
            p = permissionMapper.selectById(Long.parseLong(id.trim()));
        } catch (NumberFormatException e) {
            // 非法 ID 走 NOT_FOUND
        }
        if (p == null) {
            throw BusinessException.notFound();
        }
        return p;
    }

    private Long requireExistingId(String id) {
        Long permissionId = parseLong(id);
        if (permissionId == null || permissionMapper.selectById(permissionId) == null) {
            throw BusinessException.notFound();
        }
        return permissionId;
    }

    private SysPermission requirePermissionWithVersion(Long permissionId, Long expectedVersion) {
        SysPermission permission = permissionMapper.selectById(permissionId);
        if (permission == null) {
            throw BusinessException.notFound();
        }
        if (!Objects.equals(permission.getVersion(), expectedVersion)) {
            throw BusinessException.versionConflict();
        }
        return permission;
    }

    private void requireUpdated(int rows, Long permissionId) {
        if (rows == 1) {
            return;
        }
        throw permissionMapper.selectById(permissionId) == null
                ? BusinessException.notFound() : BusinessException.versionConflict();
    }

    /** 成功审计事件：主体取当前请求身份。 */
    private static AuditEvent successEvent(EventType type, String targetType, Long targetId) {
        CurrentUser current = CurrentUserContext.get();
        return new AuditEvent(type, targetType, String.valueOf(targetId),
                current == null ? null : current.getUserId(),
                current == null ? null : current.getUsername(),
                true, "OK");
    }

    /** 小写码字段：必填、≤ max、匹配 ^[a-z][a-z0-9_]*$。 */
    private static String requireCode(String value, String field, int max) {
        String code = requireText(value, field, 1, max);
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw invalidArgument(FieldError.format(field));
        }
        return code;
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

    private static Long parseId(String value, String field) {
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

    /** 可选文本：null 合法；空白归一为 null；非空时 trim 且 ≤ max。 */
    private static String requireOptionalText(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
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

    private LocalDateTime utcNow() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
