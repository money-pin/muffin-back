package com.muffin.investment.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muffin.investment.domain.investment.Investment;
import com.muffin.investment.domain.investment.InvestmentRepository;
import com.muffin.investment.domain.investment.enums.InvestmentStatus;
import com.muffin.investment.domain.investment.enums.SettlementStatus;
import com.muffin.investment.domain.userasset.UserAsset;
import com.muffin.investment.domain.userasset.UserAssetRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** 자정 마감은 투자 구성을 '동결'하는 것만 담당한다(가격 스냅샷은 정산 phase 1의 책임). */
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
    @DisplayName("확정 투자를 동결하고 가격은 건드리지 않는다")
    void finalizeUser_freezesConfirmed() {
        Investment investment = Investment.confirm(USER_ID, INVEST_DATE);
        investment.addSector(100L, 2, 200_000L, null);
        when(investmentRepository.findWithSectorsForUpdate(USER_ID, INVEST_DATE))
                .thenReturn(Optional.of(investment));

        processor.finalizeUser(USER_ASSET_ID, INVEST_DATE, FINALIZED_AT);

        assertEquals(FINALIZED_AT, investment.getFinalizedAt());
        assertNull(investment.getSectors().getFirst().getBuyPrice());
        assertEquals(SettlementStatus.PENDING, investment.getSettlementStatus());
    }

    @Test
    @DisplayName("투자하지 않은 사용자는 NO_INVEST 레코드를 생성하고 동결한다")
    void finalizeUser_createsNoInvest() {
        when(investmentRepository.findWithSectorsForUpdate(USER_ID, INVEST_DATE))
                .thenReturn(Optional.empty());
        when(investmentRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        processor.finalizeUser(USER_ASSET_ID, INVEST_DATE, FINALIZED_AT);

        var captor = org.mockito.ArgumentCaptor.forClass(Investment.class);
        verify(investmentRepository).save(captor.capture());
        assertEquals(InvestmentStatus.NO_INVEST, captor.getValue().getStatus());
        assertEquals(FINALIZED_AT, captor.getValue().getFinalizedAt());
    }

    @Test
    @DisplayName("이미 동결된 투자는 재실행해도 finalizedAt을 바꾸지 않는다")
    void finalizeUser_skipsAlreadyFrozen() {
        Investment investment = Investment.confirm(USER_ID, INVEST_DATE);
        investment.addSector(100L, 2, 200_000L, null);
        investment.finalizeInvestment(FINALIZED_AT);
        when(investmentRepository.findWithSectorsForUpdate(USER_ID, INVEST_DATE))
                .thenReturn(Optional.of(investment));

        processor.finalizeUser(USER_ASSET_ID, INVEST_DATE, FINALIZED_AT.plusMinutes(10));

        assertEquals(FINALIZED_AT, investment.getFinalizedAt());
        verify(investmentRepository, never()).save(investment);
    }
}
