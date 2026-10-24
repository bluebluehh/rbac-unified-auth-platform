package com.rbac.service;

import com.rbac.common.PageResult;
import com.rbac.dto.common.BinaryStatusDTO;
import com.rbac.dto.auth.PasswordResetDTO;
import com.rbac.dto.user.UserCreateDTO;
import com.rbac.dto.user.UserPatchDTO;
import com.rbac.dto.user.UserQueryDTO;
import com.rbac.dto.user.UserRoleAssignDTO;
import com.rbac.dto.user.UserStatusDTO;
import com.rbac.vo.user.UserRolesVO;
import com.rbac.vo.user.UserVO;

/**
 * 用户与角色分配业务契约（A07-A14）。方法签名对齐设计文档 §11.1。
 */
public interface UserService {

    /** A07 分页查询；keyword 匹配工号或姓名。 */
    PageResult<UserVO> list(UserQueryDTO query);

    /** A08 详情（Controller 附加 ETag）。 */
    UserVO get(String id);

    /** A09 创建：初始无密码、无角色、PENDING_ACTIVATION。 */
    UserVO create(UserCreateDTO dto);

    /** A10 部分更新：部门不可跨指定公司；调动撤销本人会话。 */
    UserVO update(String id, Long expectedVersion, UserPatchDTO dto);

    /** A11 状态变更：停用撤销会话；恢复 ENABLED 须已改首次密码。 */
    UserVO changeStatus(String id, Long expectedVersion, UserStatusDTO dto);

    /** A12 重置临时密码：DISABLED 仍 DISABLED，其余 PENDING；管理员保护；不回显密码。 */
    UserVO resetPassword(String id, Long expectedVersion, PasswordResetDTO dto);

    /** A13 当前已分配直接角色（含停用，不伪造继承角色）。 */
    UserRolesVO getUserRoles(String id);

    /** A14 完整替换角色集合：重复/不存在/新增停用角色 400；事务和管理员保护。 */
    UserRolesVO assignRoles(String id, Long expectedVersion, UserRoleAssignDTO dto);
}
