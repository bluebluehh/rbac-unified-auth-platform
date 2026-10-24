package com.rbac.vo.application;

import java.time.LocalDateTime;

/**
 * 应用输出（A28-A31）+ 公共三元组 VO_C。
 */
public class ApplicationVO {

    /** 应用 ID（十进制字符串）。 */
    private String id;

    /** 应用名称。 */
    private String appName;

    /** 应用 key，全局唯一。 */
    private String appKey;

    /** 状态 0/1。 */
    private Integer status;

    /** 数据版本号（十进制字符串，乐观锁）。 */
    private String version;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 最后更新时间。 */
    private LocalDateTime updateTime;

    public ApplicationVO() {
    }

    /** 全参构造（A05 输出顺序）。 */
    public ApplicationVO(String id, String appName, String appKey, Integer status, String version,
                         LocalDateTime createTime, LocalDateTime updateTime) {
        this.id = id;
        this.appName = appName;
        this.appKey = appKey;
        this.status = status;
        this.version = version;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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
