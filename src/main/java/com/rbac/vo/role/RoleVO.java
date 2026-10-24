package com.rbac.vo.role;

import java.time.LocalDateTime;

/**
 * 角色输出（A21-A24）+ 公共三元组 VO_C。description 允许 null。
 */
public class RoleVO {

    /** 角色 ID（十进制字符串）。 */
    private String id;

    /** 角色名称。 */
    private String roleName;

    /** 角色 key，全局唯一。 */
    private String roleKey;

    /** 描述，允许 null。 */
    private String description;

    /** 状态 0/1。 */
    private Integer status;

    /** 数据版本号（十进制字符串，乐观锁）。 */
    private String version;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 最后更新时间。 */
    private LocalDateTime updateTime;

    public RoleVO() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
