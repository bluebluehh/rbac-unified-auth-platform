package com.rbac.service;

import com.rbac.common.PageResult;
import com.rbac.dto.log.LogQueryDTO;
import com.rbac.vo.log.LogVO;

/**
 * 操作日志只读查询业务契约（A36-A37）。时间/id 降序，只读，无修改接口。
 */
public interface LogService {

    PageResult<LogVO> list(LogQueryDTO query);

    LogVO get(String id);
}
