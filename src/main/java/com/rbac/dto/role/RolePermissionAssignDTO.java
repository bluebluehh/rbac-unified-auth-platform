package com.rbac.dto.role;

import java.util.List;

/**
 * 角色权限绑定输入（A26）：重复 ID 拒绝，最多 5000 个（PERMISSION_SELECTION_MAX）；
 * 空列表表示清空全部绑定。
 */
public class RolePermissionAssignDTO {

    /** 权限 ID 列表（十进制字符串），[] 表示清空。 */
    private List<String> permissionIds;

    public RolePermissionAssignDTO() {
    }

    public List<String> getPermissionIds() {
        return permissionIds;
    }

    public void setPermissionIds(List<String> permissionIds) {
        this.permissionIds = permissionIds;
    }
}
