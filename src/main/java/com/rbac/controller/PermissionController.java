package com.rbac.controller;

import com.rbac.annotation.EndpointAccess;
import com.rbac.annotation.OperationLog;
import com.rbac.annotation.RequiresPermission;
import com.rbac.common.PageResult;
import com.rbac.common.Result;
import com.rbac.common.constants.PermissionKeyConstants;
import com.rbac.common.enums.EventType;
import com.rbac.dto.common.BinaryStatusDTO;
import com.rbac.dto.permission.PermissionCreateDTO;
import com.rbac.dto.permission.PermissionQueryDTO;
import com.rbac.security.EndpointAccessType;
import com.rbac.service.PermissionService;
import com.rbac.util.VersionPreconditionParser;
import com.rbac.vo.permission.PermissionVO;
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
 * 权限点维护接口（A32-A35）。四段权限码由服务端生成，客户端不提交 permKey/sourceRow。
 */
@RestController
@RequestMapping("/api/v1/permissions")
public class PermissionController {

    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    /** A32 分页：keyword 匹配显示名或权限码。 */
    @GetMapping
    @EndpointAccess(EndpointAccessType.PERMISSION)
    @RequiresPermission(PermissionKeyConstants.PERMISSION_VIEW)
    public Result<PageResult<PermissionVO>> list(PermissionQueryDTO query) {
        return Result.ok(permissionService.list(query));
    }

    /** A33 详情，附带版本 ETag。 */
    @GetMapping("/{id}")
    @EndpointAccess(EndpointAccessType.PERMISSION)
    @RequiresPermission(PermissionKeyConstants.PERMISSION_VIEW)
    public ResponseEntity<Result<PermissionVO>> get(@PathVariable String id) {
        PermissionVO vo = permissionService.get(id);
        return ResponseEntity.ok().eTag(etagOf(vo.getVersion())).body(Result.ok(vo));
    }

    /** A34 创建：四段码服务生成；同编码名称需一致；同元组重复 409。 */
    @PostMapping
    @EndpointAccess(EndpointAccessType.PERMISSION)
    @RequiresPermission(PermissionKeyConstants.PERMISSION_CREATE)
    @OperationLog(type = EventType.PERMISSION_CREATE, target = "PERMISSION")
    public ResponseEntity<Result<PermissionVO>> create(@RequestBody PermissionCreateDTO dto) {
        PermissionVO vo = permissionService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).eTag(etagOf(vo.getVersion())).body(Result.ok(vo));
    }

    /** A35 启停：固定技术权限（platform:*）不能停用。 */
    @PatchMapping("/{id}/status")
    @EndpointAccess(EndpointAccessType.PERMISSION)
    @RequiresPermission(PermissionKeyConstants.PERMISSION_STATUS)
    @OperationLog(type = EventType.PERMISSION_STATUS, target = "PERMISSION")
    public ResponseEntity<Result<PermissionVO>> changeStatus(@PathVariable String id,
                                                             @RequestHeader(value = "If-Match", required = false) String ifMatch,
                                                             @RequestBody BinaryStatusDTO dto) {
        PermissionVO vo = permissionService.changeStatus(id, VersionPreconditionParser.parseRequired(ifMatch), dto);
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
