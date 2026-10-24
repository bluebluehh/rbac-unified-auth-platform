package com.rbac.dto.permission;

/**
 * 创建权限输入（A34）：三个编码遵守小写码规则，actionCode 须在字典登记；
 * 不接收 sourceRow/permKey/permName（由服务端生成）；
 * resourceDescription 可空。
 */
public class PermissionCreateDTO {

    /** 所属应用 ID（十进制字符串）。 */
    private String applicationId;

    /** 模块 key，≤32 字符，小写码规则。 */
    private String moduleKey;

    /** 模块名称，≤100 字符。 */
    private String moduleName;

    /** 资源 key，≤48 字符，小写码规则。 */
    private String resourceKey;

    /** 资源名称，≤100 字符。 */
    private String resourceName;

    /** 资源描述，≤500 字符，可空。 */
    private String resourceDescription;

    /** 操作码，≤32 字符，须在字典登记。 */
    private String actionCode;

    public PermissionCreateDTO() {
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
}
