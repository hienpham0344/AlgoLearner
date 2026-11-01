package com.algolearner.platform.common.config;

import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class ApiConfiguration {
    @Bean
    Jackson2ObjectMapperBuilderCustomizer strictJson() {
        return builder -> builder.postConfigurer(mapper -> {
            mapper.coercionConfigFor(LogicalType.Boolean).setCoercion(CoercionInputShape.String, CoercionAction.Fail);
            mapper.coercionConfigFor(LogicalType.Textual).setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
        });
    }
}
