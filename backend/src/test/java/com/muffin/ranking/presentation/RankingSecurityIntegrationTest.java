package com.muffin.ranking.presentation;

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
class RankingSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccessTokenProvider accessTokenProvider;

    @Test
    @DisplayName("주간 랭킹 API는 인증 없이 요청하면 401을 반환한다")
    void getWeeklyRanking_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/rankings/weekly")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("주간 랭킹 API는 JWT의 사용자 ID로 조회한다")
    void getWeeklyRanking_usesAuthenticatedUser() throws Exception {
        String bearer = "Bearer " + accessTokenProvider.issue(1L, "USER");

        mockMvc.perform(get("/api/rankings/weekly").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.rankingStatus").value("EMPTY"));
    }
}
