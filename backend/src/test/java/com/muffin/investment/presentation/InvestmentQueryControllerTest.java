package com.muffin.investment.presentation;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.muffin.global.apiPayload.handler.GeneralExceptionAdvice;
import com.muffin.investment.application.InvestmentQueryService;
import com.muffin.investment.domain.exception.InvestmentDataIntegrityException;
import com.muffin.investment.presentation.dto.AssetChangeDirection;
import com.muffin.investment.presentation.dto.InvestmentAssetResponse;
import com.muffin.investment.presentation.dto.TodayInvestmentResponse;
import java.math.BigDecimal;
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

@ExtendWith(MockitoExtension.class)
class InvestmentQueryControllerTest {

    @Mock
    private InvestmentQueryService investmentQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(1L, null, List.of()));
        mockMvc = standaloneSetup(new InvestmentController(null, investmentQueryService, null))
                .setControllerAdvice(new GeneralExceptionAdvice())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("총자산 현황을 공통 응답으로 반환한다")
    void getAsset_returnsAssetOverview() throws Exception {
        when(investmentQueryService.getAsset(1L))
                .thenReturn(new InvestmentAssetResponse(
                        1_045_000L, 45_000L, new BigDecimal("4.5000"), AssetChangeDirection.UP, false));

        mockMvc.perform(get("/api/investments/asset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.totalAsset").value(1045000))
                .andExpect(jsonPath("$.result.dailyChangeAmount").value(45000))
                .andExpect(jsonPath("$.result.changeDirection").value("UP"))
                .andExpect(jsonPath("$.result.settlementPending").value(false));

        verify(investmentQueryService).getAsset(1L);
    }

    @Test
    @DisplayName("오늘의 화면 상태를 공통 응답으로 반환한다")
    void getToday_returnsScreenStatus() throws Exception {
        when(investmentQueryService.getToday(1L)).thenReturn(TodayInvestmentResponse.settling());

        mockMvc.perform(get("/api/investments/today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.status").value("SETTLING"))
                .andExpect(jsonPath("$.result.remainingAmount").doesNotExist())
                .andExpect(jsonPath("$.result.nextInvestmentAvailableAt").doesNotExist());

        verify(investmentQueryService).getToday(1L);
    }

    @Test
    @DisplayName("투자 섹터 기준정보 누락은 내부 정보를 노출하지 않고 500으로 반환한다")
    void getToday_missingSectorReferenceReturns500() throws Exception {
        when(investmentQueryService.getToday(1L)).thenThrow(new InvestmentDataIntegrityException(99L));

        mockMvc.perform(get("/api/investments/today"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_500_001"))
                .andExpect(jsonPath("$.message").value("예기치 않은 서버 에러가 발생했습니다."))
                .andExpect(jsonPath("$.errorDetail").doesNotExist());
    }
}
