package com.rbac.dto.user;

/**
 * 创建用户输入（A09）：除 phone 可空外均必填，长度与格式由 Service 校验。
 */
public class UserCreateDTO {

    /** 工号，1-50 字符，全局唯一。 */
    private String username;

    /** 姓名，1-80 字符。 */
    private String realName;

    /** 所属公司节点 ID（十进制字符串）。 */
    private String companyId;

    /** 所属部门节点 ID（十进制字符串）。 */
    private String deptId;

    /** 职位名称，1-80 字符。 */
    private String positionName;

    /** 手机号，可空；非空须为 11 位数字。明文仅用于创建请求，服务端加密存储。 */
    private String phone;

    public UserCreateDTO() {
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getCompanyId() {
        return companyId;
    }

    public void setCompanyId(String companyId) {
        this.companyId = companyId;
    }

    public String getDeptId() {
        return deptId;
    }

    public void setDeptId(String deptId) {
        this.deptId = deptId;
    }

    public String getPositionName() {
        return positionName;
    }

    public void setPositionName(String positionName) {
        this.positionName = positionName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
}
