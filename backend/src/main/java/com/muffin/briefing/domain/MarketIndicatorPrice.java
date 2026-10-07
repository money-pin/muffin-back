package com.muffin.briefing.domain;

import com.muffin.briefing.domain.enums.MarketIndicator;
import com.muffin.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 지표 하나의 일별 종가와 등락률.
 *
 * <p>등락률을 저장해 두는 이유는 브리핑과 같다. 발행된 콘텐츠는 불변이어야 하고, 조회 시점에 다시 계산하면 직전 거래일 판정이 달라질 때
 * 과거 브리핑의 숫자가 바뀐다.
 *
 * <p>수집에 실패한 지표도 행을 남긴다. 행이 없는 것(아직 수집 전)과 받아오지 못한 것을 구분해야 재시도 판단과 관측이 가능하다.
 */
@Entity
@Getter
@Table(
        name = "market_indicator_price",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_market_indicator_price_indicator_date",
                        columnNames = {"indicator", "price_date"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketIndicatorPrice extends BaseEntity {

    /** 등락률 계산 중간 정밀도. 최종 결과는 소수점 둘째 자리로 반올림한다. */
    private static final int DIVISION_SCALE = 10;

    private static final BigDecimal PERCENT = BigDecimal.valueOf(100);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "market_indicator_price_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "indicator", nullable = false, length = 20)
    private MarketIndicator indicator;

    @Column(name = "price_date", nullable = false)
    private LocalDate priceDate;

    /** 지수는 포인트, 환율은 원, 해외는 ETF 달러 가격. 단위는 {@link MarketIndicator#unit()}이 정한다. */
    @Column(name = "close_price", precision = 18, scale = 4)
    private BigDecimal closePrice;

    /** 직전 종가 대비 등락률(%). 비교할 직전 종가가 없으면 null이다. */
    @Column(name = "change_rate", precision = 9, scale = 2)
    private BigDecimal changeRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MarketIndicatorStatus status;

    private MarketIndicatorPrice(
            MarketIndicator indicator,
            LocalDate priceDate,
            BigDecimal closePrice,
            BigDecimal changeRate,
            MarketIndicatorStatus status) {
        this.indicator = indicator;
        this.priceDate = priceDate;
        this.closePrice = closePrice;
        this.changeRate = changeRate;
        this.status = status;
    }

    /**
     * 종가와 직전 종가로 지표를 기록한다. 직전 종가가 없으면 등락률 없이 종가만 남긴다.
     *
     * @param previousClose 직전 거래일 종가. 없으면 null
     */
    public static MarketIndicatorPrice success(
            MarketIndicator indicator, LocalDate priceDate, BigDecimal closePrice, BigDecimal previousClose) {
        if (indicator == null || priceDate == null) {
            throw new IllegalArgumentException("indicator와 priceDate는 필수입니다.");
        }
        if (closePrice == null) {
            throw new IllegalArgumentException("closePrice는 필수입니다.");
        }
        return new MarketIndicatorPrice(
                indicator, priceDate, closePrice, changeRate(closePrice, previousClose), MarketIndicatorStatus.SUCCESS);
    }

    /** 받아오지 못한 지표. 화면에서는 해당 카드만 비우고 나머지는 정상 노출한다. */
    public static MarketIndicatorPrice unavailable(
            MarketIndicator indicator, LocalDate priceDate, MarketIndicatorStatus status) {
        if (status == MarketIndicatorStatus.SUCCESS) {
            throw new IllegalArgumentException("성공 상태는 success()로 생성해야 합니다.");
        }
        return new MarketIndicatorPrice(indicator, priceDate, null, null, status);
    }

    /** 재수집에 성공하면 같은 행을 갱신한다. 하루에 여러 번 돌려도 결과가 같다. */
    public void update(BigDecimal closePrice, BigDecimal previousClose) {
        if (closePrice == null) {
            throw new IllegalArgumentException("closePrice는 필수입니다.");
        }
        this.closePrice = closePrice;
        this.changeRate = changeRate(closePrice, previousClose);
        this.status = MarketIndicatorStatus.SUCCESS;
    }

    public void markUnavailable(MarketIndicatorStatus status) {
        if (status == MarketIndicatorStatus.SUCCESS) {
            throw new IllegalArgumentException("성공 상태로는 변경할 수 없습니다.");
        }
        // 이미 받아둔 값이 있으면 지우지 않는다. 재시도 실패가 확보한 데이터를 날리면 안 된다.
        if (this.status != MarketIndicatorStatus.SUCCESS) {
            this.status = status;
        }
    }

    public boolean isUsable() {
        return status == MarketIndicatorStatus.SUCCESS && closePrice != null;
    }

    private static BigDecimal changeRate(BigDecimal closePrice, BigDecimal previousClose) {
        if (previousClose == null || previousClose.signum() == 0) {
            return null;
        }
        return closePrice
                .subtract(previousClose)
                .divide(previousClose, DIVISION_SCALE, RoundingMode.HALF_UP)
                .multiply(PERCENT)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
