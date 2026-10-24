package com.rbac.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis 配置：扫描 Mapper 接口并注册为 Bean（队友并行交付 com.rbac.mapper 包）。
 */
@Configuration
@MapperScan("com.rbac.mapper")
public class MyBatisConfig {
}
