package com.rbac.dto.user;

/**
 * 用户状态变更输入（A11）：取值 ENABLED/DISABLED/PENDING_ACTIVATION，
 * 状态机约束（首次改密、临时密码前置等）由 Service 校验。
 */
public class UserStatusDTO {

    /** 目标状态：ENABLED / DISABLED / PENDING_ACTIVATION。 */
    private String status;

    public UserStatusDTO() {
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
