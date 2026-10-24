package com.rbac.dto.application;

/**
 * 局部更新应用输入（A30）：appKey 不可变，启停走专用状态接口；仅 appName 可更新。
 */
public class ApplicationPatchDTO {

    /** 应用名称，1-100 字符，可选。 */
    private String appName;

    /** 标志：appName 是否由调用方显式提供。 */
    private boolean appNameProvided;

    public ApplicationPatchDTO() {
    }

    /**
     * 是否提供了至少一个待更新字段；全部未提供时 Service 应拒绝为空更新。
     */
    public boolean hasAnyField() {
        return appNameProvided;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
        this.appNameProvided = true;
    }

    public boolean isAppNameProvided() {
        return appNameProvided;
    }
}
