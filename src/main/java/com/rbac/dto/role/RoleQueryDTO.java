package com.rbac.dto.role;

import com.rbac.dto.common.PageQuery;

/**
 * 角色分页查询输入（A20）：过滤条件均可选。
 */
public class RoleQueryDTO extends PageQuery {

    /** 关键字，模糊匹配角色名称/key，可空。 */
    private String keyword;

    /** 按状态过滤（0/1），可空。 */
    private Integer status;

    public RoleQueryDTO() {
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
