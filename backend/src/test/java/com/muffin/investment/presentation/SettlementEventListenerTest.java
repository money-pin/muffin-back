package com.muffin.investment.presentation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.muffin.global.batch.BatchJobRunner;
import com.muffin.global.event.EtfPricesLoadedEvent;
import com.muffin.investment.application.settlement.SettlementCommandService;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettlementEventListenerTest {

    private static final LocalDate PRICE_DATE = LocalDate.of(2026, 7, 10);

    @Mock
    private SettlementCommandService settlementCommandService;

    private SettlementEventListener listener;

    @BeforeEach
    void setUp() {
        // 배치 러너는 예외를 잡아 로그로 남기는 실제 동작이 검증 대상이므로 모킹하지 않는다.
        listener = new SettlementEventListener(settlementCommandService, new BatchJobRunner());
    }

    @Test
    void onEtfPricesLoaded_isolatesSettlementFailure() {
        doThrow(new IllegalStateException("settlement failure"))
                .when(settlementCommandService)
                .settle(PRICE_DATE);

        assertDoesNotThrow(() -> listener.onEtfPricesLoaded(new EtfPricesLoadedEvent(PRICE_DATE)));

        verify(settlementCommandService).settle(PRICE_DATE);
    }
}
