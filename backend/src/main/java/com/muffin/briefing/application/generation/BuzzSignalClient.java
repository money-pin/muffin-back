package com.muffin.briefing.application.generation;

/**
 * 외부 매체의 화제도를 가져온다.
 *
 * <p>구현체를 인터페이스 뒤에 두는 이유는 신호원이 바뀔 수 있기 때문이다. 지금은 Google News RSS 하나지만, 다른 소스를 붙이거나
 * 신호를 아예 끄더라도 생성 서비스는 수정하지 않는다. {@code RssFeedClient}와 같은 구조다.
 */
public interface BuzzSignalClient {

    /** 화제도를 조회한다. 조회에 실패하면 예외 대신 {@link BuzzSignal#empty()}를 반환해 브리핑 생성을 막지 않는다. */
    BuzzSignal fetch();
}
