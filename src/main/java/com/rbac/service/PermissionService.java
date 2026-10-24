package com.rbac.service;

import com.rbac.common.PageResult;
import com.rbac.dto.common.BinaryStatusDTO;
import com.rbac.dto.permission.PermissionCreateDTO;
import com.rbac.dto.permission.PermissionQueryDTO;
import com.rbac.vo.permission.PermissionVO;

/**
 * 权限点维护业务契约（A32-A35）。四段权限码由服务端生成。
 */
public interface PermissionService {

    /** A32 分页：keyword 匹配显示名或权限码。 */
    PageResult<PermissionVO> list(PermissionQueryDTO query);

    PermissionVO get(String id);

    /** A34 创建：四段码服务生成；同编码名称需一致；同 tuple 重复 409。 */
    PermissionVO create(PermissionCreateDTO dto);

    /** A35 启停：固定技术权限（platform:*）不能停用。 */
    PermissionVO changeStatus(String id, Long expectedVersion, BinaryStatusDTO dto);
}
