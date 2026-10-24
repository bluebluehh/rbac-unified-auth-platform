package com.rbac.util;

import com.rbac.common.BusinessException;
import com.rbac.common.FieldError;

import java.util.List;
import java.util.regex.Pattern;

/**
 * If-Match 版本头解析（设计文档 §6.4）：
 * 缺失 → 428 PRECONDITION_REQUIRED；非法语法/溢出 → 400 INVALID_ARGUMENT。
 * 允许带引号的 0 或无前导零正十进制值，最大 9223372036854775807；拒绝弱 ETag、通配符、多值。
 */
public final class VersionPreconditionParser {

    private static final Pattern VERSION_PATTERN = Pattern.compile("^(0|[1-9][0-9]{0,18})$");

    private VersionPreconditionParser() {
    }

    /** 必填版本头解析，返回 expectedVersion。 */
    public static Long parseRequired(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw BusinessException.preconditionRequired();
        }
        return parse(ifMatch);
    }

    /** 已知非空值解析（内部复用）。 */
    public static Long parse(String ifMatch) {
        String v = ifMatch.trim();
        if (v.startsWith("W/")) {
            throw BusinessException.invalidArgument(FieldError.format("If-Match"));
        }
        if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
            v = v.substring(1, v.length() - 1);
        }
        if (v.contains(",") || "*".equals(v) || !VERSION_PATTERN.matcher(v).matches()) {
            throw BusinessException.invalidArgument(FieldError.format("If-Match"));
        }
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            throw BusinessException.invalidArgument(FieldError.format("If-Match"));
        }
    }

    /** ETag 值：带引号的版本字符串。 */
    public static String toETag(Long version) {
        return "\"" + version + "\"";
    }
}
