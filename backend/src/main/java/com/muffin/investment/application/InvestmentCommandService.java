package com.muffin.investment.application;

import com.muffin.investment.domain.exception.InvestmentException;
import com.muffin.investment.domain.exception.code.InvestmentErrorCode;
import com.muffin.investment.domain.investment.Investment;
import com.muffin.investment.domain.investment.Investment.SectorAllocation;
import com.muffin.investment.domain.investment.InvestmentRepository;
import com.muffin.investment.domain.investment.InvestmentSector;
import com.muffin.investment.domain.investment.enums.InvestmentStatus;
import com.muffin.investment.domain.userasset.UserAsset;
import com.muffin.investment.domain.userasset.UserAssetRepository;
import com.muffin.investment.presentation.dto.InvestmentRequest;
import com.muffin.investment.presentation.dto.InvestmentSectorRequest;
import com.muffin.investment.presentation.dto.TodayInvestmentResponse;
import com.muffin.investment.presentation.dto.TodayInvestmentSectorResponse;
import com.muffin.sector.application.TradingCalendarService;
import com.muffin.sector.application.TradingCalendarService.TradingCalendar;
import com.muffin.sector.domain.sector.Sector;
import com.muffin.sector.domain.sector.SectorRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InvestmentCommandService {

    private static final long UNIT_AMOUNT = 100_000L;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalTime INVESTMENT_START = LocalTime.of(10, 0);
    private final UserAssetRepository userAssetRepository;
    private final InvestmentRepository investmentRepository;
    private final SectorRepository sectorRepository;
    private final TradingCalendarService tradingCalendarService;
    private final PendingInvestmentChecker pendingInvestmentChecker;
    private final Clock clock;

    @Transactional
    public InvestmentCommandResult confirm(Long userId, InvestmentRequest request) {
        ZonedDateTime now = now();
        LocalDate today = now.toLocalDate();
        UserAsset asset = requiredAssetForUpdate(userId);
        NormalizedInvestment normalized = normalize(request);

        Investment existing = investmentRepository
                .findWithSectorsByUserIdAndInvestDate(userId, today)
                .orElse(null);
        if (existing != null) {
            if (sameConfiguration(existing, normalized.allocations())) {
                return new InvestmentCommandResult(
                        toResponse(existing, asset.getTotalAsset(), normalized.sectors()), false);
            }
            throw new InvestmentException(InvestmentErrorCode.INVESTMENT_ALREADY_CONFIRMED);
        }

        requireInvestmentWindow(userId, now);
        requireWithinBudget(normalized.totalAmount(), asset.getTotalAsset());

        Investment investment = Investment.confirm(userId, today);
        investment.replaceSectors(normalized.allocations());
        investmentRepository.save(investment);
        return new InvestmentCommandResult(toResponse(investment, asset.getTotalAsset(), normalized.sectors()), true);
    }

    @Transactional
    public TodayInvestmentResponse updateToday(Long userId, InvestmentRequest request) {
        ZonedDateTime requestedAt = now();
        requireInvestmentWindow(userId, requestedAt);

        UserAsset asset = requiredAssetForUpdate(userId);
        Investment investment = investmentRepository
                .findWithSectorsForUpdate(userId, requestedAt.toLocalDate())
                .filter(existing -> existing.getStatus() == InvestmentStatus.CONFIRMED)
                .orElseThrow(() -> new InvestmentException(InvestmentErrorCode.INVESTMENT_NOT_FOUND));

        ZonedDateTime lockedAt = now();
        if (!lockedAt.toLocalDate().equals(investment.getInvestDate())
                || lockedAt.toLocalTime().isBefore(INVESTMENT_START)
                || investment.getFinalizedAt() != null) {
            throw new InvestmentException(InvestmentErrorCode.INVESTMENT_WINDOW_CLOSED);
        }

        NormalizedInvestment normalized = normalize(request);
        requireWithinBudget(normalized.totalAmount(), asset.getTotalAsset());
        investment.replaceSectors(normalized.allocations());
        return toResponse(investment, asset.getTotalAsset(), normalized.sectors());
    }

    private UserAsset requiredAssetForUpdate(Long userId) {
        return userAssetRepository
                .findByUserIdForUpdate(userId)
                .orElseThrow(() -> new InvestmentException(InvestmentErrorCode.USER_ASSET_NOT_INITIALIZED));
    }

    private void requireInvestmentWindow(Long userId, ZonedDateTime now) {
        LocalDate today = now.toLocalDate();
        TradingCalendar calendar = tradingCalendarService.getCalendar(today);
        if (!calendar.tradingDay()
                || now.toLocalTime().isBefore(INVESTMENT_START)
                || pendingInvestmentChecker.hasPendingInvestment(userId, calendar)) {
            throw new InvestmentException(InvestmentErrorCode.INVESTMENT_WINDOW_CLOSED);
        }
    }

    private NormalizedInvestment normalize(InvestmentRequest request) {
        Map<String, Integer> quantities = new TreeMap<>();
        for (InvestmentSectorRequest sector : request.sectors()) {
            try {
                quantities.merge(sector.sectorCode(), sector.quantity(), Math::addExact);
            } catch (ArithmeticException exception) {
                throw new InvestmentException(InvestmentErrorCode.BUDGET_EXCEEDED);
            }
        }

        List<Sector> sectors = new ArrayList<>();
        List<SectorAllocation> allocations = new ArrayList<>();
        long totalAmount = 0L;
        for (Map.Entry<String, Integer> entry : quantities.entrySet()) {
            Sector sector = sectorRepository
                    .findBySectorCode(entry.getKey())
                    .filter(Sector::isActive)
                    .orElseThrow(() -> new InvestmentException(InvestmentErrorCode.INVALID_SECTOR));
            long amount;
            try {
                amount = Math.multiplyExact(UNIT_AMOUNT, entry.getValue().longValue());
                totalAmount = Math.addExact(totalAmount, amount);
            } catch (ArithmeticException exception) {
                throw new InvestmentException(InvestmentErrorCode.BUDGET_EXCEEDED);
            }
            sectors.add(sector);
            allocations.add(new SectorAllocation(sector.getId(), entry.getValue(), amount));
        }
        return new NormalizedInvestment(List.copyOf(allocations), List.copyOf(sectors), totalAmount);
    }

    private void requireWithinBudget(long totalAmount, long totalAsset) {
        if (totalAmount > totalAsset) {
            throw new InvestmentException(InvestmentErrorCode.BUDGET_EXCEEDED);
        }
    }

    private boolean sameConfiguration(Investment investment, List<SectorAllocation> requested) {
        Map<Long, Integer> existing = investment.getSectors().stream()
                .collect(Collectors.toMap(InvestmentSector::getSectorId, InvestmentSector::getQuantity));
        Map<Long, Integer> normalized =
                requested.stream().collect(Collectors.toMap(SectorAllocation::sectorId, SectorAllocation::quantity));
        return existing.equals(normalized);
    }

    private TodayInvestmentResponse toResponse(Investment investment, long totalAsset, List<Sector> requestedSectors) {
        Map<Long, Sector> sectorsById =
                requestedSectors.stream().collect(Collectors.toMap(Sector::getId, Function.identity()));
        List<TodayInvestmentSectorResponse> sectors = investment.getSectors().stream()
                .map(item -> sectorResponse(item, sectorsById.get(item.getSectorId()), investment.getTotalAmount()))
                .sorted(Comparator.comparing(TodayInvestmentSectorResponse::sectorCode))
                .toList();
        OffsetDateTime deadline =
                investment.getInvestDate().plusDays(1).atStartOfDay(KST).toOffsetDateTime();
        return TodayInvestmentResponse.confirmed(
                deadline, totalAsset - investment.getTotalAmount(), investment.getTotalAmount(), sectors);
    }

    private TodayInvestmentSectorResponse sectorResponse(InvestmentSector item, Sector sector, long totalAmount) {
        if (sector == null) {
            throw new InvestmentException(InvestmentErrorCode.INVALID_SECTOR);
        }
        BigDecimal ratio = InvestmentRatioCalculator.calculate(item.getAmount(), totalAmount);
        return new TodayInvestmentSectorResponse(
                sector.getSectorCode(), sector.getName(), item.getQuantity(), item.getAmount(), ratio);
    }

    private ZonedDateTime now() {
        return ZonedDateTime.now(clock).withZoneSameInstant(KST);
    }

    private record NormalizedInvestment(List<SectorAllocation> allocations, List<Sector> sectors, long totalAmount) {}
}
