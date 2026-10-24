package com.rbac.entity;

import java.time.LocalDateTime;

/**
 * 操作/安全审计日志实体，对应表 sys_operation_log（只插入，不修改）。
 */
public class SysOperationLog {

    /** 主键 */
    private Long id;
    /** 操作用户 ID（可为 NULL，如匿名请求） */
    private Long userId;
    /** 操作时的用户名快照 */
    private String usernameSnapshot;
    /** 事件类型 */
    private String eventType;
    /** 目标对象类型 */
    private String targetType;
    /** 目标对象 ID */
    private String targetId;
    /** HTTP 方法 */
    private String httpMethod;
    /** 请求路径 */
    private String requestPath;
    /** 请求追踪 ID */
    private String requestId;
    /** 结果（SUCCESS / DENIED / FAILED 等） */
    private String result;
    /** 失败/拒绝原因码 */
    private String reasonCode;
    /** 来源 IP */
    private String sourceIp;
    /** 补充详情（JSON 文本等） */
    private String details;
    /** 创建时间 */
    private LocalDateTime createTime;

    public SysOperationLog() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsernameSnapshot() {
        return usernameSnapshot;
    }

    public void setUsernameSnapshot(String usernameSnapshot) {
        this.usernameSnapshot = usernameSnapshot;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public String getRequestPath() {
        return requestPath;
    }

    public void setRequestPath(String requestPath) {
        this.requestPath = requestPath;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    public String getSourceIp() {
        return sourceIp;
    }

    public void setSourceIp(String sourceIp) {
        this.sourceIp = sourceIp;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
