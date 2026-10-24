package com.rbac.service.struct;

import com.rbac.common.BusinessException;
import com.rbac.common.PageResult;
import com.rbac.common.ResultCode;
import com.rbac.common.constants.SystemConstants;
import com.rbac.dto.log.LogQueryDTO;
import com.rbac.entity.SysOperationLog;
import com.rbac.mapper.OperationLogMapper;
import com.rbac.service.LogService;
import com.rbac.vo.log.LogVO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 结构化操作日志只读查询实现（A36-A37）：时间/id 降序、必填时间窗口（跨度 ≤ 31 日）。
 * 只读，无 ETag。
 */
@Service
@ConditionalOnProperty(prefix = "rbac.service", name = "impl", havingValue = "struct", matchIfMissing = true)
public class StructLogService implements LogService {

    private final OperationLogMapper operationLogMapper;

    public StructLogService(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    @Override
    public PageResult<LogVO> list(LogQueryDTO query) {
        LocalDateTime from = parseTime(query.getFrom());
        LocalDateTime to = parseTime(query.getTo());
        if (from == null || to == null || !from.isBefore(to)) {
            throw BusinessException.invalidArgument("from/to 为必填 ISO-8601 时间且 from < to", List.of());
        }
        if (to.minusDays(SystemConstants.LOG_RANGE_DAYS_MAX).isAfter(from)) {
            throw BusinessException.invalidArgument("时间窗口跨度不能超过 31 日", List.of());
        }
        int page = query.pageNumber();
        int size = query.sizeNumber();
        List<SysOperationLog> records = operationLogMapper.selectPage(from, to, parseLong(query.getUserId()),
                blankToNull(query.getEventType()), blankToNull(query.getResult()),
                blankToNull(query.getRequestId()), (page - 1) * size, size);
        long total = operationLogMapper.countPage(from, to, parseLong(query.getUserId()),
                blankToNull(query.getEventType()), blankToNull(query.getResult()),
                blankToNull(query.getRequestId()));
        return PageResult.of(records.stream().map(this::toVO).toList(), total, page, size);
    }

    @Override
    public LogVO get(String id) {
        SysOperationLog log = null;
        try {
            log = operationLogMapper.selectById(Long.parseLong(id.trim()));
        } catch (NumberFormatException e) {
            // 非法 ID 走 NOT_FOUND
        }
        if (log == null) {
            throw BusinessException.notFound();
        }
        return toVO(log);
    }

    // ---------- 内部 ----------

    private LogVO toVO(SysOperationLog log) {
        LogVO vo = new LogVO();
        vo.setId(String.valueOf(log.getId()));
        vo.setUserId(log.getUserId() == null ? null : String.valueOf(log.getUserId()));
        vo.setUsernameSnapshot(log.getUsernameSnapshot());
        vo.setEventType(log.getEventType());
        vo.setTargetType(log.getTargetType());
        vo.setTargetId(log.getTargetId());
        vo.setHttpMethod(log.getHttpMethod());
        vo.setRequestPath(log.getRequestPath());
        vo.setRequestId(log.getRequestId());
        vo.setResult(log.getResult());
        vo.setReasonCode(log.getReasonCode());
        vo.setCreateTime(log.getCreateTime());
        vo.setDetails(null); // details 文本骨架不解析，完整版按 JSON 还原
        return vo;
    }

    private static LocalDateTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value.trim()).toInstant()
                    .atZone(ZoneOffset.UTC).toLocalDateTime();
        } catch (Exception e) {
            throw BusinessException.invalidArgument("时间格式须为 ISO-8601（含时区）", List.of());
        }
    }

    private static Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
