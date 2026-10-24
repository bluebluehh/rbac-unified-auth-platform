package com.rbac.controller;

import com.rbac.annotation.EndpointAccess;
import com.rbac.annotation.RequiresPermission;
import com.rbac.common.PageResult;
import com.rbac.common.Result;
import com.rbac.common.constants.PermissionKeyConstants;
import com.rbac.dto.log.LogQueryDTO;
import com.rbac.security.EndpointAccessType;
import com.rbac.service.LogService;
import com.rbac.vo.log.LogVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作日志只读查询接口（A36-A37）。时间/id 降序；无 ETag（不可修改），无写接口。
 */
@RestController
@RequestMapping("/api/v1/logs")
public class LogController {

    private final LogService logService;

    public LogController(LogService logService) {
        this.logService = logService;
    }

    /** A36 分页查询：from/to 必填（跨度 ≤ 31 日），userId/eventType/result/requestId 可选。 */
    @GetMapping
    @EndpointAccess(EndpointAccessType.PERMISSION)
    @RequiresPermission(PermissionKeyConstants.LOG_VIEW)
    public Result<PageResult<LogVO>> list(LogQueryDTO query) {
        return Result.ok(logService.list(query));
    }

    /** A37 日志详情（无 ETag，只读）。 */
    @GetMapping("/{id}")
    @EndpointAccess(EndpointAccessType.PERMISSION)
    @RequiresPermission(PermissionKeyConstants.LOG_VIEW)
    public Result<LogVO> get(@PathVariable String id) {
        return Result.ok(logService.get(id));
    }
}
