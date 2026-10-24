package com.rbac.util;

import com.rbac.common.BusinessException;
import com.rbac.common.ResultCode;
import java.util.Set;

/** Display/search classification only. Never used for granting roles or permissions. */
public final class StaffGrade {
    private StaffGrade() { }
    public static final String SQL = "CASE "
        + "WHEN position_name = '控制面管理员' THEN 'PLATFORM' "
        + "WHEN position_name IN ('董事长','总经理','副总经理','总工程师','总会计师','党委副书记','工会主席','总经理助理','董事会秘书','经理','副经理','安全总监','经理助理') THEN 'LEADER' "
        + "WHEN position_name IN ('主任','副主任','部长','副部长') THEN 'DEPT_MANAGER' "
        + "WHEN position_name IN ('项目经理','项目副经理','项目总工程师') THEN 'PROJECT_MANAGER' "
        + "ELSE 'EMPLOYEE' END";
    public static String validate(String value) {
        if (value == null || value.isBlank()) return null;
        if (!Set.of("PLATFORM", "LEADER", "DEPT_MANAGER", "PROJECT_MANAGER", "EMPLOYEE").contains(value)) {
            throw new BusinessException(ResultCode.INVALID_ARGUMENT, 400, "未知的职级分类");
        }
        return value;
    }
}
