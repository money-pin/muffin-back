package com.muffin.sector.application;

import com.muffin.global.apiPayload.exception.GeneralException;
import com.muffin.sector.exception.SectorErrorCode;
import com.muffin.sector.infrastructure.toss.TossMarketDataClient;
import com.muffin.sector.infrastructure.toss.dto.TossMarketCalendarResponse.BusinessDay;
import com.muffin.sector.infrastructure.toss.dto.TossMarketCalendarResponse.Result;
import com.muffin.sector.infrastructure.toss.dto.TossMarketCalendarResponse.Session;
import com.muffin.sector.infrastructure.toss.exception.TossApiException;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 토스증권 국내 마켓 캘린더를 검증하고 모의투자에서 사용할 거래일 정보로 변환한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradingCalendarService {

    private final TossMarketDataClient tossMarketDataClient;

    /** 요청 날짜의 거래일 여부와 직전·다음 거래일을 한 번의 외부 API 호출로 조회한다. */
    public TradingCalendar getCalendar(LocalDate date) {
        Result result;
        try {
            result = tossMarketDataClient.getMarketCalendar(date);
        } catch (TossApiException exception) {
            log.warn("거래일 API 호출에 실패했습니다. date={}, code={}", date, exception.getTossCode());
            throw unavailable();
        }

        if (result == null || !isRequestedDay(result.today(), date)) {
            throw unavailable(date, "requested date mismatch");
        }

        BusinessDay previous = result.previousBusinessDay();
        BusinessDay next = result.nextBusinessDay();
        if (!isCompleteTradingDay(previous)
                || !isCompleteTradingDay(next)
                || !previous.date().isBefore(date)
                || !next.date().isAfter(date)) {
            throw unavailable(date, "previous or next trading day is incomplete");
        }

        BusinessDay today = result.today();
        boolean tradingDay = hasRegularMarket(today);
        if (tradingDay && !hasCompleteRegularMarket(today)) {
            throw unavailable(date, "regular market session is incomplete");
        }

        return new TradingCalendar(date, tradingDay, previous.date(), next.date());
    }

    private boolean isRequestedDay(BusinessDay businessDay, LocalDate requestedDate) {
        return businessDay != null && requestedDate != null && requestedDate.equals(businessDay.date());
    }

    private boolean isCompleteTradingDay(BusinessDay businessDay) {
        return businessDay != null && businessDay.date() != null && hasCompleteRegularMarket(businessDay);
    }

    private boolean hasRegularMarket(BusinessDay businessDay) {
        return businessDay.integrated() != null && businessDay.integrated().regularMarket() != null;
    }

    private boolean hasCompleteRegularMarket(BusinessDay businessDay) {
        if (!hasRegularMarket(businessDay)) {
            return false;
        }
        Session regularMarket = businessDay.integrated().regularMarket();
        return regularMarket.startTime() != null
                && !regularMarket.startTime().isBlank()
                && regularMarket.endTime() != null
                && !regularMarket.endTime().isBlank();
    }

    private GeneralException unavailable() {
        return new GeneralException(SectorErrorCode.MARKET_CALENDAR_UNAVAILABLE);
    }

    private GeneralException unavailable(LocalDate date, String reason) {
        log.warn("거래일 API 응답을 사용할 수 없습니다. date={}, reason={}", date, reason);
        return unavailable();
    }

    public record TradingCalendar(
            LocalDate date, boolean tradingDay, LocalDate previousTradingDay, LocalDate nextTradingDay) {}
}
