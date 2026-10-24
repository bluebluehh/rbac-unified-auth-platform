package com.rbac.controller;

import com.rbac.annotation.EndpointAccess;
import com.rbac.annotation.OperationLog;
import com.rbac.common.BusinessException;
import com.rbac.common.Result;
import com.rbac.common.ResultCode;
import com.rbac.common.constants.SystemConstants;
import com.rbac.common.enums.EventType;
import com.rbac.dto.auth.LoginDTO;
import com.rbac.dto.auth.PasswordChangeDTO;
import com.rbac.security.EndpointAccessType;
import com.rbac.service.AuthService;
import com.rbac.vo.application.ApplicationVO;
import com.rbac.vo.auth.CurrentPermissionsVO;
import com.rbac.vo.auth.LoginVO;
import com.rbac.vo.user.UserVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 认证门户接口（A01-A06）。身份统一取自 CurrentUserContext，不信任客户端传入 userId。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** A01 登录：公开端点，客户端 IP 供限流与锁定判定。 */
    @PostMapping("/login")
    @EndpointAccess(EndpointAccessType.PUBLIC)
    public Result<LoginVO> login(@RequestBody LoginDTO dto, HttpServletRequest request) {
        return Result.ok(authService.login(dto, request.getRemoteAddr()));
    }

    /** A02 退出：公开端点，无 Token / 失效 Token 重复退出同样成功，不暴露主体。 */
    @PostMapping("/logout")
    @EndpointAccess(EndpointAccessType.PUBLIC)
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.logout(extractRawToken(authorization));
        return Result.ok();
    }

    /** A03 当前用户信息。 */
    @GetMapping("/me")
    @EndpointAccess(EndpointAccessType.SELF)
    public Result<UserVO> me() {
        return Result.ok(authService.getCurrentUser());
    }

    /** A04 改密/首次激活：受限会话唯一可访问的业务端点。 */
    @PutMapping("/password")
    @EndpointAccess(value = EndpointAccessType.SELF, allowRestricted = true)
    @OperationLog(type = EventType.PASSWORD_CHANGE)
    public Result<Void> changePassword(@RequestBody PasswordChangeDTO dto) {
        authService.changePassword(dto);
        return Result.ok();
    }

    /** A05 门户应用卡片：至少一个有效原子权限才显示对应启用应用。 */
    @GetMapping("/apps")
    @EndpointAccess(EndpointAccessType.SELF)
    public Result<List<ApplicationVO>> apps() {
        return Result.ok(authService.getApps());
    }

    /** A06 当前有效权限 key 与启用直接角色，可按应用过滤。 */
    @GetMapping("/permissions")
    @EndpointAccess(EndpointAccessType.SELF)
    public Result<CurrentPermissionsVO> permissions(
            @RequestParam(value = "applicationId", required = false) String applicationId) {
        return Result.ok(authService.getPermissions(parseApplicationId(applicationId)));
    }

    /** 剥离 Bearer 前缀，得到原文 Token（无前缀时按原文处理）。 */
    private static String extractRawToken(String authorization) {
        if (authorization == null) {
            return null;
        }
        String trimmed = authorization.trim();
        if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return trimmed.substring(7).trim();
        }
        return trimmed;
    }

    /** applicationId 查询参数 → Long；空串视为未传；非法十进制 ID 返回 400。 */
    private static Long parseApplicationId(String applicationId) {
        if (applicationId == null || applicationId.isBlank()) {
            return null;
        }
        if (!applicationId.matches(SystemConstants.ID_PATTERN)) {
            throw new BusinessException(ResultCode.INVALID_ARGUMENT, "applicationId 格式不正确");
        }
        return Long.parseLong(applicationId);
    }
}
