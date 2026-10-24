package com.rbac.dto.dept;

/**
 * 局部更新部门输入（A18）：nodeKey、parentId、nodeType 不可变；
 * 仅 deptName/managerUserId 可更新；managerUserId 显式 null 表示清除负责人，
 * 负责人须在该节点子树内或已确认的直属领导节点（由 Service 校验）。
 */
public class DeptPatchDTO {

    /** 节点名称，1-160 字符，可选。 */
    private String deptName;

    /** 负责人用户 ID，可选；显式 null 表示清除。 */
    private String managerUserId;

    /** 标志：deptName 是否由调用方显式提供。 */
    private boolean deptNameProvided;

    /** 标志：managerUserId 是否由调用方显式提供。 */
    private boolean managerUserIdProvided;

    public DeptPatchDTO() {
    }

    /**
     * 是否提供了至少一个待更新字段；全部未提供时 Service 应拒绝为空更新。
     */
    public boolean hasAnyField() {
        return deptNameProvided || managerUserIdProvided;
    }

    public String getDeptName() {
        return deptName;
    }

    public void setDeptName(String deptName) {
        this.deptName = deptName;
        this.deptNameProvided = true;
    }

    public boolean isDeptNameProvided() {
        return deptNameProvided;
    }

    public String getManagerUserId() {
        return managerUserId;
    }

    public void setManagerUserId(String managerUserId) {
        this.managerUserId = managerUserId;
        this.managerUserIdProvided = true;
    }

    public boolean isManagerUserIdProvided() {
        return managerUserIdProvided;
    }
}
