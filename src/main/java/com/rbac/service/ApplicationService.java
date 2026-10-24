package com.rbac.service;

import com.rbac.common.PageResult;
import com.rbac.dto.application.ApplicationCreateDTO;
import com.rbac.dto.application.ApplicationPatchDTO;
import com.rbac.dto.application.ApplicationQueryDTO;
import com.rbac.dto.common.BinaryStatusDTO;
import com.rbac.vo.application.ApplicationVO;

/**
 * 应用登记维护业务契约（A27-A31）。
 */
public interface ApplicationService {

    PageResult<ApplicationVO> list(ApplicationQueryDTO query);

    ApplicationVO get(String id);

    /** A29 创建：不自动授权限。 */
    ApplicationVO create(ApplicationCreateDTO dto);

    ApplicationVO update(String id, Long expectedVersion, ApplicationPatchDTO dto);

    /** A31 启停：停用使全部下属权限失效，platform 应用保护。 */
    ApplicationVO changeStatus(String id, Long expectedVersion, BinaryStatusDTO dto);
}
