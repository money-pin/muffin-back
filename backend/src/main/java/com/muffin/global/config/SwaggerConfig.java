package com.muffin.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.Comparator;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    private static final String SECURITY_SCHEME_NAME = "JWT TOKEN";

    /**
     * 도메인 그룹 순서. REST Docs 목차 순서와 맞춘다. 목록에 없는 태그(Health 등)는 항상 맨 뒤로 간다.
     */
    private static final List<String> TAG_ORDER =
            List.of("Auth", "User", "Investment", "Sector", "Mypage", "Notification", "Quiz", "News", "Scrap", "Stats");

    @Bean
    public OpenAPI muffinOpenAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(securityComponents());
    }

    @Bean
    public OpenApiCustomizer tagOrderCustomizer() {
        return openApi -> {
            if (openApi.getTags() == null) {
                return;
            }
            openApi.getTags().sort(Comparator.comparingInt(tag -> {
                int index = TAG_ORDER.indexOf(tag.getName());
                return index < 0 ? Integer.MAX_VALUE : index;
            }));
        };
    }

    private Info apiInfo() {
        return new Info()
                .title("Muffin API")
                .description("매일 아침 가볍게 즐기는 금융 핀셋 가이드")
                .version("0.0.1");
    }

    private Components securityComponents() {
        return new Components()
                .addSecuritySchemes(
                        SECURITY_SCHEME_NAME,
                        new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("Bearer")
                                .bearerFormat("JWT"));
    }
}
