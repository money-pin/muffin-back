package com.muffin.auth.application.withdraw;

/**
 * 탈퇴 데이터 정리 배치 1회 실행 결과.
 *
 * <p>투자와 퀴즈를 각각 세는 이유는 두 정리가 독립적이기 때문이다. 한쪽만 계속 0이면 그 조회 조건이나 삭제 경로를 의심할 수 있다.
 *
 * @param investmentUserCount 투자 기록을 정리한 유저 수
 * @param quizUserCount 퀴즈 기록을 정리한 유저 수
 */
public record WithdrawnDataCleanupResult(int investmentUserCount, int quizUserCount) {}
