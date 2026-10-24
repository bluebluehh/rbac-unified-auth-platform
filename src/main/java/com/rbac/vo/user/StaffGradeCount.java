package com.rbac.vo.user;

/** Aggregate counts only; no employee records are exposed through the organization tree. */
public class StaffGradeCount {
    private Long deptId;
    private Long companyId;
    private String grade;
    private Long userCount;
    public Long getDeptId() { return deptId; }
    public void setDeptId(Long value) { deptId = value; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long value) { companyId = value; }
    public String getGrade() { return grade; }
    public void setGrade(String value) { grade = value; }
    public Long getUserCount() { return userCount; }
    public void setUserCount(Long value) { userCount = value; }
}
