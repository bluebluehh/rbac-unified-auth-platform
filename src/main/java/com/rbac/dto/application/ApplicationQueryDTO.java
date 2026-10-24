package com.rbac.dto.application;

import com.rbac.dto.common.PageQuery;

/**
 * 应用分页查询输入（A27）：过滤条件均可选。
 */
public class ApplicationQueryDTO extends PageQuery {

    /** 关键字，模糊匹配应用名称/key，可空。 */
    private String keyword;

    /** 按状态过滤（0/1），可空。 */
    private Integer status;

    public ApplicationQueryDTO() {
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
