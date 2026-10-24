package com.rbac.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * AOP 配置：启用 AspectJ 自动代理（操作日志、端点鉴权等切面依赖，切面本体由其他成员交付）。
 */
@Configuration
@EnableAspectJAutoProxy
public class AopConfig {
}
