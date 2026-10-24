package com.rbac.dto.permission;

import com.rbac.dto.common.PageQuery;

/**
 * 权限分页查询输入（A32）：过滤条件均可选。
 */
public class PermissionQueryDTO extends PageQuery {

    /** 按所属应用 ID 过滤，可空。 */
    private String applicationId;

    /** 按功能板块编码（module_key）过滤，可空。 */
    private String moduleKey;

    /** 关键字，模糊匹配权限名称/key，可空。 */
    private String keyword;

    /** 按状态过滤（0/1），可空。 */
    private Integer status;

    public PermissionQueryDTO() {
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

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
