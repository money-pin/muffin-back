package com.muffin.global.batch;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;

/**
 * 테스트용 {@link BatchJobRunner} 생성기.
 *
 * <p>러너는 예외를 잡아 로그로 옮기는 동작 자체가 검증 대상이라 모킹하지 않고 실제 인스턴스를 쓴다. 의존성 조립을 한 곳에 모아 러너에 협력자가 늘어도 테스트 8곳을 고치지
 * 않게 한다.
 */
public final class BatchJobRunners {

    private BatchJobRunners() {}

    public static BatchJobRunner forTest() {
        return new BatchJobRunner(new BatchJobMetrics(new SimpleMeterRegistry(), Clock.systemDefaultZone()));
    }
}
