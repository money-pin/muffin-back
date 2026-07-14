package com.muffin.news.application.rss;

import java.util.List;

/** AI pipeline consumers can subscribe to this event after collected news is committed. */
public record NewsCollectedEvent(List<Long> newsIds) {

    /** 이벤트에 전달할 뉴스 ID 목록을 불변 목록으로 보관한다. */
    public NewsCollectedEvent {
        newsIds = List.copyOf(newsIds);
    }
}
