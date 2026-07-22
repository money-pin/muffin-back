package com.muffin.scrap.application;

import com.muffin.news.application.exception.NewsErrorCode;
import com.muffin.news.application.exception.NewsException;
import com.muffin.news.domain.news.News;
import com.muffin.news.domain.news.NewsRepository;
import com.muffin.news.domain.news.enums.NewsStatus;
import com.muffin.scrap.domain.Scrap;
import com.muffin.scrap.domain.ScrapRepository;
import com.muffin.scrap.presentation.dto.ScrapResponse;
import java.time.Clock;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 뉴스 스크랩 쓰기 서비스. 스크랩/해제는 모두 멱등이다.
 *
 * <p>스크랩 리소스는 {@code (user_id, news_id)} 유니크 제약이 걸린 싱글턴이라, 같은 요청을 여러 번 보내도 상태가 같아야 한다(PUT 계약).
 * 스크랩은 존재하지 않으면 생성하고 이미 있으면 최초 저장 시각을 유지하며, 해제는 있으면 삭제하고 없어도 성공한다.
 *
 * <p>뉴스 검증은 scrap 애그리거트가 소유하지 않는 정보라 {@link NewsRepository}로 조회해 조립한다. 삭제되었거나 없는 뉴스는
 * {@code CONTENT_404_001}, 아직 공개되지 않은 뉴스는 스크랩 시 {@code CONTENT_403_001}로 막는다.
 */
@Service
@RequiredArgsConstructor
public class ScrapCommandService {

    private final ScrapRepository scrapRepository;
    private final NewsRepository newsRepository;
    private final Clock clock;

    /** 뉴스를 스크랩한다(멱등). 공개된 뉴스만 스크랩할 수 있다. */
    @Transactional
    public ScrapResponse scrap(Long userId, Long newsId) {
        News news = requireNews(newsId);
        if (news.getStatus() != NewsStatus.PUBLISHED) {
            throw new NewsException(NewsErrorCode.NEWS_NOT_PUBLISHED);
        }

        Scrap scrap = findOrCreate(userId, newsId);
        OffsetDateTime scrappedAt = scrap.getCreatedAt().atZone(clock.getZone()).toOffsetDateTime();
        return ScrapResponse.scrapped(newsId, scrappedAt);
    }

    /** 뉴스 스크랩을 해제한다(멱등). 공개 상태와 무관하게 이미 저장한 스크랩은 언제든 해제할 수 있다. */
    @Transactional
    public ScrapResponse unscrap(Long userId, Long newsId) {
        requireNews(newsId);
        scrapRepository.deleteByUserIdAndNewsId(userId, newsId);
        return ScrapResponse.unscrapped(newsId);
    }

    private Scrap findOrCreate(Long userId, Long newsId) {
        return scrapRepository.findByUserIdAndNewsId(userId, newsId).orElseGet(() -> {
            try {
                return scrapRepository.saveAndFlush(Scrap.create(userId, newsId));
            } catch (DataIntegrityViolationException exception) {
                // 동시 스크랩으로 유니크 제약에 걸리면 이미 저장된 행을 최초 시각으로 반환한다.
                return scrapRepository.findByUserIdAndNewsId(userId, newsId).orElseThrow(() -> exception);
            }
        });
    }

    private News requireNews(Long newsId) {
        return newsRepository
                .findById(newsId)
                .filter(found -> found.getDeletedAt() == null)
                .orElseThrow(() -> new NewsException(NewsErrorCode.NEWS_NOT_FOUND));
    }
}
