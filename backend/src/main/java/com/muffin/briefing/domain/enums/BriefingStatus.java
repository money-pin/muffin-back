package com.muffin.briefing.domain.enums;

/** 모닝 브리핑 발행 상태. PUBLISHED만 사용자에게 노출한다. */
public enum BriefingStatus {
    /** 생성 예약만 잡힌 상태. briefing_date 유니크 제약이 인스턴스 간 동시 생성을 막는 락 역할을 한다. */
    GENERATING,
    /** 생성이 끝나 발행 시각을 기다리는 상태. */
    READY,
    /** 사용자에게 공개된 상태. */
    PUBLISHED,
    /** 생성 또는 검증에 실패해 그날 브리핑을 제공하지 않는 상태. */
    UNAVAILABLE
}
