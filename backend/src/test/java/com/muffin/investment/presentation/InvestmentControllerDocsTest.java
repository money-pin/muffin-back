package com.muffin.investment.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muffin.global.apiPayload.handler.GeneralExceptionAdvice;
import com.muffin.investment.application.InvestmentCommandResult;
import com.muffin.investment.application.InvestmentCommandService;
import com.muffin.investment.application.InvestmentQueryService;
import com.muffin.investment.domain.exception.InvestmentException;
import com.muffin.investment.domain.exception.code.InvestmentErrorCode;
import com.muffin.investment.presentation.dto.AssetChangeDirection;
import com.muffin.investment.presentation.dto.InvestmentAssetResponse;
import com.muffin.investment.presentation.dto.PreviousInvestmentResponse;
import com.muffin.investment.presentation.dto.TodayInvestmentResponse;
import com.muffin.investment.presentation.dto.TodayInvestmentSectorResponse;
import com.muffin.sector.domain.exception.SectorException;
import com.muffin.sector.domain.exception.code.SectorErrorCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** INVEST-01, INVEST-03 모의투자 API의 REST Docs 스니펫을 생성한다. */
@ExtendWith({MockitoExtension.class, RestDocumentationExtension.class})
class InvestmentControllerDocsTest {

    private static final Long USER_ID = 1L;
    private static final String AUTHORIZATION = "Bearer {accessToken}";
    private static final String REQUEST =
            """
            {"sectors":[{"sectorCode":"SEMICONDUCTOR","quantity":3},{"sectorCode":"GOLD","quantity":2}]}
            """;
    private static final OffsetDateTime CONFIRM_DEADLINE =
            OffsetDateTime.of(2026, 7, 30, 0, 0, 0, 0, ZoneOffset.ofHours(9));

    @Mock
    private InvestmentCommandService investmentCommandService;

    @Mock
    private InvestmentQueryService investmentQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp(RestDocumentationContextProvider restDocumentation) {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(USER_ID, null, List.of()));
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new InvestmentController(investmentCommandService, investmentQueryService, null))
                .setControllerAdvice(new GeneralExceptionAdvice())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .apply(documentationConfiguration(restDocumentation)
                        .operationPreprocessors()
                        .withRequestDefaults(prettyPrint())
                        .withResponseDefaults(prettyPrint()))
                .build();
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("투자 확정 성공 응답을 문서화한다")
    void documentInvestmentConfirm() throws Exception {
        when(investmentCommandService.confirm(eq(USER_ID), any()))
                .thenReturn(new InvestmentCommandResult(confirmedResponse(), true));

        mockMvc.perform(post("/api/investments")
                        .header("Authorization", AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST))
                .andExpect(status().isCreated())
                .andDo(document(
                        "investment-confirm-success",
                        requestHeaders(headerWithName("Authorization").description("Bearer access token")),
                        requestFields(
                                fieldWithPath("sectors").description("확정할 섹터 목록. 최소 1개"),
                                fieldWithPath("sectors[].sectorCode").description("섹터 코드"),
                                fieldWithPath("sectors[].quantity").description("투자 수량. 1 이상")),
                        todayResponseFields()));
    }

    @Test
    @DisplayName("투자 확정 불가 시간 오류 응답을 문서화한다")
    void documentInvestmentConfirmWindowClosed() throws Exception {
        when(investmentCommandService.confirm(eq(USER_ID), any()))
                .thenThrow(new InvestmentException(InvestmentErrorCode.INVESTMENT_WINDOW_CLOSED));

        mockMvc.perform(post("/api/investments")
                        .header("Authorization", AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST))
                .andExpect(status().isBadRequest())
                .andDo(document(
                        "investment-confirm-window-closed",
                        requestHeaders(headerWithName("Authorization").description("Bearer access token")),
                        requestFields(
                                fieldWithPath("sectors").description("확정할 섹터 목록"),
                                fieldWithPath("sectors[].sectorCode").description("섹터 코드"),
                                fieldWithPath("sectors[].quantity").description("투자 수량")),
                        errorResponseFields("INVESTMENT_400_001")));
    }

    @Test
    @DisplayName("오늘 투자 수정 성공 응답을 문서화한다")
    void documentInvestmentUpdateToday() throws Exception {
        when(investmentCommandService.updateToday(eq(USER_ID), any())).thenReturn(confirmedResponse());

        mockMvc.perform(patch("/api/investments/today")
                        .header("Authorization", AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST))
                .andExpect(status().isOk())
                .andDo(document(
                        "investment-update-today-success",
                        requestHeaders(headerWithName("Authorization").description("Bearer access token")),
                        requestFields(
                                fieldWithPath("sectors").description("수정할 섹터 목록. 최소 1개"),
                                fieldWithPath("sectors[].sectorCode").description("섹터 코드"),
                                fieldWithPath("sectors[].quantity").description("투자 수량. 1 이상")),
                        todayResponseFields()));
    }

    @Test
    @DisplayName("총자산 현황 성공 응답을 문서화한다")
    void documentInvestmentAsset() throws Exception {
        when(investmentQueryService.getAsset(USER_ID))
                .thenReturn(new InvestmentAssetResponse(
                        1_045_000L, 45_000L, new BigDecimal("4.5000"), AssetChangeDirection.UP, false));

        mockMvc.perform(get("/api/investments/asset").header("Authorization", AUTHORIZATION))
                .andExpect(status().isOk())
                .andDo(document(
                        "investment-asset-success",
                        requestHeaders(headerWithName("Authorization").description("Bearer access token")),
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("result.totalAsset").description("현재 총자산"),
                                fieldWithPath("result.dailyChangeAmount").description("직전 정산 대비 자산 변동 금액"),
                                fieldWithPath("result.dailyChangeRate").description("직전 정산 대비 자산 변동률(%)"),
                                fieldWithPath("result.changeDirection").description("자산 변동 방향: UP, DOWN, NONE"),
                                fieldWithPath("result.settlementPending").description("처리 대기 투자가 있는지 여부"))));
    }

    @Test
    @DisplayName("오늘의 모의투자 현황 성공 응답을 문서화한다")
    void documentInvestmentToday() throws Exception {
        when(investmentQueryService.getToday(USER_ID)).thenReturn(confirmedResponse());

        mockMvc.perform(get("/api/investments/today").header("Authorization", AUTHORIZATION))
                .andExpect(status().isOk())
                .andDo(document(
                        "investment-today-success",
                        requestHeaders(headerWithName("Authorization").description("Bearer access token")),
                        todayResponseFields()));
    }

    @Test
    @DisplayName("투자 불가 시간의 직전 거래일 투자 내역 응답을 문서화한다")
    void documentInvestmentTodayUnavailableWithPreviousInvestment() throws Exception {
        PreviousInvestmentResponse previousInvestment = new PreviousInvestmentResponse(
                LocalDate.of(2026, 7, 29),
                500_000L,
                List.of(
                        new TodayInvestmentSectorResponse("GOLD", "금", 2, 200_000L, new BigDecimal("40.00")),
                        new TodayInvestmentSectorResponse(
                                "SEMICONDUCTOR", "반도체", 3, 300_000L, new BigDecimal("60.00"))));
        TodayInvestmentResponse response = TodayInvestmentResponse.unavailable(
                OffsetDateTime.of(2026, 7, 30, 10, 0, 0, 0, ZoneOffset.ofHours(9)), previousInvestment);
        when(investmentQueryService.getToday(USER_ID)).thenReturn(response);

        mockMvc.perform(get("/api/investments/today").header("Authorization", AUTHORIZATION))
                .andExpect(status().isOk())
                .andDo(document(
                        "investment-today-unavailable-with-previous",
                        requestHeaders(headerWithName("Authorization").description("Bearer access token")),
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("result.status").description("현재 화면 상태(UNAVAILABLE 또는 SETTLING)"),
                                fieldWithPath("result.nextInvestmentAvailableAt")
                                        .description("다음 투자 가능 시각(KST). UNAVAILABLE에서 제공"),
                                fieldWithPath("result.previousInvestment").description("직전 거래일 확정 투자 내역"),
                                fieldWithPath("result.previousInvestment.investDate")
                                        .description("직전 투자 거래일"),
                                fieldWithPath("result.previousInvestment.totalAmount")
                                        .description("직전 거래일의 총 투자 금액"),
                                fieldWithPath("result.previousInvestment.sectors")
                                        .description("직전 거래일의 섹터별 투자 내역"),
                                fieldWithPath("result.previousInvestment.sectors[].sectorCode")
                                        .description("섹터 코드"),
                                fieldWithPath("result.previousInvestment.sectors[].sectorName")
                                        .description("섹터 이름"),
                                fieldWithPath("result.previousInvestment.sectors[].quantity")
                                        .description("투자 수량"),
                                fieldWithPath("result.previousInvestment.sectors[].amount")
                                        .description("섹터별 투자 금액"),
                                fieldWithPath("result.previousInvestment.sectors[].ratio")
                                        .description("총 투자 금액 대비 비중(%)"))));
    }

    @Test
    @DisplayName("오늘의 모의투자 현황 거래일 정보 오류 응답을 문서화한다")
    void documentInvestmentTodayCalendarUnavailable() throws Exception {
        when(investmentQueryService.getToday(USER_ID))
                .thenThrow(new SectorException(SectorErrorCode.MARKET_CALENDAR_UNAVAILABLE));

        mockMvc.perform(get("/api/investments/today").header("Authorization", AUTHORIZATION))
                .andExpect(status().isServiceUnavailable())
                .andDo(document(
                        "investment-today-calendar-unavailable",
                        requestHeaders(headerWithName("Authorization").description("Bearer access token")),
                        errorResponseFields("SECTOR_503_001")));
    }

    private TodayInvestmentResponse confirmedResponse() {
        return TodayInvestmentResponse.confirmed(
                CONFIRM_DEADLINE,
                500_000L,
                500_000L,
                List.of(
                        new TodayInvestmentSectorResponse("GOLD", "금", 2, 200_000L, new BigDecimal("40.00")),
                        new TodayInvestmentSectorResponse(
                                "SEMICONDUCTOR", "반도체", 3, 300_000L, new BigDecimal("60.00"))));
    }

    private org.springframework.restdocs.payload.ResponseFieldsSnippet todayResponseFields() {
        return responseFields(
                fieldWithPath("isSuccess").description("성공 여부"),
                fieldWithPath("code").description("응답 코드"),
                fieldWithPath("message").description("응답 메시지"),
                fieldWithPath("result.status")
                        .description(
                                "화면 상태: AVAILABLE, CONFIRMED_EDITABLE, UNAVAILABLE, SETTLING, SETTLEMENT_DELAYED, CLOSED_WEEKEND, CLOSED_HOLIDAY"),
                fieldWithPath("result.confirmDeadline").optional().description("확정 투자 수정 마감 시각(KST)"),
                fieldWithPath("result.remainingAmount").optional().description("추가 투자 가능 금액"),
                fieldWithPath("result.totalAmount").optional().description("오늘 확정한 총 투자 금액"),
                fieldWithPath("result.sectors").optional().description("오늘 확정한 섹터 목록"),
                fieldWithPath("result.sectors[].sectorCode").optional().description("섹터 코드"),
                fieldWithPath("result.sectors[].sectorName").optional().description("섹터 이름"),
                fieldWithPath("result.sectors[].quantity").optional().description("투자 수량"),
                fieldWithPath("result.sectors[].amount").optional().description("섹터별 투자 금액"),
                fieldWithPath("result.sectors[].ratio").optional().description("총 투자 금액 대비 비중(%)"),
                fieldWithPath("result.nextInvestmentAvailableAt")
                        .optional()
                        .type(JsonFieldType.STRING)
                        .description("다음 투자 가능 시각(KST). 투자 불가 상태에서만 제공"));
    }

    private org.springframework.restdocs.payload.ResponseFieldsSnippet errorResponseFields(String code) {
        return responseFields(
                fieldWithPath("isSuccess").description("성공 여부(false)"),
                fieldWithPath("code").description("오류 코드(" + code + ")"),
                fieldWithPath("message").description("오류 메시지"),
                fieldWithPath("errorDetail").description("오류 상세 사유"));
    }
}
