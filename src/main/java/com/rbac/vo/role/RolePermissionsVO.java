package com.rbac.vo.role;

import com.rbac.vo.permission.PermissionVO;

import java.util.List;

/**
 * 角色权限绑定输出（A25/A26）：含已绑定停用权限；permissions 与 permissionIds 一一对应。
 */
public class RolePermissionsVO {

    /** 角色 ID（十进制字符串）。 */
    private String roleId;

    /** 权限 ID 列表。 */
    private List<String> permissionIds;

    /** 权限明细列表，与 permissionIds 顺序对应。 */
    private List<PermissionVO> permissions;

    /** 绑定数据版本号（十进制字符串，乐观锁）。 */
    private String version;

    public RolePermissionsVO() {
    }

    public String getRoleId() {
        return roleId;
    }

    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }

    public List<String> getPermissionIds() {
        return permissionIds;
    }

    public void setPermissionIds(List<String> permissionIds) {
        this.permissionIds = permissionIds;
    }

    public List<PermissionVO> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<PermissionVO> permissions) {
        this.permissions = permissions;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
