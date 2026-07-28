package com.muffin.ranking.presentation;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muffin.global.apiPayload.handler.GeneralExceptionAdvice;
import com.muffin.ranking.application.WeeklyRankingQueryService;
import com.muffin.ranking.presentation.dto.WeeklyRankingResponse;
import com.muffin.ranking.presentation.dto.WeeklyRankingResponse.MyRankResponse;
import com.muffin.ranking.presentation.dto.WeeklyRankingResponse.SectorResponse;
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
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(RestDocumentationExtension.class)
class RankingControllerDocsTest {

    @BeforeEach
    void setUpAuthentication() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(1L, null, List.of()));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("지난주 수익률 랭킹 조회 API 문서를 생성한다")
    void documentWeeklyRanking(RestDocumentationContextProvider restDocumentation) throws Exception {
        WeeklyRankingResponse response = new WeeklyRankingResponse(
                com.muffin.ranking.domain.RankingStatus.READY,
                LocalDate.of(2026, 7, 20),
                LocalDate.of(2026, 7, 26),
                30,
                "7월 3주차 기준",
                MyRankResponse.participated(12, "투자왕김씨", 12),
                List.of(new Top10Response(
                        1,
                        "수익마스터왕",
                        98_000L,
                        new BigDecimal("9.8"),
                        new Top10DetailResponse(
                                1_000_000L,
                                List.of(new SectorResponse(
                                        "SEMICONDUCTOR", "반도체", 48_000L, new BigDecimal("16.0"), 300_000L))))));
        MockMvc mockMvc = mockMvcWith(stub(response), restDocumentation);

        mockMvc.perform(get("/api/rankings/weekly"))
                .andExpect(status().isOk())
                .andDo(document(
                        "ranking-weekly",
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("result.rankingStatus").description("READY, CALCULATING, EMPTY"),
                                fieldWithPath("result.weekStartDate").description("집계 주 시작일(월요일, KST)"),
                                fieldWithPath("result.weekEndDate").description("집계 주 종료일(일요일, KST)"),
                                fieldWithPath("result.weekOfYear").description("ISO 주차"),
                                fieldWithPath("result.weekLabel").description("화면 표시용 주차 라벨"),
                                fieldWithPath("result.myRank.participated").description("지난주 모의투자 참여 여부"),
                                fieldWithPath("result.myRank.rank").description("내 순위. 미참여면 null"),
                                fieldWithPath("result.myRank.nickname").description("닉네임 스냅샷. 미참여면 null"),
                                fieldWithPath("result.myRank.topPercent").description("상위 백분율. 미참여면 null"),
                                fieldWithPath("result.top10[].rank").description("순위(1~10)"),
                                fieldWithPath("result.top10[].nickname").description("닉네임 스냅샷"),
                                fieldWithPath("result.top10[].profitAmount").description("주간 수익금"),
                                fieldWithPath("result.top10[].profitRate").description("주간 수익률(소수 첫째 자리)"),
                                fieldWithPath("result.top10[].detail.totalInvestment")
                                        .description("주간 총 투자금"),
                                fieldWithPath("result.top10[].detail.sectors[].sectorCode")
                                        .description("섹터 코드"),
                                fieldWithPath("result.top10[].detail.sectors[].sectorName")
                                        .description("섹터명"),
                                fieldWithPath("result.top10[].detail.sectors[].profitAmount")
                                        .description("섹터 수익금"),
                                fieldWithPath("result.top10[].detail.sectors[].profitRate")
                                        .description("섹터 수익률(소수 첫째 자리)"),
                                fieldWithPath("result.top10[].detail.sectors[].totalInvestment")
                                        .description("섹터 투자금"))));
    }

    private WeeklyRankingQueryService stub(WeeklyRankingResponse response) {
        return new WeeklyRankingQueryService(null, null) {
            @Override
            public WeeklyRankingResponse getWeeklyRanking(Long userId) {
                return response;
            }
        };
    }

    private MockMvc mockMvcWith(WeeklyRankingQueryService service, RestDocumentationContextProvider restDocumentation) {
        return MockMvcBuilders.standaloneSetup(new RankingController(service))
                .setControllerAdvice(new GeneralExceptionAdvice())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .apply(documentationConfiguration(restDocumentation)
                        .operationPreprocessors()
                        .withRequestDefaults(prettyPrint())
                        .withResponseDefaults(prettyPrint()))
                .build();
    }
}
