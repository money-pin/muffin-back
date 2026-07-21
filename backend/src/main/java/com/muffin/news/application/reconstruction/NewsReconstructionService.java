package com.muffin.news.application.reconstruction;

import com.muffin.news.domain.news.News;
import com.muffin.news.domain.news.NewsRepository;
import com.muffin.news.domain.news.enums.NewsStatus;
import com.muffin.news.domain.sectorimpact.NewsSectorImpact;
import com.muffin.news.domain.sectorimpact.NewsSectorImpactRepository;
import com.muffin.sector.domain.sector.Sector;
import com.muffin.sector.domain.sector.SectorRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "muffin.news.ai.enabled", havingValue = "true")
public class NewsReconstructionService {

    private final NewsRepository newsRepository;
    private final NewsSectorImpactRepository newsSectorImpactRepository;
    private final SectorRepository sectorRepository;
    private final NewsArticleContentClient articleContentClient;
    private final NewsRewriter newsRewriter;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    /** 전달받은 뉴스를 건별로 재구성하며 한 건의 실패가 다음 뉴스에 영향을 주지 않게 처리한다. */
    public void reconstruct(List<Long> newsIds) {
        for (Long newsId : newsIds) {
            reconstructOne(newsId);
        }
    }

    /**
     * 지정된 뉴스를 재구성하고 처리 결과에 따라 상태를 변경한다.
     *
     * <p>{@link NewsStatus#PROCESSING} 상태인 뉴스만 처리하며, 원문을 조회한 뒤 AI 재구성 결과를 저장하고 발행 대기 상태로 전환한다.
     * 처리 중 예외가 발생하면 해당 뉴스만 실패 상태로 변경하여 다른 뉴스의 재구성에 영향을 주지 않도록 한다.
     *
     * <p>원문 조회와 AI 재구성은 느린 외부 I/O이므로 트랜잭션 밖에서 수행하고, 저장만
     * {@link #persistReconstruction}에서 하나의 트랜잭션으로 처리한다.
     *
     * @param newsId 재구성할 뉴스 ID
     */
    private void reconstructOne(Long newsId) {
        try {
            News news = newsRepository
                    .findById(newsId)
                    .orElseThrow(() -> new IllegalArgumentException("News not found: " + newsId));

            if (news.getStatus() != NewsStatus.PROCESSING) {
                log.info("News reconstruction skipped: newsId={}, status={}", newsId, news.getStatus());
                return;
            }

            String originalContent = articleContentClient.fetch(news.getOriginalUrl());

            NewsReconstructionResult result = newsRewriter.rewrite(new NewsReconstructionRequest(
                    news.getTitle(), news.getPublisher(), news.getPublishedAt(), originalContent));

            persistReconstruction(news, result);

            if (!result.warningFlags().isEmpty()) {
                log.warn(
                        "News reconstruction completed with warnings: newsId={}, flags={}",
                        newsId,
                        result.warningFlags());
            }
        } catch (Exception exception) {
            log.error("News reconstruction failed: newsId={}", newsId, exception);

            markAsFailedSafely(newsId);
        }
    }

    /**
     * 재구성 결과와 섹터 영향도를 하나의 트랜잭션으로 저장하고 후속 처리 이벤트를 발행한다.
     *
     * <p>저장 중 하나라도 실패하면 전체가 롤백되어 뉴스는 {@link NewsStatus#PROCESSING} 상태로 남는다. 덕분에
     * {@link #markAsFailed}의 상태 가드가 정상 동작해 실패한 뉴스를 확실히 FAILED로 전환할 수 있다. 이벤트는
     * {@code AFTER_COMMIT} 리스너가 구독하므로 커밋에 성공한 경우에만 후속 처리가 트리거된다.
     */
    private void persistReconstruction(News news, NewsReconstructionResult result) {
        transactionTemplate.executeWithoutResult(status -> {
            List<NewsSectorImpact> sectorImpacts = toSectorImpacts(news, result.sectorImpacts());

            news.completeReconstruction(result.summary(), result.rewrittenBody());

            newsRepository.save(news);
            newsSectorImpactRepository.saveAll(sectorImpacts);
            eventPublisher.publishEvent(new NewsReconstructedEvent(news.getId()));
        });
    }

    private List<NewsSectorImpact> toSectorImpacts(News news, List<SectorImpactResult> results) {
        return results.stream()
                .map(result -> {
                    Sector sector = sectorRepository
                            .findBySectorCode(result.sectorCode())
                            .orElseThrow(() -> new IllegalStateException("Sector not found: " + result.sectorCode()));
                    return NewsSectorImpact.create(news.getId(), sector.getId(), result.impact());
                })
                .toList();
    }

    private void markAsFailed(Long newsId) {
        newsRepository.findById(newsId).ifPresent(news -> {
            if (news.getStatus() == NewsStatus.PROCESSING) {
                news.fail();
                newsRepository.save(news);
            }
        });
    }

    private void markAsFailedSafely(Long newsId) {
        try {
            markAsFailed(newsId);
        } catch (Exception exception) {
            log.error("Failed to mark news reconstruction as failed: newsId={}", newsId, exception);
        }
    }
}
