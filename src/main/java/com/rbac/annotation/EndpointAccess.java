package com.rbac.annotation;

import com.rbac.security.EndpointAccessType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 端点访问分类：PUBLIC / AUTHENTICATED / SELF / PERMISSION。
 * 每个 Controller 方法必须显式标注（未标注由启动检查拒绝，默认拒绝生效）。
 * PERMISSION 还必须配置固定 @RequiresPermission。
 * 仅 A04（本人改密）等受限会话可访问的端点 allowRestricted=true。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface EndpointAccess {
    EndpointAccessType value();

    /** 受限会话（首次改密前）是否允许访问；仅 A04 使用 true。 */
    boolean allowRestricted() default false;
}
