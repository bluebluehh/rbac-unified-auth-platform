package com.rbac.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.Set;

/**
 * Service 实现模式选择（rbac.service.impl = struct | oop）。
 * 启动期校验取值合法性，非法配置直接失败（fail-fast）。
 * 两套实现分别由队友通过 @ConditionalOnProperty(prefix = "rbac.service", name = "impl", havingValue = "struct"/"oop")
 * 注册为 com.rbac.service.struct.StructXxxService / com.rbac.service.oop.OopXxxService，
 * 切换模式只需改配置，无需改动 Controller。
 */
@Configuration
public class ServiceModeConfig implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(ServiceModeConfig.class);

    /** 允许的模式取值。 */
    private static final Set<String> SUPPORTED_MODES = Set.of("struct", "oop");

    @Value("${rbac.service.impl:struct}")
    private String serviceMode;

    @Override
    public void afterPropertiesSet() {
        // 严格匹配（不归一化大小写），保证与实现类的 @ConditionalOnProperty 精确一致
        if (serviceMode == null || !SUPPORTED_MODES.contains(serviceMode)) {
            throw new IllegalStateException(
                    "rbac.service.impl 配置非法：" + serviceMode + "，仅支持 struct | oop");
        }
        log.info("当前 Service 实现模式：{}（struct -> com.rbac.service.struct.StructXxxService，"
                + "oop -> com.rbac.service.oop.OopXxxService，由 @ConditionalOnProperty 注册，切换无需改 Controller）", serviceMode);
    }

    /** 供其他组件读取当前模式（"struct" 或 "oop"）。 */
    public String getServiceMode() {
        return serviceMode;
    }

    /** 系统时钟（struct 认证实现注入使用，便于测试替换）。 */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
