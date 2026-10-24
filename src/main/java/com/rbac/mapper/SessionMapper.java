package com.rbac.mapper;

import com.rbac.entity.SysSession;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 会话表 sys_session 的 Mapper（动态 SQL 全部参数化）。
 */
@Mapper
public interface SessionMapper {

    /** 按令牌哈希查询会话 */
    @Select("SELECT * FROM sys_session WHERE token_hash = #{tokenHash}")
    SysSession selectByTokenHash(@Param("tokenHash") byte[] tokenHash);

    /** 按令牌哈希加行锁查询会话（struct 退出/撤销链路使用，须在事务内调用） */
    @Select("SELECT * FROM sys_session WHERE token_hash = #{tokenHash} FOR UPDATE")
    SysSession lockByTokenHash(@Param("tokenHash") byte[] tokenHash);

    /** 插入会话（主键为调用方生成的会话 ID，不回填自增） */
    @Insert("INSERT INTO sys_session (id, user_id, token_hash, restricted, created_at, expires_at, last_seen_at) " +
            "VALUES (#{id}, #{userId}, #{tokenHash}, #{restricted}, #{createdAt}, #{expiresAt}, #{lastSeenAt})")
    int insert(SysSession session);

    /** 按 ID 撤销单个会话（幂等：仅未撤销的会话生效） */
    @Update("UPDATE sys_session SET revoked_at = #{now} WHERE id = #{id} AND revoked_at IS NULL")
    int revoke(@Param("id") String id, @Param("now") LocalDateTime now);

    /** 撤销指定用户的全部未撤销会话 */
    @Update("UPDATE sys_session SET revoked_at = #{now} WHERE user_id = #{userId} AND revoked_at IS NULL")
    int revokeAllByUser(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    /** 撤销指定用户的全部未撤销会话（struct A04 改密后使用，与 revokeAllByUser 同语义） */
    @Update("UPDATE sys_session SET revoked_at = #{now} WHERE user_id = #{userId} AND revoked_at IS NULL")
    int revokeAllByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    /** 续活会话：仅未撤销、未过期且未超过空闲阈值的会话更新最近活跃时间 */
    @Update("UPDATE sys_session SET last_seen_at = #{now} " +
            "WHERE id = #{id} AND revoked_at IS NULL AND expires_at > #{now} AND last_seen_at > #{idleCutoff}")
    int touch(@Param("id") String id, @Param("now") LocalDateTime now,
              @Param("idleCutoff") LocalDateTime idleCutoff);
}
