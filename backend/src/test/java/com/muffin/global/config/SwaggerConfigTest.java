package com.muffin.global.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** SecurityConfig의 permitAll 오퍼레이션이 OpenAPI 문서에서도 인증 불필요로 표시되는지 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SwaggerConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("공개 오퍼레이션은 OpenAPI 문서에서 security가 빈 배열로 오버라이드된다")
    void publicOperationsHaveEmptySecurity() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/auth/signup'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/auth/login'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/auth/google'].post.security").isEmpty())
                .andExpect(
                        jsonPath("$.paths['/auth/token/refresh'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/health/readiness'].get.security")
                        .isEmpty());
    }

    @Test
    @DisplayName("인증이 필요한 오퍼레이션은 전역 JWT 보안 요구를 그대로 상속한다")
    void protectedOperationsInheritGlobalSecurity() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/logout'].post.security").doesNotExist())
                .andExpect(
                        jsonPath("$.paths['/api/auth/account'].delete.security").doesNotExist())
                .andExpect(jsonPath("$.security[0]['JWT TOKEN']").exists());
    }
}
