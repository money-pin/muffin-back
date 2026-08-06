package com.muffin.global.batch;

/** {@link BatchJobRunner}가 감싸 실행하는 배치 잡 본문. */
@FunctionalInterface
public interface BatchJobCallback {

    /**
     * 잡을 수행하고 결과를 보고한다.
     *
     * @return 실행 결과. {@code null}이면 수치 없는 성공으로 간주한다.
     */
    BatchJobReport execute();
}
