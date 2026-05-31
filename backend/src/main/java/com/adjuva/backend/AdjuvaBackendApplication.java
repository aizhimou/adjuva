package com.adjuva.backend;

import com.adjuva.backend.config.ExecutorProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.adjuva.backend.mapper")
@EnableConfigurationProperties(ExecutorProperties.class)
@SpringBootApplication
public class AdjuvaBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdjuvaBackendApplication.class, args);
    }
}
