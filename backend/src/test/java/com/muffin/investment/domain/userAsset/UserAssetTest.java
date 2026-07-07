package com.muffin.investment.domain.userAsset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.muffin.investment.domain.userasset.UserAsset;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserAssetTest {

    @Test
    @DisplayName("생성하면 초기 자산이 설정되고 일간 변동은 0으로 시작한다")
    void create_initializesAssetWithZeroDailyChange() {
        UserAsset userAsset = UserAsset.create(1L, 1_000_000L);

        assertEquals(1_000_000L, userAsset.getTotalAsset());
        assertEquals(0L, userAsset.getDailyChangeAmount());
        assertEquals(0, BigDecimal.ZERO.compareTo(userAsset.getDailyChangeRate()));
    }

    @Test
    @DisplayName("정산을 반영하면 총자산이 변동액만큼 증가하고 일간 변동이 갱신된다")
    void applySettlement_increasesTotalAssetOnProfit() {
        UserAsset userAsset = UserAsset.create(1L, 1_000_000L);
        LocalDateTime settledAt = LocalDateTime.of(2026, 5, 8, 9, 0);

        userAsset.applySettlement(45_000L, BigDecimal.valueOf(4.5), settledAt);

        assertEquals(1_045_000L, userAsset.getTotalAsset());
        assertEquals(45_000L, userAsset.getDailyChangeAmount());
        assertEquals(0, BigDecimal.valueOf(4.5).compareTo(userAsset.getDailyChangeRate()));
        assertEquals(settledAt, userAsset.getLastSettledAt());
    }

    @Test
    @DisplayName("손실 정산을 반영하면 총자산이 손실액만큼 감소한다")
    void applySettlement_decreasesTotalAssetOnLoss() {
        UserAsset userAsset = UserAsset.create(1L, 1_000_000L);

        userAsset.applySettlement(-30_000L, BigDecimal.valueOf(-3.0), LocalDateTime.of(2026, 5, 8, 9, 0));

        assertEquals(970_000L, userAsset.getTotalAsset());
        assertEquals(-30_000L, userAsset.getDailyChangeAmount());
    }

    @Test
    @DisplayName("손실이 총자산을 초과해 결과가 음수가 되면 예외가 발생한다")
    void applySettlement_throwsWhenResultNegative() {
        UserAsset userAsset = UserAsset.create(1L, 10_000L);

        assertThrows(
                IllegalArgumentException.class,
                () -> userAsset.applySettlement(
                        -20_000L, BigDecimal.valueOf(-200.0), LocalDateTime.of(2026, 5, 8, 9, 0)));
    }
}
