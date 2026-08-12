package com.muffin.investment.presentation;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.muffin.global.apiPayload.handler.GeneralExceptionAdvice;
import com.muffin.investment.application.InvestmentCommandResult;
import com.muffin.investment.application.InvestmentCommandService;
import com.muffin.investment.domain.exception.InvestmentException;
import com.muffin.investment.domain.exception.code.InvestmentErrorCode;
import com.muffin.investment.presentation.dto.TodayInvestmentResponse;
import com.muffin.sector.domain.exception.SectorException;
import com.muffin.sector.domain.exception.code.SectorErrorCode;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(MockitoExtension.class)
class InvestmentCommandControllerTest {

    private static final Long USER_ID = 1L;
    private static final String REQUEST =
            """
            {"sectors":[{"sectorCode":"SEMICONDUCTOR","quantity":1}]}
            """;

    @Mock
    private InvestmentCommandService investmentCommandService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(USER_ID, null, List.of()));
        mockMvc = standaloneSetup(new InvestmentController(investmentCommandService, null, null))
                .setControllerAdvice(new GeneralExceptionAdvice())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("최초 POST는 실제 HTTP 201과 생성 성공 코드를 반환한다")
    void confirm_createdReturns201() throws Exception {
        when(investmentCommandService.confirm(
                        org.mockito.ArgumentMatchers.eq(USER_ID), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new InvestmentCommandResult(TodayInvestmentResponse.available(900_000L), true));

        mockMvc.perform(post("/api/investments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("COMMON_201_001"));

        verify(investmentCommandService)
                .confirm(org.mockito.ArgumentMatchers.eq(USER_ID), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("동일 POST 재요청은 실제 HTTP 200을 반환한다")
    void confirm_idempotentRetryReturns200() throws Exception {
        when(investmentCommandService.confirm(
                        org.mockito.ArgumentMatchers.eq(USER_ID), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new InvestmentCommandResult(TodayInvestmentResponse.available(900_000L), false));

        mockMvc.perform(post("/api/investments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON_200_001"));
    }

    @Test
    @DisplayName("PATCH는 오늘 투자 수정 결과를 200으로 반환한다")
    void updateToday_returns200() throws Exception {
        when(investmentCommandService.updateToday(
                        org.mockito.ArgumentMatchers.eq(USER_ID), org.mockito.ArgumentMatchers.any()))
                .thenReturn(TodayInvestmentResponse.available(900_000L));

        mockMvc.perform(patch("/api/investments/today")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON_200_001"));
    }

    @Test
    @DisplayName("빈 섹터 요청은 검증 오류로 거부한다")
    void confirm_emptySectorsReturns400() throws Exception {
        mockMvc.perform(post("/api/investments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectors\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400_002"));
    }

    @Test
    @DisplayName("null 섹터 요소는 검증 오류로 거부한다")
    void confirm_nullSectorReturns400() throws Exception {
        mockMvc.perform(post("/api/investments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectors\":[null]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400_002"));
    }

    @ParameterizedTest(name = "{1}은 HTTP {2}로 반환한다")
    @MethodSource("confirmInvestmentErrorCases")
    @DisplayName("투자 확정 도메인 오류를 공통 오류 응답으로 반환한다")
    void confirm_mapsInvestmentErrors(
            InvestmentErrorCode errorCode, String expectedCode, int expectedStatus, String expectedMessage)
            throws Exception {
        when(investmentCommandService.confirm(
                        org.mockito.ArgumentMatchers.eq(USER_ID), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new InvestmentException(errorCode));

        mockMvc.perform(post("/api/investments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.message").value(expectedMessage))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    @DisplayName("수정할 오늘 투자가 없으면 404 공통 오류 응답을 반환한다")
    void updateToday_investmentNotFoundReturns404() throws Exception {
        when(investmentCommandService.updateToday(
                        org.mockito.ArgumentMatchers.eq(USER_ID), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new InvestmentException(InvestmentErrorCode.INVESTMENT_NOT_FOUND));

        mockMvc.perform(patch("/api/investments/today")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("INVESTMENT_404_001"))
                .andExpect(jsonPath("$.message").value("오늘 확정한 투자를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    @DisplayName("거래일 조회 장애는 503 공통 오류 응답을 반환한다")
    void confirm_calendarUnavailableReturns503() throws Exception {
        when(investmentCommandService.confirm(
                        org.mockito.ArgumentMatchers.eq(USER_ID), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new SectorException(SectorErrorCode.MARKET_CALENDAR_UNAVAILABLE));

        mockMvc.perform(post("/api/investments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("SECTOR_503_001"))
                .andExpect(jsonPath("$.message").value("거래일 정보를 확인할 수 없습니다. 잠시 후 다시 시도해주세요."))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    private static Stream<Arguments> confirmInvestmentErrorCases() {
        return Stream.of(
                Arguments.of(
                        InvestmentErrorCode.INVESTMENT_WINDOW_CLOSED,
                        "INVESTMENT_400_001",
                        400,
                        "현재는 투자할 수 있는 시간이 아닙니다."),
                Arguments.of(InvestmentErrorCode.INVALID_SECTOR, "INVESTMENT_400_002", 400, "투자할 수 없는 섹터가 포함되어 있습니다."),
                Arguments.of(InvestmentErrorCode.BUDGET_EXCEEDED, "INVESTMENT_400_003", 400, "투자 금액이 보유 자산을 초과합니다."),
                Arguments.of(
                        InvestmentErrorCode.INVESTMENT_ALREADY_CONFIRMED,
                        "INVESTMENT_409_001",
                        409,
                        "오늘 이미 다른 구성으로 투자를 확정했습니다."),
                Arguments.of(
                        InvestmentErrorCode.USER_ASSET_NOT_INITIALIZED,
                        "INVESTMENT_409_002",
                        409,
                        "투자에 필요한 사용자 자산이 아직 생성되지 않았습니다."));
    }
}
