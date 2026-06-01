package dev.adjuva;

import dev.adjuva.config.ExecutorProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("dev.adjuva.mapper")
@EnableConfigurationProperties(ExecutorProperties.class)
@SpringBootApplication
public class Adjuva {

    public static void main(String[] args) {
        SpringApplication.run(dev.adjuva.Adjuva.class, args);
    }
}
