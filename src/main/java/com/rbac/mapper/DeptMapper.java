package com.rbac.mapper;

import com.rbac.entity.SysDept;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 部门表 sys_dept 的 Mapper（动态 SQL 全部参数化，更新走乐观锁）。
 */
@Mapper
public interface DeptMapper {

    /** 查询全部部门 */
    @Select("SELECT * FROM sys_dept")
    List<SysDept> selectAll();

    /** 按主键查询部门 */
    @Select("SELECT * FROM sys_dept WHERE id = #{id}")
    SysDept selectById(@Param("id") Long id);

    /** 按主键查询部门（struct 权限计算使用的别名） */
    @Select("SELECT * FROM sys_dept WHERE id = #{id}")
    SysDept findById(@Param("id") Long id);

    /** 查询节点自身与全部祖先（自底向上递归，struct 组织链启用校验使用） */
    @Select("WITH RECURSIVE ancestors AS (" +
            "SELECT * FROM sys_dept WHERE id = #{id} " +
            "UNION ALL " +
            "SELECT d.* FROM sys_dept d JOIN ancestors a ON d.id = a.parent_id" +
            ") SELECT * FROM ancestors")
    List<SysDept> selectSelfAndAncestors(@Param("id") Long id);

    @Select("WITH RECURSIVE descendants AS (SELECT * FROM sys_dept WHERE id = #{id} " +
            "UNION ALL SELECT d.* FROM sys_dept d JOIN descendants p ON d.parent_id = p.id) SELECT * FROM descendants")
    List<SysDept> selectSelfAndDescendants(@Param("id") Long id);

    /** 按节点唯一键查询部门 */
    @Select("SELECT * FROM sys_dept WHERE node_key = #{nodeKey}")
    SysDept selectByNodeKey(@Param("nodeKey") String nodeKey);

    /** 插入部门，回填自增主键 */
    @Insert("INSERT INTO sys_dept (dept_name, node_key, parent_id, node_type, manager_user_id, " +
            "status, create_time, update_time, version) " +
            "VALUES (#{deptName}, #{nodeKey}, #{parentId}, #{nodeType}, #{managerUserId}, " +
            "#{status}, #{createTime}, #{updateTime}, #{version})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SysDept dept);

    /** 更新部门资料：名称非空才更新；clearManager 为 true 时清空负责人；乐观锁校验版本号 */
    @Update("<script>" +
            "UPDATE sys_dept" +
            "<set>" +
            "<if test='deptName != null'>dept_name = #{deptName},</if>" +
            "<choose>" +
            "<when test='clearManager'>manager_user_id = NULL,</when>" +
            "<otherwise><if test='managerUserId != null'>manager_user_id = #{managerUserId},</if></otherwise>" +
            "</choose>" +
            "update_time = UTC_TIMESTAMP(3), version = version + 1" +
            "</set>" +
            " WHERE id = #{id} AND version = #{expectedVersion}" +
            "</script>")
    int updateProfile(@Param("id") Long id, @Param("expectedVersion") Long expectedVersion,
                      @Param("deptName") String deptName, @Param("managerUserId") Long managerUserId,
                      @Param("clearManager") boolean clearManager);

    /** 更新部门状态，乐观锁校验版本号 */
    @Update("UPDATE sys_dept SET status = #{status}, " +
            "update_time = UTC_TIMESTAMP(3), version = version + 1 " +
            "WHERE id = #{id} AND version = #{expectedVersion}")
    int updateStatus(@Param("id") Long id, @Param("expectedVersion") Long expectedVersion,
                     @Param("status") Integer status);

    /** 统计部门 ID 集合下（含子孙部门 ID 集合，由调用方展开）的在编用户数；空集合语义由调用方保证 */
    @Select("<script>" +
            "SELECT COUNT(DISTINCT id) FROM sys_user WHERE dept_id IN " +
            "<foreach collection='deptIds' item='deptId' open='(' separator=',' close=')'>#{deptId}</foreach>" +
            "</script>")
    long countUsersByDeptIds(@Param("deptIds") List<Long> deptIds);
}
