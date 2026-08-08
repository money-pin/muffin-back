package com.muffin.ranking.application.projection;

import com.muffin.character.domain.enums.MuffinType;
import java.math.BigDecimal;

/** weekly_ranking 스냅샷과 현재 사용자 캐릭터를 함께 읽은 TOP 10 전용 조회 값이다. */
public record Top10RankingProjection(
        Long userId,
        String nicknameSnapshot,
        int rankingPosition,
        Long weeklyProfit,
        BigDecimal weeklyProfitRate,
        Integer percentile,
        Long characterId,
        MuffinType characterType,
        String characterName,
        String characterImageUrl) {}
