package com.muffin.global.event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 발행 대기 뉴스가 사용자에게 공개되었음을 알리는 도메인 이벤트. 알림(NOTI-05)이 구독한다. 공개된 뉴스가 없으면 발행하지 않는다.
 *
 * @param newsIds 이번 실행에서 공개된 뉴스 ID
 */
public record NewsPublishedEvent(List<Long> newsIds) {

    public NewsPublishedEvent {
        newsIds = Collections.unmodifiableList(new ArrayList<>(newsIds));
    }
}
