package com.muffin.notification.domain.delivery;

/**
 * 알림 종류. 기획서의 NOTI 번호와 1:1로 대응한다.
 *
 * <p>{@code keyPrefix}는 중복 방지 키({@code PREFIX:user_id:scope})의 앞부분으로, 기획서가 정한 값을 그대로 쓴다.
 */
public enum NotificationType {
    /** NOTI-01 모닝 브리핑. 설정은 뉴스 알림 토글을 함께 쓴다. */
    BRIEFING("BRIEFING"),
    /** NOTI-02 퀴즈 리마인드. */
    QUIZ("QUIZ"),
    /** NOTI-03 투자 결과. */
    INVESTMENT_RESULT("RESULT"),
    /** NOTI-04 랭킹 변동. */
    RANKING("RANK"),
    /** NOTI-05 뉴스 업데이트. */
    NEWS("NEWS");

    private final String keyPrefix;

    NotificationType(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    /**
     * 사용자당 한 번만 보내야 하는 단위의 키를 만든다.
     *
     * @param scope 발송 단위. 날짜 알림은 날짜, 투자 결과는 투자일, 랭킹은 ISO 주차
     */
    public String dedupKey(Long userId, String scope) {
        return keyPrefix + ":" + userId + ":" + scope;
    }
}
