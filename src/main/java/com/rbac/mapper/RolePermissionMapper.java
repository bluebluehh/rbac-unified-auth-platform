package com.rbac.mapper;

import com.rbac.entity.SysPermission;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色-权限关联表 sys_role_permission 的 Mapper（动态 SQL 全部参数化）。
 */
@Mapper
public interface RolePermissionMapper {

    /** 查询角色拥有的全部权限 ID */
    @Select("SELECT permission_id FROM sys_role_permission WHERE role_id = #{roleId}")
    List<Long> selectPermissionIdsByRoleId(@Param("roleId") Long roleId);

    /** 查询角色拥有的全部权限明细 */
    @Select("SELECT p.* FROM sys_role_permission rp " +
            "JOIN sys_permission p ON p.id = rp.permission_id " +
            "WHERE rp.role_id = #{roleId} ORDER BY p.id")
    List<SysPermission> selectPermissionsByRoleId(@Param("roleId") Long roleId);

    /** 删除角色的全部权限分配 */
    @Delete("DELETE FROM sys_role_permission WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") Long roleId);

    /** 批量插入角色-权限关联 */
    @Insert("<script>" +
            "INSERT INTO sys_role_permission (role_id, permission_id) VALUES " +
            "<foreach collection='permissionIds' item='permissionId' separator=','>(#{roleId}, #{permissionId})</foreach>" +
            "</script>")
    int batchInsert(@Param("roleId") Long roleId, @Param("permissionIds") List<Long> permissionIds);
}
