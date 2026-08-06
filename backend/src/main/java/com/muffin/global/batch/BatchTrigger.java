package com.muffin.global.batch;

/**
 * 배치 잡을 실행시킨 주체. 같은 잡이 여러 경로로 들어올 때 로그만으로 구분하기 위해 남긴다.
 *
 * <p>정산이 대표 사례다. ETF 시세 적재 완료 이벤트(1차)와 안전망 스케줄러(2차) 양쪽에서 들어오는데, 지금까지는 로그로 어느 쪽이 실제로 정산을 수행했는지 알 수 없었다.
 */
public enum BatchTrigger {
    SCHEDULER("scheduler"),
    EVENT("event");

    private final String code;

    BatchTrigger(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
