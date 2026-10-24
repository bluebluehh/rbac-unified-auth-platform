package com.rbac.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Arrays;
import java.util.Set;

/** 课程权限展示配置只能在显式指定的课程数据库及 course profile 下使用。 */
@Component
public class CourseConfigurationGuard implements SmartInitializingSingleton {
    private final JdbcTemplate jdbc;
    private final Environment environment;
    private final RequestMappingHandlerMapping mappings;
    private final String mode;
    private final String datasourceUrl;
    private final String courseDatasourceId;

    public CourseConfigurationGuard(JdbcTemplate jdbc, Environment environment,
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings,
            @Value("${rbac.business-mode:DISABLED}") String mode,
            @Value("${spring.datasource.url}") String datasourceUrl,
            @Value("${rbac.course-datasource-id:}") String courseDatasourceId) {
        this.jdbc = jdbc;
        this.environment = environment;
        this.mappings = mappings;
        this.mode = mode;
        this.datasourceUrl = datasourceUrl;
        this.courseDatasourceId = courseDatasourceId;
    }

    @Override
    public void afterSingletonsInstantiated() {
        Long courseRoles = jdbc.queryForObject("SELECT COUNT(*) FROM sys_role WHERE role_key='COMMON_EMPLOYEE' OR RIGHT(role_key,7)='_SOURCE'", Long.class);
        if (!"PERMISSION_DISPLAY_ONLY".equals(mode)) {
            if (courseRoles != null && courseRoles > 0) {
                throw new IllegalStateException("课程角色数据库必须使用 PERMISSION_DISPLAY_ONLY 模式及 course profile");
            }
            return;
        }
        Set<String> profiles = Set.copyOf(Arrays.asList(environment.getActiveProfiles()));
        String datasourceId = datasourceUrl.split("\\?", 2)[0];
        if (!profiles.contains("course") || profiles.contains("prod") || profiles.contains("production")
                || courseDatasourceId.isBlank() || !courseDatasourceId.equals(datasourceId)) {
            throw new IllegalStateException("课程展示模式只能连接显式登记的独立课程数据库，禁止生产环境启用");
        }
        long endpointCount = mappings.getHandlerMethods().values().stream()
                .filter(handler -> handler.getBeanType().getPackageName().equals("com.rbac.controller"))
                .count();
        if (endpointCount != 40) {
            throw new IllegalStateException("课程展示模式要求固定40个端点，不得直接接入真实业务接口");
        }
    }
}
