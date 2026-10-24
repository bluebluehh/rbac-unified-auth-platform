package com.rbac.security;

import com.rbac.common.enums.UserStatus;
import com.rbac.entity.SysDept;
import com.rbac.entity.SysPermission;
import com.rbac.entity.SysUser;
import com.rbac.mapper.DeptMapper;
import com.rbac.mapper.PermissionMapper;
import com.rbac.mapper.SecurityStateMapper;
import com.rbac.mapper.UserMapper;
import com.rbac.mapper.UserRoleMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * RBAC0 有效权限计算（设计文档 §3.2、§5.5）。
 *
 * <pre>
 * 有效权限(u) = DISTINCT union(有效直接角色 r 的启用权限)
 *
 * 前置条件（任一不满足即为空集）：
 *   user.status        = ENABLED
 *   dept.status        = 1（company_id 与 dept_id 对应的全部祖先）
 *   role.status        = 1
 *   permission.status  = 1
 *   application.status = 1
 * </pre>
 *
 * RBAC0 不沿角色继承递归，也不按部门过滤人员数据；部门只用于组织归属与启用状态校验。
 * 权限每次请求从数据库重新计算，不做缓存（§2.1、§3.2）。
 *
 * 实现刻意分成两段：
 *   1. {@link #evaluate(long)} 负责取数与状态判定；
 *   2. 记录类型 {@link EffectivePermissions} 上的方法负责纯计算（去重、排序、按应用过滤）。
 * 第二阶段的 func 实现改写的是第 2 段，OOP 实现会把第 2 段搬进领域对象。
 */
@Service
public class PermissionEvaluator {

    private final UserMapper userMapper;
    private final DeptMapper deptMapper;
    private final UserRoleMapper userRoleMapper;
    private final PermissionMapper permissionMapper;
    private final SecurityStateMapper securityStateMapper;

    public PermissionEvaluator(UserMapper userMapper,
                               DeptMapper deptMapper,
                               UserRoleMapper userRoleMapper,
                               PermissionMapper permissionMapper,
                               SecurityStateMapper securityStateMapper) {
        this.userMapper = userMapper;
        this.deptMapper = deptMapper;
        this.userRoleMapper = userRoleMapper;
        this.permissionMapper = permissionMapper;
        this.securityStateMapper = securityStateMapper;
    }

    /**
     * 计算指定用户的有效权限。
     * 账号不可用（停用、组织链停用、不存在）时返回空集，而不是抛异常——
     * 认证层已经负责拒绝这类请求，权限计算只回答"有哪些权限"。
     */
    public EffectivePermissions evaluate(long userId) {
        long policyVersion = securityStateMapper.selectPolicyVersion();

        SysUser user = userMapper.findById(userId);
        if (user == null || !isAccountUsable(user)) {
            return new EffectivePermissions(false, List.of(), List.of(), policyVersion);
        }

        List<Long> roleIds = userRoleMapper.selectEnabledRoleIdsByUserId(userId);
        if (roleIds == null || roleIds.isEmpty()) {
            return new EffectivePermissions(true, List.of(), List.of(), policyVersion);
        }

        List<EffectivePermission> grants = new ArrayList<>();
        List<SysPermission> rows = permissionMapper.selectEffectiveByRoleIds(roleIds);
        if (rows != null) {
            for (SysPermission row : rows) {
                grants.add(new EffectivePermission(row.getApplicationId(), row.getPermKey()));
            }
        }
        return new EffectivePermissions(true, distinct(grants), List.copyOf(roleIds), policyVersion);
    }

    /**
     * 账号可用性：状态 ENABLED，且 company_id 与 dept_id 的祖先链全部启用。
     * 待激活账号不参与业务权限计算（§5.2：受限会话不计算、不开放管理权限）。
     */
    public boolean isAccountUsable(SysUser user) {
        if (user == null || !statusIs(user.getStatus(), UserStatus.ENABLED)) {
            return false;
        }
        return isOrganizationChainEnabled(user);
    }

    /** 组织链启用校验：用户所属公司与直接部门各自的祖先链都必须存在且启用。 */
    public boolean isOrganizationChainEnabled(SysUser user) {
        List<SysDept> chain = new ArrayList<>();
        appendChain(chain, deptMapper.selectSelfAndAncestors(user.getDeptId()));
        appendChain(chain, deptMapper.selectSelfAndAncestors(user.getCompanyId()));
        return isChainEnabled(chain);
    }

    // ------------------------------------------------------------------
    // 纯计算部分（无 I/O，可直接单测；第二阶段 func 改写的落点）
    // ------------------------------------------------------------------

    /** 祖先链非空且全部启用才算通过；空链视为不通过，避免查不到组织时误放行。 */
    public static boolean isChainEnabled(List<SysDept> chain) {
        if (chain == null || chain.isEmpty()) {
            return false;
        }
        for (SysDept node : chain) {
            if (!enabledFlag(node.getStatus())) {
                return false;
            }
        }
        return true;
    }

    /** 按 (应用, 权限码) 去重，并按权限码稳定排序，保证同一份数据每次输出一致。 */
    public static List<EffectivePermission> distinct(List<EffectivePermission> grants) {
        if (grants == null || grants.isEmpty()) {
            return List.of();
        }
        Set<EffectivePermission> unique = new LinkedHashSet<>(grants);
        List<EffectivePermission> sorted = new ArrayList<>(unique);
        sorted.sort(Comparator.comparing(EffectivePermission::permissionKey)
                .thenComparingLong(EffectivePermission::applicationId));
        return List.copyOf(sorted);
    }

    /** 一条有效授权：它属于哪个应用、权限码是什么。 */
    public record EffectivePermission(long applicationId, String permissionKey) {
        public EffectivePermission {
            Objects.requireNonNull(permissionKey, "permissionKey");
        }
    }

    /**
     * 一次权限计算的结果快照。对外只读，不缓存。
     *
     * @param accountUsable 账号是否具备业务访问资格
     * @param grants        去重后的有效授权
     * @param roleIds       当前启用的直接角色
     * @param policyVersion 安全配置版本
     */
    public record EffectivePermissions(boolean accountUsable,
                                       List<EffectivePermission> grants,
                                       List<Long> roleIds,
                                       long policyVersion) {

        /** 全部有效权限码，去重且升序。 */
        public List<String> permissionKeys() {
            Set<String> keys = new TreeSet<>();
            for (EffectivePermission grant : grants) {
                keys.add(grant.permissionKey());
            }
            return List.copyOf(keys);
        }

        /** 至少拥有一个有效权限点的应用，去重且升序（A05 的输入）。 */
        public List<Long> applicationIds() {
            Set<Long> ids = new TreeSet<>();
            for (EffectivePermission grant : grants) {
                ids.add(grant.applicationId());
            }
            return List.copyOf(ids);
        }

        /** 指定应用下的有效权限码（A06 的 applicationId 过滤）。 */
        public List<String> permissionKeysOf(long applicationId) {
            Set<String> keys = new TreeSet<>();
            for (EffectivePermission grant : grants) {
                if (grant.applicationId() == applicationId) {
                    keys.add(grant.permissionKey());
                }
            }
            return List.copyOf(keys);
        }

        public boolean has(String permissionKey) {
            for (EffectivePermission grant : grants) {
                if (grant.permissionKey().equals(permissionKey)) {
                    return true;
                }
            }
            return false;
        }
    }

    // ------------------------------------------------------------------

    private static void appendChain(List<SysDept> target, List<SysDept> chain) {
        if (chain == null) {
            return;
        }
        for (SysDept node : chain) {
            if (node != null && target.stream().noneMatch(existing -> sameId(existing, node))) {
                target.add(node);
            }
        }
    }

    private static boolean sameId(SysDept left, SysDept right) {
        return Objects.equals(left.getId(), right.getId());
    }

    /**
     * 比较账号状态。实体可能用 String 也可能用枚举承载状态，
     * 统一按枚举名比较，避免两边类型不一致时编译不过或静默失效。
     */
    static boolean statusIs(Object rawStatus, UserStatus expected) {
        return rawStatus != null && expected.name().equals(rawStatus.toString());
    }

    /** TINYINT 0/1 可能被映射成 Integer、Byte 或 Short，统一按数值判断。 */
    static boolean enabledFlag(Object rawStatus) {
        return rawStatus instanceof Number number && number.intValue() == 1;
    }
}
