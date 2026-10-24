package com.rbac.service.struct;

import com.rbac.common.enums.EventType;
import com.rbac.security.CurrentUser;
import com.rbac.security.CurrentUserContext;
import com.rbac.service.AuditService;
import com.rbac.service.AuditService.AuditEvent;
import com.rbac.service.DemoService;
import com.rbac.vo.demo.DemoActionVO;
import com.rbac.vo.demo.DemoProjectVO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * A38 / A39 固定权限演示（设计文档 §1.1 第 8 条、§10.1）。
 *
 * <p>只返回合成数据，不读写任何真实业务表。A38 由端点权限注解拦截，
 * A39 在短事务内写审计：审计失败整笔回滚并返回 500，不返回 accepted=true。
 */
@Service
@ConditionalOnProperty(prefix = "rbac.service", name = "impl", havingValue = "struct", matchIfMissing = true)
public class StructDemoService implements DemoService {

    /** 合成演示项目，id 与业务表无关。 */
    private static final List<DemoProjectVO> SYNTHETIC_PROJECTS =
            List.of(new DemoProjectVO("9001", "合成道路项目"));

    private static final String OPERATION_SUBMIT = "DEMO_SUBMIT";

    private final AuditService auditService;
    private final TransactionTemplate transactionTemplate;

    public StructDemoService(AuditService auditService, PlatformTransactionManager transactionManager) {
        this.auditService = auditService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public List<DemoProjectVO> listProjects() {
        return SYNTHETIC_PROJECTS;
    }

    @Override
    public DemoActionVO submit() {
        CurrentUser current = CurrentUserContext.get();
        Long subjectUserId = current == null ? null : current.userId();
        String subjectUsername = current == null ? null : current.username();

        return transactionTemplate.execute(status -> {
            auditService.writeInTransaction(
                    new AuditEvent(EventType.DEMO_SUBMIT, null, null,
                            subjectUserId, subjectUsername, true, "OK"));
            return new DemoActionVO(true, OPERATION_SUBMIT);
        });
    }
}
