package com.rbac.service;

import com.rbac.dto.common.BinaryStatusDTO;
import com.rbac.dto.dept.DeptCreateDTO;
import com.rbac.dto.dept.DeptPatchDTO;
import com.rbac.vo.dept.DeptStatusVO;
import com.rbac.vo.dept.DeptTreeVO;
import com.rbac.vo.dept.DeptVO;

import java.util.List;

/**
 * 部门组织树业务契约（A15-A19）。树形组装由 Service 完成，Mapper 只返回平面数据。
 */
public interface DeptService {

    /** A15 组织树：rootId 可选默认 ROOT；includeDisabled 默认 false。 */
    List<DeptTreeVO> tree(String rootId, Boolean includeDisabled);

    DeptVO get(String id);

    /** A17 创建：父节点需启用；节点键唯一；不能创建 ROOT。 */
    DeptVO create(DeptCreateDTO dto);

    /** A18 部分更新：不改变父节点/nodeKey/nodeType。 */
    DeptVO update(String id, Long expectedVersion, DeptPatchDTO dto);

    /** A19 启停：返回受影响子树不同用户数；停用撤销子树账号会话并检查管理员。 */
    DeptStatusVO changeStatus(String id, Long expectedVersion, BinaryStatusDTO dto);
}
