package com.rbac.security;

/**
 * 有效权限联查结果行（Mapper 投影）：权限 id、四段权限 key、所属应用 id。
 */
public class EffectivePermission {

    private Long id;
    private String permKey;
    private Long applicationId;

    public EffectivePermission() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPermKey() {
        return permKey;
    }

    public void setPermKey(String permKey) {
        this.permKey = permKey;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }
}
