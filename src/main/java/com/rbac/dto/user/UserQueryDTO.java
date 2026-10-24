package com.rbac.dto.user;

import com.rbac.dto.common.PageQuery;

/**
 * 用户分页查询输入（A07）：所有过滤条件可选，组合语义由 Service 解释。
 */
public class UserQueryDTO extends PageQuery {
    private Boolean includeDescendants;
    private String companyId;
    public Boolean getIncludeDescendants() { return includeDescendants; }
    public void setIncludeDescendants(Boolean value) { includeDescendants = value; }
    public String getCompanyId() { return companyId; }
    public void setCompanyId(String value) { companyId = value; }
    /** 展示用职级分类，不参与授权。 */
    private String grade;
    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }

    /** 关键字，模糊匹配工号/姓名，可空。 */
    private String keyword;

    /** 按部门节点 ID 过滤，可空。 */
    private String deptId;

    /** 按用户状态过滤（ENABLED/DISABLED/PENDING_ACTIVATION），可空。 */
    private String status;

    public UserQueryDTO() {
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getDeptId() {
        return deptId;
    }

    public void setDeptId(String deptId) {
        this.deptId = deptId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
