package com.rbac.mapper;

import com.rbac.entity.SysApplication;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 应用表 sys_application 的 Mapper（动态 SQL 全部参数化，更新走乐观锁）。
 */
@Mapper
public interface ApplicationMapper {

    /** 分页查询：keyword 模糊匹配应用名称/应用键，status 等值过滤 */
    @Select("<script>" +
            "SELECT * FROM sys_application" +
            "<where>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            " AND (app_name LIKE CONCAT('%',#{keyword},'%') OR app_key LIKE CONCAT('%',#{keyword},'%'))" +
            "</if>" +
            "<if test='status != null'> AND status = #{status}</if>" +
            "</where>" +
            " ORDER BY id ASC LIMIT #{offset},#{size}" +
            "</script>")
    List<SysApplication> selectPage(@Param("keyword") String keyword, @Param("status") Integer status,
                                    @Param("offset") int offset, @Param("size") int size);

    /** 分页计数：过滤条件同 selectPage */
    @Select("<script>" +
            "SELECT COUNT(*) FROM sys_application" +
            "<where>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            " AND (app_name LIKE CONCAT('%',#{keyword},'%') OR app_key LIKE CONCAT('%',#{keyword},'%'))" +
            "</if>" +
            "<if test='status != null'> AND status = #{status}</if>" +
            "</where>" +
            "</script>")
    long countPage(@Param("keyword") String keyword, @Param("status") Integer status);

    /** 按主键查询应用 */
    @Select("SELECT * FROM sys_application WHERE id = #{id}")
    SysApplication selectById(@Param("id") Long id);

    /** 按主键查询应用（struct 认证实现使用的别名） */
    @Select("SELECT * FROM sys_application WHERE id = #{id}")
    SysApplication findById(@Param("id") Long id);

    /** 按主键集合查询启用状态的应用（A05 门户卡片；空集合返回空列表） */
    @Select("<script>" +
            "SELECT * FROM sys_application WHERE status = 1 AND id IN " +
            "<foreach collection='ids' item='appId' open='(' separator=',' close=')'>#{appId}</foreach>" +
            " ORDER BY id ASC" +
            "</script>")
    List<SysApplication> selectEnabledByIds(@Param("ids") List<Long> ids);

    /** 按应用键查询应用 */
    @Select("SELECT * FROM sys_application WHERE app_key = #{appKey}")
    SysApplication selectByAppKey(@Param("appKey") String appKey);

    /** 查询全部启用状态的应用 */
    @Select("SELECT * FROM sys_application WHERE status = 1")
    List<SysApplication> selectAllEnabled();

    /** 插入应用，回填自增主键 */
    @Insert("INSERT INTO sys_application (app_name, app_key, status, create_time, update_time, version) " +
            "VALUES (#{appName}, #{appKey}, #{status}, #{createTime}, #{updateTime}, #{version})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SysApplication app);

    /** 更新应用名称：名称非空才更新，乐观锁校验版本号 */
    @Update("<script>" +
            "UPDATE sys_application" +
            "<set>" +
            "<if test='appName != null'>app_name = #{appName},</if>" +
            "update_time = UTC_TIMESTAMP(3), version = version + 1" +
            "</set>" +
            " WHERE id = #{id} AND version = #{expectedVersion}" +
            "</script>")
    int updateName(@Param("id") Long id, @Param("expectedVersion") Long expectedVersion,
                   @Param("appName") String appName);

    /** 更新应用状态，乐观锁校验版本号 */
    @Update("UPDATE sys_application SET status = #{status}, " +
            "update_time = UTC_TIMESTAMP(3), version = version + 1 " +
            "WHERE id = #{id} AND version = #{expectedVersion}")
    int updateStatus(@Param("id") Long id, @Param("expectedVersion") Long expectedVersion,
                     @Param("status") Integer status);
}
