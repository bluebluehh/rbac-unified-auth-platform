package com.rbac.config;

import com.rbac.security.RequestIdFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;

/**
 * Web MVC 配置：CORS 策略与 RequestId 过滤器注册。
 * AuthenticationFilter 不在此处注册，统一由 SecurityConfig 挂入 Spring Security 过滤链。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final RbacSecurityProperties properties;

    public WebConfig(RbacSecurityProperties properties) {
        this.properties = properties;
    }

    /**
     * CORS 配置来源：供 Spring Security cors(withDefaults()) 与 MVC 共用。
     * 不允许携带凭据（allowCredentials=false），来源列表来自 rbac.security.corsAllowedOrigins。
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(new ArrayList<>(
                properties.getCorsAllowedOrigins() == null ? List.of() : properties.getCorsAllowedOrigins()));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "If-Match", "X-Request-Id"));
        config.setExposedHeaders(List.of("X-Request-Id"));
        config.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /** RequestId 过滤器实例：同时供 Servlet 容器注册与 Security 过滤链复用。 */
    @Bean
    public RequestIdFilter requestIdFilter() {
        return new RequestIdFilter();
    }

    /**
     * 将 RequestIdFilter 注册为最高优先级 Servlet 过滤器，
     * 保证早于 Spring Security 过滤链（DelegatingFilterProxy，order=-100）执行。
     * 显式注册后 Spring Boot 不会对同一 Bean 再次自动注册，避免重复执行。
     */
    @Bean
    public FilterRegistrationBean<RequestIdFilter> requestIdFilterRegistration(RequestIdFilter requestIdFilter) {
        FilterRegistrationBean<RequestIdFilter> registration = new FilterRegistrationBean<>(requestIdFilter);
        registration.setOrder(Integer.MIN_VALUE + 10);
        registration.addUrlPatterns("/*");
        registration.setName("requestIdFilter");
        return registration;
    }
}
