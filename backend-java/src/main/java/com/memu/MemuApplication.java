package com.memu;

import com.memu.config.KernelProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(KernelProperties.class)
@EnableAsync
@EnableScheduling
public class MemuApplication {
    public static void main(String[] args) {
        SpringApplication.run(MemuApplication.class, args);
    }
}
