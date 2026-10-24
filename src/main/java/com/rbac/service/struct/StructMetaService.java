package com.rbac.service.struct;

import com.rbac.common.enums.EventType;
import com.rbac.common.enums.UserStatus;
import com.rbac.service.MetaService;
import com.rbac.vo.auth.DictionaryVO;
import com.rbac.vo.auth.DictionaryVO.ActionOption;
import com.rbac.vo.auth.DictionaryVO.Limits;
import com.rbac.vo.auth.DictionaryVO.Option;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * A40 集中字典（设计文档 §10.5）。
 *
 * <p>字典是版本化只读配置：不新增数据库表、不提供写接口，也不表示当前用户已被授权。
 * 前端统一从此接口取枚举与中文标签，避免前后端各维护一套不一致的映射。
 */
@Service
@ConditionalOnProperty(prefix = "rbac.service", name = "impl", havingValue = "struct", matchIfMissing = true)
public class StructMetaService implements MetaService {

    public static final String DICTIONARY_VERSION = "RBAC0-V2.1";

    /** 事件白名单与中文标签，顺序与设计文档 §4.10 一致。 */
    private static final List<Option> EVENT_TYPES = List.of(
            new Option(EventType.LOGIN.name(), "登录"),
            new Option(EventType.LOGOUT.name(), "退出"),
            new Option(EventType.USER_CREATE.name(), "新增员工"),
            new Option(EventType.USER_UPDATE.name(), "修改员工"),
            new Option(EventType.USER_STATUS.name(), "员工状态"),
            new Option(EventType.PASSWORD_RESET.name(), "重置密码"),
            new Option(EventType.PASSWORD_CHANGE.name(), "修改密码"),
            new Option(EventType.USER_ROLE_ASSIGN.name(), "分配用户角色"),
            new Option(EventType.ROLE_CREATE.name(), "新增角色"),
            new Option(EventType.ROLE_UPDATE.name(), "修改角色"),
            new Option(EventType.ROLE_STATUS.name(), "角色状态"),
            new Option(EventType.ROLE_PERMISSION_ASSIGN.name(), "分配角色权限"),
            new Option(EventType.DEPT_CREATE.name(), "新增部门"),
            new Option(EventType.DEPT_UPDATE.name(), "修改部门"),
            new Option(EventType.DEPT_STATUS.name(), "部门状态"),
            new Option(EventType.APP_CREATE.name(), "新增应用"),
            new Option(EventType.APP_UPDATE.name(), "修改应用"),
            new Option(EventType.APP_STATUS.name(), "应用状态"),
            new Option(EventType.PERMISSION_CREATE.name(), "新增权限"),
            new Option(EventType.PERMISSION_STATUS.name(), "权限状态"),
            new Option(EventType.AUTHZ_DENY.name(), "权限拒绝"),
            new Option(EventType.DEMO_SUBMIT.name(), "演示提交"));

    /** 业务动作（§7.4）+ 技术动作（§10.1）+ 演示动作（§10.1）的并集。 */
    private static final List<ActionOption> ACTIONS = List.of(
            new ActionOption("view", "查看"),
            new ActionOption("create", "新增"),
            new ActionOption("edit", "编辑"),
            new ActionOption("export", "导出"),
            new ActionOption("approve", "审批"),
            new ActionOption("review", "审核"),
            new ActionOption("register", "登记"),
            new ActionOption("forward", "转发"),
            new ActionOption("draft", "拟稿"),
            new ActionOption("archive", "归档"),
            new ActionOption("organize", "组织"),
            new ActionOption("publish", "发布"),
            new ActionOption("score", "打分"),
            new ActionOption("enroll", "报名"),
            new ActionOption("borrow", "借阅"),
            new ActionOption("apply", "申请"),
            new ActionOption("check_in", "打卡"),
            new ActionOption("handle", "处理"),
            new ActionOption("report", "上报"),
            new ActionOption("dispatch", "派发"),
            new ActionOption("receive", "接收"),
            new ActionOption("feedback", "反馈"),
            new ActionOption("accept", "验收"),
            new ActionOption("initiate", "发起"),
            new ActionOption("host", "主持"),
            new ActionOption("push", "推送"),
            new ActionOption("search", "检索"),
            new ActionOption("learn", "学习"),
            new ActionOption("take_exam", "参加考试"),
            new ActionOption("grade", "阅卷"),
            new ActionOption("statistics", "统计"),
            new ActionOption("dispose", "处置"),
            new ActionOption("status", "启停"),
            new ActionOption("reset_password", "重置密码"),
            new ActionOption("assign_role", "分配角色"),
            new ActionOption("grant", "授予权限"),
            new ActionOption("submit", "演示提交"));

    private static final Limits LIMITS = new Limits(100, 100, 5000, 12, 64, 31);

    private static final DictionaryVO DICTIONARY = new DictionaryVO(
            DICTIONARY_VERSION,
            List.of(
                    new Option(UserStatus.ENABLED.name(), "正常"),
                    new Option(UserStatus.DISABLED.name(), "停用"),
                    new Option(UserStatus.PENDING_ACTIVATION.name(), "待激活")),
            List.of(
                    new Option("1", "启用"),
                    new Option("0", "停用")),
            List.of(
                    new Option("ROOT", "总公司"),
                    new Option("COMPANY", "分公司"),
                    new Option("DEPARTMENT", "部门"),
                    new Option("PROJECT_DEPT", "项目部")),
            List.of(
                    new Option("SUCCESS", "成功"),
                    new Option("FAILURE", "失败")),
            EVENT_TYPES,
            ACTIONS,
            LIMITS,
            List.of("PLATFORM_ADMIN"),
            List.of("platform"));

    @Override
    public DictionaryVO getDictionaries() {
        return DICTIONARY;
    }

    /** 便于前端与测试核对计数：事件 22 条、动作 37 条。 */
    public static List<String> actionCodes() {
        List<String> codes = new ArrayList<>(ACTIONS.size());
        for (ActionOption action : ACTIONS) {
            codes.add(action.code());
        }
        return List.copyOf(codes);
    }
}
