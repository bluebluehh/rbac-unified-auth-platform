package com.rbac.entity;

/**
 * 角色-权限关联实体，对应表 sys_role_permission。
 */
public class SysRolePermission {

    /** 主键 */
    private Long id;
    /** 角色 ID */
    private Long roleId;
    /** 权限 ID */
    private Long permissionId;

    public SysRolePermission() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public Long getPermissionId() {
        return permissionId;
    }

    public void setPermissionId(Long permissionId) {
        this.permissionId = permissionId;
    }
}
