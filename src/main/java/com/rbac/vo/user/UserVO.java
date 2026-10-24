package com.rbac.vo.user;

import java.time.LocalDateTime;

/**
 * 用户输出（A03/A08-A12）+ 公共三元组 VO_C。
 * 所有字段必返回；phoneMasked、lockedUntil 允许 null。
 */
public class UserVO {

    /** 用户 ID（十进制字符串）。 */
    private String id;

    /** 工号。 */
    private String username;

    /** 姓名。 */
    private String realName;

    /** 所属公司节点 ID。 */
    private String companyId;

    /** 所属部门节点 ID。 */
    private String deptId;

    /** 所属部门节点名称。 */
    private String deptName;

    /** 职位名称。 */
    private String positionName;

    /** 掩码后的手机号，允许 null（未登记手机号）。 */
    private String phoneMasked;

    /** 用户状态：ENABLED / DISABLED / PENDING_ACTIVATION。 */
    private String status;

    /** 是否须完成首次改密。 */
    private Boolean mustChangePassword;

    /** 登录锁定截止时间，允许 null（未锁定）。 */
    private LocalDateTime lockedUntil;

    /** 数据版本号（十进制字符串，乐观锁）。 */
    private String version;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 最后更新时间。 */
    private LocalDateTime updateTime;

    public UserVO() {
    }

    /** 全参构造（A03 输出顺序）。 */
    public UserVO(String id, String username, String realName, String companyId, String deptId,
                  String deptName, String positionName, String phoneMasked, String status,
                  boolean mustChangePassword, LocalDateTime lockedUntil, String version,
                  LocalDateTime createTime, LocalDateTime updateTime) {
        this.id = id;
        this.username = username;
        this.realName = realName;
        this.companyId = companyId;
        this.deptId = deptId;
        this.deptName = deptName;
        this.positionName = positionName;
        this.phoneMasked = phoneMasked;
        this.status = status;
        this.mustChangePassword = mustChangePassword;
        this.lockedUntil = lockedUntil;
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

    public String getDeptName() {
        return deptName;
    }

    public void setDeptName(String deptName) {
        this.deptName = deptName;
    }

    public String getPositionName() {
        return positionName;
    }

    public void setPositionName(String positionName) {
        this.positionName = positionName;
    }

    public String getPhoneMasked() {
        return phoneMasked;
    }

    public void setPhoneMasked(String phoneMasked) {
        this.phoneMasked = phoneMasked;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getMustChangePassword() {
        return mustChangePassword;
    }

    public void setMustChangePassword(Boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }

    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(LocalDateTime lockedUntil) {
        this.lockedUntil = lockedUntil;
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
