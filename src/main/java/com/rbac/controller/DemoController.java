package com.rbac.controller;

import com.rbac.annotation.EndpointAccess;
import com.rbac.annotation.OperationLog;
import com.rbac.annotation.RequiresPermission;
import com.rbac.common.Result;
import com.rbac.common.constants.PermissionKeyConstants;
import com.rbac.common.enums.EventType;
import com.rbac.security.EndpointAccessType;
import com.rbac.service.DemoService;
import com.rbac.vo.demo.DemoActionVO;
import com.rbac.vo.demo.DemoProjectVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 固定权限演示接口（A38-A39）。engineering:demo:project:* 为额外测试权限，
 * 合成只读数据，不修改真实业务；A39 演示事件登记为 DEMO_SUBMIT。
 */
@RestController
@RequestMapping("/api/v1/demo")
public class DemoController {

    private final DemoService demoService;

    public DemoController(DemoService demoService) {
        this.demoService = demoService;
    }

    /** A38 固定合成项目列表（无正文）。 */
    @GetMapping("/projects")
    @EndpointAccess(EndpointAccessType.PERMISSION)
    @RequiresPermission(PermissionKeyConstants.DEMO_PROJECT_VIEW)
    public Result<List<DemoProjectVO>> projects() {
        return Result.ok(demoService.listProjects());
    }

    /** A39 合成提交演示：短事务写 DEMO_SUBMIT 审计，审计失败返回 500，不修改真实业务。 */
    @PostMapping("/projects/submit")
    @EndpointAccess(EndpointAccessType.PERMISSION)
    @RequiresPermission(PermissionKeyConstants.DEMO_PROJECT_SUBMIT)
    @OperationLog(type = EventType.DEMO_SUBMIT)
    public Result<DemoActionVO> submit(@RequestBody(required = false) Object body) {
        return Result.ok(demoService.submit());
    }
}
