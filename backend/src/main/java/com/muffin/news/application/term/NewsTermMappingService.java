package com.muffin.news.application.term;

import com.muffin.news.domain.news.News;
import com.muffin.news.domain.news.NewsRepository;
import com.muffin.news.domain.term.TermDictionaryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsTermMappingService {

    private final NewsRepository newsRepository;
    private final TermDictionaryRepository termDictionaryRepository;

    /**
     * 재구성 본문에 포함된 경제금융용어를 찾아 NewsTerm으로 연결한다.
     *
     * <p>추가만 하지 않고 {@link News#syncTerms}로 맞추는 이유는, 이 메서드가 같은 뉴스에 두 번 이상 불릴 수 있기 때문이다
     * (재구성 이벤트 재발행, 수동 재시도). 두 번째 호출이 첫 번째 결과를 그대로 유지하도록 두면 매칭 규칙이 바뀌어도 옛 연결이 남는다.
     */
    @Transactional
    public int mapTerms(Long newsId) {
        News news = newsRepository
                .findById(newsId)
                .orElseThrow(() -> new IllegalArgumentException("News not found: " + newsId));

        if (!news.hasReconstructionResult()) {
            log.info("News term mapping skipped: reconstruction result is missing, newsId={}", newsId);
            return 0;
        }

        List<Long> termIds = NewsTermMatcher.matchedTermIds(
                news.getContent(), NewsTermMatcher.matchableTerms(termDictionaryRepository.findAll()));

        int changedCount = news.syncTerms(termIds);
        if (changedCount > 0) {
            newsRepository.save(news);
        }

        log.info(
                "News term mapping completed: newsId={}, matched={}, changed={}", newsId, termIds.size(), changedCount);
        return changedCount;
    }
}
