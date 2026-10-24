package com.rbac.vo.dept;

/**
 * 部门状态变更输出（A19）：item 携带 VO_C 三元组；
 * affectedUserCount 为该节点子树中受影响的不同用户数。
 */
public class DeptStatusVO {

    /** 变更后的部门节点详情。 */
    private DeptVO item;

    /** 该节点子树中受影响的不同用户数（安全整数）。 */
    private long affectedUserCount;

    public DeptStatusVO() {
    }

    public DeptVO getItem() {
        return item;
    }

    public void setItem(DeptVO item) {
        this.item = item;
    }

    public long getAffectedUserCount() {
        return affectedUserCount;
    }

    public void setAffectedUserCount(long affectedUserCount) {
        this.affectedUserCount = affectedUserCount;
    }
}
