package com.muffin.news.infrastructure.jev;

/**
 * Jev 답변 하나. 질문 타입에 따라 채워지는 필드가 다르다.
 *
 * @param type {@code score}, {@code noul}, {@code choice}
 * @param score 척도 위 위치. 레벨 번호 × 확률의 합이라 연속값이다. score 질문에만 있다
 * @param noul 참일 확률 0~1. noul 질문에만 있다
 * @param confidence 확률이 한 레벨에 몰려 있는 정도. 낮으면 모델이 판단을 확신하지 못한 것이다
 */
public record JevAnswer(String type, double score, double noul, double confidence) {

    /** 답변이 없을 때 쓰는 값. 점수 0은 "판단 근거 없음"으로 취급되어 순위에서 밀린다. */
    public static JevAnswer empty() {
        return new JevAnswer("", 0, 0, 0);
    }
}
