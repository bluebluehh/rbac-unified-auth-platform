package com.rbac.vo.user;

import com.rbac.vo.role.RoleVO;

import java.util.List;

/**
 * 用户角色关系输出（A13/A14）：含停用角色以供清理；roles 与 roleIds 一一对应。
 */
public class UserRolesVO {

    /** 用户 ID（十进制字符串）。 */
    private String userId;

    /** 角色 ID 列表。 */
    private List<String> roleIds;

    /** 角色明细列表，与 roleIds 顺序对应。 */
    private List<RoleVO> roles;

    /** 关系数据版本号（十进制字符串，乐观锁）。 */
    private String version;

    public UserRolesVO() {
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public List<String> getRoleIds() {
        return roleIds;
    }

    public void setRoleIds(List<String> roleIds) {
        this.roleIds = roleIds;
    }

    public List<RoleVO> getRoles() {
        return roles;
    }

    public void setRoles(List<RoleVO> roles) {
        this.roles = roles;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
