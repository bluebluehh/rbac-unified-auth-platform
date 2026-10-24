package com.rbac;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 市政公司统一权限管理平台后端启动类。
 * 负责人：后端 F（公共底座 + Controller + AOP + entity/mapper + 测试）。
 */
@SpringBootApplication
public class RbacApplication {

    public static void main(String[] args) {
        SpringApplication.run(RbacApplication.class, args);
    }
}
