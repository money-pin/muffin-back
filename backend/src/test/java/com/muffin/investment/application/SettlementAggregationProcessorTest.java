package com.muffin.investment.application;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.muffin.investment.application.settlement.SettlementAggregationProcessor;
import com.muffin.investment.domain.investment.Investment;
import com.muffin.investment.domain.investment.InvestmentRepository;
import com.muffin.investment.domain.userasset.UserAsset;
import com.muffin.investment.domain.userasset.UserAssetRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 정산 phase 2 집계 처리기의 정합성 사후조건(자산 델타 = 총손익)을 검증한다. */
@ExtendWith(MockitoExtension.class)
class SettlementAggregationProcessorTest {

    private static final Long INVESTMENT_ID = 1L;
    private static final Long USER_ASSET_ID = 10L;
    private static final LocalDate INVEST_DATE = LocalDate.of(2026, 5, 7);

    @Mock
    private InvestmentRepository investmentRepository;

    @Mock
    private UserAssetRepository userAssetRepository;

    private SettlementAggregationProcessor processor;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                ZonedDateTime.of(2026, 5, 8, 9, 35, 0, 0, ZoneId.of("Asia/Seoul"))
                        .toInstant(),
                ZoneId.of("Asia/Seoul"));
        processor = new SettlementAggregationProcessor(investmentRepository, userAssetRepository, clock);
    }

    @Test
    @DisplayName("자산 반영 후 증가분이 총손익과 다르면(정합성 위반) 예외를 던져 롤백/FAILED로 넘긴다")
    void settle_throwsWhenAssetDeltaMismatch() {
        Investment investment = Investment.confirm(1L, USER_ASSET_ID, INVEST_DATE);
        investment.addSector(100L, 10, 300_000L, null);
        investment.stampSectorNormal(100L, BigDecimal.valueOf(30_000), BigDecimal.valueOf(31_800)); // 총손익 +18,000
        when(investmentRepository.findWithSectorsById(INVESTMENT_ID)).thenReturn(Optional.of(investment));

        // applySettlement이 총자산을 바꾸지 않는(버그를 흉내낸) 자산 → 델타 0 ≠ 18,000
        UserAsset asset = mock(UserAsset.class);
        when(asset.getTotalAsset()).thenReturn(1_000_000L);
        when(userAssetRepository.findById(USER_ASSET_ID)).thenReturn(Optional.of(asset));

        assertThrows(IllegalStateException.class, () -> processor.settle(INVESTMENT_ID));
    }
}
