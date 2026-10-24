package com.rbac.service;

import com.rbac.dto.auth.LoginDTO;
import com.rbac.dto.auth.PasswordChangeDTO;
import com.rbac.vo.auth.CurrentPermissionsVO;
import com.rbac.vo.auth.LoginVO;
import com.rbac.vo.application.ApplicationVO;
import com.rbac.vo.user.UserVO;

import java.util.List;

/**
 * 认证门户业务契约（A01-A06）。实现类由 struct/oop 提供者交付。
 * Service 只返回业务数据，不返回 ResponseEntity，不自行包装 Result。
 */
public interface AuthService {

    /** A01 登录：校验工号密码、限流与锁定，签发普通或受限会话，返回唯一一次 Token。 */
    LoginVO login(LoginDTO dto, String clientIp);

    /** A02 退出：撤销对应会话；无 Token/旧 Token 重复退出也成功，不暴露主体。 */
    void logout(String rawToken);

    /** A03 当前用户信息（SELF，受限会话 403）。 */
    UserVO getCurrentUser();

    /** A04 改密/首次激活：成功后撤销全部会话，前端清 Token 重新登录。 */
    void changePassword(PasswordChangeDTO dto);

    /** A05 门户应用卡片：至少有一个有效原子权限才显示对应启用应用。 */
    List<ApplicationVO> getApps();

    /** A06 当前有效权限 key 与启用直接角色；applicationId 为 null 返回全部；未知应用 404。 */
    CurrentPermissionsVO getPermissions(Long applicationId);
}
