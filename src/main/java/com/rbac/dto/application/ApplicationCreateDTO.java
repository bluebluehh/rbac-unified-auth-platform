package com.rbac.dto.application;

/**
 * 创建应用输入（A29）：appKey 须匹配 [a-z][a-z0-9_]*；status 可空，缺省 1。
 */
public class ApplicationCreateDTO {

    /** 应用名称，1-100 字符。 */
    private String appName;

    /** 应用 key，1-32 字符，格式 [a-z][a-z0-9_]*，全局唯一。 */
    private String appKey;

    /** 状态 0/1，可空，缺省 1。 */
    private Integer status;

    public ApplicationCreateDTO() {
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
