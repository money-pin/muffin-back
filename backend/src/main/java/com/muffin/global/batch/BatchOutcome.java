package com.muffin.global.batch;

/**
 * 배치 잡 1회 실행의 결말. <b>마지막 성공 시각을 갱신하는지</b>가 이 구분의 핵심이다.
 *
 * <p>{@link #SKIPPED}와 {@link #DEFERRED}는 둘 다 "아무것도 하지 않았다"지만 성격이 정반대다. 휴장일에 정산을 건너뛴 것은 <b>할 일이 없어서</b>이므로
 * 정상이고, 갱신하지 않으면 연휴마다 알림이 울린다. 반면 정산이 밀려 주간 랭킹을 미룬 것은 <b>할 일이 있는데 못 한 것</b>이라, 갱신해 버리면 산출물이 하나도 안 나오는데도
 * 10분마다 성공 시각이 새로 찍혀 알림이 영원히 울리지 않는다.
 */
public enum BatchOutcome {
    /** 수행하고 정상 종료했다. 마지막 성공 시각을 갱신한다. */
    SUCCESS("success"),
    /** 할 일이 없어 수행하지 않았다(휴장일, 이미 처리됨). 정상이므로 마지막 성공 시각을 갱신한다. */
    SKIPPED("skipped"),
    /**
     * 할 일은 있으나 선행 조건이 갖춰지지 않아 미뤘다(정산 미완료, 뉴스 부족, 발행할 세트 없음).
     *
     * <p>한 번 미루는 것은 재시도로 해소되는 정상 흐름이라 실패로 보지 않는다. 대신 <b>마지막 성공 시각을 갱신하지 않아</b> 미룸이 계속되면 침묵이 쌓여 알림으로
     * 이어진다.
     */
    DEFERRED("deferred"),
    /** 실패했다. 마지막 성공 시각을 갱신하지 않는다. */
    FAILURE("failure");

    private final String code;

    BatchOutcome(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
