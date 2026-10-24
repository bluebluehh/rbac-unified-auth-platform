package com.rbac.dto.log;

import com.rbac.dto.common.PageQuery;

/**
 * 审计日志分页查询输入（A36）：from/to 为 ISO8601 UTC 字符串（必填），
 * 解析、跨度（≤31 天）与枚举合法性均由 Service 校验；其余条件可选。
 */
public class LogQueryDTO extends PageQuery {

    /** 起始时间，ISO8601 UTC 字符串，必填，由 Service 校验解析。 */
    private String from;

    /** 截止时间，ISO8601 UTC 字符串，必填，由 Service 校验解析。 */
    private String to;

    /** 按用户 ID 过滤，可空。 */
    private String userId;

    /** 按事件类型过滤（EventType 枚举值），可空。 */
    private String eventType;

    /** 按审计结果过滤（SUCCESS/FAILURE），可空。 */
    private String result;

    /** 按请求 ID 精确过滤，可空。 */
    private String requestId;

    public LogQueryDTO() {
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
