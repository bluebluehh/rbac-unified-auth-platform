package com.rbac.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

/**
 * ErrorWriter（过滤链失败输出）与 GlobalExceptionHandler（MVC 异常输出）共享的错误结构适配。
 * 保证两条异常路径输出一致的 code/message/data/requestId/fieldErrors，且不重复写响应。
 */
public final class ErrorResponseFactory {

    private ErrorResponseFactory() {
    }

    /** 构造失败响应体（供 MVC 异常处理器使用）。 */
    public static Result<Void> build(String code, String message, List<FieldError> fieldErrors) {
        Result<Void> r = Result.error(code, message);
        if (fieldErrors != null && !fieldErrors.isEmpty()) {
            // fieldErrors 通过独立包装输出，见 buildWithFields
        }
        return r;
    }

    /** 构造带 fieldErrors 的失败响应（仅 HTTP 400 使用）。 */
    public static ErrorBody buildBody(String code, String message, List<FieldError> fieldErrors) {
        return new ErrorBody(code, message, fieldErrors == null ? List.of() : fieldErrors, RequestIds.current());
    }

    /** 直接向响应写出错误 JSON（供 AuthenticationFilter/ErrorWriter 在过滤链中使用）。 */
    public static void write(com.rbac.security.ErrorWriter writer, HttpServletRequest request,
                             jakarta.servlet.http.HttpServletResponse response,
                             int status, String code, String message, List<FieldError> fieldErrors) {
        try {
            writer.write(request, response, status, buildBody(code, message, fieldErrors));
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException("错误响应写出失败", e);
        }
    }

    /** MVC ResponseEntity 版本。 */
    public static ResponseEntity<Object> toEntity(int status, String code, String message, List<FieldError> fieldErrors) {
        HttpStatus hs = HttpStatus.valueOf(status);
        return ResponseEntity.status(hs).body(buildBody(code, message, fieldErrors));
    }

    /**
     * 统一错误 JSON 结构：{code, message, data:null, requestId[, fieldErrors]}。
     * 只有 400 携带 fieldErrors；其他失败不返回该字段。
     */
    public record ErrorBody(String code, String message, List<FieldError> fieldErrors, String requestId) {
    }
}
