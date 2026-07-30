package com.muffin.stats.presentation;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muffin.global.apiPayload.handler.GeneralExceptionAdvice;
import com.muffin.stats.application.StatsQueryService;
import com.muffin.stats.domain.HistorySort;
import com.muffin.stats.domain.InvestmentType;
import com.muffin.stats.domain.StatsPeriod;
import com.muffin.stats.presentation.dto.ProfitHistoryResponse;
import com.muffin.stats.presentation.dto.ProfitHistoryResponse.SectorHistoryResponse;
import com.muffin.stats.presentation.dto.ProfitHistoryResponse.SummaryResponse;
import com.muffin.stats.presentation.dto.RecentDetailResponse;
import com.muffin.stats.presentation.dto.RecentDetailResponse.SectorDetailResponse;
import com.muffin.stats.presentation.dto.StatsSummaryResponse;
import com.muffin.stats.presentation.dto.StatsSummaryResponse.GraphPointResponse;
import com.muffin.stats.presentation.dto.StatsSummaryResponse.InvestmentTypeResponse;
import com.muffin.stats.presentation.dto.TopSectorResponse;
import com.muffin.stats.presentation.dto.TopSectorsResponse;
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

/**
 * STATS-02-1 수익 통계 / STATS-02-2 수익 TOP3 섹터 / STATS-08 누적 수익 내역 / STATS-03 최근 투자 성과 상세 조회 API의 REST Docs 스니펫을
 * 생성한다.
 */
@ExtendWith(RestDocumentationExtension.class)
class StatsControllerDocsTest {

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
    @DisplayName("수익 통계 정상 응답 문서화")
    void documentSummary(RestDocumentationContextProvider restDocumentation) throws Exception {
        StatsSummaryResponse response = new StatsSummaryResponse(
                LocalDate.of(2026, 5, 8),
                128_000L,
                new BigDecimal("12.8"),
                List.of(
                        new GraphPointResponse(LocalDate.of(2026, 5, 2), new BigDecimal("0.0")),
                        new GraphPointResponse(LocalDate.of(2026, 5, 3), new BigDecimal("0.0")),
                        new GraphPointResponse(LocalDate.of(2026, 5, 4), new BigDecimal("2.1")),
                        new GraphPointResponse(LocalDate.of(2026, 5, 5), new BigDecimal("4.5")),
                        new GraphPointResponse(LocalDate.of(2026, 5, 6), new BigDecimal("4.5")),
                        new GraphPointResponse(LocalDate.of(2026, 5, 7), new BigDecimal("9.3")),
                        new GraphPointResponse(LocalDate.of(2026, 5, 8), new BigDecimal("12.8"))),
                List.of(
                        new TopSectorResponse(1, "SEMICONDUCTOR", "반도체", 52_000L, new BigDecimal("8.1")),
                        new TopSectorResponse(2, "GOLD", "금", 31_000L, new BigDecimal("5.2")),
                        new TopSectorResponse(3, "TECH", "테크", 24_000L, new BigDecimal("3.9"))),
                InvestmentTypeResponse.of(InvestmentType.GROWTH, "기초 자산 44%, 기술주 56%, 실물 경제 0%"));
        MockMvc mockMvc = mockMvcWith(summaryStub(response), restDocumentation);

        mockMvc.perform(get("/api/stats/summary"))
                .andExpect(status().isOk())
                .andDo(document(
                        "stats-summary",
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("result.investDate").description("가장 최근 정산 완료 일자(KST)"),
                                fieldWithPath("result.cumulativeProfitAmount").description("누적 손익금(손실 시 음수)"),
                                fieldWithPath("result.cumulativeProfitRate")
                                        .description("누적 손익률(%). 초기자본 100만 기준, 소수 첫째자리"),
                                fieldWithPath("result.graph[].date").description("날짜(KST). 최근 정산일 포함 7일"),
                                fieldWithPath("result.graph[].cumulativeProfitRate")
                                        .description("해당 일자까지의 누적 손익률(%). 미투자일은 직전 값 유지"),
                                fieldWithPath("result.topSectors[].rank").description("순위(1~3)"),
                                fieldWithPath("result.topSectors[].sectorCode").description("섹터 코드"),
                                fieldWithPath("result.topSectors[].sectorName").description("섹터 표시명"),
                                fieldWithPath("result.topSectors[].profitAmount")
                                        .description("섹터 누적 손익금"),
                                fieldWithPath("result.topSectors[].profitRate")
                                        .description("섹터 누적 손익률(%). 그 섹터 누적 매수금 기준"),
                                fieldWithPath("result.investmentType.type")
                                        .description("STABLE/BALANCED/GROWTH/AGGRESSIVE"),
                                fieldWithPath("result.investmentType.label").description("성향 레이블"),
                                fieldWithPath("result.investmentType.description")
                                        .description("한 줄 설명"),
                                fieldWithPath("result.investmentType.bullets")
                                        .description("불릿 3개(자산군 비중 + 고정 문구 2개)"))));
    }

    @Test
    @DisplayName("정산 이력 없는 신규 사용자 빈 상태 응답 문서화")
    void documentEmptySummary(RestDocumentationContextProvider restDocumentation) throws Exception {
        List<GraphPointResponse> flatGraph = java.util.stream.IntStream.rangeClosed(0, 6)
                .mapToObj(i -> new GraphPointResponse(LocalDate.of(2026, 5, 2).plusDays(i), new BigDecimal("0.0")))
                .toList();
        StatsSummaryResponse response =
                new StatsSummaryResponse(null, 0L, new BigDecimal("0.0"), flatGraph, List.of(), null);
        MockMvc mockMvc = mockMvcWith(summaryStub(response), restDocumentation);

        mockMvc.perform(get("/api/stats/summary"))
                .andExpect(status().isOk())
                .andDo(document(
                        "stats-summary-empty",
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("result.investDate").description("정산 이력이 없어 null"),
                                fieldWithPath("result.cumulativeProfitAmount").description("누적 손익금(이력 없음: 0)"),
                                fieldWithPath("result.cumulativeProfitRate").description("누적 손익률(이력 없음: 0.0)"),
                                fieldWithPath("result.graph[].date").description("오늘 포함 7일(이력 없어도 항상 7일치)"),
                                fieldWithPath("result.graph[].cumulativeProfitRate")
                                        .description("전부 0.0(이력 없음)"),
                                fieldWithPath("result.topSectors").description("빈 배열(투자 이력 없음)"),
                                fieldWithPath("result.investmentType").description("투자 이력이 없어 null"))));
    }

    @Test
    @DisplayName("수익 TOP3 섹터 정상 응답 문서화")
    void documentTopSectors(RestDocumentationContextProvider restDocumentation) throws Exception {
        TopSectorsResponse response = new TopSectorsResponse(List.of(
                new TopSectorResponse(1, "SEMICONDUCTOR", "반도체", 52_000L, new BigDecimal("8.1")),
                new TopSectorResponse(2, "GOLD", "금", 31_000L, new BigDecimal("5.2")),
                new TopSectorResponse(3, "TECH", "테크", 24_000L, new BigDecimal("3.9"))));
        MockMvc mockMvc = mockMvcWith(topSectorsStub(response), restDocumentation);

        mockMvc.perform(get("/api/stats/top-sectors"))
                .andExpect(status().isOk())
                .andDo(document(
                        "stats-top-sectors",
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("result.topSectors[].rank").description("순위(1~3). 최대 3개"),
                                fieldWithPath("result.topSectors[].sectorCode").description("섹터 코드"),
                                fieldWithPath("result.topSectors[].sectorName").description("섹터 표시명"),
                                fieldWithPath("result.topSectors[].profitAmount")
                                        .description("섹터 누적 손익금(손실 시 음수)"),
                                fieldWithPath("result.topSectors[].profitRate")
                                        .description("섹터 누적 손익률(%). 그 섹터 누적 매수금 기준, 소수 첫째자리"))));
    }

    @Test
    @DisplayName("수익 TOP3 섹터: 정산 이력이 없는 빈 상태 응답 문서화")
    void documentEmptyTopSectors(RestDocumentationContextProvider restDocumentation) throws Exception {
        MockMvc mockMvc = mockMvcWith(topSectorsStub(new TopSectorsResponse(List.of())), restDocumentation);

        mockMvc.perform(get("/api/stats/top-sectors"))
                .andExpect(status().isOk())
                .andDo(document(
                        "stats-top-sectors-empty",
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("result.topSectors").description("빈 배열(정산 이력이 없거나 매수금이 있는 섹터 없음)"))));
    }

    @Test
    @DisplayName("누적 수익 내역 정상 응답 문서화")
    void documentHistory(RestDocumentationContextProvider restDocumentation) throws Exception {
        ProfitHistoryResponse response = new ProfitHistoryResponse(
                "MONTH",
                "2026-06",
                true,
                false,
                new SummaryResponse(132_000L, new BigDecimal("13.2"), 1_000_000L),
                "RATE_DESC",
                List.of(
                        new SectorHistoryResponse("SEMICONDUCTOR", "반도체", 80_000L, new BigDecimal("22.3"), 360_000L),
                        new SectorHistoryResponse("GOLD", "금", -12_000L, new BigDecimal("-3.1"), 200_000L)));
        MockMvc mockMvc = mockMvcWith(historyStub(response), restDocumentation);

        mockMvc.perform(get("/api/stats/history")
                        .param("period", "MONTH")
                        .param("date", "2026-06")
                        .param("sort", "RATE_DESC"))
                .andExpect(status().isOk())
                .andDo(document(
                        "stats-history",
                        queryParameters(
                                parameterWithName("period").description("기간 탭. DAY/WEEK/MONTH/YEAR/ALL (필수)"),
                                parameterWithName("date")
                                        .optional()
                                        .description("조회 기준 시점. period 형식에 맞춰 지정, 미지정 시 현재. ALL이면 무시"),
                                parameterWithName("sort")
                                        .optional()
                                        .description(
                                                "종목 정렬. AMOUNT_DESC/AMOUNT_ASC/RATE_DESC/RATE_ASC (기본 RATE_DESC)")),
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("result.period").description("조회한 기간 탭"),
                                fieldWithPath("result.date").description("정규화된 조회 기준 시점(ALL이면 생략)"),
                                fieldWithPath("result.hasPrev").description("이전 기간에 정산 데이터가 있는지(화살표 활성화용)"),
                                fieldWithPath("result.hasNext").description("다음 기간에 정산 데이터가 있는지"),
                                fieldWithPath("result.summary.profitAmount").description("기간 누적 손익금(손실 시 음수)"),
                                fieldWithPath("result.summary.profitRate")
                                        .description("기간 손익률(%). 기간 누적 매수금 기준, 소수 첫째자리"),
                                fieldWithPath("result.summary.totalInvestment").description("기간 누적 매수금"),
                                fieldWithPath("result.sort").description("적용된 정렬 기준"),
                                fieldWithPath("result.sectors[].sectorCode").description("섹터 코드"),
                                fieldWithPath("result.sectors[].sectorName").description("섹터 표시명"),
                                fieldWithPath("result.sectors[].profitAmount").description("섹터 손익금"),
                                fieldWithPath("result.sectors[].profitRate").description("섹터 손익률(%). 그 섹터 누적 매수금 기준"),
                                fieldWithPath("result.sectors[].totalInvestment")
                                        .description("섹터 누적 매수금"))));
    }

    @Test
    @DisplayName("해당 기간에 정산 데이터가 없는 빈 상태 응답 문서화")
    void documentEmptyHistory(RestDocumentationContextProvider restDocumentation) throws Exception {
        ProfitHistoryResponse response = new ProfitHistoryResponse(
                "MONTH",
                "2026-06",
                true,
                false,
                new SummaryResponse(0L, new BigDecimal("0.0"), 0L),
                "RATE_DESC",
                List.of());
        MockMvc mockMvc = mockMvcWith(historyStub(response), restDocumentation);

        mockMvc.perform(get("/api/stats/history").param("period", "MONTH").param("date", "2026-06"))
                .andExpect(status().isOk())
                .andDo(document(
                        "stats-history-empty",
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("result.period").description("조회한 기간 탭"),
                                fieldWithPath("result.date").description("정규화된 조회 기준 시점"),
                                fieldWithPath("result.hasPrev").description("이전 기간에 정산 데이터가 있는지"),
                                fieldWithPath("result.hasNext").description("다음 기간에 정산 데이터가 있는지"),
                                fieldWithPath("result.summary.profitAmount").description("기간 누적 손익금(데이터 없음: 0)"),
                                fieldWithPath("result.summary.profitRate").description("기간 손익률(데이터 없음: 0.0)"),
                                fieldWithPath("result.summary.totalInvestment").description("기간 누적 매수금(데이터 없음: 0)"),
                                fieldWithPath("result.sort").description("적용된 정렬 기준"),
                                fieldWithPath("result.sectors").description("빈 배열(기간 내 정산 데이터 없음)"))));
    }

    @Test
    @DisplayName("period 값이 허용되지 않으면 400(STATS_400_001) 문서화")
    void documentInvalidPeriod(RestDocumentationContextProvider restDocumentation) throws Exception {
        // 서비스에 도달하기 전 요청 검증에서 실패하므로 stub 응답은 쓰이지 않는다.
        MockMvc mockMvc = mockMvcWith(historyStub(null), restDocumentation);

        mockMvc.perform(get("/api/stats/history").param("period", "MONTHLY"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("STATS_400_001"))
                .andDo(document(
                        "stats-history-invalid-period",
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부(false)"),
                                fieldWithPath("code").description("에러 코드(STATS_400_001)"),
                                fieldWithPath("message").description("에러 메시지"),
                                fieldWithPath("errorDetail").description("허용값/입력값 등 상세 원인"))));
    }

    @Test
    @DisplayName("period 파라미터 자체가 없으면 400(STATS_400_001) 문서화")
    void documentMissingPeriod(RestDocumentationContextProvider restDocumentation) throws Exception {
        // 서비스에 도달하기 전 요청 검증에서 실패하므로 stub 응답은 쓰이지 않는다.
        MockMvc mockMvc = mockMvcWith(historyStub(null), restDocumentation);

        mockMvc.perform(get("/api/stats/history"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("STATS_400_001"));
    }

    @Test
    @DisplayName("sort 값이 허용되지 않으면 400(STATS_400_002) 문서화")
    void documentInvalidSort(RestDocumentationContextProvider restDocumentation) throws Exception {
        // 서비스에 도달하기 전 요청 검증에서 실패하므로 stub 응답은 쓰이지 않는다.
        MockMvc mockMvc = mockMvcWith(historyStub(null), restDocumentation);

        mockMvc.perform(get("/api/stats/history").param("period", "MONTH").param("sort", "BOGUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("STATS_400_002"))
                .andDo(document(
                        "stats-history-invalid-sort",
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부(false)"),
                                fieldWithPath("code").description("에러 코드(STATS_400_002)"),
                                fieldWithPath("message").description("에러 메시지"),
                                fieldWithPath("errorDetail").description("허용값/입력값 등 상세 원인"))));
    }

    @Test
    @DisplayName("최근 투자 성과 상세 정상 응답 문서화")
    void documentRecentDetail(RestDocumentationContextProvider restDocumentation) throws Exception {
        RecentDetailResponse response = new RecentDetailResponse(
                LocalDate.of(2026, 5, 7),
                500_000L,
                18_000L,
                List.of(
                        new SectorDetailResponse(
                                "SEMICONDUCTOR", "반도체", 18_000L, new BigDecimal("6.0"), 300_000L, false),
                        new SectorDetailResponse("GOLD", "금", 0L, new BigDecimal("0.0"), 200_000L, true)));
        MockMvc mockMvc = mockMvcWith(recentDetailStub(response), restDocumentation);

        mockMvc.perform(get("/api/stats/recent-detail"))
                .andExpect(status().isOk())
                .andDo(document(
                        "stats-recent-detail",
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("result.date").description("최근 정산 완료 투자 일자(KST). 이력 없으면 null"),
                                fieldWithPath("result.totalInvestment").description("총 매수금"),
                                fieldWithPath("result.profitAmount").description("총 손익금(손실 시 음수)"),
                                fieldWithPath("result.sectors[].sectorCode").description("섹터 코드"),
                                fieldWithPath("result.sectors[].sectorName").description("섹터 표시명"),
                                fieldWithPath("result.sectors[].profitAmount").description("섹터 손익금"),
                                fieldWithPath("result.sectors[].profitRate")
                                        .description("섹터 손익률(%). 그 섹터 매수금 기준, 소수 첫째자리"),
                                fieldWithPath("result.sectors[].totalInvestment")
                                        .description("섹터 매수금"),
                                fieldWithPath("result.sectors[].isFallback").description("거래정지 등으로 0%가 적용된 섹터인지 여부"))));
    }

    @Test
    @DisplayName("최근 투자 이력이 없는 빈 상태 응답 문서화")
    void documentEmptyRecentDetail(RestDocumentationContextProvider restDocumentation) throws Exception {
        RecentDetailResponse response = new RecentDetailResponse(null, 0L, 0L, List.of());
        MockMvc mockMvc = mockMvcWith(recentDetailStub(response), restDocumentation);

        mockMvc.perform(get("/api/stats/recent-detail"))
                .andExpect(status().isOk())
                .andDo(document(
                        "stats-recent-detail-empty",
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("result.date").description("정산 이력이 없어 null"),
                                fieldWithPath("result.totalInvestment").description("총 매수금(이력 없음: 0)"),
                                fieldWithPath("result.profitAmount").description("총 손익금(이력 없음: 0)"),
                                fieldWithPath("result.sectors").description("빈 배열(정산 이력 없음)"))));
    }

    private StatsQueryService summaryStub(StatsSummaryResponse response) {
        return new StatsQueryService(null, null) {
            @Override
            public StatsSummaryResponse getSummary(Long userId) {
                return response;
            }
        };
    }

    private StatsQueryService topSectorsStub(TopSectorsResponse response) {
        return new StatsQueryService(null, null) {
            @Override
            public TopSectorsResponse getTopSectors(Long userId) {
                return response;
            }
        };
    }

    private StatsQueryService historyStub(ProfitHistoryResponse response) {
        return new StatsQueryService(null, null) {
            @Override
            public ProfitHistoryResponse getHistory(Long userId, StatsPeriod period, String date, HistorySort sort) {
                return response;
            }
        };
    }

    private StatsQueryService recentDetailStub(RecentDetailResponse response) {
        return new StatsQueryService(null, null) {
            @Override
            public RecentDetailResponse getRecentDetail(Long userId) {
                return response;
            }
        };
    }

    private MockMvc mockMvcWith(StatsQueryService stubService, RestDocumentationContextProvider restDocumentation) {
        return MockMvcBuilders.standaloneSetup(new StatsController(stubService))
                .setControllerAdvice(new GeneralExceptionAdvice())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .apply(documentationConfiguration(restDocumentation)
                        .operationPreprocessors()
                        .withRequestDefaults(prettyPrint())
                        .withResponseDefaults(prettyPrint()))
                .build();
    }
}
