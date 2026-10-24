package com.rbac.common;

import java.util.Collections;
import java.util.List;

/**
 * 业务异常，携带业务错误码与 HTTP 状态；由 GlobalExceptionHandler 统一输出。
 * Controller 不复制 try-catch。
 */
public class BusinessException extends RuntimeException {

    private final String code;
    private final int httpStatus;
    private final List<FieldError> fieldErrors;

    public BusinessException(String code, int httpStatus, String message) {
        this(code, httpStatus, message, Collections.emptyList());
    }

    public BusinessException(String code, int httpStatus, String message, List<FieldError> fieldErrors) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        this.fieldErrors = fieldErrors == null ? Collections.emptyList() : fieldErrors;
    }

    /** 两参构造：按语义错误码映射默认 HTTP 状态（struct 认证实现使用）。 */
    public BusinessException(String code, String message) {
        this(code, defaultHttpStatus(code), message, Collections.emptyList());
    }

    private static int defaultHttpStatus(String code) {
        return switch (code == null ? "" : code) {
            case ResultCode.LOGIN_FAILED, ResultCode.SESSION_INVALID -> 401;
            case ResultCode.NOT_FOUND -> 404;
            case ResultCode.RATE_LIMITED, ResultCode.LOGIN_LOCKED -> 429;
            case ResultCode.INVALID_ARGUMENT, ResultCode.CURRENT_PASSWORD_INVALID,
                 ResultCode.WEAK_PASSWORD -> 400;
            case ResultCode.AUTH_UNAVAILABLE -> 503;
            default -> 500;
        };
    }

    // ===== 常用静态工厂 =====

    public static BusinessException invalidArgument(String message, List<FieldError> fieldErrors) {
        return new BusinessException(ResultCode.INVALID_ARGUMENT, 400, message, fieldErrors);
    }

    public static BusinessException invalidArgument(FieldError... errors) {
        return new BusinessException(ResultCode.INVALID_ARGUMENT, 400, "请求参数不合法", List.of(errors));
    }

    public static BusinessException notFound() {
        return new BusinessException(ResultCode.NOT_FOUND, 404, "目标资源不存在");
    }

    public static BusinessException versionConflict() {
        return new BusinessException(ResultCode.VERSION_CONFLICT, 412, "数据已变更，请刷新后重试");
    }

    public static BusinessException preconditionRequired() {
        return new BusinessException(ResultCode.PRECONDITION_REQUIRED, 428, "需要提交 If-Match 版本头");
    }

    public static BusinessException permissionDenied() {
        return new BusinessException(ResultCode.PERMISSION_DENIED, 403, "没有访问该功能的权限");
    }

    public static BusinessException passwordChangeRequired() {
        return new BusinessException(ResultCode.PASSWORD_CHANGE_REQUIRED, 403, "请先完成首次改密");
    }

    public static BusinessException stateConflict(String message) {
        return new BusinessException(ResultCode.STATE_CONFLICT, 409, message);
    }

    public static BusinessException reservedResource() {
        return new BusinessException(ResultCode.RESERVED_RESOURCE, 409, "保留资源禁止变更");
    }

    public static BusinessException lastAdminRequired() {
        return new BusinessException(ResultCode.LAST_ADMIN_REQUIRED, 409, "该变更会使有效管理员数量为零");
    }

    public static BusinessException loginFailed() {
        return new BusinessException(ResultCode.LOGIN_FAILED, 401, "工号或密码不匹配，或账号不能登录");
    }

    public static BusinessException sessionInvalid() {
        return new BusinessException(ResultCode.SESSION_INVALID, 401, "会话无效或已过期");
    }

    public static BusinessException authUnavailable() {
        return new BusinessException(ResultCode.AUTH_UNAVAILABLE, 503, "鉴权服务暂时不可用，默认拒绝");
    }

    public static BusinessException internalError() {
        return new BusinessException(ResultCode.INTERNAL_ERROR, 500, "系统内部错误");
    }

    public String getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public List<FieldError> getFieldErrors() {
        return fieldErrors;
    }
}
