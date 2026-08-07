package com.muffin.global.batch;

/** {@link BatchJobRunner}가 감싸 실행하는 배치 잡 본문. */
@FunctionalInterface
public interface BatchJobCallback {

    /**
     * 잡을 수행하고 결과를 보고한다.
     *
     * @return 실행 결과. {@code null}을 반환하면 안 된다. 관용적으로 성공 처리하면 보고서를 만들지 못한 버그가 {@code outcome=success}로 관측되므로,
     *     {@link BatchJobRunner}는 이를 잡의 실패로 취급한다.
     */
    BatchJobReport execute();
}
