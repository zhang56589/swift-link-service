package com.swiftlink;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableDiscoveryClient
@MapperScan("com.swiftlink.mapper")
public class SwiftLinkApplication {

    public static void main(String[] args) {
        SpringApplication.run(SwiftLinkApplication.class, args);
    }
}
