package com.muffin.news.application.explanation;

import com.muffin.news.domain.explanation.NewsExplanation;
import com.muffin.news.domain.explanation.NewsExplanationRepository;
import com.muffin.news.domain.explanation.enums.NewsExplanationStatus;
import com.muffin.news.domain.news.News;
import com.muffin.news.domain.news.NewsRepository;
import com.muffin.news.domain.news.NewsTerm;
import com.muffin.news.domain.term.TermDictionaryRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "muffin.news.ai.enabled", havingValue = "true")
public class NewsExplanationGenerationService {

    private final NewsRepository newsRepository;
    private final TermDictionaryRepository termDictionaryRepository;
    private final NewsExplanationRepository newsExplanationRepository;
    private final NewsExplanationGenerator newsExplanationGenerator;
    private final TransactionTemplate transactionTemplate;

    /** 재구성이 끝난 뉴스와 매핑된 용어를 바탕으로 경제 상식 해설카드를 생성해 저장한다. */
    public void generate(Long newsId) {
        try {
            // 외부 AI 호출 중 DB 커넥션을 점유하지 않도록 요청 데이터 조회와 결과 저장만 짧은 트랜잭션으로 감싼다.
            Optional<NewsExplanationGenerationRequest> request =
                    transactionTemplate.execute(status -> buildGenerationRequest(newsId));
            if (request.isEmpty()) {
                return;
            }

            NewsExplanationGenerationResult result = newsExplanationGenerator.generate(request.get());
            Integer savedCount = transactionTemplate.execute(
                    status -> saveGeneratedCards(request.get().newsId(), result));

            log.info("News explanation generation completed: newsId={}, cards={}", newsId, savedCount);
        } catch (Exception exception) {
            log.error("News explanation generation failed: newsId={}", newsId, exception);
            try {
                // 조회 API는 DONE 카드만 노출하므로 실패 마커를 남겨도 사용자 화면은 빈 상태로 유지된다.
                transactionTemplate.execute(status -> {
                    saveFailureMarker(newsId);
                    return null;
                });
            } catch (Exception failurePersistenceException) {
                log.error(
                        "News explanation failure marker save failed: newsId={}", newsId, failurePersistenceException);
            }
            log.warn("News explanation retry queue is not implemented yet: newsId={}", newsId);
        }
    }

    private Optional<NewsExplanationGenerationRequest> buildGenerationRequest(Long newsId) {
        // 이미 생성된 카드가 있으면 재호출되어도 중복 생성하지 않는다.
        if (newsExplanationRepository.existsByNewsIdAndStatus(newsId, NewsExplanationStatus.DONE)) {
            log.info("News explanation generation skipped: already generated, newsId={}", newsId);
            return Optional.empty();
        }

        News news = newsRepository
                .findById(newsId)
                .orElseThrow(() -> new IllegalArgumentException("News not found: " + newsId));

        if (!news.hasReconstructionResult()) {
            log.info("News explanation generation skipped: reconstruction result is missing, newsId={}", newsId);
            return Optional.empty();
        }

        return Optional.of(new NewsExplanationGenerationRequest(
                news.getId(), news.getTitle(), news.getSummary(), news.getContent(), toTermCandidates(news)));
    }

    private int saveGeneratedCards(Long newsId, NewsExplanationGenerationResult result) {
        // AI 호출 중 다른 흐름이 먼저 저장했을 수 있으므로 저장 직전에 한 번 더 확인한다.
        if (newsExplanationRepository.existsByNewsIdAndStatus(newsId, NewsExplanationStatus.DONE)) {
            log.info("News explanation generation skipped: already generated before save, newsId={}", newsId);
            return 0;
        }

        List<NewsExplanation> explanations = result.cards().stream()
                .limit(3)
                .map(card -> {
                    NewsExplanation explanation =
                            NewsExplanation.create(newsId, card.order(), card.title(), card.body(), card.keyTerm());
                    explanation.complete();
                    return explanation;
                })
                .toList();

        newsExplanationRepository.saveAll(explanations);
        return explanations.size();
    }

    private void saveFailureMarker(Long newsId) {
        // 실제 카드 order(1~3)와 충돌하지 않는 order 0 실패 마커를 한 번만 저장한다.
        if (!newsRepository.existsById(newsId)) {
            return;
        }
        if (newsExplanationRepository.existsByNewsIdAndStatus(newsId, NewsExplanationStatus.DONE)
                || newsExplanationRepository.existsByNewsIdAndStatus(newsId, NewsExplanationStatus.FAILED)) {
            return;
        }

        newsExplanationRepository.save(NewsExplanation.failed(newsId));
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
