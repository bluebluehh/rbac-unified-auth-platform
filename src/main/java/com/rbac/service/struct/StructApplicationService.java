package com.rbac.service.struct;

import com.rbac.common.BusinessException;
import com.rbac.common.FieldError;
import com.rbac.common.PageResult;
import com.rbac.common.ResultCode;
import com.rbac.common.constants.PermissionKeyConstants;
import com.rbac.common.constants.SystemConstants;
import com.rbac.common.enums.EventType;
import com.rbac.dto.application.ApplicationCreateDTO;
import com.rbac.dto.application.ApplicationPatchDTO;
import com.rbac.dto.application.ApplicationQueryDTO;
import com.rbac.dto.common.BinaryStatusDTO;
import com.rbac.entity.SysApplication;
import com.rbac.mapper.ApplicationMapper;
import com.rbac.mapper.SecurityStateMapper;
import com.rbac.security.CurrentUser;
import com.rbac.security.CurrentUserContext;
import com.rbac.service.AuditService;
import com.rbac.service.AuditService.AuditEvent;
import com.rbac.service.ApplicationService;
import com.rbac.vo.application.ApplicationVO;
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
 * 结构化应用登记实现（A27-A31）。
 *
 * <p>写路径约定：A29 appKey 全局唯一（保留键 platform 已被种子数据占用，天然冲突 409）、
 * 创建不自动授权限；A30 appKey 不可变（DTO 不携带）；A31 保留应用禁停，
 * 停用后下属权限因有效权限计算的应用状态过滤而自动失效。
 */
@Service
@ConditionalOnProperty(prefix = "rbac.service", name = "impl", havingValue = "struct", matchIfMissing = true)
public class StructApplicationService implements ApplicationService {

    private static final Pattern APP_KEY_PATTERN = Pattern.compile(SystemConstants.APP_KEY_PATTERN);

    private final ApplicationMapper applicationMapper;
    private final SecurityStateMapper securityStateMapper;
    private final AuditService auditService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public StructApplicationService(ApplicationMapper applicationMapper,
                                    SecurityStateMapper securityStateMapper,
                                    AuditService auditService,
                                    PlatformTransactionManager transactionManager,
                                    Clock clock) {
        this.applicationMapper = applicationMapper;
        this.securityStateMapper = securityStateMapper;
        this.auditService = auditService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Override
    public PageResult<ApplicationVO> list(ApplicationQueryDTO query) {
        int page = query.pageNumber();
        int size = query.sizeNumber();
        List<SysApplication> records = applicationMapper.selectPage(blankToNull(query.getKeyword()),
                query.getStatus(), (page - 1) * size, size);
        long total = applicationMapper.countPage(blankToNull(query.getKeyword()), query.getStatus());
        return PageResult.of(records.stream().map(this::toVO).toList(), total, page, size);
    }

    @Override
    public ApplicationVO get(String id) {
        return toVO(requireApp(id));
    }

    // ==================================================================
    // A29 创建应用：appKey 唯一；不自动授权限
    // ==================================================================

    @Override
    public ApplicationVO create(ApplicationCreateDTO dto) {
        if (dto == null) {
            throw invalidArgument("请求正文不能为空");
        }
        String appName = requireText(dto.getAppName(), "appName", 1, 100);
        String appKey = requireText(dto.getAppKey(), "appKey", 1, 32);
        if (!APP_KEY_PATTERN.matcher(appKey).matches()) {
            throw invalidArgument(FieldError.format("appKey"));
        }
        int status = normalizeBinaryStatus(dto.getStatus(), "status", true);

        SysApplication app = new SysApplication();
        app.setAppName(appName);
        app.setAppKey(appKey);
        app.setStatus(status);
        app.setCreateTime(utcNow());
        app.setUpdateTime(app.getCreateTime());
        app.setVersion(0L);

        try {
            transactionTemplate.executeWithoutResult(tx -> {
                securityStateMapper.lockStateRow();
                if (applicationMapper.selectByAppKey(appKey) != null) {
                    throw new BusinessException(ResultCode.APP_KEY_EXISTS, 409, "应用键已存在");
                }
                applicationMapper.insert(app);
                auditService.writeInTransaction(successEvent(EventType.APP_CREATE, "APP", app.getId()));
            });
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.APP_KEY_EXISTS, 409, "应用键已存在");
        }
        return toVO(applicationMapper.selectById(app.getId()));
    }

    // ==================================================================
    // A30 部分更新：appKey 不可变（DTO 不携带），仅 appName 可更新
    // ==================================================================

    @Override
    public ApplicationVO update(String id, Long expectedVersion, ApplicationPatchDTO dto) {
        Long appId = requireExistingId(id);
        if (dto == null || !dto.hasAnyField()) {
            throw invalidArgument("至少提供一个待更新字段");
        }
        String appName = dto.isAppNameProvided() ? requireText(dto.getAppName(), "appName", 1, 100) : null;
        requireVersion(expectedVersion);

        transactionTemplate.executeWithoutResult(tx -> {
            securityStateMapper.lockStateRow();
            requireAppWithVersion(appId, expectedVersion);
            requireUpdated(applicationMapper.updateName(appId, expectedVersion, appName), appId);
            auditService.writeInTransaction(successEvent(EventType.APP_UPDATE, "APP", appId));
        });
        return toVO(applicationMapper.selectById(appId));
    }

    // ==================================================================
    // A31 启停：platform 保留应用禁停；停用使下属权限自动失效
    // ==================================================================

    @Override
    public ApplicationVO changeStatus(String id, Long expectedVersion, BinaryStatusDTO dto) {
        Long appId = requireExistingId(id);
        SysApplication app = requireApp(id);
        int target = normalizeBinaryStatus(dto == null ? null : dto.getStatus(), "status", false);
        if (target == 0 && PermissionKeyConstants.RESERVED_APP_KEY.equals(app.getAppKey())) {
            throw BusinessException.reservedResource();
        }
        requireVersion(expectedVersion);

        transactionTemplate.executeWithoutResult(tx -> {
            securityStateMapper.lockStateRow();
            requireAppWithVersion(appId, expectedVersion);
            requireUpdated(applicationMapper.updateStatus(appId, expectedVersion, target), appId);
            auditService.writeInTransaction(successEvent(EventType.APP_STATUS, "APP", appId));
        });
        return toVO(applicationMapper.selectById(appId));
    }

    // ---------- 内部 ----------

    private ApplicationVO toVO(SysApplication app) {
        ApplicationVO vo = new ApplicationVO();
        vo.setId(String.valueOf(app.getId()));
        vo.setAppName(app.getAppName());
        vo.setAppKey(app.getAppKey());
        vo.setStatus(app.getStatus());
        vo.setVersion(String.valueOf(app.getVersion()));
        vo.setCreateTime(app.getCreateTime());
        vo.setUpdateTime(app.getUpdateTime());
        return vo;
    }

    private SysApplication requireApp(String id) {
        SysApplication app = null;
        try {
            app = applicationMapper.selectById(Long.parseLong(id.trim()));
        } catch (NumberFormatException e) {
            // 非法 ID 走 NOT_FOUND
        }
        if (app == null) {
            throw BusinessException.notFound();
        }
        return app;
    }

    private Long requireExistingId(String id) {
        Long appId = parseLong(id);
        if (appId == null || applicationMapper.selectById(appId) == null) {
            throw BusinessException.notFound();
        }
        return appId;
    }

    private SysApplication requireAppWithVersion(Long appId, Long expectedVersion) {
        SysApplication app = applicationMapper.selectById(appId);
        if (app == null) {
            throw BusinessException.notFound();
        }
        if (!Objects.equals(app.getVersion(), expectedVersion)) {
            throw BusinessException.versionConflict();
        }
        return app;
    }

    private void requireUpdated(int rows, Long appId) {
        if (rows == 1) {
            return;
        }
        throw applicationMapper.selectById(appId) == null
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

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static BusinessException invalidArgument(String message) {
        return new BusinessException(ResultCode.INVALID_ARGUMENT, 400, message);
    }

    private static BusinessException invalidArgument(FieldError error) {
        return new BusinessException(ResultCode.INVALID_ARGUMENT, 400, "请求参数不合法", List.of(error));
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
