package com.muffin.investment.presentation;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muffin.investment.application.settlement.SettlementQueryService;
import com.muffin.investment.presentation.dto.SettlementReason;
import com.muffin.investment.presentation.dto.SettlementResultResponse;
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

/** INVEST-06 정산 결과 조회 API의 REST Docs 스니펫을 생성한다(정산 완료 결과 / 결과 없음 두 케이스). */
@ExtendWith(RestDocumentationExtension.class)
class SettlementControllerDocsTest {

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
    @DisplayName("정산 완료(SETTLED) 결과 응답 문서화")
    void documentSettledResult(RestDocumentationContextProvider restDocumentation) throws Exception {
        SettlementResultResponse response = SettlementResultResponse.settled(
                LocalDate.of(2026, 5, 8), 45_000L, new BigDecimal("4.5"), 1_000_000L, 1_045_000L);
        // settled(investDate, totalProfitLoss, totalProfitLossRate, totalAmount, totalAsset)
        MockMvc mockMvc = mockMvcReturning(response, restDocumentation);

        mockMvc.perform(get("/api/investments/settlement/result"))
                .andExpect(status().isOk())
                .andDo(document(
                        "settlement-result-settled",
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("result.investDate").description("투자 일자, KST"),
                                fieldWithPath("result.totalProfitLoss").description("최종 손익금(손실 시 음수, 부호로 수익/손실 판단)"),
                                fieldWithPath("result.totalProfitLossRate").description("투자 원금 대비 손익률(%)"),
                                fieldWithPath("result.totalAmount").description("투자 원금"),
                                fieldWithPath("result.totalAsset").description("정산 반영 후 최종 총자산"))));
    }

    @Test
    @DisplayName("결과 없음/정산 중 응답 문서화")
    void documentReasonResult(RestDocumentationContextProvider restDocumentation) throws Exception {
        SettlementResultResponse response = SettlementResultResponse.reason(SettlementReason.SETTLEMENT_PENDING);
        MockMvc mockMvc = mockMvcReturning(response, restDocumentation);

        mockMvc.perform(get("/api/investments/settlement/result"))
                .andExpect(status().isOk())
                .andDo(document(
                        "settlement-result-reason",
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("result.reason")
                                        .description(
                                                "결과가 없는 사유: NO_INVESTMENT(투자/정산 결과 없음) / SETTLEMENT_PENDING(정산 중)"))));
    }

    private MockMvc mockMvcReturning(
            SettlementResultResponse response, RestDocumentationContextProvider restDocumentation) {
        SettlementQueryService stubService = new SettlementQueryService(null, null) {
            @Override
            public SettlementResultResponse getRecentSettlementResult(Long userId) {
                return response;
            }
        };
        return MockMvcBuilders.standaloneSetup(new SettlementController(stubService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .apply(documentationConfiguration(restDocumentation)
                        .operationPreprocessors()
                        .withRequestDefaults(prettyPrint())
                        .withResponseDefaults(prettyPrint()))
                .build();
    }
}
