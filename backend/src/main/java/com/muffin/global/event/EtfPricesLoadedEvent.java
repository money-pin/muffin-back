package com.muffin.global.event;

import java.time.LocalDate;

/**
 * 특정 일자의 ETF 시가 적재가 완료되었음을 알리는 도메인 이벤트.
 *
 * <p>모든 활성 ETF의 시가가 정상 수집되거나 10:00 이후 FINAL_MISSING으로 확정되면 발행하고, 정산 배치가 이를 구독해 실행된다. 시각을 추측하지 않고 데이터가 준비된 순간 정산을 시작하기 위한 것. 발행 측(ETF 로더)과 구독
 * 측(정산)이 서로를 직접 알지 않도록 공용(global)에 둔다.
 *
 * @param priceDate 적재된 시가의 기준 일자
 */
public record EtfPricesLoadedEvent(LocalDate priceDate) {}
