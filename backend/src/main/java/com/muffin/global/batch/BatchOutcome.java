package com.muffin.global.batch;

/**
 * 배치 잡 1회 실행의 결말.
 *
 * <p>{@link #SKIPPED}를 {@link #SUCCESS}와 구분하는 이유는 "할 일이 없어서 안 했다"(휴장일, 이미 처리됨, 선행 데이터 미적재)와 "해서 성공했다"가 관측상
 * 다른 사건이기 때문이다. 다만 알림 관점에서는 둘 다 정상이므로, 마지막 성공 시각은 두 경우 모두 갱신한다.
 */
public enum BatchOutcome {
    SUCCESS("success"),
    SKIPPED("skipped"),
    FAILURE("failure");

    private final String code;

    BatchOutcome(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
