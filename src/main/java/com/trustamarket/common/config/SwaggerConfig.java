package com.trustamarket.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Springdoc 의존성이 있을 때만 자동 활성화. OpenAPI 메타 + Keycloak JWT Bearer SecurityScheme 등록.
// 각 서비스가 자체 OpenAPI 빈을 등록하면 그쪽이 우선 (ConditionalOnMissingBean).
@Configuration
@ConditionalOnClass(name = "io.swagger.v3.oas.models.OpenAPI")
public class SwaggerConfig {

    private static final String SECURITY_SCHEME_NAME = "BearerAuth";

    @Bean
    @ConditionalOnMissingBean
    public OpenAPI trustaOpenAPI(@Value("${spring.application.name:trusta-service}") String appName) {
        return new OpenAPI()
                .info(new Info()
                        .title(appName + " API")
                        .version("v1")
                        .description("Trusta Market — " + appName))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Keycloak JWT access_token")));
    }
}
