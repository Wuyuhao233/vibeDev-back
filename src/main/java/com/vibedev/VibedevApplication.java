package com.vibedev;

import com.vibedev.config.CasConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(CasConfig.class)
public class VibedevApplication {

    public static void main(String[] args) {
        SpringApplication.run(VibedevApplication.class, args);
    }
}
