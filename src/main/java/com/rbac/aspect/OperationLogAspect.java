package com.rbac.aspect;

import com.rbac.annotation.OperationLog;
import com.rbac.common.BusinessException;
import com.rbac.common.RequestIds;
import com.rbac.common.ResultCode;
import com.rbac.common.enums.EventType;
import com.rbac.entity.SysOperationLog;
import com.rbac.mapper.OperationLogMapper;
import com.rbac.security.CurrentUser;
import com.rbac.security.CurrentUserContext;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 操作审计切面（Order 1，位于鉴权切面外层，可捕获鉴权拒绝）。
 * 只负责失败审计（FAILURE）：成功审计由 Service 在事务内显式写入；
 * 失败审计在原业务事务结束后以独立短事务落库，写失败仅告警，绝不影响原异常传播。
 * 鉴权类拒绝（PERMISSION_DENIED / PASSWORD_CHANGE_REQUIRED）无论端点是否标注 @OperationLog，
 * 统一记为 AUTHZ_DENY。
 */
@Aspect
@Component
@Order(1)
public class OperationLogAspect {

    private static final Logger log = LoggerFactory.getLogger(OperationLogAspect.class);

    /** 请求级操作上下文：proceed 前构建，finally 清理。 */
    private static final ThreadLocal<OpContext> OP_CONTEXT = new ThreadLocal<>();

    private final TransactionTemplate transactionTemplate;
    private final OperationLogMapper operationLogMapper;

    public OperationLogAspect(TransactionTemplate transactionTemplate, OperationLogMapper operationLogMapper) {
        this.transactionTemplate = transactionTemplate;
        this.operationLogMapper = operationLogMapper;
    }

    @Around("within(com.rbac.controller..*)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        OperationLog annotation = method.getAnnotation(OperationLog.class);

        // 构建操作上下文快照
        OpContext ctx = new OpContext();
        if (annotation != null) {
            ctx.eventType = annotation.type();
            ctx.targetType = annotation.target().isEmpty() ? null : annotation.target();
        }
        HttpServletRequest request = currentRequest();
        if (request != null) {
            ctx.httpMethod = request.getMethod();
            ctx.requestPath = request.getRequestURI();
            ctx.sourceIp = request.getRemoteAddr();
        }
        ctx.requestId = RequestIds.current();
        CurrentUser user = CurrentUserContext.get();
        if (user != null) {
            ctx.userId = user.getUserId();
            ctx.username = user.getUsername();
        }
        OP_CONTEXT.set(ctx);
        try {
            return pjp.proceed();
        } catch (Throwable ex) {
            // 先清理 ThreadLocal，再独立记录失败审计；必须 rethrow 原异常
            OP_CONTEXT.remove();
            recordFailureQuietly(ctx, annotation, ex);
            throw ex;
        } finally {
            OP_CONTEXT.remove();
        }
    }

    /**
     * 失败审计判定与落库（独立短事务，异常仅告警）：
     * 有 @OperationLog → 记 FAILURE（鉴权类拒绝改记 AUTHZ_DENY）；
     * 无 @OperationLog → 仅鉴权类拒绝记一条 AUTHZ_DENY FAILURE，其余异常不记。
     */
    private void recordFailureQuietly(OpContext ctx, OperationLog annotation, Throwable ex) {
        try {
            EventType eventType;
            String reasonCode;
            if (ex instanceof BusinessException be) {
                reasonCode = be.getCode();
                boolean authzDeny = ResultCode.PERMISSION_DENIED.equals(reasonCode)
                        || ResultCode.PASSWORD_CHANGE_REQUIRED.equals(reasonCode);
                if (authzDeny) {
                    eventType = EventType.AUTHZ_DENY;
                } else if (annotation != null) {
                    eventType = annotation.type();
                } else {
                    return;
                }
            } else {
                if (annotation == null) {
                    return;
                }
                eventType = annotation.type();
                reasonCode = ResultCode.INTERNAL_ERROR;
            }

            SysOperationLog entity = new SysOperationLog();
            entity.setUserId(ctx.userId);
            entity.setUsernameSnapshot(ctx.username);
            entity.setEventType(eventType.name());
            entity.setTargetType(ctx.targetType);
            entity.setTargetId(null);
            entity.setHttpMethod(ctx.httpMethod);
            entity.setRequestPath(ctx.requestPath);
            entity.setRequestId(ctx.requestId);
            entity.setResult("FAILURE");
            entity.setReasonCode(reasonCode);
            entity.setSourceIp(ctx.sourceIp);
            entity.setDetails(null);
            entity.setCreateTime(LocalDateTime.now(ZoneOffset.UTC));

            transactionTemplate.executeWithoutResult(status -> operationLogMapper.insert(entity));
        } catch (Exception logEx) {
            log.warn("失败审计写入未成功，不影响原异常传播: {}", logEx.getMessage());
        }
    }

    /** 当前 MVC 请求；无请求上下文时返回 null（理论不发生，防御 PUBLIC 线程场景）。 */
    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }

    /** 操作上下文（仅本类内部持有）。 */
    private static final class OpContext {
        private EventType eventType;
        private String targetType;
        private String httpMethod;
        private String requestPath;
        private String requestId;
        private Long userId;
        private String username;
        private String sourceIp;
    }
}
