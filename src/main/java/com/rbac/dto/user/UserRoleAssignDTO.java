package com.rbac.dto.user;

import java.util.List;

/**
 * 用户角色分配输入（A14）：重复 ID 拒绝，最多 100 个（ROLE_SELECTION_MAX）；
 * 空列表表示清空全部角色。
 */
public class UserRoleAssignDTO {

    /** 角色 ID 列表（十进制字符串），[] 表示清空。 */
    private List<String> roleIds;

    public UserRoleAssignDTO() {
    }

    public List<String> getRoleIds() {
        return roleIds;
    }

    public void setRoleIds(List<String> roleIds) {
        this.roleIds = roleIds;
    }
}
