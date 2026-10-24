package com.rbac.dto.role;

/**
 * 创建角色输入（A22）：roleKey 须匹配 [A-Z][A-Z0-9_]*；
 * description 可空（≤300）；status 可空，缺省 1 由 Service 处理（JSON 仅接受数字 0/1）。
 */
public class RoleCreateDTO {

    /** 角色名称，1-80 字符。 */
    private String roleName;

    /** 角色 key，1-50 字符，格式 [A-Z][A-Z0-9_]*，全局唯一。 */
    private String roleKey;

    /** 描述，≤300 字符，可空。 */
    private String description;

    /** 状态 0/1，可空，缺省 1。 */
    private Integer status;

    public RoleCreateDTO() {
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public String getRoleKey() {
        return roleKey;
    }

    public void setRoleKey(String roleKey) {
        this.roleKey = roleKey;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
