package com.muffin.news.application.explanation;

import com.muffin.news.domain.explanation.NewsExplanation;
import com.muffin.news.domain.explanation.NewsExplanationRepository;
import com.muffin.news.domain.explanation.enums.NewsExplanationStatus;
import com.muffin.news.domain.news.News;
import com.muffin.news.domain.news.NewsRepository;
import com.muffin.news.domain.news.NewsTerm;
import com.muffin.news.domain.term.TermDictionaryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "muffin.news.ai.enabled", havingValue = "true")
public class NewsExplanationGenerationService {

    private final NewsRepository newsRepository;
    private final TermDictionaryRepository termDictionaryRepository;
    private final NewsExplanationRepository newsExplanationRepository;
    private final NewsExplanationGenerator newsExplanationGenerator;

    /** 재구성이 끝난 뉴스와 매핑된 용어를 바탕으로 경제 상식 해설카드를 생성해 저장한다. */
    @Transactional
    public void generate(Long newsId) {
        try {
            if (newsExplanationRepository.existsByNewsIdAndStatus(newsId, NewsExplanationStatus.DONE)) {
                log.info("News explanation generation skipped: already generated, newsId={}", newsId);
                return;
            }

            News news = newsRepository
                    .findById(newsId)
                    .orElseThrow(() -> new IllegalArgumentException("News not found: " + newsId));

            if (!news.hasReconstructionResult()) {
                log.info("News explanation generation skipped: reconstruction result is missing, newsId={}", newsId);
                return;
            }

            List<NewsExplanationTermCandidate> terms = toTermCandidates(news);
            NewsExplanationGenerationResult result =
                    newsExplanationGenerator.generate(new NewsExplanationGenerationRequest(
                            news.getId(), news.getTitle(), news.getSummary(), news.getContent(), terms));

            List<NewsExplanation> explanations = result.cards().stream()
                    .limit(3)
                    .map(card -> {
                        NewsExplanation explanation = NewsExplanation.create(
                                news.getId(), card.order(), card.title(), card.body(), card.keyTerm());
                        explanation.complete();
                        return explanation;
                    })
                    .toList();

            newsExplanationRepository.saveAll(explanations);
            log.info("News explanation generation completed: newsId={}, cards={}", newsId, explanations.size());
        } catch (Exception exception) {
            log.error("News explanation generation failed: newsId={}", newsId, exception);
            log.warn("News explanation retry queue is not implemented yet: newsId={}", newsId);
        }
    }

    private List<NewsExplanationTermCandidate> toTermCandidates(News news) {
        List<Long> termIds = news.getTerms().stream().map(NewsTerm::getTermId).toList();
        if (termIds.isEmpty()) {
            return List.of();
        }

        return termDictionaryRepository.findAllById(termIds).stream()
                .map(term -> new NewsExplanationTermCandidate(term.getId(), term.getTerm()))
                .toList();
    }
}
