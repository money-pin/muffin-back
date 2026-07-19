package com.muffin.news.application.term;

import com.muffin.news.domain.news.News;
import com.muffin.news.domain.news.NewsRepository;
import com.muffin.news.domain.term.TermDictionary;
import com.muffin.news.domain.term.TermDictionaryRepository;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsTermMappingService {

    private static final int MIN_MATCH_TERM_LENGTH = 2;

    private final NewsRepository newsRepository;
    private final TermDictionaryRepository termDictionaryRepository;

    /** 재구성 본문에 포함된 경제금융용어를 찾아 NewsTerm으로 연결한다. */
    @Transactional
    public int mapTerms(Long newsId) {
        News news = newsRepository
                .findById(newsId)
                .orElseThrow(() -> new IllegalArgumentException("News not found: " + newsId));

        if (!news.hasReconstructionResult()) {
            log.info("News term mapping skipped: reconstruction result is missing, newsId={}", newsId);
            return 0;
        }

        List<TermDictionary> matchedTerms = termDictionaryRepository.findAll().stream()
                .filter(term -> isMatchable(term.getTerm()))
                .sorted(Comparator.comparingInt(
                                (TermDictionary term) -> term.getTerm().length())
                        .reversed())
                .filter(term -> news.getContent().contains(term.getTerm()))
                .toList();

        int mappedCount = 0;
        for (TermDictionary term : matchedTerms) {
            if (news.addTerm(term.getId())) {
                mappedCount++;
            }
        }

        if (mappedCount > 0) {
            newsRepository.save(news);
        }

        log.info("News term mapping completed: newsId={}, mapped={}", newsId, mappedCount);
        return mappedCount;
    }

    private static boolean isMatchable(String term) {
        return term != null && term.strip().length() >= MIN_MATCH_TERM_LENGTH;
    }
}
