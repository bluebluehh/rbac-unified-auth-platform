package com.rbac.aspect;

import com.rbac.annotation.EndpointAccess;
import com.rbac.annotation.RequiresPermission;
import com.rbac.common.BusinessException;
import com.rbac.security.CurrentUser;
import com.rbac.security.CurrentUserContext;
import com.rbac.security.EndpointAccessType;
import com.rbac.security.PermissionEvaluator;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 端点访问控制切面（Order 2，位于审计切面内层）。
 * 读取 @EndpointAccess 分类执行分级校验：PUBLIC 放行；受限会话仅允许 allowRestricted=true；
 * PERMISSION 依据 @RequiresPermission 固定 key 与 PermissionEvaluator 的有效权限集比对。
 * 默认拒绝：方法与类均未标注 @EndpointAccess 一律 403（启动校验器负责上线前拦截）。
 */
@Aspect
@Component
@Order(2)
public class PermissionAspect {

    private final PermissionEvaluator permissionEvaluator;

    public PermissionAspect(PermissionEvaluator permissionEvaluator) {
        this.permissionEvaluator = permissionEvaluator;
    }

    @Around("within(com.rbac.controller..*)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();

        // 方法优先，其次类级；都没有 → 默认拒绝
        EndpointAccess access = method.getAnnotation(EndpointAccess.class);
        if (access == null) {
            access = pjp.getTarget().getClass().getAnnotation(EndpointAccess.class);
        }
        if (access == null) {
            throw BusinessException.permissionDenied();
        }

        if (access.value() == EndpointAccessType.PUBLIC) {
            return pjp.proceed();
        }

        CurrentUser user = CurrentUserContext.get();
        if (user == null) {
            // 防御：正常由认证过滤器先行拦截
            throw BusinessException.sessionInvalid();
        }

        // 受限会话（首次改密前）仅放行显式允许的端点
        if (user.isRestricted() && !access.allowRestricted()) {
            throw BusinessException.passwordChangeRequired();
        }

        if (access.value() == EndpointAccessType.PERMISSION) {
            RequiresPermission requires = method.getAnnotation(RequiresPermission.class);
            if (requires == null) {
                throw new IllegalStateException("PERMISSION 端点缺少 @RequiresPermission 注解: " + method);
            }
            if (!permissionEvaluator.evaluate(user.getUserId()).has(requires.value())) {
                throw BusinessException.permissionDenied();
            }
        }

        // SELF / AUTHENTICATED 已确认有效会话，直接放行
        return pjp.proceed();
    }
}
