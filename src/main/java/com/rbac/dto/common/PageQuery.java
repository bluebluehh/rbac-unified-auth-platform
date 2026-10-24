package com.rbac.dto.common;

import com.rbac.common.BusinessException;
import com.rbac.common.FieldError;
import com.rbac.common.constants.SystemConstants;

/**
 * 分页查询基础 DTO：page/size 以字符串接收（与前端 ID/数字字符串契约一致）。
 * 校验逻辑写在 pageNumber()/sizeNumber() 中，首次调用时解析并缓存结果；
 * 非法取值抛出 400 参数错误（RANGE）。
 *
 * <p>注意：page/size 必须是标准 String bean（getter 与 setter 返回/接收同一类型），
 * 否则 Spring MVC 数据绑定会因 JavaBeans 类型不一致而静默丢弃请求参数。
 */
public class PageQuery {

    /** 页码，十进制字符串，可空（缺省 1）。 */
    private String page;

    /** 每页条数，十进制字符串，可空（缺省 20，最大 100）。 */
    private String size;

    /** 页码解析结果缓存（首次调用 pageNumber() 后写入）。 */
    private Integer cachedPage;

    /** 每页条数解析结果缓存（首次调用 sizeNumber() 后写入）。 */
    private Integer cachedSize;

    public PageQuery() {
    }

    public String getPage() {
        return page;
    }

    public void setPage(String page) {
        this.page = page;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    /**
     * 获取页码：默认 1，必须为正整数，否则抛 RANGE 参数错误。
     */
    public int pageNumber() {
        if (cachedPage == null) {
            cachedPage = parseIntOrDefault(page, SystemConstants.PAGE_DEFAULT, "page");
        }
        return cachedPage;
    }

    /**
     * 获取每页条数：默认 20，范围 1-100，否则抛 RANGE 参数错误。
     */
    public int sizeNumber() {
        if (cachedSize == null) {
            int value = parseIntOrDefault(size, SystemConstants.SIZE_DEFAULT, "size");
            if (value > SystemConstants.SIZE_MAX) {
                throw BusinessException.invalidArgument(FieldError.range("size"));
            }
            cachedSize = value;
        }
        return cachedSize;
    }

    /**
     * 解析分页参数字符串：空白按缺省值处理，非数字或小于 1 抛 RANGE 参数错误。
     */
    private static int parseIntOrDefault(String raw, int defaultValue, String field) {
        if (raw == null || raw.trim().isEmpty()) {
            return defaultValue;
        }
        int value;
        try {
            value = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw BusinessException.invalidArgument(FieldError.range(field));
        }
        if (value < 1) {
            throw BusinessException.invalidArgument(FieldError.range(field));
        }
        return value;
    }
}