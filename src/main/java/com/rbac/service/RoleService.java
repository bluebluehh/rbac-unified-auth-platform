package com.rbac.service;

import com.rbac.common.PageResult;
import com.rbac.dto.common.BinaryStatusDTO;
import com.rbac.dto.role.RoleCreateDTO;
import com.rbac.dto.role.RolePatchDTO;
import com.rbac.dto.role.RolePermissionAssignDTO;
import com.rbac.dto.role.RoleQueryDTO;
import com.rbac.vo.role.RolePermissionsVO;
import com.rbac.vo.role.RoleVO;

/**
 * 角色与权限分配业务契约（A20-A26）。
 */
public interface RoleService {

    PageResult<RoleVO> list(RoleQueryDTO query);

    RoleVO get(String id);

    /** A22 创建：初始无权限、不分配给创建者。 */
    RoleVO create(RoleCreateDTO dto);

    /** A23 部分更新：保留角色 PLATFORM_ADMIN 禁止变更。 */
    RoleVO update(String id, Long expectedVersion, RolePatchDTO dto);

    /** A24 启停：停用保留关系但不产生有效权限。 */
    RoleVO changeStatus(String id, Long expectedVersion, BinaryStatusDTO dto);

    /** A25 当前权限配置（含已绑定停用权限）。 */
    RolePermissionsVO getPermissions(String id);

    /** A26 完整替换权限集合；不能修改 PLATFORM_ADMIN；新增关联权限和应用须启用。 */
    RolePermissionsVO assignPermissions(String id, Long expectedVersion, RolePermissionAssignDTO dto);
}
