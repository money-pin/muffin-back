package com.muffin.ranking.presentation;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muffin.ranking.application.WeeklyRankingQueryService;
import com.muffin.ranking.presentation.dto.WeeklyRankingResponse;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class RankingControllerTest {

    @Mock
    private WeeklyRankingQueryService weeklyRankingQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(1L, null, List.of()));
        mockMvc = MockMvcBuilders.standaloneSetup(new RankingController(weeklyRankingQueryService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("지난주 랭킹을 인증 사용자 기준 공통 응답으로 반환한다")
    void getWeeklyRanking_returnsResponse() throws Exception {
        WeeklyRankingResponse.WeekInfo weekInfo = new WeeklyRankingResponse.WeekInfo(
                LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 26), 30, "7월 3주차 기준");
        when(weeklyRankingQueryService.getWeeklyRanking(1L)).thenReturn(WeeklyRankingResponse.empty(weekInfo));

        mockMvc.perform(get("/api/rankings/weekly"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.rankingStatus").value("EMPTY"))
                .andExpect(jsonPath("$.result.myRank.participated").value(false))
                .andExpect(jsonPath("$.result.top10").isArray());

        verify(weeklyRankingQueryService).getWeeklyRanking(1L);
    }
}
