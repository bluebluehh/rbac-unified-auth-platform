package com.rbac.mapper;

import com.rbac.entity.SysRole;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 角色表 sys_role 的 Mapper（动态 SQL 全部参数化，更新走乐观锁）。
 */
@Mapper
public interface RoleMapper {

    /** 分页查询：keyword 模糊匹配角色名称/角色键，status 等值过滤 */
    @Select("<script>" +
            "SELECT * FROM sys_role" +
            "<where>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            " AND (role_name LIKE CONCAT('%',#{keyword},'%') OR role_key LIKE CONCAT('%',#{keyword},'%'))" +
            "</if>" +
            "<if test='status != null'> AND status = #{status}</if>" +
            "</where>" +
            " ORDER BY id ASC LIMIT #{offset},#{size}" +
            "</script>")
    List<SysRole> selectPage(@Param("keyword") String keyword, @Param("status") Integer status,
                             @Param("offset") int offset, @Param("size") int size);

    /** 分页计数：过滤条件同 selectPage */
    @Select("<script>" +
            "SELECT COUNT(*) FROM sys_role" +
            "<where>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            " AND (role_name LIKE CONCAT('%',#{keyword},'%') OR role_key LIKE CONCAT('%',#{keyword},'%'))" +
            "</if>" +
            "<if test='status != null'> AND status = #{status}</if>" +
            "</where>" +
            "</script>")
    long countPage(@Param("keyword") String keyword, @Param("status") Integer status);

    /** 按主键查询角色 */
    @Select("SELECT * FROM sys_role WHERE id = #{id}")
    SysRole selectById(@Param("id") Long id);

    /** 按角色键查询角色 */
    @Select("SELECT * FROM sys_role WHERE role_key = #{roleKey}")
    SysRole selectByRoleKey(@Param("roleKey") String roleKey);

    /** 插入角色，回填自增主键 */
    @Insert("INSERT INTO sys_role (role_name, role_key, description, status, create_time, update_time, version) " +
            "VALUES (#{roleName}, #{roleKey}, #{description}, #{status}, #{createTime}, #{updateTime}, #{version})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SysRole role);

    /** 更新角色资料：名称非空才更新；clearDescription 为 true 时清空描述；乐观锁校验版本号 */
    @Update("<script>" +
            "UPDATE sys_role" +
            "<set>" +
            "<if test='roleName != null'>role_name = #{roleName},</if>" +
            "<choose>" +
            "<when test='clearDescription'>description = NULL,</when>" +
            "<otherwise><if test='description != null'>description = #{description},</if></otherwise>" +
            "</choose>" +
            "update_time = UTC_TIMESTAMP(3), version = version + 1" +
            "</set>" +
            " WHERE id = #{id} AND version = #{expectedVersion}" +
            "</script>")
    int updateProfile(@Param("id") Long id, @Param("expectedVersion") Long expectedVersion,
                      @Param("roleName") String roleName, @Param("description") String description,
                      @Param("clearDescription") boolean clearDescription);

    /** 更新角色状态，乐观锁校验版本号 */
    @Update("UPDATE sys_role SET status = #{status}, " +
            "update_time = UTC_TIMESTAMP(3), version = version + 1 " +
            "WHERE id = #{id} AND version = #{expectedVersion}")
    int updateStatus(@Param("id") Long id, @Param("expectedVersion") Long expectedVersion,
                     @Param("status") Integer status);

    /** 仅递增业务版本号（A26 权限全量替换），乐观锁校验版本号 */
    @Update("UPDATE sys_role SET update_time = UTC_TIMESTAMP(3), version = version + 1 " +
            "WHERE id = #{id} AND version = #{expectedVersion}")
    int bumpVersion(@Param("id") Long id, @Param("expectedVersion") Long expectedVersion);
}
