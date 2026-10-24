package com.rbac.controller;

import com.rbac.annotation.EndpointAccess;
import com.rbac.annotation.OperationLog;
import com.rbac.annotation.RequiresPermission;
import com.rbac.common.PageResult;
import com.rbac.common.Result;
import com.rbac.common.constants.PermissionKeyConstants;
import com.rbac.common.enums.EventType;
import com.rbac.dto.auth.PasswordResetDTO;
import com.rbac.dto.user.UserCreateDTO;
import com.rbac.dto.user.UserPatchDTO;
import com.rbac.dto.user.UserQueryDTO;
import com.rbac.dto.user.UserRoleAssignDTO;
import com.rbac.dto.user.UserStatusDTO;
import com.rbac.security.EndpointAccessType;
import com.rbac.service.UserService;
import com.rbac.util.VersionPreconditionParser;
import com.rbac.vo.user.UserRolesVO;
import com.rbac.vo.user.UserVO;
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
 * 用户管理接口（A07-A14）。变更接口一律要求 If-Match 乐观锁头，响应附带新版本 ETag。
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** A07 分页查询（keyword 匹配工号或姓名）。 */
    @GetMapping
    @EndpointAccess(EndpointAccessType.PERMISSION)
    @RequiresPermission(PermissionKeyConstants.USER_ACCOUNT_VIEW)
    public Result<PageResult<UserVO>> list(UserQueryDTO query) {
        return Result.ok(userService.list(query));
    }

    /** A08 用户详情，附带用户版本 ETag。 */
    @GetMapping("/{id}")
    @EndpointAccess(EndpointAccessType.PERMISSION)
    @RequiresPermission(PermissionKeyConstants.USER_ACCOUNT_VIEW)
    public ResponseEntity<Result<UserVO>> get(@PathVariable String id) {
        UserVO vo = userService.get(id);
        return ResponseEntity.ok().eTag(etagOf(vo.getVersion())).body(Result.ok(vo));
    }

    /** A09 创建用户：初始无密码、无角色。 */
    @PostMapping
    @EndpointAccess(EndpointAccessType.PERMISSION)
    @RequiresPermission(PermissionKeyConstants.USER_ACCOUNT_CREATE)
    @OperationLog(type = EventType.USER_CREATE, target = "USER")
    public ResponseEntity<Result<UserVO>> create(@RequestBody UserCreateDTO dto) {
        UserVO vo = userService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).eTag(etagOf(vo.getVersion())).body(Result.ok(vo));
    }

    /** A10 部分更新用户。 */
    @PatchMapping("/{id}")
    @EndpointAccess(EndpointAccessType.PERMISSION)
    @RequiresPermission(PermissionKeyConstants.USER_ACCOUNT_EDIT)
    @OperationLog(type = EventType.USER_UPDATE, target = "USER")
    public ResponseEntity<Result<UserVO>> update(@PathVariable String id,
                                                 @RequestHeader(value = "If-Match", required = false) String ifMatch,
                                                 @RequestBody UserPatchDTO dto) {
        UserVO vo = userService.update(id, VersionPreconditionParser.parseRequired(ifMatch), dto);
        return ResponseEntity.ok().eTag(etagOf(vo.getVersion())).body(Result.ok(vo));
    }

    /** A11 用户启停。 */
    @PatchMapping("/{id}/status")
    @EndpointAccess(EndpointAccessType.PERMISSION)
    @RequiresPermission(PermissionKeyConstants.USER_ACCOUNT_STATUS)
    @OperationLog(type = EventType.USER_STATUS, target = "USER")
    public ResponseEntity<Result<UserVO>> changeStatus(@PathVariable String id,
                                                       @RequestHeader(value = "If-Match", required = false) String ifMatch,
                                                       @RequestBody UserStatusDTO dto) {
        UserVO vo = userService.changeStatus(id, VersionPreconditionParser.parseRequired(ifMatch), dto);
        return ResponseEntity.ok().eTag(etagOf(vo.getVersion())).body(Result.ok(vo));
    }

    /** A12 重置临时密码，不回显密码。 */
    @PostMapping("/{id}/password-reset")
    @EndpointAccess(EndpointAccessType.PERMISSION)
    @RequiresPermission(PermissionKeyConstants.USER_ACCOUNT_RESET_PASSWORD)
    @OperationLog(type = EventType.PASSWORD_RESET, target = "USER")
    public ResponseEntity<Result<UserVO>> resetPassword(@PathVariable String id,
                                                        @RequestHeader(value = "If-Match", required = false) String ifMatch,
                                                        @RequestBody PasswordResetDTO dto) {
        UserVO vo = userService.resetPassword(id, VersionPreconditionParser.parseRequired(ifMatch), dto);
        return ResponseEntity.ok().eTag(etagOf(vo.getVersion())).body(Result.ok(vo));
    }

    /** A13 查询当前已分配直接角色，附带用户版本 ETag。 */
    @GetMapping("/{id}/roles")
    @EndpointAccess(EndpointAccessType.PERMISSION)
    @RequiresPermission(PermissionKeyConstants.USER_ACCOUNT_VIEW)
    public ResponseEntity<Result<UserRolesVO>> getUserRoles(@PathVariable String id) {
        UserRolesVO vo = userService.getUserRoles(id);
        return ResponseEntity.ok().eTag(etagOf(vo.getVersion())).body(Result.ok(vo));
    }

    /** A14 完整替换角色集合，返回新用户版本 ETag。 */
    @PutMapping("/{id}/roles")
    @EndpointAccess(EndpointAccessType.PERMISSION)
    @RequiresPermission(PermissionKeyConstants.USER_ACCOUNT_ASSIGN_ROLE)
    @OperationLog(type = EventType.USER_ROLE_ASSIGN, target = "USER")
    public ResponseEntity<Result<UserRolesVO>> assignRoles(@PathVariable String id,
                                                           @RequestHeader(value = "If-Match", required = false) String ifMatch,
                                                           @RequestBody UserRoleAssignDTO dto) {
        UserRolesVO vo = userService.assignRoles(id, VersionPreconditionParser.parseRequired(ifMatch), dto);
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
