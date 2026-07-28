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
import com.muffin.investment.presentation.dto.TodayInvestmentResponse;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
}
