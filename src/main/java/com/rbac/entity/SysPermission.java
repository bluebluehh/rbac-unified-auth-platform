package com.rbac.entity;

import java.time.LocalDateTime;

/**
 * 权限实体，对应表 sys_permission（应用-模块-资源-动作 四元组唯一定位，带乐观锁版本号）。
 */
public class SysPermission {

    /** 主键 */
    private Long id;
    /** 所属应用 ID */
    private Long applicationId;
    /** 模块键 */
    private String moduleKey;
    /** 模块名称 */
    private String moduleName;
    /** 资源键 */
    private String resourceKey;
    /** 资源名称 */
    private String resourceName;
    /** 资源描述 */
    private String resourceDescription;
    /** 动作码 */
    private String actionCode;
    /** 权限名称 */
    private String permName;
    /** 权限唯一键 */
    private String permKey;
    /** 权限来源行标识（如 seed/导入来源） */
    private Integer sourceRow;
    /** 状态：1 启用 / 0 停用 */
    private Integer status;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
    /** 乐观锁版本号 */
    private Long version;

    public SysPermission() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public String getModuleKey() {
        return moduleKey;
    }

    public void setModuleKey(String moduleKey) {
        this.moduleKey = moduleKey;
    }

    public String getModuleName() {
        return moduleName;
    }

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    public String getResourceKey() {
        return resourceKey;
    }

    public void setResourceKey(String resourceKey) {
        this.resourceKey = resourceKey;
    }

    public String getResourceName() {
        return resourceName;
    }

    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }

    public String getResourceDescription() {
        return resourceDescription;
    }

    public void setResourceDescription(String resourceDescription) {
        this.resourceDescription = resourceDescription;
    }

    public String getActionCode() {
        return actionCode;
    }

    public void setActionCode(String actionCode) {
        this.actionCode = actionCode;
    }

    public String getPermName() {
        return permName;
    }

    public void setPermName(String permName) {
        this.permName = permName;
    }

    public String getPermKey() {
        return permKey;
    }

    public void setPermKey(String permKey) {
        this.permKey = permKey;
    }

    public Integer getSourceRow() {
        return sourceRow;
    }

    public void setSourceRow(Integer sourceRow) {
        this.sourceRow = sourceRow;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
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

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
