package com.rbac.vo.dept;

import java.time.LocalDateTime;

/**
 * 部门节点输出（A16-A18）+ 公共三元组 VO_C。parentId、managerUserId 允许 null。
 */
public class DeptVO {

    /** 节点 ID（十进制字符串）。 */
    private String id;

    /** 节点名称。 */
    private String deptName;

    /** 节点 key，全局唯一。 */
    private String nodeKey;

    /** 父节点 ID，允许 null（仅 ROOT 节点为 null）。 */
    private String parentId;

    /** 节点类型：ROOT / COMPANY / DEPARTMENT / PROJECT_DEPT。 */
    private String nodeType;

    /** 负责人用户 ID，允许 null（未设置负责人）。 */
    private String managerUserId;

    /** 状态 0/1。 */
    private Integer status;

    /** 数据版本号（十进制字符串，乐观锁）。 */
    private String version;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 最后更新时间。 */
    private LocalDateTime updateTime;

    public DeptVO() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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
