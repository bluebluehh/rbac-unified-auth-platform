package com.rbac.entity;

/**
 * 用户-角色关联实体，对应表 sys_user_role。
 */
public class SysUserRole {

    /** 主键 */
    private Long id;
    /** 用户 ID */
    private Long userId;
    /** 角色 ID */
    private Long roleId;

    public SysUserRole() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }
}
