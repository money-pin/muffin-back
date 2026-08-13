package com.muffin.global.apiPayload.handler;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 에러 응답이 요청의 Accept 헤더와 무관하게 항상 공통 포맷(ApiResponse JSON)으로 나가는지 검증한다.
 *
 * <p>회귀 방지 대상: 에러 응답에 Content-Type을 명시하지 않으면 스프링이 Accept로 콘텐츠 협상을 하고, JSON을
 * 받아들이지 않는 Accept가 오면 에러 응답 자체를 쓰지 못해 원래 의미와 다른 응답으로 둔갑했다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ErrorResponseFormatTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("XML만 받겠다는 요청이어도 검증 실패 응답은 400과 공통 JSON 포맷으로 나간다")
    void validationFailure_staysJsonWhenAcceptExcludesJson() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_XML)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_400_002"));
    }

    @Test
    @DisplayName("XML만 받겠다는 요청이어도 본문 파싱 실패 응답은 400과 공통 JSON 포맷으로 나간다")
    void malformedBody_staysJsonWhenAcceptExcludesJson() throws Exception {
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_XML)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_400_001"));
    }

    @Test
    @DisplayName("서버가 만들 수 없는 Accept 타입이면 406과 공통 JSON 포맷으로 나간다")
    void unsupportedAccept_returnsNotAcceptableInCommonFormat() throws Exception {
        mockMvc.perform(get("/api/health/readiness").accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_406_001"));
    }

    @Test
    @DisplayName("정상 요청의 성공 응답은 종전과 동일하게 유지된다")
    void successResponse_isUnchanged() throws Exception {
        mockMvc.perform(get("/api/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(content().string("READY"));
    }

    @Test
    @DisplayName("/error로 직접 보낸 요청은 여전히 인증을 요구한다")
    void errorPath_directRequestStillRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/error")).andExpect(status().isUnauthorized());
    }
}
