package com.muffin.news.domain.news.enums;

public enum NewsStatus {
    /** RSS 수집 후 AI 콘텐츠를 생성하는 중. */
    PROCESSING,
    /** AI 콘텐츠 생성이 완료되어 정해진 발행 시각을 기다리는 중. */
    PENDING,
    /** 사용자에게 공개된 상태. */
    PUBLISHED,
    /** 뉴스 처리 과정이 최종 실패한 상태. */
    FAILED
}
