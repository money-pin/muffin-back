package com.muffin.briefing.application.generation;

import java.util.List;

/**
 * 외부 매체가 오늘 무엇을 크게 다뤘는지 나타내는 신호. 매경 뉴스 중 어느 것이 진짜 큰 이슈인지 판단하는 근거로만 쓰고 사용자에게 노출하지 않는다.
 *
 * <p>제목과 출처만 담는다. 본문은 수집하지 않으므로 저작권 노출이 늘지 않는다.
 *
 * @param topics 여러 매체가 반복해 쓴 키워드
 * @param headlines 수집한 헤드라인. 키워드 추출이 놓친 맥락을 AI가 직접 읽을 수 있도록 함께 넘긴다
 */
public record BuzzSignal(List<BuzzTopic> topics, List<BuzzHeadline> headlines) {

    public BuzzSignal {
        topics = List.copyOf(topics);
        headlines = List.copyOf(headlines);
    }

    /** 신호를 받지 못했을 때 쓰는 빈 값. 브리핑 생성은 이 상태로도 계속된다. */
    public static BuzzSignal empty() {
        return new BuzzSignal(List.of(), List.of());
    }

    public boolean isEmpty() {
        return topics.isEmpty() && headlines.isEmpty();
    }

    /**
     * @param mentionCount 해당 키워드가 등장한 기사 수
     * @param outletCount 해당 키워드를 쓴 서로 다른 매체 수. 한 매체가 반복 보도한 것과 여러 매체가 동시에 다룬 것을 구분한다
     */
    public record BuzzTopic(String keyword, int mentionCount, int outletCount) {}

    public record BuzzHeadline(String title, String outlet) {}
}
