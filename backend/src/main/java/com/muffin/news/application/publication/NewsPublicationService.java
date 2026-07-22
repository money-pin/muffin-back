package com.muffin.news.application.publication;

import com.muffin.news.domain.news.News;
import com.muffin.news.domain.news.NewsRepository;
import com.muffin.news.domain.news.enums.NewsStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsPublicationService {

    private final NewsRepository newsRepository;

    /** AI 처리가 끝난 발행 대기 뉴스를 모두 사용자 공개 상태로 변경한다. */
    @Transactional
    public int publishPendingNews() {
        List<News> pendingNews = newsRepository.findAllByStatus(NewsStatus.PENDING);
        int publishedCount = 0;

        for (News news : pendingNews) {
            if (!news.hasReconstructionResult()) {
                // 재구성 결과 없는 PENDING은 다시 처리될 경로가 없으므로, 매일 스킵을 반복하지 않고 실패로 종결한다.
                log.error("News publication failed: reconstruction result is missing, newsId={}", news.getId());
                news.fail();
                continue;
            }

            news.publish();
            publishedCount++;
        }

        return publishedCount;
    }
}
