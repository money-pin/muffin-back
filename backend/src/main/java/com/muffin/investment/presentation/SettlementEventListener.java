package com.muffin.investment.presentation;

import com.muffin.global.event.EtfPricesLoadedEvent;
import com.muffin.investment.application.SettlementCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * ETF 시세 적재 완료 이벤트를 받아 정산을 실행하는 인바운드 어댑터(1차 트리거). 시각을 추측하지 않고 데이터가 준비된 순간 정산을 시작한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementEventListener {

    private final SettlementCommandService settlementCommandService;

    @EventListener
    public void onEtfPricesLoaded(EtfPricesLoadedEvent event) {
        log.info("[settlement] triggered by EtfPricesLoadedEvent priceDate={}", event.priceDate());
        settlementCommandService.settle(event.priceDate());
    }
}
