package com.muffin.global.batch;

/**
 * 배치 잡 식별자. 로그의 {@code job=} 값이자 이후 메트릭의 {@code job} 태그가 된다.
 *
 * <p>{@code code}를 enum 이름에서 유도하지 않고 명시적으로 들고 있는 이유는, 이 값이 대시보드 질의와 알림 룰이 참조하는 <b>외부 계약</b>이기 때문이다. enum 상수를
 * 리팩터링해도 관측 쪽이 조용히 깨지지 않는다.
 *
 * <p>{@code domain}은 잡을 수행하는 서비스가 속한 도메인 패키지명이다. 로그 한 줄의 {@code domain} 필드가 이 값으로 채워지도록 {@link #loggerName()}을
 * 도메인 패키지 아래로 잡는다.
 *
 * <p>하루에 여러 번 트리거되는 잡(주간 랭킹의 오전/최종 등)은 하나의 상수를 공유한다. 관측 단위가 "이 잡이 오늘 성공했는가"이므로 트리거마다 쪼개면 마지막 성공 시각이 갈라진다.
 * 반대로 수집과 미수집 확정처럼 하는 일이 다르면 분리한다.
 */
public enum BatchJob {
    EMAIL_VERIFICATION_CLEANUP("email_verification_cleanup", "auth"),
    UNVERIFIED_ACCOUNT_CLEANUP("unverified_account_cleanup", "auth"),
    WITHDRAWN_DATA_CLEANUP("withdrawn_data_cleanup", "auth"),
    INVESTMENT_FINALIZATION("investment_finalization", "investment"),
    SETTLEMENT("settlement", "investment"),
    NEWS_PUBLICATION("news_publication", "news"),
    RSS_COLLECTION("rss_collection", "news"),
    QUIZ_GENERATION_RETRY("quiz_generation_retry", "quiz"),
    QUIZ_PUBLICATION("quiz_publication", "quiz"),
    WEEKLY_RANKING("weekly_ranking", "ranking"),
    OPEN_PRICE_COLLECT("open_price_collect", "sector"),
    OPEN_PRICE_FINALIZE("open_price_finalize", "sector"),
    CLOSE_PRICE_COLLECT("close_price_collect", "sector"),
    CLOSE_PRICE_FINALIZE("close_price_finalize", "sector");

    private static final String ROOT_PACKAGE = "com.muffin.";
    private static final String BATCH_LOGGER_SUFFIX = ".batch";

    private final String code;
    private final String domain;

    BatchJob(String code, String domain) {
        this.code = code;
        this.domain = domain;
    }

    public String code() {
        return code;
    }

    public String domain() {
        return domain;
    }

    /** 이 잡의 실행 로그를 남길 로거 이름. 도메인 패키지 아래에 두어 로그의 {@code domain} 필드가 잡의 도메인으로 채워지게 한다. */
    public String loggerName() {
        return ROOT_PACKAGE + domain + BATCH_LOGGER_SUFFIX;
    }
}
