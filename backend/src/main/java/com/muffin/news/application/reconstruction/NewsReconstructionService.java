package com.muffin.news.application.reconstruction;

import com.muffin.news.domain.news.News;
import com.muffin.news.domain.news.NewsRepository;
import com.muffin.news.domain.news.enums.NewsStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "muffin.news.ai.enabled", havingValue = "true")
public class NewsReconstructionService {

    private final NewsRepository newsRepository;
    private final NewsArticleContentClient articleContentClient;
    private final NewsRewriter newsRewriter;

    /** 전달받은 뉴스를 건별로 재구성하며 한 건의 실패가 다음 뉴스에 영향을 주지 않게 처리한다. */
    public void reconstruct(List<Long> newsIds) {
        for (Long newsId : newsIds) {
            reconstructOne(newsId);
        }
    }

    /** 뉴스 재구성 */
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

            news.completeReconstruction(result.summary(), result.rewrittenBody());

            newsRepository.save(news);

            if (!result.warningFlags().isEmpty()) {
                log.warn(
                        "News reconstruction completed with warnings: newsId={}, flags={}",
                        newsId,
                        result.warningFlags());
            }
        } catch (Exception exception) {
            log.error("News reconstruction failed: newsId={}", newsId, exception);

            markAsFailed(newsId);
        }
    }

    private void markAsFailed(Long newsId) {
        newsRepository.findById(newsId).ifPresent(news -> {
            if (news.getStatus() == NewsStatus.PROCESSING) {
                news.fail();
                newsRepository.save(news);
            }
        });
    }
}
