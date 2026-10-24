package com.rbac.common.enums;

/**
 * 动作字典（源业务动作 + 技术动作 submit/status/reset_password/assign_role/grant）。
 * code 为 API 提交值，label 仅用于展示；字典接口返回 code/label。
 */
public enum ActionCode {
    VIEW("view", "查看"),
    CREATE("create", "新增"),
    EDIT("edit", "编辑"),
    EXPORT("export", "导出"),
    APPROVE("approve", "审批"),
    REVIEW("review", "审核"),
    REGISTER("register", "登记"),
    FORWARD("forward", "转发"),
    DRAFT("draft", "起草"),
    ARCHIVE("archive", "归档"),
    ORGANIZE("organize", "组织"),
    PUBLISH("publish", "发布"),
    SCORE("score", "评分"),
    ENROLL("enroll", "报名"),
    BORROW("borrow", "借阅"),
    APPLY("apply", "申请"),
    CHECK_IN("check_in", "签到"),
    HANDLE("handle", "办理"),
    REPORT("report", "上报"),
    DISPATCH("dispatch", "调度"),
    RECEIVE("receive", "接收"),
    FEEDBACK("feedback", "反馈"),
    ACCEPT("accept", "受理"),
    INITIATE("initiate", "发起"),
    HOST("host", "主持"),
    PUSH("push", "推送"),
    SEARCH("search", "检索"),
    LEARN("learn", "学习"),
    TAKE_EXAM("take_exam", "参加考试"),
    GRADE("grade", "阅卷"),
    STATISTICS("statistics", "统计"),
    DISPOSE("dispose", "处置"),
    STATUS("status", "启停"),
    RESET_PASSWORD("reset_password", "重置密码"),
    ASSIGN_ROLE("assign_role", "分配角色"),
    GRANT("grant", "授权"),
    SUBMIT("submit", "提交");

    private final String code;
    private final String label;

    ActionCode(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /** 按 code 查找枚举；未登记返回 null。 */
    public static ActionCode of(String code) {
        for (ActionCode a : values()) {
            if (a.code.equals(code)) {
                return a;
            }
        }
        return null;
    }
}
