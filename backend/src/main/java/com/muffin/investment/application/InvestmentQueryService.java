package com.muffin.investment.application;

import com.muffin.investment.domain.investment.Investment;
import com.muffin.investment.domain.investment.InvestmentRepository;
import com.muffin.investment.domain.investment.InvestmentSector;
import com.muffin.investment.domain.investment.enums.InvestmentStatus;
import com.muffin.investment.domain.userasset.UserAsset;
import com.muffin.investment.domain.userasset.UserAssetRepository;
import com.muffin.investment.presentation.dto.AssetChangeDirection;
import com.muffin.investment.presentation.dto.InvestmentAssetResponse;
import com.muffin.investment.presentation.dto.PreviousInvestmentResponse;
import com.muffin.investment.presentation.dto.TodayInvestmentResponse;
import com.muffin.investment.presentation.dto.TodayInvestmentSectorResponse;
import com.muffin.sector.application.TradingCalendarService;
import com.muffin.sector.application.TradingCalendarService.TradingCalendar;
import com.muffin.sector.domain.sector.Sector;
import com.muffin.sector.domain.sector.SectorRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvestmentQueryService {

    private static final long INITIAL_ASSET = 1_000_000L;
    private static final LocalTime SETTLEMENT_START = LocalTime.of(9, 0);
    private static final LocalTime INVESTMENT_START = LocalTime.of(10, 0);
    private final UserAssetRepository userAssetRepository;
    private final InvestmentRepository investmentRepository;
    private final SectorRepository sectorRepository;
    private final TradingCalendarService tradingCalendarService;
    private final PendingInvestmentChecker pendingInvestmentChecker;
    private final Clock clock;

    public InvestmentAssetResponse getAsset(Long userId) {
        UserAsset asset = userAssetRepository.findByUserId(userId).orElse(null);
        if (asset == null) {
            return new InvestmentAssetResponse(INITIAL_ASSET, 0L, BigDecimal.ZERO, AssetChangeDirection.NONE, false);
        }

        ZonedDateTime now = now();
        LocalDate today = now.toLocalDate();
        TradingCalendar calendar = tradingCalendarService.getCalendar(today);
        boolean settlementPending =
                calendar.tradingDay() && pendingInvestmentChecker.hasPendingInvestment(userId, calendar);
        return new InvestmentAssetResponse(
                asset.getTotalAsset(),
                asset.getDailyChangeAmount(),
                asset.getDailyChangeRate(),
                directionOf(asset.getDailyChangeAmount()),
                settlementPending);
    }

    public TodayInvestmentResponse getToday(Long userId) {
        ZonedDateTime now = now();
        LocalDate today = now.toLocalDate();
        TradingCalendar calendar = tradingCalendarService.getCalendar(today);
        return resolveToday(userId, now, calendar);
    }

    private TodayInvestmentResponse resolveToday(Long userId, ZonedDateTime now, TradingCalendar calendar) {
        LocalDate today = now.toLocalDate();
        if (isWeekend(today)) {
            return TodayInvestmentResponse.closedWeekend(investmentStartAt(calendar.nextTradingDay()));
        }
        if (!calendar.tradingDay()) {
            return TodayInvestmentResponse.closedHoliday(investmentStartAt(calendar.nextTradingDay()));
        }

        LocalTime time = now.toLocalTime();
        PreviousInvestmentResponse previousInvestment =
                time.isBefore(INVESTMENT_START) ? previousInvestment(userId, calendar.previousTradingDay()) : null;
        if (time.isBefore(SETTLEMENT_START)) {
            return TodayInvestmentResponse.unavailable(investmentStartAt(today), previousInvestment);
        }
        if (time.isBefore(INVESTMENT_START)) {
            return TodayInvestmentResponse.settling(previousInvestment);
        }
        if (pendingInvestmentChecker.hasPendingInvestment(userId, calendar)) {
            return TodayInvestmentResponse.delayed();
        }

        return investmentRepository
                .findWithSectorsByUserIdAndInvestDateAndStatus(userId, today, InvestmentStatus.CONFIRMED)
                .map(investment -> confirmedResponse(investment, totalAssetOf(userId)))
                .orElseGet(() -> TodayInvestmentResponse.available(totalAssetOf(userId)));
    }

    private ZonedDateTime now() {
        return ZonedDateTime.now(clock);
    }

    private TodayInvestmentResponse confirmedResponse(Investment investment, long totalAsset) {
        List<TodayInvestmentSectorResponse> sectors = sectorResponses(investment);
        OffsetDateTime confirmDeadline = investment
                .getInvestDate()
                .plusDays(1)
                .atStartOfDay(clock.getZone())
                .toOffsetDateTime();
        return TodayInvestmentResponse.confirmed(
                confirmDeadline, totalAsset - investment.getTotalAmount(), investment.getTotalAmount(), sectors);
    }

    private PreviousInvestmentResponse previousInvestment(Long userId, LocalDate previousTradingDay) {
        return investmentRepository
                .findWithSectorsByUserIdAndInvestDateAndStatus(userId, previousTradingDay, InvestmentStatus.CONFIRMED)
                .map(investment -> new PreviousInvestmentResponse(
                        investment.getInvestDate(), investment.getTotalAmount(), sectorResponses(investment)))
                .orElse(null);
    }

    private List<TodayInvestmentSectorResponse> sectorResponses(Investment investment) {
        Map<Long, Sector> sectorsById =
                sectorRepository
                        .findAllById(investment.getSectors().stream()
                                .map(InvestmentSector::getSectorId)
                                .toList())
                        .stream()
                        .collect(Collectors.toMap(Sector::getId, Function.identity()));

        return investment.getSectors().stream()
                .map(investmentSector -> sectorResponse(
                        investmentSector,
                        requiredSector(sectorsById, investmentSector.getSectorId()),
                        investment.getTotalAmount()))
                .sorted(Comparator.comparing(TodayInvestmentSectorResponse::sectorCode))
                .toList();
    }

    private TodayInvestmentSectorResponse sectorResponse(
            InvestmentSector investmentSector, Sector sector, long totalAmount) {
        BigDecimal ratio = InvestmentRatioCalculator.calculate(investmentSector.getAmount(), totalAmount);
        return new TodayInvestmentSectorResponse(
                sector.getSectorCode(),
                sector.getName(),
                investmentSector.getQuantity(),
                investmentSector.getAmount(),
                ratio);
    }

    private Sector requiredSector(Map<Long, Sector> sectorsById, Long sectorId) {
        Sector sector = sectorsById.get(sectorId);
        if (sector == null) {
            throw new IllegalStateException("투자 섹터 기준 정보를 찾을 수 없습니다: sectorId=" + sectorId);
        }
        return sector;
    }

    private long totalAssetOf(Long userId) {
        return userAssetRepository
                .findByUserId(userId)
                .map(UserAsset::getTotalAsset)
                .orElse(INITIAL_ASSET);
    }

    private AssetChangeDirection directionOf(long dailyChangeAmount) {
        if (dailyChangeAmount > 0) {
            return AssetChangeDirection.UP;
        }
        if (dailyChangeAmount < 0) {
            return AssetChangeDirection.DOWN;
        }
        return AssetChangeDirection.NONE;
    }

    private boolean isWeekend(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    private OffsetDateTime investmentStartAt(LocalDate date) {
        return date.atTime(INVESTMENT_START).atZone(clock.getZone()).toOffsetDateTime();
    }
}
