package com.rbac.entity;

import java.time.LocalDateTime;

/**
 * 用户实体，对应表 sys_user（手机号为密文字节，带乐观锁版本号与锁定状态）。
 */
public class SysUser {

    /** 主键 */
    private Long id;
    /** 登录用户名 */
    private String username;
    /** 密码哈希 */
    private String passwordHash;
    /** 真实姓名 */
    private String realName;
    /** 所属公司 ID */
    private Long companyId;
    /** 所属部门 ID */
    private Long deptId;
    /** 职位名称 */
    private String positionName;
    /** 手机号密文（AES 等加密后的字节） */
    private byte[] phoneCipher;
    /** 状态（ENABLED / DISABLED 等） */
    private String status;
    /** 是否必须修改密码：1 是 / 0 否 */
    private Integer mustChangePassword;
    /** 连续登录失败次数 */
    private Integer failedLoginCount;
    /** 锁定截止时间，NULL 表示未锁定 */
    private LocalDateTime lockedUntil;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
    /** 乐观锁版本号 */
    private Long version;

    public SysUser() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    public String getPositionName() {
        return positionName;
    }

    public void setPositionName(String positionName) {
        this.positionName = positionName;
    }

    public byte[] getPhoneCipher() {
        return phoneCipher;
    }

    public void setPhoneCipher(byte[] phoneCipher) {
        this.phoneCipher = phoneCipher;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getMustChangePassword() {
        return mustChangePassword;
    }

    public void setMustChangePassword(Integer mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }

    public Integer getFailedLoginCount() {
        return failedLoginCount;
    }

    public void setFailedLoginCount(Integer failedLoginCount) {
        this.failedLoginCount = failedLoginCount;
    }

    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(LocalDateTime lockedUntil) {
        this.lockedUntil = lockedUntil;
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

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
