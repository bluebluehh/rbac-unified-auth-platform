package com.rbac.vo.dept;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * 部门树节点输出（A15）：在 DeptVO 全部字段基础上追加 children；
 * 叶子节点 children 为空数组（构造时初始化）。
 */
public class DeptTreeVO extends DeptVO {
    private Map<String, Long> directGradeCounts = new LinkedHashMap<>();
    private Map<String, Long> subtreeGradeCounts = new LinkedHashMap<>();
    private Map<String, Long> companyGradeCounts = new LinkedHashMap<>();
    public Map<String, Long> getDirectGradeCounts() { return directGradeCounts; }
    public Map<String, Long> getSubtreeGradeCounts() { return subtreeGradeCounts; }
    public Map<String, Long> getCompanyGradeCounts() { return companyGradeCounts; }

    /** 子节点列表，构造时初始化为空列表，叶子节点保持 []。 */
    private List<DeptTreeVO> children = new ArrayList<>();

    public DeptTreeVO() {
    }

    public List<DeptTreeVO> getChildren() {
        return children;
    }

    public void setChildren(List<DeptTreeVO> children) {
        this.children = children;
    }
}
