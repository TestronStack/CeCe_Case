package com.example.demo.config;

import com.example.demo.common.CryptoUtil;
import com.example.demo.entity.UserEntity;
import com.example.demo.mapper.UserMapper;
import com.example.demo.service.ProjectService;
import com.example.demo.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 首次启动初始化演示账号：admin / 123456 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataInitializer {

    private final UserService userService;
    private final ProjectService projectService;

    @Bean
    public ApplicationRunner initDemoAccount() {
        return args -> {
            CryptoUtil.configure("cece-ai-key-encryption-secret-2026");
            if (userService.findByUsername("admin") == null) {
                try {
                    UserEntity admin = userService.register("admin", "123456", "演示账号");
                    admin.setEmail("admin@cece.dev");
                    projectService.ensureDefault(admin.getId());
                    log.info("✅ 已创建演示账号 admin / 123456");
                } catch (Exception e) {
                    log.warn("演示账号初始化失败: {}", e.getMessage());
                }
            }
        };
    }
}
