package com.rbac.service;

import com.rbac.vo.demo.DemoActionVO;
import com.rbac.vo.demo.DemoProjectVO;

import java.util.List;

/**
 * 固定权限演示业务契约（A38-A39）。合成只读数据，不修改真实业务。
 */
public interface DemoService {

    /** A38 固定合成项目列表。 */
    List<DemoProjectVO> listProjects();

    /** A39 合成提交演示：短事务写 DEMO_SUBMIT 审计，审计失败返回 500。 */
    DemoActionVO submit();
}
