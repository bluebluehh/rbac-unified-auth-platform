package com.rbac.annotation;

import com.rbac.common.enums.EventType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需审计的管理操作，由 OperationLogAspect 采集操作上下文。
 * 注解只负责管理 Controller 上下文标记；成功审计由 Service 在事务内显式调用 AuditService 写入，
 * 切面不使用 AfterReturning 另行写 SUCCESS；失败审计由切面在事务结束后独立记录。
 * 登录/退出/改密及过滤器拒绝由认证 Service/过滤器直接确定事件，不要求穿过本注解。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {
    /** 事件类型（白名单 EventType）。 */
    EventType type();

    /** 目标类型 USER/ROLE/DEPT/APP/PERMISSION/SESSION；无固定目标留空。 */
    String target() default "";
}
