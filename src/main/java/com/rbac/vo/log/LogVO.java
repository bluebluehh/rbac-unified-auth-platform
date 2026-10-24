package com.rbac.vo.log;

import java.time.LocalDateTime;

/**
 * 审计日志输出（A37）。userId、usernameSnapshot、targetType、targetId、details 允许 null；
 * details 仅为 {changedFields,before,after} 形式的脱敏差异 Map，未适用时为 null；
 * 密码、哈希、Token、会话摘要永不返回。
 */
public class LogVO {

    /** 日志 ID（十进制字符串）。 */
    private String id;

    /** 操作用户 ID，允许 null（匿名/系统事件）。 */
    private String userId;

    /** 操作用户工号快照，允许 null。 */
    private String usernameSnapshot;

    /** 事件类型（EventType 枚举值）。 */
    private String eventType;

    /** 目标类型（TargetType 枚举值），允许 null。 */
    private String targetType;

    /** 目标 ID，允许 null。 */
    private String targetId;

    /** HTTP 方法。 */
    private String httpMethod;

    /** 请求路径。 */
    private String requestPath;

    /** 请求 ID（链路追踪）。 */
    private String requestId;

    /** 审计结果：SUCCESS / FAILURE。 */
    private String result;

    /** 失败原因码，成功时为约定占位值（如 OK），由 Service 决定。 */
    private String reasonCode;

    /** 事件发生时间。 */
    private LocalDateTime createTime;

    /** 脱敏差异明细（changedFields/before/after），允许 null。 */
    private Object details;

    public LogVO() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
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

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public Object getDetails() {
        return details;
    }

    public void setDetails(Object details) {
        this.details = details;
    }
}
