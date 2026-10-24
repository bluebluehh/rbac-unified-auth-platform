package com.rbac.dto.common;

/**
 * 二值启停输入（A19/A24/A31/A35）。JSON 正文只接受数字 0/1；
 * 字符串 "1"、null、boolean 等由 Service 校验后返回 400 INVALID_ARGUMENT。
 */
public class BinaryStatusDTO {

    /** 目标状态：1 启用 / 0 停用。 */
    private Integer status;

    public BinaryStatusDTO() {
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
