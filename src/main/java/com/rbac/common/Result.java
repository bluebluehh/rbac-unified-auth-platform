package com.rbac.common;

/**
 * 统一响应体 {code, message, data, requestId}。
 * Controller / 异常处理器负责包装；Service 只返回业务数据，不返回 Result。
 */
public class Result<T> {

    private String code;
    private String message;
    private T data;
    private String requestId;

    public Result() {
    }

    public Result(String code, String message, T data, String requestId) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.requestId = requestId;
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(ResultCode.OK, "success", data, RequestIds.current());
    }

    public static Result<Void> ok() {
        return ok(null);
    }

    public static <T> Result<T> error(String code, String message) {
        return new Result<>(code, message, null, RequestIds.current());
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
