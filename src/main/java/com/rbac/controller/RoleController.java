package com.rbac.controller;

import com.rbac.annotation.EndpointAccess;
import com.rbac.annotation.OperationLog;
import com.rbac.annotation.RequiresPermission;
import com.rbac.common.PageResult;
import com.rbac.common.Result;
import com.rbac.common.constants.PermissionKeyConstants;
import com.rbac.common.enums.EventType;
import com.rbac.dto.common.BinaryStatusDTO;
import com.rbac.dto.role.RoleCreateDTO;
import com.rbac.dto.role.RolePatchDTO;
import com.rbac.dto.role.RolePermissionAssignDTO;
import com.rbac.dto.role.RoleQueryDTO;
import com.rbac.security.EndpointAccessType;
import com.rbac.service.RoleService;
import com.rbac.util.VersionPreconditionParser;
import com.rbac.vo.role.RolePermissionsVO;
import com.rbac.vo.role.RoleVO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 角色管理接口（A20-A26）。变更接口要求 If-Match，响应附带新版本 ETag。
 */
@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    /** A20 分页查询。 */
    @GetMapping
    @EndpointAccess(EndpointAccessType.PERMISSION)
    @RequiresPermission(PermissionKeyConstants.ROLE_VIEW)
    public Result<PageResult<RoleVO>> list(RoleQueryDTO query) {
        return Result.ok(roleService.list(query));
    }

    /** A21 详情，附带版本 ETag。 */
    @GetMapping("/{id}")
    @EndpointAccess(EndpointAccessType.PERMISSION)
    @RequiresPermission(PermissionKeyConstants.ROLE_VIEW)
    public ResponseEntity<Result<RoleVO>> get(@PathVariable String id) {
        RoleVO vo = roleService.get(id);
        return ResponseEntity.ok().eTag(etagOf(vo.getVersion())).body(Result.ok(vo));
    }

    /** A22 创建：初始无权限、不分配给创建者。 */
    @PostMapping
    @EndpointAccess(EndpointAccessType.PERMISSION)
    @RequiresPermission(PermissionKeyConstants.ROLE_CREATE)
    @OperationLog(type = EventType.ROLE_CREATE, target = "ROLE")
    public ResponseEntity<Result<RoleVO>> create(@RequestBody RoleCreateDTO dto) {
        RoleVO vo = roleService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).eTag(etagOf(vo.getVersion())).body(Result.ok(vo));
    }

    /** A23 部分更新：保留角色 PLATFORM_ADMIN 禁止变更。 */
    @PatchMapping("/{id}")
    @EndpointAccess(EndpointAccessType.PERMISSION)
    @RequiresPermission(PermissionKeyConstants.ROLE_EDIT)
    @OperationLog(type = EventType.ROLE_UPDATE, target = "ROLE")
    public ResponseEntity<Result<RoleVO>> update(@PathVariable String id,
                                                 @RequestHeader(value = "If-Match", required = false) String ifMatch,
                                                 @RequestBody RolePatchDTO dto) {
        RoleVO vo = roleService.update(id, VersionPreconditionParser.parseRequired(ifMatch), dto);
        return ResponseEntity.ok().eTag(etagOf(vo.getVersion())).body(Result.ok(vo));
    }

    /** A24 启停：停用保留关系但不产生有效权限。 */
    @PatchMapping("/{id}/status")
    @EndpointAccess(EndpointAccessType.PERMISSION)
    @RequiresPermission(PermissionKeyConstants.ROLE_STATUS)
    @OperationLog(type = EventType.ROLE_STATUS, target = "ROLE")
    public ResponseEntity<Result<RoleVO>> changeStatus(@PathVariable String id,
                                                       @RequestHeader(value = "If-Match", required = false) String ifMatch,
                                                       @RequestBody BinaryStatusDTO dto) {
        RoleVO vo = roleService.changeStatus(id, VersionPreconditionParser.parseRequired(ifMatch), dto);
        return ResponseEntity.ok().eTag(etagOf(vo.getVersion())).body(Result.ok(vo));
    }

    /** A25 当前权限配置（含已绑定停用权限），附带角色版本 ETag。 */
    @GetMapping("/{id}/permissions")
    @EndpointAccess(EndpointAccessType.PERMISSION)
    @RequiresPermission(PermissionKeyConstants.ROLE_VIEW)
    public ResponseEntity<Result<RolePermissionsVO>> getPermissions(@PathVariable String id) {
        RolePermissionsVO vo = roleService.getPermissions(id);
        return ResponseEntity.ok().eTag(etagOf(vo.getVersion())).body(Result.ok(vo));
    }

    /** A26 完整替换权限集合：不能修改 PLATFORM_ADMIN；新增关联权限和应用须启用。 */
    @PutMapping("/{id}/permissions")
    @EndpointAccess(EndpointAccessType.PERMISSION)
    @RequiresPermission(PermissionKeyConstants.ROLE_GRANT)
    @OperationLog(type = EventType.ROLE_PERMISSION_ASSIGN, target = "ROLE")
    public ResponseEntity<Result<RolePermissionsVO>> assignPermissions(@PathVariable String id,
                                                                       @RequestHeader(value = "If-Match", required = false) String ifMatch,
                                                                       @RequestBody RolePermissionAssignDTO dto) {
        RolePermissionsVO vo = roleService.assignPermissions(id, VersionPreconditionParser.parseRequired(ifMatch), dto);
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
