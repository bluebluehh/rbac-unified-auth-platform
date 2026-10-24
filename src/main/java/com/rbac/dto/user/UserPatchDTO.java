package com.rbac.dto.user;

/**
 * 局部更新用户输入（A10）：全部字段可选，仅更新调用方显式提供的字段。
 * 通过 xxxProvided 标志区分"字段未提供"与"显式 null"；
 * phone=null 表示清除手机号；改变公司或部门时由 Service 验证最终组合。
 */
public class UserPatchDTO {

    /** 姓名，1-80 字符，可选。 */
    private String realName;

    /** 所属公司节点 ID，可选。 */
    private String companyId;

    /** 所属部门节点 ID，可选。 */
    private String deptId;

    /** 职位名称，1-80 字符，可选。 */
    private String positionName;

    /** 手机号，可选；显式 null 表示清除。 */
    private String phone;

    /** 标志：realName 是否由调用方显式提供。 */
    private boolean realNameProvided;

    /** 标志：companyId 是否由调用方显式提供。 */
    private boolean companyIdProvided;

    /** 标志：deptId 是否由调用方显式提供。 */
    private boolean deptIdProvided;

    /** 标志：positionName 是否由调用方显式提供。 */
    private boolean positionNameProvided;

    /** 标志：phone 是否由调用方显式提供。 */
    private boolean phoneProvided;

    public UserPatchDTO() {
    }

    /**
     * 是否提供了至少一个待更新字段；全部未提供时 Service 应拒绝为空更新。
     */
    public boolean hasAnyField() {
        return realNameProvided || companyIdProvided || deptIdProvided
                || positionNameProvided || phoneProvided;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
        this.realNameProvided = true;
    }

    public boolean isRealNameProvided() {
        return realNameProvided;
    }

    public String getCompanyId() {
        return companyId;
    }

    public void setCompanyId(String companyId) {
        this.companyId = companyId;
        this.companyIdProvided = true;
    }

    public boolean isCompanyIdProvided() {
        return companyIdProvided;
    }

    public String getDeptId() {
        return deptId;
    }

    public void setDeptId(String deptId) {
        this.deptId = deptId;
        this.deptIdProvided = true;
    }

    public boolean isDeptIdProvided() {
        return deptIdProvided;
    }

    public String getPositionName() {
        return positionName;
    }

    public void setPositionName(String positionName) {
        this.positionName = positionName;
        this.positionNameProvided = true;
    }

    public boolean isPositionNameProvided() {
        return positionNameProvided;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
        this.phoneProvided = true;
    }

    public boolean isPhoneProvided() {
        return phoneProvided;
    }
}
