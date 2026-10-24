package com.rbac.util;

import com.rbac.common.BusinessException;
import com.rbac.common.FieldError;
import com.rbac.common.constants.SystemConstants;

import java.util.List;
import java.util.regex.Pattern;

/**
 * ID 字符串解析：格式 ^[1-9][0-9]{0,18}$ 且 ≤ 9223372036854775807；非法返回 400 字段错误。
 */
public final class Ids {

    private static final Pattern ID_PATTERN = Pattern.compile(SystemConstants.ID_PATTERN);

    private Ids() {
    }

    public static Long parse(String value, String field) {
        if (value == null || value.isBlank()) {
            throw BusinessException.invalidArgument(FieldError.required(field));
        }
        if (!ID_PATTERN.matcher(value).matches()) {
            throw BusinessException.invalidArgument(FieldError.format(field));
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw BusinessException.invalidArgument(FieldError.format(field));
        }
    }

    /** 可空解析：null/空白返回 null；非空则严格校验。 */
    public static Long parseNullable(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return parse(value, field);
    }
}
