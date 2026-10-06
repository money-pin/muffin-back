package com.muffin.news.application.rss;

import java.util.List;
import java.util.Map;

/**
 * 수집한 RSS 후보 중 저장할 기사를 고르는 방식.
 *
 * <p>선별 방식이 두 가지라 호출 단위가 다르다. 카테고리별 선별은 카테고리마다 AI를 부르고, 통합 선별은 전체를 한 번에 부른다.
 * 그 차이를 이 인터페이스 뒤에 숨겨 {@link RssCollectionService}가 방식을 알지 않게 한다.
 */
public interface RssSelectionStrategy {

    /**
     * @param candidatesByCategory 카테고리별 수집 후보
     * @return 저장할 기사를 카테고리별로 묶은 결과. 선택된 기사가 없는 카테고리는 키가 없을 수 있다
     */
    Map<String, List<RssArticle>> select(Map<String, List<RssArticle>> candidatesByCategory);
}
