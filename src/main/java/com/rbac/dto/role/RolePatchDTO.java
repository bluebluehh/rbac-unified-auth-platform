package com.rbac.dto.role;

/**
 * 局部更新角色输入（A23）：roleKey 不可变；仅更新显式提供的字段；
 * description 显式 null 表示清除描述。
 */
public class RolePatchDTO {

    /** 角色名称，1-80 字符，可选。 */
    private String roleName;

    /** 描述，≤300 字符，可选；显式 null 表示清除。 */
    private String description;

    /** 标志：roleName 是否由调用方显式提供。 */
    private boolean roleNameProvided;

    /** 标志：description 是否由调用方显式提供。 */
    private boolean descriptionProvided;

    public RolePatchDTO() {
    }

    /**
     * 是否提供了至少一个待更新字段；全部未提供时 Service 应拒绝为空更新。
     */
    public boolean hasAnyField() {
        return roleNameProvided || descriptionProvided;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
        this.roleNameProvided = true;
    }

    public boolean isRoleNameProvided() {
        return roleNameProvided;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        this.descriptionProvided = true;
    }

    public boolean isDescriptionProvided() {
        return descriptionProvided;
    }
}
