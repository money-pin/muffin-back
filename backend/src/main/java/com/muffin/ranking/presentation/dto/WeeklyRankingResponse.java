package com.muffin.ranking.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.muffin.ranking.domain.RankingStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 랭킹 탭의 나의 순위, 지난주 TOP 10, TOP 10 수익 상세를 함께 반환한다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WeeklyRankingResponse(
        RankingStatus rankingStatus,
        LocalDate weekStartDate,
        LocalDate weekEndDate,
        int weekOfYear,
        String weekLabel,
        @JsonInclude(JsonInclude.Include.ALWAYS) MyRankResponse myRank,
        List<Top10Response> top10) {

    public static WeeklyRankingResponse ready(WeekInfo weekInfo, MyRankResponse myRank, List<Top10Response> top10) {
        return new WeeklyRankingResponse(
                RankingStatus.READY,
                weekInfo.weekStartDate(),
                weekInfo.weekEndDate(),
                weekInfo.weekOfYear(),
                weekInfo.weekLabel(),
                myRank,
                top10);
    }

    public static WeeklyRankingResponse calculating(WeekInfo weekInfo) {
        return new WeeklyRankingResponse(
                RankingStatus.CALCULATING,
                weekInfo.weekStartDate(),
                weekInfo.weekEndDate(),
                weekInfo.weekOfYear(),
                weekInfo.weekLabel(),
                null,
                List.of());
    }

    public static WeeklyRankingResponse empty(WeekInfo weekInfo) {
        return new WeeklyRankingResponse(
                RankingStatus.EMPTY,
                weekInfo.weekStartDate(),
                weekInfo.weekEndDate(),
                weekInfo.weekOfYear(),
                weekInfo.weekLabel(),
                MyRankResponse.notParticipated(),
                List.of());
    }

    public record WeekInfo(LocalDate weekStartDate, LocalDate weekEndDate, int weekOfYear, String weekLabel) {}

    public record MyRankResponse(boolean participated, Integer rank, String nickname, Integer topPercent) {

        public static MyRankResponse participated(int rank, String nickname, Integer topPercent) {
            return new MyRankResponse(true, rank, nickname, topPercent);
        }

        public static MyRankResponse notParticipated() {
            return new MyRankResponse(false, null, null, null);
        }
    }

    public record Top10Response(
            int rank, String nickname, long profitAmount, BigDecimal profitRate, Top10DetailResponse detail) {}

    public record Top10DetailResponse(long totalInvestment, List<SectorResponse> sectors) {}

    public record SectorResponse(
            String sectorCode, String sectorName, long profitAmount, BigDecimal profitRate, long totalInvestment) {}
}
