package com.muffin.global.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * preflight 응답이 브라우저에 캐시되도록 Access-Control-Max-Age가 실려 나가는지 검증한다. 이 헤더가 빠지면 브라우저
 * 기본값인 5초만 캐시돼 본 요청마다 OPTIONS 왕복이 한 번씩 더 붙는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorsPreflightCacheTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";
    private static final String ONE_HOUR_IN_SECONDS = "3600";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("허용된 출처의 preflight 요청에는 캐시 기간이 1시간으로 실려 나간다")
    void preflight_respondsWithOneHourMaxAge() throws Exception {
        mockMvc.perform(options("/api/investments/asset")
                        .header("Origin", ALLOWED_ORIGIN)
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Max-Age", ONE_HOUR_IN_SECONDS));
    }

    @Test
    @DisplayName("preflight는 인증 없이도 통과하고 허용 출처와 헤더를 그대로 돌려준다")
    void preflight_passesWithoutAuthenticationAndEchoesAllowedOrigin() throws Exception {
        mockMvc.perform(options("/api/investments/asset")
                        .header("Origin", ALLOWED_ORIGIN)
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    @DisplayName("허용되지 않은 출처의 preflight는 거부되고 캐시 기간도 실리지 않는다")
    void preflight_rejectsDisallowedOrigin() throws Exception {
        mockMvc.perform(options("/api/investments/asset")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Max-Age"));
    }
}
