package com.hdfs.transfer.server.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("HDFS文件迁移平台 - API文档")
                        .version("1.0.0")
                        .description("HDFS文件迁移平台接口文档"));
    }
}
