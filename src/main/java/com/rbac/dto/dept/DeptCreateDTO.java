package com.rbac.dto.dept;

/**
 * 创建部门节点输入（A17）：nodeType 取 COMPANY/DEPARTMENT/PROJECT_DEPT，
 * 不能通过 API 创建 ROOT；managerUserId、status 可空，status 缺省 1。
 */
public class DeptCreateDTO {

    /** 节点名称，1-160 字符。 */
    private String deptName;

    /** 节点 key，1-160 字符，全局唯一。 */
    private String nodeKey;

    /** 父节点 ID（十进制字符串）。 */
    private String parentId;

    /** 节点类型：COMPANY / DEPARTMENT / PROJECT_DEPT。 */
    private String nodeType;

    /** 负责人用户 ID，可空。 */
    private String managerUserId;

    /** 状态 0/1，可空，缺省 1。 */
    private Integer status;

    public DeptCreateDTO() {
    }

    public String getDeptName() {
        return deptName;
    }

    public void setDeptName(String deptName) {
        this.deptName = deptName;
    }

    public String getNodeKey() {
        return nodeKey;
    }

    public void setNodeKey(String nodeKey) {
        this.nodeKey = nodeKey;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public String getNodeType() {
        return nodeType;
    }

    public void setNodeType(String nodeType) {
        this.nodeType = nodeType;
    }

    public String getManagerUserId() {
        return managerUserId;
    }

    public void setManagerUserId(String managerUserId) {
        this.managerUserId = managerUserId;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
