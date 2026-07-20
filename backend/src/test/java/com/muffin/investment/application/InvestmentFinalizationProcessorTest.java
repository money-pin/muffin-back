package com.muffin.investment.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muffin.investment.domain.investment.Investment;
import com.muffin.investment.domain.investment.InvestmentRepository;
import com.muffin.investment.domain.investment.enums.InvestmentStatus;
import com.muffin.investment.domain.investment.enums.SettlementStatus;
import com.muffin.investment.domain.userasset.UserAsset;
import com.muffin.investment.domain.userasset.UserAssetRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InvestmentFinalizationProcessorTest {

    private static final Long USER_ID = 1L;
    private static final Long USER_ASSET_ID = 10L;
    private static final LocalDate INVEST_DATE = LocalDate.of(2026, 7, 13);
    private static final LocalDateTime FINALIZED_AT = LocalDateTime.of(2026, 7, 14, 0, 0);

    @Mock
    private UserAssetRepository userAssetRepository;

    @Mock
    private InvestmentRepository investmentRepository;

    private InvestmentFinalizationProcessor processor;
    private UserAsset asset;

    @BeforeEach
    void setUp() {
        processor = new InvestmentFinalizationProcessor(userAssetRepository, investmentRepository);
        asset = UserAsset.create(USER_ID, 1_000_000L);
        ReflectionTestUtils.setField(asset, "id", USER_ASSET_ID);
        when(userAssetRepository.findByIdForUpdate(USER_ASSET_ID)).thenReturn(Optional.of(asset));
    }

    @Test
    @DisplayName("확정 투자에 종가를 매수가로 반영하고 구성을 동결한다")
    void finalizeUser_assignsClosePrice() {
        Investment investment = Investment.confirm(USER_ID, USER_ASSET_ID, INVEST_DATE);
        investment.addSector(100L, 2, 200_000L, null);
        when(investmentRepository.findWithSectorsForUpdate(USER_ID, INVEST_DATE))
                .thenReturn(Optional.of(investment));

        processor.finalizeUser(USER_ASSET_ID, INVEST_DATE, FINALIZED_AT, Map.of(100L, BigDecimal.valueOf(12_345)));

        assertEquals(
                BigDecimal.valueOf(12_345), investment.getSectors().getFirst().getBuyPrice());
        assertEquals(FINALIZED_AT, investment.getFinalizedAt());
        assertEquals(SettlementStatus.PENDING, investment.getSettlementStatus());
    }

    @Test
    @DisplayName("종가가 없으면 투자는 동결하고 FAILED 재처리 대상으로 유지한다")
    void finalizeUser_marksFailedWhenCloseMissing() {
        Investment investment = Investment.confirm(USER_ID, USER_ASSET_ID, INVEST_DATE);
        investment.addSector(100L, 2, 200_000L, null);
        when(investmentRepository.findWithSectorsForUpdate(USER_ID, INVEST_DATE))
                .thenReturn(Optional.of(investment));

        processor.finalizeUser(USER_ASSET_ID, INVEST_DATE, FINALIZED_AT, Map.of());

        assertEquals(FINALIZED_AT, investment.getFinalizedAt());
        assertEquals(SettlementStatus.FAILED, investment.getSettlementStatus());
    }

    @Test
    @DisplayName("투자하지 않은 사용자는 NO_INVEST 레코드를 생성하고 동결한다")
    void finalizeUser_createsNoInvest() {
        when(investmentRepository.findWithSectorsForUpdate(USER_ID, INVEST_DATE))
                .thenReturn(Optional.empty());
        when(investmentRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        processor.finalizeUser(USER_ASSET_ID, INVEST_DATE, FINALIZED_AT, Map.of());

        var captor = org.mockito.ArgumentCaptor.forClass(Investment.class);
        verify(investmentRepository).save(captor.capture());
        assertEquals(InvestmentStatus.NO_INVEST, captor.getValue().getStatus());
        assertEquals(FINALIZED_AT, captor.getValue().getFinalizedAt());
    }

    @Test
    @DisplayName("이미 매수가까지 마감된 투자는 재실행해도 변경하지 않는다")
    void finalizeUser_skipsCompletedFinalization() {
        Investment investment = Investment.confirm(USER_ID, USER_ASSET_ID, INVEST_DATE);
        investment.addSector(100L, 2, 200_000L, null);
        investment.finalizeInvestment(Map.of(100L, BigDecimal.valueOf(12_345)), FINALIZED_AT);
        when(investmentRepository.findWithSectorsForUpdate(USER_ID, INVEST_DATE))
                .thenReturn(Optional.of(investment));

        processor.finalizeUser(
                USER_ASSET_ID, INVEST_DATE, FINALIZED_AT.plusMinutes(10), Map.of(100L, BigDecimal.valueOf(99_999)));

        assertEquals(
                BigDecimal.valueOf(12_345), investment.getSectors().getFirst().getBuyPrice());
        assertFalse(investment.hasMissingBuyPrice());
        verify(investmentRepository, never()).save(investment);
    }
}
