package com.rbac.service.struct;

import com.rbac.common.BusinessException;
import com.rbac.common.FieldError;
import com.rbac.common.ResultCode;
import com.rbac.common.constants.PermissionKeyConstants;
import com.rbac.common.enums.EventType;
import com.rbac.dto.common.BinaryStatusDTO;
import com.rbac.dto.dept.DeptCreateDTO;
import com.rbac.dto.dept.DeptPatchDTO;
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
import com.rbac.service.DeptService;
import com.rbac.vo.dept.DeptStatusVO;
import com.rbac.vo.dept.DeptTreeVO;
import com.rbac.vo.dept.DeptVO;
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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 结构化部门组织树实现（A15-A19）。
 *
 * <p>写路径约定：A17 父节点必须存在且启用、nodeKey 全局唯一、禁止创建 ROOT；
 * A18 只允许改名称与负责人（nodeKey/parentId/nodeType 天然不可变）；
 * A19 停用级联撤销子树受影响用户会话并做管理员检查（子树内有效管理员撤出后全局为零则 409），
 * 启用要求父链全部启用。
 */
@Service
@ConditionalOnProperty(prefix = "rbac.service", name = "impl", havingValue = "struct", matchIfMissing = true)
public class StructDeptService implements DeptService {

    private final DeptMapper deptMapper;
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final SessionMapper sessionMapper;
    private final SecurityStateMapper securityStateMapper;
    private final PermissionEvaluator permissionEvaluator;
    private final AuditService auditService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public StructDeptService(DeptMapper deptMapper,
                             UserMapper userMapper,
                             UserRoleMapper userRoleMapper,
                             RoleMapper roleMapper,
                             SessionMapper sessionMapper,
                             SecurityStateMapper securityStateMapper,
                             PermissionEvaluator permissionEvaluator,
                             AuditService auditService,
                             PlatformTransactionManager transactionManager,
                             Clock clock) {
        this.deptMapper = deptMapper;
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.sessionMapper = sessionMapper;
        this.securityStateMapper = securityStateMapper;
        this.permissionEvaluator = permissionEvaluator;
        this.auditService = auditService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<DeptTreeVO> tree(String rootId, Boolean includeDisabled) {
        boolean withDisabled = includeDisabled != null && includeDisabled;
        List<SysDept> all = deptMapper.selectAll();
        Map<Long, DeptTreeVO> nodeById = new HashMap<>();
        List<DeptTreeVO> eligible = new ArrayList<>();
        for (SysDept d : all) {
            if (!withDisabled && (d.getStatus() == null || d.getStatus() != 1)) {
                continue;
            }
            DeptTreeVO vo = new DeptTreeVO();
            copy(d, vo);
            nodeById.put(d.getId(), vo);
            eligible.add(vo);
        }
        List<DeptTreeVO> roots = new ArrayList<>();
        for (int i = 0; i < eligible.size(); i++) {
            DeptTreeVO vo = eligible.get(i);
            SysDept source = all.stream().filter(d -> d.getId().toString().equals(vo.getId())).findFirst().orElse(null);
            DeptTreeVO parent = source == null || source.getParentId() == null ? null : nodeById.get(source.getParentId());
            if (parent == null || parent == vo) {
                roots.add(vo);
            } else {
                parent.getChildren().add(vo);
            }
        }
        for (var count : userMapper.selectGradeCounts()) {
            DeptTreeVO department = nodeById.get(count.getDeptId());
            if (department != null) department.getDirectGradeCounts().merge(count.getGrade(), count.getUserCount(), Long::sum);
            DeptTreeVO company = nodeById.get(count.getCompanyId());
            if (company != null) company.getCompanyGradeCounts().merge(count.getGrade(), count.getUserCount(), Long::sum);
        }
        for (DeptTreeVO root : roots) aggregateGrades(root);
        if (rootId != null && !rootId.isBlank()) {
            Long root = parseLong(rootId);
            DeptTreeVO rootNode = root == null ? null : nodeById.get(root);
            return rootNode == null ? List.of() : List.of(rootNode);
        }
        return roots;
    }

    private void aggregateGrades(DeptTreeVO node) {
        node.getSubtreeGradeCounts().putAll(node.getDirectGradeCounts());
        for (DeptTreeVO child : node.getChildren()) {
            aggregateGrades(child);
            child.getSubtreeGradeCounts().forEach((grade, count) -> node.getSubtreeGradeCounts().merge(grade, count, Long::sum));
        }
    }

    @Override
    public DeptVO get(String id) {
        Long deptId = parseLong(id);
        if (deptId != null) {
            SysDept dept = deptMapper.selectById(deptId);
            if (dept != null) {
                DeptVO vo = new DeptVO();
                copy(dept, vo);
                return vo;
            }
        }
        throw BusinessException.notFound();
    }

    // ==================================================================
    // A17 创建节点：父节点须启用；nodeKey 唯一；不能创建 ROOT
    // ==================================================================

    @Override
    public DeptVO create(DeptCreateDTO dto) {
        if (dto == null) {
            throw invalidArgument("请求正文不能为空");
        }
        String deptName = requireText(dto.getDeptName(), "deptName", 1, 160);
        String nodeKey = requireText(dto.getNodeKey(), "nodeKey", 1, 160);
        Long parentId = parseId(dto.getParentId(), "parentId");
        if (parentId == null) {
            throw invalidArgument(FieldError.required("parentId"));
        }
        String nodeType = dto.getNodeType() == null ? null : dto.getNodeType().trim();
        if (nodeType == null
                || (!"COMPANY".equals(nodeType) && !"DEPARTMENT".equals(nodeType)
                && !"PROJECT_DEPT".equals(nodeType))) {
            throw invalidArgument("nodeType 仅允许 COMPANY / DEPARTMENT / PROJECT_DEPT",
                    FieldError.format("nodeType"));
        }
        Long managerUserId = dto.getManagerUserId() == null || dto.getManagerUserId().isBlank()
                ? null : parseId(dto.getManagerUserId(), "managerUserId");
        int status = normalizeBinaryStatus(dto.getStatus(), "status", true);

        SysDept dept = new SysDept();
        dept.setDeptName(deptName);
        dept.setNodeKey(nodeKey);
        dept.setParentId(parentId);
        dept.setNodeType(nodeType);
        dept.setManagerUserId(managerUserId);
        dept.setStatus(status);
        dept.setCreateTime(utcNow());
        dept.setUpdateTime(dept.getCreateTime());
        dept.setVersion(0L);

        try {
            transactionTemplate.executeWithoutResult(tx -> {
                securityStateMapper.lockStateRow();
                SysDept parent = deptMapper.selectById(parentId);
                if (parent == null) {
                    throw invalidArgument(FieldError.referenceInvalid("parentId"));
                }
                if (!enabled(parent.getStatus())) {
                    throw BusinessException.stateConflict("父节点已停用，不能在其下创建节点");
                }
                if (managerUserId != null && userMapper.selectById(managerUserId) == null) {
                    throw invalidArgument(FieldError.referenceInvalid("managerUserId"));
                }
                if (deptMapper.selectByNodeKey(nodeKey) != null) {
                    throw new BusinessException(ResultCode.NODE_KEY_EXISTS, 409, "节点键已存在");
                }
                deptMapper.insert(dept);
                auditService.writeInTransaction(successEvent(EventType.DEPT_CREATE, "DEPT", dept.getId()));
            });
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.NODE_KEY_EXISTS, 409, "节点键已存在");
        }
        return toVO(deptMapper.selectById(dept.getId()));
    }

    // ==================================================================
    // A18 部分更新：不改父节点/nodeKey/nodeType；负责人显式 null 清除
    // ==================================================================

    @Override
    public DeptVO update(String id, Long expectedVersion, DeptPatchDTO dto) {
        Long deptId = requireExistingId(id);
        if (dto == null || !dto.hasAnyField()) {
            throw invalidArgument("至少提供一个待更新字段");
        }
        String deptName = dto.isDeptNameProvided() ? requireText(dto.getDeptName(), "deptName", 1, 160) : null;
        Long managerUserId = null;
        boolean clearManager = false;
        if (dto.isManagerUserIdProvided()) {
            if (dto.getManagerUserId() == null) {
                clearManager = true;
            } else {
                managerUserId = parseId(dto.getManagerUserId(), "managerUserId");
            }
        }
        requireVersion(expectedVersion);

        final Long managerUserIdFinal = managerUserId;
        final boolean clearManagerFinal = clearManager;
        transactionTemplate.executeWithoutResult(tx -> {
            securityStateMapper.lockStateRow();
            requireDeptWithVersion(deptId, expectedVersion);
            if (managerUserIdFinal != null && userMapper.selectById(managerUserIdFinal) == null) {
                throw invalidArgument(FieldError.referenceInvalid("managerUserId"));
            }
            int rows = deptMapper.updateProfile(deptId, expectedVersion, deptName, managerUserIdFinal, clearManagerFinal);
            requireUpdated(rows, deptId);
            auditService.writeInTransaction(successEvent(EventType.DEPT_UPDATE, "DEPT", deptId));
        });
        return toVO(deptMapper.selectById(deptId));
    }

    // ==================================================================
    // A19 启停：停用撤销子树会话并查管理员；启用要求父链启用
    // ==================================================================

    @Override
    public DeptStatusVO changeStatus(String id, Long expectedVersion, BinaryStatusDTO dto) {
        Long deptId = requireExistingId(id);
        int target = normalizeBinaryStatus(dto == null ? null : dto.getStatus(), "status", false);
        requireVersion(expectedVersion);

        DeptStatusVO result = new DeptStatusVO();
        transactionTemplate.executeWithoutResult(tx -> {
            securityStateMapper.lockStateRow();
            SysDept dept = requireDeptWithVersion(deptId, expectedVersion);

            if (target == 0) {
                List<Long> subtree = subtreeIds(dept.getId());
                List<SysUser> affected = new ArrayList<>(userMapper.selectByDeptIds(subtree));
                checkLastAdminBeforeSubtreeDisable(subtree, affected);
                LocalDateTime now = utcNow();
                Set<Long> notifiedUserIds = new LinkedHashSet<>();
                for (SysUser user : affected) {
                    if (notifiedUserIds.add(user.getId())) {
                        sessionMapper.revokeAllByUserId(user.getId(), now);
                    }
                }
                requireUpdated(deptMapper.updateStatus(deptId, expectedVersion, 0), deptId);
                result.setAffectedUserCount(notifiedUserIds.size());
            } else {
                if (dept.getParentId() != null) {
                    List<SysDept> parentChain = deptMapper.selectSelfAndAncestors(dept.getParentId());
                    if (parentChain == null || parentChain.isEmpty()
                            || parentChain.stream().anyMatch(node -> !enabled(node.getStatus()))) {
                        throw BusinessException.stateConflict("父组织链存在停用节点，不能启用该节点");
                    }
                }
                requireUpdated(deptMapper.updateStatus(deptId, expectedVersion, 1), deptId);
                result.setAffectedUserCount(0L);
            }
            auditService.writeInTransaction(successEvent(EventType.DEPT_STATUS, "DEPT", deptId));
        });
        DeptVO item = toVO(deptMapper.selectById(deptId));
        result.setItem(item);
        return result;
    }

    /** 子树停用管理员检查：撤出子树管理员后全局有效管理员不得为零。 */
    private void checkLastAdminBeforeSubtreeDisable(List<Long> subtree, List<SysUser> affectedUsers) {
        SysRole adminRole = roleMapper.selectByRoleKey(PermissionKeyConstants.RESERVED_ROLE_KEY);
        if (adminRole == null || !enabled(adminRole.getStatus())) {
            return;
        }
        // affectedUsers 已是子树受影响用户（dept_id 或 company_id 命中）
        long adminsInside = affectedUsers.stream()
                .filter(this::isEffectiveAdmin)
                .count();
        if (adminsInside == 0) {
            return;
        }
        long adminsTotal = userMapper.selectByRoleKey(PermissionKeyConstants.RESERVED_ROLE_KEY).stream()
                .filter(this::isEffectiveAdmin)
                .count();
        if (adminsTotal - adminsInside == 0) {
            throw BusinessException.lastAdminRequired();
        }
    }

    /** 有效管理员：持有启用 PLATFORM_ADMIN 角色且账号可用。 */
    private boolean isEffectiveAdmin(SysUser user) {
        return permissionEvaluator.isAccountUsable(user)
                && userRoleMapper.selectRoleIdsByUserId(user.getId())
                .contains(roleMapper.selectByRoleKey(PermissionKeyConstants.RESERVED_ROLE_KEY).getId());
    }

    /** 自身 + 全部子孙节点 ID（数据量在骨架阶段规模内，内存组装）。 */
    private List<Long> subtreeIds(Long rootId) {
        List<SysDept> all = deptMapper.selectAll();
        Map<Long, List<Long>> childrenByParent = new HashMap<>();
        for (SysDept dept : all) {
            if (dept.getParentId() != null) {
                childrenByParent.computeIfAbsent(dept.getParentId(), k -> new ArrayList<>()).add(dept.getId());
            }
        }
        List<Long> subtree = new ArrayList<>();
        subtree.add(rootId);
        for (int i = 0; i < subtree.size(); i++) {
            List<Long> children = childrenByParent.get(subtree.get(i));
            if (children != null) {
                subtree.addAll(children);
            }
        }
        return subtree;
    }

    // ---------- 内部 ----------

    private SysDept requireDeptWithVersion(Long deptId, Long expectedVersion) {
        SysDept dept = deptMapper.selectById(deptId);
        if (dept == null) {
            throw BusinessException.notFound();
        }
        if (!Objects.equals(dept.getVersion(), expectedVersion)) {
            throw BusinessException.versionConflict();
        }
        return dept;
    }

    private void requireUpdated(int rows, Long deptId) {
        if (rows == 1) {
            return;
        }
        throw deptMapper.selectById(deptId) == null ? BusinessException.notFound() : BusinessException.versionConflict();
    }

    /** 成功审计事件：主体取当前请求身份。 */
    private static AuditEvent successEvent(EventType type, String targetType, Long targetId) {
        CurrentUser current = CurrentUserContext.get();
        return new AuditEvent(type, targetType, String.valueOf(targetId),
                current == null ? null : current.getUserId(),
                current == null ? null : current.getUsername(),
                true, "OK");
    }

    private Long requireExistingId(String id) {
        Long deptId = parseLong(id);
        if (deptId == null || deptMapper.selectById(deptId) == null) {
            throw BusinessException.notFound();
        }
        return deptId;
    }

    /** 二值启停归一：allowNullDefault 为 true 时 null 缺省 1（创建场景）。 */
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

    private void copy(SysDept d, DeptVO vo) {
        vo.setId(String.valueOf(d.getId()));
        vo.setDeptName(d.getDeptName());
        vo.setNodeKey(d.getNodeKey());
        vo.setParentId(d.getParentId() == null ? null : String.valueOf(d.getParentId()));
        vo.setNodeType(d.getNodeType());
        vo.setManagerUserId(d.getManagerUserId() == null ? null : String.valueOf(d.getManagerUserId()));
        vo.setStatus(d.getStatus());
        vo.setVersion(String.valueOf(d.getVersion()));
        vo.setCreateTime(d.getCreateTime());
        vo.setUpdateTime(d.getUpdateTime());
    }

    private DeptVO toVO(SysDept d) {
        DeptVO vo = new DeptVO();
        copy(d, vo);
        return vo;
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

    private static boolean enabled(Object rawStatus) {
        return rawStatus instanceof Number number && number.intValue() == 1;
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
