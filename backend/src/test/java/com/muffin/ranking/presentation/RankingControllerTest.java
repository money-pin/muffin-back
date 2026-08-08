package com.muffin.ranking.presentation;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muffin.character.domain.enums.MuffinType;
import com.muffin.ranking.application.WeeklyRankingQueryService;
import com.muffin.ranking.presentation.dto.WeeklyRankingResponse;
import com.muffin.ranking.presentation.dto.WeeklyRankingResponse.CharacterResponse;
import com.muffin.ranking.presentation.dto.WeeklyRankingResponse.Top10DetailResponse;
import com.muffin.ranking.presentation.dto.WeeklyRankingResponse.Top10Response;
import java.math.BigDecimal;
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

    @Test
    @DisplayName("TOP 10 사용자 캐릭터를 기존 랭킹 필드와 함께 반환한다")
    void getWeeklyRanking_includesTop10Character() throws Exception {
        WeeklyRankingResponse.WeekInfo weekInfo = new WeeklyRankingResponse.WeekInfo(
                LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 26), 30, "7월 3주차 기준");
        Top10Response top10 = new Top10Response(
                1,
                "수익왕",
                new CharacterResponse(1L, MuffinType.PLAIN, "플레인 머핀", "plain.png"),
                10_000L,
                new BigDecimal("10.0"),
                new Top10DetailResponse(100_000L, List.of()));
        when(weeklyRankingQueryService.getWeeklyRanking(1L))
                .thenReturn(WeeklyRankingResponse.ready(
                        weekInfo, WeeklyRankingResponse.MyRankResponse.notParticipated(), List.of(top10)));

        mockMvc.perform(get("/api/rankings/weekly"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.top10[0].rank").value(1))
                .andExpect(jsonPath("$.result.top10[0].nickname").value("수익왕"))
                .andExpect(jsonPath("$.result.top10[0].character.characterId").value(1))
                .andExpect(jsonPath("$.result.top10[0].character.characterType").value("PLAIN"))
                .andExpect(jsonPath("$.result.top10[0].character.characterName").value("플레인 머핀"))
                .andExpect(jsonPath("$.result.top10[0].character.characterImageUrl")
                        .value("plain.png"))
                .andExpect(jsonPath("$.result.top10[0].profitAmount").value(10_000L));

        verify(weeklyRankingQueryService).getWeeklyRanking(1L);
    }

    @Test
    @DisplayName("집계 중에는 내 순위를 null로 명시해 반환한다")
    void getWeeklyRanking_includesNullMyRankWhenCalculating() throws Exception {
        WeeklyRankingResponse.WeekInfo weekInfo = new WeeklyRankingResponse.WeekInfo(
                LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 26), 30, "7월 3주차 기준");
        when(weeklyRankingQueryService.getWeeklyRanking(1L)).thenReturn(WeeklyRankingResponse.calculating(weekInfo));

        mockMvc.perform(get("/api/rankings/weekly"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.rankingStatus").value("CALCULATING"))
                .andExpect(content().string(containsString("\"myRank\":null")));

        verify(weeklyRankingQueryService).getWeeklyRanking(1L);
    }
}
