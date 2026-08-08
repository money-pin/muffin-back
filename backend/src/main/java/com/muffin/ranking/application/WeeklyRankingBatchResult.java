package com.muffin.ranking.application;

import java.time.LocalDate;

/**
 * 주간 랭킹 집계 1회 실행 결과.
 *
 * <p>이 잡은 하루 세 번 돌지만 실제로 저장하는 것은 그중 한 번뿐이다(나머지는 이미 생성됨으로 끝난다). 그래서 "성공했다"만으로는 부족하고 <b>왜 아무것도 안 했는지</b>가
 * 구분돼야 한다. 특히 {@link Outcome#SETTLEMENT_PENDING}이 계속되면 랭킹이 아니라 정산이 밀리고 있다는 신호다.
 *
 * @param outcome 이번 실행이 무엇을 했는지
 * @param weekStartDate 집계 대상 주의 시작일(월요일)
 * @param participantCount 저장한 랭킹 참가자 수. 저장하지 않았으면 0
 */
public record WeeklyRankingBatchResult(Outcome outcome, LocalDate weekStartDate, int participantCount) {

    public enum Outcome {
        /** 랭킹을 새로 저장했다. */
        CREATED,
        /** 이미 저장돼 있어 아무것도 하지 않았다. */
        ALREADY_CREATED,
        /** 대상 주에 미정산 투자가 남아 있어 집계를 미뤘다. */
        SETTLEMENT_PENDING,
        /** 집계 대상 참가자가 없다. */
        NO_PARTICIPANTS
    }

    static WeeklyRankingBatchResult of(Outcome outcome, LocalDate weekStartDate) {
        return new WeeklyRankingBatchResult(outcome, weekStartDate, 0);
    }

    static WeeklyRankingBatchResult created(LocalDate weekStartDate, int participantCount) {
        return new WeeklyRankingBatchResult(Outcome.CREATED, weekStartDate, participantCount);
    }
}
