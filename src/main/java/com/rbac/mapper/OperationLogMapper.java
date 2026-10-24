package com.rbac.mapper;

import com.rbac.entity.SysOperationLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 操作/审计日志表 sys_operation_log 的 Mapper（动态 SQL 全部参数化，只插入与查询）。
 */
@Mapper
public interface OperationLogMapper {

    /** 插入审计日志，回填自增主键 */
    @Insert("INSERT INTO sys_operation_log (user_id, username_snapshot, event_type, target_type, target_id, " +
            "http_method, request_path, request_id, result, reason_code, source_ip, details, create_time) " +
            "VALUES (#{userId}, #{usernameSnapshot}, #{eventType}, #{targetType}, #{targetId}, " +
            "#{httpMethod}, #{requestPath}, #{requestId}, #{result}, #{reasonCode}, #{sourceIp}, #{details}, " +
            "#{createTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SysOperationLog log);

    /**
     * 分页查询审计日志：固定按创建时间范围过滤，动态追加用户/事件类型/结果/请求 ID 等值过滤。
     */
    @Select("<script>" +
            "SELECT * FROM sys_operation_log " +
            "WHERE create_time &gt;= #{from} AND create_time &lt; #{to}" +
            "<if test='userId != null'> AND user_id = #{userId}</if>" +
            "<if test='eventType != null and eventType != \"\"'> AND event_type = #{eventType}</if>" +
            "<if test='result != null and result != \"\"'> AND result = #{result}</if>" +
            "<if test='requestId != null and requestId != \"\"'> AND request_id = #{requestId}</if>" +
            " ORDER BY create_time DESC, id DESC LIMIT #{offset},#{size}" +
            "</script>")
    List<SysOperationLog> selectPage(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                     @Param("userId") Long userId, @Param("eventType") String eventType,
                                     @Param("result") String result, @Param("requestId") String requestId,
                                     @Param("offset") int offset, @Param("size") int size);

    /** 分页计数：过滤条件同 selectPage */
    @Select("<script>" +
            "SELECT COUNT(*) FROM sys_operation_log " +
            "WHERE create_time &gt;= #{from} AND create_time &lt; #{to}" +
            "<if test='userId != null'> AND user_id = #{userId}</if>" +
            "<if test='eventType != null and eventType != \"\"'> AND event_type = #{eventType}</if>" +
            "<if test='result != null and result != \"\"'> AND result = #{result}</if>" +
            "<if test='requestId != null and requestId != \"\"'> AND request_id = #{requestId}</if>" +
            "</script>")
    long countPage(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                   @Param("userId") Long userId, @Param("eventType") String eventType,
                   @Param("result") String result, @Param("requestId") String requestId);

    /** 按主键查询审计日志 */
    @Select("SELECT * FROM sys_operation_log WHERE id = #{id}")
    SysOperationLog selectById(@Param("id") Long id);
}
