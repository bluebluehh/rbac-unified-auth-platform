package com.rbac.vo.permission;

import java.time.LocalDateTime;

/**
 * 权限输出（A33-A35）+ 公共三元组 VO_C。resourceDescription、sourceRow 允许 null。
 */
public class PermissionVO {

    /** 权限 ID（十进制字符串）。 */
    private String id;

    /** 所属应用 ID。 */
    private String applicationId;

    /** 模块 key。 */
    private String moduleKey;

    /** 模块名称。 */
    private String moduleName;

    /** 资源 key。 */
    private String resourceKey;

    /** 资源名称。 */
    private String resourceName;

    /** 资源描述，允许 null。 */
    private String resourceDescription;

    /** 操作码。 */
    private String actionCode;

    /** 权限展示名（服务端按模块/资源/操作拼装）。 */
    private String permName;

    /** 权限唯一 key（服务端生成，格式 module:resource:action）。 */
    private String permKey;

    /** 同步来源行号，允许 null（手工创建为 null）。 */
    private Integer sourceRow;

    /** 状态 0/1。 */
    private Integer status;

    /** 数据版本号（十进制字符串，乐观锁）。 */
    private String version;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 最后更新时间。 */
    private LocalDateTime updateTime;

    public PermissionVO() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
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
