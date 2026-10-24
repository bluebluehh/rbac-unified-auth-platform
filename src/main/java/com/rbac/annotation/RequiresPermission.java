package com.rbac.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 服务端固定权限 key（四段式 应用:模块:资源:操作），由 PermissionAspect 读取校验。
 * 与 PermissionKeyConstants、数据库 sys_permission.perm_key 一致，不允许客户端传入。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {
    String value();
}
