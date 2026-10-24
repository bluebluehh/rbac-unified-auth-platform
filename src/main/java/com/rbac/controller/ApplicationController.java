package com.rbac.controller;

import com.rbac.annotation.EndpointAccess;
import com.rbac.annotation.OperationLog;
import com.rbac.annotation.RequiresPermission;
import com.rbac.common.PageResult;
import com.rbac.common.Result;
import com.rbac.common.constants.PermissionKeyConstants;
import com.rbac.common.enums.EventType;
import com.rbac.dto.application.ApplicationCreateDTO;
import com.rbac.dto.application.ApplicationPatchDTO;
import com.rbac.dto.application.ApplicationQueryDTO;
import com.rbac.dto.common.BinaryStatusDTO;
import com.rbac.security.EndpointAccessType;
import com.rbac.service.ApplicationService;
import com.rbac.util.VersionPreconditionParser;
import com.rbac.vo.application.ApplicationVO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 应用登记维护接口（A27-A31）。门户卡片使用 A05，不走本控制器。
 */
@RestController
@RequestMapping("/api/v1/applications")
public class ApplicationController {

    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /** A27 分页查询。 */
    @GetMapping
    @EndpointAccess(EndpointAccessType.PERMISSION)
    @RequiresPermission(PermissionKeyConstants.APP_VIEW)
    public Result<PageResult<ApplicationVO>> list(ApplicationQueryDTO query) {
        return Result.ok(applicationService.list(query));
    }

    /** A28 详情，附带版本 ETag。 */
    @GetMapping("/{id}")
    @EndpointAccess(EndpointAccessType.PERMISSION)
    @RequiresPermission(PermissionKeyConstants.APP_VIEW)
    public ResponseEntity<Result<ApplicationVO>> get(@PathVariable String id) {
        ApplicationVO vo = applicationService.get(id);
        return ResponseEntity.ok().eTag(etagOf(vo.getVersion())).body(Result.ok(vo));
    }

    /** A29 创建：不自动授权限。 */
    @PostMapping
    @EndpointAccess(EndpointAccessType.PERMISSION)
    @RequiresPermission(PermissionKeyConstants.APP_CREATE)
    @OperationLog(type = EventType.APP_CREATE, target = "APP")
    public ResponseEntity<Result<ApplicationVO>> create(@RequestBody ApplicationCreateDTO dto) {
        ApplicationVO vo = applicationService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).eTag(etagOf(vo.getVersion())).body(Result.ok(vo));
    }

    /** A30 部分更新：appKey 不可变；启停走专用接口。 */
    @PatchMapping("/{id}")
    @EndpointAccess(EndpointAccessType.PERMISSION)
    @RequiresPermission(PermissionKeyConstants.APP_EDIT)
    @OperationLog(type = EventType.APP_UPDATE, target = "APP")
    public ResponseEntity<Result<ApplicationVO>> update(@PathVariable String id,
                                                        @RequestHeader(value = "If-Match", required = false) String ifMatch,
                                                        @RequestBody ApplicationPatchDTO dto) {
        ApplicationVO vo = applicationService.update(id, VersionPreconditionParser.parseRequired(ifMatch), dto);
        return ResponseEntity.ok().eTag(etagOf(vo.getVersion())).body(Result.ok(vo));
    }

    /** A31 启停：停用使全部下属权限失效，platform 应用保护。 */
    @PatchMapping("/{id}/status")
    @EndpointAccess(EndpointAccessType.PERMISSION)
    @RequiresPermission(PermissionKeyConstants.APP_STATUS)
    @OperationLog(type = EventType.APP_STATUS, target = "APP")
    public ResponseEntity<Result<ApplicationVO>> changeStatus(@PathVariable String id,
                                                              @RequestHeader(value = "If-Match", required = false) String ifMatch,
                                                              @RequestBody BinaryStatusDTO dto) {
        ApplicationVO vo = applicationService.changeStatus(id, VersionPreconditionParser.parseRequired(ifMatch), dto);
        return ResponseEntity.ok().eTag(etagOf(vo.getVersion())).body(Result.ok(vo));
    }

    /** 版本字符串 → 带引号 ETag；解析失败回退 "0"（防御，正常由 Service 保证）。 */
    private static String etagOf(String version) {
        try {
            return VersionPreconditionParser.toETag(Long.parseLong(version));
        } catch (NumberFormatException e) {
            return "\"0\"";
        }
    }
}
