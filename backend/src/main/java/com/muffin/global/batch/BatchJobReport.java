package com.muffin.global.batch;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 배치 잡이 스스로 보고하는 실행 결과. {@link BatchJobRunner}가 이 값을 로그 한 줄의 뒷부분으로 펼친다.
 *
 * <p>실패는 보통 예외가 러너까지 올라온 사실로 판정하지만, {@link #failure(String)}로 직접 보고할 수도 있다. 스스로 예외를 처리하고 실패 흔적을 도메인에
 * 남기는 잡(퀴즈 생성이 실패 시 UNAVAILABLE 세트를 저장하는 것처럼) 때문이다. 이런 잡이 정상 종료한다고 성공으로 집계되면 <b>실패가 계속돼도 마지막 성공 시각이 갱신돼
 * 알림이 영영 울리지 않는다.</b>
 *
 * <p>{@code details}는 삽입 순서를 유지한다. 로그 한 줄의 필드 순서가 실행마다 달라지면 눈으로 훑기도, 파싱 규칙을 세우기도 어렵다.
 */
public record BatchJobReport(BatchOutcome outcome, String reason, Map<String, Object> details) {

    public BatchJobReport {
        details = Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }

    /** 잡이 실제로 일을 수행하고 정상 종료했다. */
    public static BatchJobReport success() {
        return new BatchJobReport(BatchOutcome.SUCCESS, null, Map.of());
    }

    /**
     * 할 일이 없어 수행하지 않았다(휴장일, 이미 처리됨, 선행 데이터 미적재 등).
     *
     * @param reason 건너뛴 이유. 로그의 {@code reason=} 값이 되므로 값의 종류가 유한한 짧은 토큰으로 적는다.
     */
    public static BatchJobReport skipped(String reason) {
        return new BatchJobReport(BatchOutcome.SKIPPED, reason, Map.of());
    }

    /**
     * 잡이 실패를 스스로 확인했다. 예외를 내부에서 처리하고 정상 종료하는 잡이 쓴다.
     *
     * @param reason 실패 사유. 로그의 {@code reason=} 값이 되므로 값의 종류가 유한한 짧은 토큰으로 적는다.
     */
    public static BatchJobReport failure(String reason) {
        return new BatchJobReport(BatchOutcome.FAILURE, reason, Map.of());
    }

    /** 로그 한 줄에 덧붙일 실행 결과 수치를 추가한 새 보고서를 반환한다. */
    public BatchJobReport with(String key, Object value) {
        Map<String, Object> merged = new LinkedHashMap<>(details);
        merged.put(key, value);
        return new BatchJobReport(outcome, reason, merged);
    }
}
