package com.rbac.entity;

import java.time.LocalDateTime;

/**
 * 安全全局状态实体，对应表 sys_security_state（单行记录，policy_version 变更用于使权限缓存失效）。
 */
public class SysSecurityState {

    /** 主键，固定为 1（单行） */
    private Integer id;
    /** 安全策略版本号 */
    private Long policyVersion;
    /** 更新时间 */
    private LocalDateTime updateTime;

    public SysSecurityState() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Long getPolicyVersion() {
        return policyVersion;
    }

    public void setPolicyVersion(Long policyVersion) {
        this.policyVersion = policyVersion;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
