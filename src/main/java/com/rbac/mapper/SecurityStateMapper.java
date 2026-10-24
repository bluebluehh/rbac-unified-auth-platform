package com.rbac.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 安全全局状态表 sys_security_state 的 Mapper（单行记录，FOR UPDATE 用于策略变更加锁）。
 */
@Mapper
public interface SecurityStateMapper {

    /** 悲观锁读取当前策略版本号（行锁，需在事务中使用） */
    @Select("SELECT policy_version FROM sys_security_state WHERE id = 1 FOR UPDATE")
    Long selectForUpdate();

    /** 锁定全局状态行（struct 认证/管理写操作统一锁入口，返回值无业务含义） */
    @Select("SELECT id FROM sys_security_state WHERE id = 1 FOR UPDATE")
    Integer lockStateRow();

    /** 普通读取当前策略版本号 */
    @Select("SELECT policy_version FROM sys_security_state WHERE id = 1")
    Long selectPolicyVersion();

    /** 策略版本号自增（用于使权限等缓存失效） */
    @Update("UPDATE sys_security_state SET policy_version = policy_version + 1, update_time = #{now} WHERE id = 1")
    int incrementPolicyVersion(@Param("now") LocalDateTime now);
}
