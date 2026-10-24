package com.rbac.common;

/**
 * 字段级错误 {field, code, message}。HTTP 400 响应固定携带 fieldErrors 数组。
 */
public class FieldError {

    private String field;
    private String code;
    private String message;

    public FieldError() {
    }

    public FieldError(String field, String code, String message) {
        this.field = field;
        this.code = code;
        this.message = message;
    }

    public static FieldError required(String field) {
        return new FieldError(field, "REQUIRED", "字段必填");
    }

    public static FieldError format(String field) {
        return new FieldError(field, "FORMAT", "字段格式不正确");
    }

    public static FieldError length(String field) {
        return new FieldError(field, "LENGTH", "字段长度不符合要求");
    }

    public static FieldError range(String field) {
        return new FieldError(field, "RANGE", "字段取值超出范围");
    }

    public static FieldError duplicate(String field) {
        return new FieldError(field, "DUPLICATE", "字段取值重复");
    }

    public static FieldError referenceInvalid(String field) {
        return new FieldError(field, "REFERENCE_INVALID", "引用的目标不存在或无效");
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
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
}
