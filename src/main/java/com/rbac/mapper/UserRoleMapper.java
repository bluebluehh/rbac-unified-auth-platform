package com.rbac.mapper;

import com.rbac.entity.SysRole;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户-角色关联表 sys_user_role 的 Mapper（动态 SQL 全部参数化）。
 */
@Mapper
public interface UserRoleMapper {

    /** 查询用户拥有的全部角色（含停用角色） */
    @Select("SELECT r.* FROM sys_user_role ur " +
            "JOIN sys_role r ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId} ORDER BY r.id")
    List<SysRole> selectRolesByUserId(@Param("userId") Long userId);

    /** 查询用户的全部角色 ID（含停用角色） */
    @Select("SELECT role_id FROM sys_user_role WHERE user_id = #{userId}")
    List<Long> selectRoleIdsByUserId(@Param("userId") Long userId);

    /** 查询用户的启用状态角色 ID */
    @Select("SELECT ur.role_id FROM sys_user_role ur " +
            "JOIN sys_role r ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND r.status = 1")
    List<Long> selectEnabledRoleIdsByUser(@Param("userId") Long userId);

    /** 查询用户的启用状态角色 ID（struct 权限计算使用，与 selectEnabledRoleIdsByUser 同语义） */
    @Select("SELECT ur.role_id FROM sys_user_role ur " +
            "JOIN sys_role r ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND r.status = 1")
    List<Long> selectEnabledRoleIdsByUserId(@Param("userId") Long userId);

    /** 删除用户的全部分配角色 */
    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);

    /** 批量插入用户-角色关联 */
    @Insert("<script>" +
            "INSERT INTO sys_user_role (user_id, role_id) VALUES " +
            "<foreach collection='roleIds' item='roleId' separator=','>(#{userId}, #{roleId})</foreach>" +
            "</script>")
    int batchInsert(@Param("userId") Long userId, @Param("roleIds") List<Long> roleIds);

    /** 查询拥有指定角色键的全部用户 ID */
    @Select("SELECT ur.user_id FROM sys_user_role ur " +
            "JOIN sys_role r ON r.id = ur.role_id " +
            "WHERE r.role_key = #{roleKey}")
    List<Long> selectUserIdsByRoleKey(@Param("roleKey") String roleKey);
}
