package com.rbac.mapper;

import com.rbac.entity.SysPermission;
import com.rbac.security.EffectivePermission;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 权限表 sys_permission 的 Mapper（动态 SQL 全部参数化，更新走乐观锁）。
 */
@Mapper
public interface PermissionMapper {

    /** 分页查询：applicationId 等值过滤，keyword 模糊匹配资源名称/权限键/权限名称；按功能板块聚组排序 */
    @Select("<script>" +
            "SELECT * FROM sys_permission" +
            "<where>" +
            "<if test='applicationId != null'> AND application_id = #{applicationId}</if>" +
            "<if test='moduleKey != null and moduleKey != \"\"'> AND module_key = #{moduleKey}</if>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            " AND (resource_name LIKE CONCAT('%',#{keyword},'%') OR perm_key LIKE CONCAT('%',#{keyword},'%')" +
            " OR perm_name LIKE CONCAT('%',#{keyword},'%'))" +
            "</if>" +
            "<if test='status != null'> AND status = #{status}</if>" +
            "</where>" +
            " ORDER BY module_key ASC, module_name ASC, id ASC LIMIT #{offset},#{size}" +
            "</script>")
    List<SysPermission> selectPage(@Param("applicationId") Long applicationId, @Param("moduleKey") String moduleKey,
                                   @Param("keyword") String keyword, @Param("status") Integer status,
                                   @Param("offset") int offset, @Param("size") int size);

    /** 分页计数：过滤条件同 selectPage */
    @Select("<script>" +
            "SELECT COUNT(*) FROM sys_permission" +
            "<where>" +
            "<if test='applicationId != null'> AND application_id = #{applicationId}</if>" +
            "<if test='moduleKey != null and moduleKey != \"\"'> AND module_key = #{moduleKey}</if>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            " AND (resource_name LIKE CONCAT('%',#{keyword},'%') OR perm_key LIKE CONCAT('%',#{keyword},'%')" +
            " OR perm_name LIKE CONCAT('%',#{keyword},'%'))" +
            "</if>" +
            "<if test='status != null'> AND status = #{status}</if>" +
            "</where>" +
            "</script>")
    long countPage(@Param("applicationId") Long applicationId, @Param("moduleKey") String moduleKey,
                   @Param("keyword") String keyword, @Param("status") Integer status);

    /** 按主键查询权限 */
    @Select("SELECT * FROM sys_permission WHERE id = #{id}")
    SysPermission selectById(@Param("id") Long id);

    /** 按权限键查询权限 */
    @Select("SELECT * FROM sys_permission WHERE perm_key = #{permKey}")
    SysPermission selectByPermKey(@Param("permKey") String permKey);

    /** 按应用-模块-资源-动作四元组精确定位权限 */
    @Select("SELECT * FROM sys_permission WHERE application_id = #{applicationId} " +
            "AND module_key = #{moduleKey} AND resource_key = #{resourceKey} AND action_code = #{actionCode}")
    SysPermission selectByTuple(@Param("applicationId") Long applicationId, @Param("moduleKey") String moduleKey,
                                @Param("resourceKey") String resourceKey, @Param("actionCode") String actionCode);

    /** 插入权限，回填自增主键 */
    @Insert("INSERT INTO sys_permission (application_id, module_key, module_name, resource_key, resource_name, " +
            "resource_description, action_code, perm_name, perm_key, source_row, status, " +
            "create_time, update_time, version) " +
            "VALUES (#{applicationId}, #{moduleKey}, #{moduleName}, #{resourceKey}, #{resourceName}, " +
            "#{resourceDescription}, #{actionCode}, #{permName}, #{permKey}, #{sourceRow}, #{status}, " +
            "#{createTime}, #{updateTime}, #{version})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SysPermission p);

    /** 更新权限状态，乐观锁校验版本号 */
    @Update("UPDATE sys_permission SET status = #{status}, " +
            "update_time = UTC_TIMESTAMP(3), version = version + 1 " +
            "WHERE id = #{id} AND version = #{expectedVersion}")
    int updateStatus(@Param("id") Long id, @Param("expectedVersion") Long expectedVersion,
                     @Param("status") Integer status);

    /** 按启用角色集合查询有效权限（角色/权限/应用全 status=1；struct 权限计算使用） */
    @Select("<script>" +
            "SELECT p.* FROM sys_role_permission rp " +
            "JOIN sys_role r ON r.id = rp.role_id AND r.status = 1 " +
            "JOIN sys_permission p ON p.id = rp.permission_id AND p.status = 1 " +
            "JOIN sys_application a ON a.id = p.application_id AND a.status = 1 " +
            "WHERE rp.role_id IN " +
            "<foreach collection='roleIds' item='roleId' open='(' separator=',' close=')'>#{roleId}</foreach>" +
            "</script>")
    List<SysPermission> selectEffectiveByRoleIds(@Param("roleIds") List<Long> roleIds);

    /**
     * 查询用户生效权限（仅启用角色/权限/应用，且要求用户自身处于可用状态）。
     * 用户状态前置条件：ENABLED、未强制改密、密码哈希非空。
     */
    @Select("SELECT p.id AS id, p.perm_key AS permKey, p.application_id AS applicationId " +
            "FROM sys_user_role ur " +
            "JOIN sys_role r ON r.id = ur.role_id AND r.status = 1 " +
            "JOIN sys_role_permission rp ON rp.role_id = r.id " +
            "JOIN sys_permission p ON p.id = rp.permission_id AND p.status = 1 " +
            "JOIN sys_application a ON a.id = p.application_id AND a.status = 1 " +
            "WHERE ur.user_id = #{userId} " +
            "AND EXISTS (SELECT 1 FROM sys_user uu WHERE uu.id = #{userId} AND uu.status = 'ENABLED' " +
            "AND uu.must_change_password = 0 AND uu.password_hash IS NOT NULL)")
    List<EffectivePermission> selectEffectivePermissionsByUser(@Param("userId") Long userId);

    /** 查询用户生效权限所涉及的全部启用应用 ID */
    @Select("SELECT DISTINCT p.application_id FROM sys_user_role ur " +
            "JOIN sys_role r ON r.id = ur.role_id AND r.status = 1 " +
            "JOIN sys_role_permission rp ON rp.role_id = r.id " +
            "JOIN sys_permission p ON p.id = rp.permission_id AND p.status = 1 " +
            "JOIN sys_application a ON a.id = p.application_id AND a.status = 1 " +
            "WHERE ur.user_id = #{userId}")
    List<Long> selectEnabledAppIdsByUser(@Param("userId") Long userId);
}
