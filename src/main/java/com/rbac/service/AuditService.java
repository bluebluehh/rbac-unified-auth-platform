package com.rbac.service;

import com.rbac.common.enums.EventType;
import com.rbac.entity.SysOperationLog;

/**
 * 审计写入契约。成功审计由业务 Service 在当前事务内显式调用（MANDATORY 传播）；
 * 管理失败在原事务结束后由失败记录组件以独立事务（REQUIRES_NEW）写入。
 * 不向浏览器开放写日志 API。
 */
public interface AuditService {

    /** 事务内成功审计：只能通过 Spring 代理在已有事务中调用（MANDATORY）。 */
    void writeSuccess(SysOperationLog log);

    /** 独立失败事件：REQUIRES_NEW，由切面/过滤器在原事务结束后调用。 */
    void recordFailure(SysOperationLog log);

    /**
     * 结构化审计事件（A01–A06、A38–A39 使用）。
     *
     * @param eventType       事件类型（22 值白名单）
     * @param targetType      目标对象类型（USER / SESSION 等，可为 null）
     * @param targetId        目标对象 ID（可为 null，如匿名登录失败）
     * @param subjectUserId   主体用户 ID（可为 null）
     * @param subjectUsername 主体用户名快照（可为 null）
     * @param success         结果是否成功
     * @param reasonCode      结果原因码（OK / LOGIN_FAILED 等）
     */
    record AuditEvent(EventType eventType, String targetType, String targetId,
                      Long subjectUserId, String subjectUsername,
                      boolean success, String reasonCode) {
    }

    /** 事务内写入结构化审计事件（MANDATORY，调用方必须已有事务）。 */
    void writeInTransaction(AuditEvent event);

    /** 以独立新事务（REQUIRES_NEW）写入结构化失败事件，不受调用方事务回滚影响。 */
    void writeFailureInNewTransaction(AuditEvent event);
}
