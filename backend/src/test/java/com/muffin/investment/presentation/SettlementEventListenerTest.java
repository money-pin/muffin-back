package com.muffin.investment.presentation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.muffin.global.event.EtfPricesLoadedEvent;
import com.muffin.investment.application.SettlementCommandService;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettlementEventListenerTest {

    private static final LocalDate PRICE_DATE = LocalDate.of(2026, 7, 10);

    @Mock
    private SettlementCommandService settlementCommandService;

    @InjectMocks
    private SettlementEventListener listener;

    @Test
    void onEtfPricesLoaded_isolatesSettlementFailure() {
        doThrow(new IllegalStateException("settlement failure"))
                .when(settlementCommandService)
                .settle(PRICE_DATE);

        assertDoesNotThrow(() -> listener.onEtfPricesLoaded(new EtfPricesLoadedEvent(PRICE_DATE)));

        verify(settlementCommandService).settle(PRICE_DATE);
    }
}
