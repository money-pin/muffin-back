package com.muffin.investment.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muffin.auth.domain.AccessTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InvestmentSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccessTokenProvider accessTokenProvider;

    @Test
    @DisplayName("투자 조회 API는 JWT 인증이 없으면 401을 반환한다")
    void getAsset_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/investments/asset")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("투자 조회 API는 JWT 사용자 ID를 사용한다")
    void getAsset_usesAuthenticatedUser() throws Exception {
        String bearer = "Bearer " + accessTokenProvider.issue(1L, "USER");

        mockMvc.perform(get("/api/investments/asset").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalAsset").value(1_000_000));
    }

    @Test
    @DisplayName("다른 담당자의 정산 결과 API는 기존 임시 헤더 계약을 유지한다")
    void settlementResult_keepsExistingHeaderContract() throws Exception {
        mockMvc.perform(get("/api/investments/settlement/result").header("X-User-Id", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.reason").value("NO_INVESTMENT"));
    }
}
