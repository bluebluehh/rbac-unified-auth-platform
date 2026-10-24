package com.rbac.service.struct;

import com.rbac.common.RequestIds;
import com.rbac.common.enums.EventType;
import com.rbac.entity.SysOperationLog;
import com.rbac.mapper.OperationLogMapper;
import com.rbac.mapper.SecurityStateMapper;
import com.rbac.service.AuditService;
import com.rbac.service.AuditService.AuditEvent;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 结构化审计写入实现：成功审计 MANDATORY（必须已有事务）、管理失败审计 REQUIRES_NEW。
 * 注意：必须通过 Spring 代理调用（注入 AuditService 接口），类内自调用不生效。
 *
 * <p>两组契约并存：
 * <ul>
 *   <li>{@link #writeSuccess}/{@link #recordFailure}：管理侧 SysOperationLog 直传；</li>
 *   <li>{@link #writeInTransaction}/{@link #writeFailureInNewTransaction}：
 *       struct 认证实现（A01–A06、A38–A39）的 {@link AuditEvent} 结构化事件。</li>
 * </ul>
 */
@Service
@ConditionalOnProperty(prefix = "rbac.service", name = "impl", havingValue = "struct", matchIfMissing = true)
public class StructAuditService implements AuditService {

    private final OperationLogMapper operationLogMapper;
    private final SecurityStateMapper securityStateMapper;

    public StructAuditService(OperationLogMapper operationLogMapper, SecurityStateMapper securityStateMapper) {
        this.operationLogMapper = operationLogMapper;
        this.securityStateMapper = securityStateMapper;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void writeSuccess(SysOperationLog log) {
        operationLogMapper.insert(log);
        if ("SUCCESS".equals(log.getResult())) incrementPolicyFor(EventType.valueOf(log.getEventType()));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(SysOperationLog log) {
        operationLogMapper.insert(log);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void writeInTransaction(AuditEvent event) {
        operationLogMapper.insert(toLog(event));
        if (event.success()) incrementPolicyFor(event.eventType());
    }

    private void incrementPolicyFor(EventType eventType) {
        if (eventType == null) return;
        boolean mutation = switch (eventType) {
            case LOGIN, LOGOUT, PASSWORD_CHANGE, AUTHZ_DENY, DEMO_SUBMIT -> false;
            default -> true;
        };
        if (mutation && securityStateMapper.incrementPolicyVersion(LocalDateTime.now(ZoneOffset.UTC)) != 1) {
            throw new IllegalStateException("缺少安全配置状态行");
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeFailureInNewTransaction(AuditEvent event) {
        operationLogMapper.insert(toLog(event));
    }

    /** AuditEvent → sys_operation_log 行；HTTP 上下文（方法/路径/IP/追踪 ID）取自当前请求，事件与触发它的 HTTP 请求同记一条。 */
    private static SysOperationLog toLog(AuditEvent event) {
        SysOperationLog log = new SysOperationLog();
        log.setUserId(event.subjectUserId());
        log.setUsernameSnapshot(event.subjectUsername());
        log.setEventType(event.eventType() == null ? null : event.eventType().name());
        log.setTargetType(event.targetType());
        log.setTargetId(event.targetId());
        log.setHttpMethod(currentHttpMethod());
        log.setRequestPath(currentRequestPath());
        log.setRequestId(RequestIds.current());
        log.setResult(event.success() ? "SUCCESS" : "FAILURE");
        log.setReasonCode(event.reasonCode());
        log.setSourceIp(currentSourceIp());
        log.setCreateTime(LocalDateTime.now(ZoneOffset.UTC));
        return log;
    }

    /** 当前 MVC 请求；AuditEvent 均由 HTTP 端点触发，理论上必能取到，兜底仅为满足列 NOT NULL/CHECK。 */
    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }

    private static String currentHttpMethod() {
        HttpServletRequest request = currentRequest();
        return request != null ? request.getMethod() : "POST";
    }

    private static String currentRequestPath() {
        HttpServletRequest request = currentRequest();
        return request != null ? request.getRequestURI() : "internal";
    }

    private static String currentSourceIp() {
        HttpServletRequest request = currentRequest();
        return request != null ? request.getRemoteAddr() : null;
    }
}
