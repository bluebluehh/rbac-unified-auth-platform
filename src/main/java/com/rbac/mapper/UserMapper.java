package com.rbac.mapper;

import com.rbac.entity.SysUser;
import com.rbac.util.StaffGrade;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户表 sys_user 的 Mapper（动态 SQL 全部参数化，更新走乐观锁）。
 */
@Mapper
public interface UserMapper {

    @Select("SELECT dept_id, company_id, (" + StaffGrade.SQL + ") AS grade, COUNT(*) AS user_count " +
            "FROM sys_user GROUP BY dept_id, company_id, (" + StaffGrade.SQL + ")")
    List<com.rbac.vo.user.StaffGradeCount> selectGradeCounts();

    /** 按用户名查询用户 */
    @Select("SELECT * FROM sys_user WHERE username = #{username}")
    SysUser selectByUsername(@Param("username") String username);

    /** 按用户名查询用户（struct 认证实现使用的别名） */
    @Select("SELECT * FROM sys_user WHERE username = #{username}")
    SysUser findByUsername(@Param("username") String username);

    /** 按主键查询用户 */
    @Select("SELECT * FROM sys_user WHERE id = #{id}")
    SysUser selectById(@Param("id") Long id);

    /** 按主键查询用户（struct 认证实现使用的别名） */
    @Select("SELECT * FROM sys_user WHERE id = #{id}")
    SysUser findById(@Param("id") Long id);

    /** 按主键加行锁查询用户（须在事务内调用，与 security_state 锁顺序一致） */
    @Select("SELECT * FROM sys_user WHERE id = #{id} FOR UPDATE")
    SysUser lockById(@Param("id") Long id);

    /** 更新密码（struct A04 语义）：PENDING_ACTIVATION 首改即激活、清除改密标记与锁定状态；不改业务版本号。与下方 6 参乐观锁版不可同名——MyBatis statement id 不支持重载 */
    @Update("UPDATE sys_user SET password_hash = #{passwordHash}, " +
            "status = CASE WHEN status = 'PENDING_ACTIVATION' THEN 'ENABLED' ELSE status END, " +
            "must_change_password = 0, failed_login_count = 0, locked_until = NULL, update_time = #{now} " +
            "WHERE id = #{id}")
    int updatePasswordHash(@Param("id") Long id, @Param("passwordHash") String passwordHash,
                           @Param("now") LocalDateTime now);

    /** 分页查询：keyword 模糊匹配用户名/姓名，deptId、status 等值过滤 */
    @Select("<script>" +
            "SELECT * FROM sys_user" +
            "<where>" +
            "<if test='grade != null'> AND (" + StaffGrade.SQL + ") = #{grade}</if>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            " AND (username LIKE CONCAT('%',#{keyword},'%') OR real_name LIKE CONCAT('%',#{keyword},'%'))" +
            "</if>" +
            "<if test='deptIds != null'> AND dept_id IN <foreach collection='deptIds' item='d' open='(' separator=',' close=')'>#{d}</foreach></if>" +
            "<if test='companyId != null'> AND company_id = #{companyId}</if>" +
            "<if test='status != null and status != \"\"'> AND status = #{status}</if>" +
            "</where>" +
            " ORDER BY id ASC LIMIT #{offset},#{size}" +
            "</script>")
    List<SysUser> selectPage(@Param("keyword") String keyword, @Param("deptIds") List<Long> deptIds, @Param("companyId") Long companyId,
                             @Param("status") String status, @Param("grade") String grade,
                             @Param("offset") int offset, @Param("size") int size);

    /** 分页计数：过滤条件同 selectPage */
    @Select("<script>" +
            "SELECT COUNT(*) FROM sys_user" +
            "<where>" +
            "<if test='grade != null'> AND (" + StaffGrade.SQL + ") = #{grade}</if>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            " AND (username LIKE CONCAT('%',#{keyword},'%') OR real_name LIKE CONCAT('%',#{keyword},'%'))" +
            "</if>" +
            "<if test='deptIds != null'> AND dept_id IN <foreach collection='deptIds' item='d' open='(' separator=',' close=')'>#{d}</foreach></if>" +
            "<if test='companyId != null'> AND company_id = #{companyId}</if>" +
            "<if test='status != null and status != \"\"'> AND status = #{status}</if>" +
            "</where>" +
            "</script>")
    long countPage(@Param("keyword") String keyword, @Param("deptIds") List<Long> deptIds, @Param("companyId") Long companyId,
                   @Param("status") String status, @Param("grade") String grade);

    /** 插入用户，回填自增主键 */
    @Insert("INSERT INTO sys_user (username, password_hash, real_name, company_id, dept_id, position_name, " +
            "phone_cipher, status, must_change_password, create_time, update_time, version) " +
            "VALUES (#{username}, #{passwordHash}, #{realName}, #{companyId}, #{deptId}, #{positionName}, " +
            "#{phoneCipher}, #{status}, #{mustChangePassword}, #{createTime}, #{updateTime}, #{version})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SysUser user);

    /** 更新基本资料：字段非空才更新；clearPhone 为 true 时清空手机号密文；乐观锁校验版本号 */
    @Update("<script>" +
            "UPDATE sys_user" +
            "<set>" +
            "<if test='realName != null'>real_name = #{realName},</if>" +
            "<if test='companyId != null'>company_id = #{companyId},</if>" +
            "<if test='deptId != null'>dept_id = #{deptId},</if>" +
            "<if test='positionName != null'>position_name = #{positionName},</if>" +
            "<choose>" +
            "<when test='clearPhone'>phone_cipher = NULL,</when>" +
            "<otherwise><if test='phoneCipher != null'>phone_cipher = #{phoneCipher},</if></otherwise>" +
            "</choose>" +
            "update_time = UTC_TIMESTAMP(3), version = version + 1" +
            "</set>" +
            " WHERE id = #{id} AND version = #{expectedVersion}" +
            "</script>")
    int updateProfile(@Param("id") Long id, @Param("expectedVersion") Long expectedVersion,
                      @Param("realName") String realName, @Param("companyId") Long companyId,
                      @Param("deptId") Long deptId, @Param("positionName") String positionName,
                      @Param("phoneCipher") byte[] phoneCipher, @Param("clearPhone") boolean clearPhone);

    /** 更新状态：可选重置必须改密标记；clearLockState 为 true 时清零失败计数并解除锁定；乐观锁校验版本号 */
    @Update("<script>" +
            "UPDATE sys_user" +
            "<set>" +
            "status = #{status}," +
            "<if test='mustChangePassword != null'>must_change_password = #{mustChangePassword},</if>" +
            "<if test='clearLockState'>failed_login_count = 0, locked_until = NULL,</if>" +
            "update_time = UTC_TIMESTAMP(3), version = version + 1" +
            "</set>" +
            " WHERE id = #{id} AND version = #{expectedVersion}" +
            "</script>")
    int updateStatus(@Param("id") Long id, @Param("expectedVersion") Long expectedVersion,
                     @Param("status") String status, @Param("mustChangePassword") Integer mustChangePassword,
                     @Param("clearLockState") boolean clearLockState);

    /** 更新密码：可选联动状态与必须改密标记、清除锁定；乐观锁校验版本号 */
    @Update("<script>" +
            "UPDATE sys_user" +
            "<set>" +
            "password_hash = #{passwordHash}," +
            "<if test='status != null'>status = #{status},</if>" +
            "<if test='mustChangePassword != null'>must_change_password = #{mustChangePassword},</if>" +
            "<if test='clearLockState'>failed_login_count = 0, locked_until = NULL,</if>" +
            "update_time = UTC_TIMESTAMP(3), version = version + 1" +
            "</set>" +
            " WHERE id = #{id} AND version = #{expectedVersion}" +
            "</script>")
    int updatePassword(@Param("id") Long id, @Param("expectedVersion") Long expectedVersion,
                       @Param("passwordHash") String passwordHash, @Param("status") String status,
                       @Param("mustChangePassword") Integer mustChangePassword,
                       @Param("clearLockState") boolean clearLockState);

    /** 记录登录失败：直接覆盖失败次数与锁定时间，不递增版本号 */
    @Update("UPDATE sys_user SET failed_login_count = #{failedLoginCount}, locked_until = #{lockedUntil} " +
            "WHERE id = #{id}")
    int updateLoginFailure(@Param("id") Long id, @Param("failedLoginCount") int failedLoginCount,
                           @Param("lockedUntil") LocalDateTime lockedUntil);

    /** 登录成功后清零失败计数并解除锁定 */
    @Update("UPDATE sys_user SET failed_login_count = 0, locked_until = NULL WHERE id = #{id}")
    int clearLoginFailure(@Param("id") Long id);

    /** 查询拥有指定角色键的全部用户 */
    @Select("SELECT u.* FROM sys_user u " +
            "JOIN sys_user_role ur ON ur.user_id = u.id " +
            "JOIN sys_role r ON r.id = ur.role_id " +
            "WHERE r.role_key = #{roleKey}")
    List<SysUser> selectByRoleKey(@Param("roleKey") String roleKey);

    /** 仅递增业务版本号（A14 角色分配等无字段变化的关系变更），乐观锁校验版本号 */
    @Update("UPDATE sys_user SET update_time = UTC_TIMESTAMP(3), version = version + 1 " +
            "WHERE id = #{id} AND version = #{expectedVersion}")
    int bumpVersion(@Param("id") Long id, @Param("expectedVersion") Long expectedVersion);

    /**
     * 查询组织节点 ID 集合下的受影响用户：dept_id 或 company_id 命中即算
     * （A19 部门停用子树会话撤销；组织链校验同时覆盖两个归属字段，空集合语义由调用方保证）。
     */
    @Select("<script>" +
            "SELECT * FROM sys_user WHERE dept_id IN " +
            "<foreach collection='deptIds' item='deptId' open='(' separator=',' close=')'>#{deptId}</foreach>" +
            " OR company_id IN " +
            "<foreach collection='deptIds' item='deptId' open='(' separator=',' close=')'>#{deptId}</foreach>" +
            "</script>")
    List<SysUser> selectByDeptIds(@Param("deptIds") List<Long> deptIds);
}
