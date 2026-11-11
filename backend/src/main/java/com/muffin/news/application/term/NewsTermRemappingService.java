package com.muffin.news.application.term;

import com.muffin.news.domain.news.News;
import com.muffin.news.domain.news.NewsRepository;
import com.muffin.news.domain.term.TermDictionary;
import com.muffin.news.domain.term.TermDictionaryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 이미 매핑된 뉴스를 현재 매칭 규칙으로 다시 매핑한다.
 *
 * <p>용어 연결은 재구성 직후 한 번만 계산되어 {@code news_term}에 남는다. 그래서 매칭 규칙을 고쳐도 과거 뉴스의 잘못된 연결은
 * 그대로 남는데, 이 연결은 본문 하이라이트뿐 아니라 <b>오늘의 용어 후보, 해설 카드 용어, 퀴즈 생성</b>까지 먹인다. 규칙만 고치고
 * 끝내면 "마찬가지로"에서 잘못 걸린 용어가 계속 모닝 브리핑의 오늘의 용어로 뽑힐 수 있다.
 *
 * <p>용어 사전은 한 번만 읽어 모든 뉴스에 재사용한다. 뉴스마다 사전 700여 건을 다시 조회하면 재매핑이 질의 수에 비례해 느려진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NewsTermRemappingService {

    /** 한 트랜잭션에서 처리할 뉴스 수. 전체를 한 트랜잭션에 담으면 긴 잠금과 큰 영속성 컨텍스트가 생긴다. */
    private static final int CHUNK_SIZE = 200;

    private final NewsRepository newsRepository;
    private final TermDictionaryRepository termDictionaryRepository;
    private final TransactionTemplate transactionTemplate;

    /** 본문이 있는 모든 뉴스의 용어 연결을 현재 규칙에 맞게 고친다. */
    public NewsTermRemappingReport remapAll() {
        List<TermDictionary> matchableTerms = NewsTermMatcher.matchableTerms(
                transactionTemplate.execute(status -> termDictionaryRepository.findAll()));
        List<Long> newsIds = transactionTemplate.execute(status -> newsRepository.findIdsForTermRemapping());
        if (newsIds.isEmpty()) {
            return new NewsTermRemappingReport(0, 0, 0);
        }

        int changedNews = 0;
        int changedLinks = 0;
        for (int from = 0; from < newsIds.size(); from += CHUNK_SIZE) {
            List<Long> chunk = newsIds.subList(from, Math.min(from + CHUNK_SIZE, newsIds.size()));
            NewsTermRemappingReport chunkReport = remapChunk(chunk, matchableTerms);
            changedNews += chunkReport.changedNews();
            changedLinks += chunkReport.changedLinks();
        }

        NewsTermRemappingReport report = new NewsTermRemappingReport(newsIds.size(), changedNews, changedLinks);
        log.info(
                "News term remapping completed: scanned={}, changedNews={}, changedLinks={}",
                report.scannedNews(),
                report.changedNews(),
                report.changedLinks());
        return report;
    }

    private NewsTermRemappingReport remapChunk(List<Long> newsIds, List<TermDictionary> matchableTerms) {
        return transactionTemplate.execute(status -> {
            int changedNews = 0;
            int changedLinks = 0;
            for (News news : newsRepository.findAllById(newsIds)) {
                int changed = news.syncTerms(NewsTermMatcher.matchedTermIds(news.getContent(), matchableTerms));
                if (changed > 0) {
                    newsRepository.save(news);
                    changedNews++;
                    changedLinks += changed;
                }
            }
            return new NewsTermRemappingReport(newsIds.size(), changedNews, changedLinks);
        });
    }

    /**
     * @param scannedNews 본문이 있어 재매핑 대상이 된 뉴스 수
     * @param changedNews 연결이 실제로 바뀐 뉴스 수
     * @param changedLinks 추가되거나 삭제된 연결 수
     */
    public record NewsTermRemappingReport(int scannedNews, int changedNews, int changedLinks) {}
}
