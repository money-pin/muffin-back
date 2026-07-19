package com.muffin.news.application.query;

import com.muffin.global.apiPayload.code.GeneralErrorCode;
import com.muffin.global.apiPayload.exception.GeneralException;
import com.muffin.news.application.exception.NewsErrorCode;
import com.muffin.news.application.exception.NewsException;
import com.muffin.news.domain.category.Category;
import com.muffin.news.domain.category.CategoryRepository;
import com.muffin.news.domain.news.News;
import com.muffin.news.domain.news.NewsRepository;
import com.muffin.news.domain.news.enums.NewsStatus;
import com.muffin.news.domain.readhistory.ReadHistory;
import com.muffin.news.domain.readhistory.ReadHistoryRepository;
import com.muffin.news.domain.sectorimpact.NewsSectorImpact;
import com.muffin.news.domain.sectorimpact.NewsSectorImpactRepository;
import com.muffin.news.domain.sectorimpact.enums.ImpactType;
import com.muffin.news.presentation.dto.NewsDetailResponse;
import com.muffin.news.presentation.dto.NewsDetailResponse.BodySegment;
import com.muffin.news.presentation.dto.NewsListResponse;
import com.muffin.news.presentation.dto.NewsListResponse.NewsListItem;
import com.muffin.news.presentation.dto.NewsSectorImpactResponse;
import com.muffin.news.presentation.dto.NewsSectorImpactResponse.SectorImpactItem;
import com.muffin.news.presentation.dto.NewsTodayResponse;
import com.muffin.news.presentation.dto.NewsTodayResponse.NewsTodayItem;
import com.muffin.scrap.domain.ScrapRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NewsQueryService {

    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 50;
    private static final int TODAY_NEWS_LIMIT = 3;

    private final NewsRepository newsRepository;
    private final NewsQueryRepository newsQueryRepository;
    private final NewsSectorImpactRepository newsSectorImpactRepository;
    private final CategoryRepository categoryRepository;
    private final ReadHistoryRepository readHistoryRepository;
    private final ScrapRepository scrapRepository;
    private final NewsCursorCodec newsCursorCodec;
    private final Clock clock;

    /** 공개 뉴스를 커서 기반으로 최신순 조회한다. 인증이 필요 없다. */
    @Transactional(readOnly = true)
    public NewsListResponse getNewsList(String cursorParam, int size, Long categoryId) {
        if (size < MIN_PAGE_SIZE || size > MAX_PAGE_SIZE) {
            throw new GeneralException(GeneralErrorCode.BAD_REQUEST, "size: 1 이상 50 이하여야 합니다.");
        }

        NewsCursor cursor = (cursorParam == null || cursorParam.isBlank()) ? null : newsCursorCodec.decode(cursorParam);

        List<NewsSummaryRow> rows = newsQueryRepository.findPublishedNewsPage(cursor, categoryId, size + 1);
        boolean hasNext = rows.size() > size;
        List<NewsSummaryRow> pageRows = hasNext ? rows.subList(0, size) : rows;

        List<NewsListItem> items = pageRows.stream()
                .map(row -> new NewsListItem(
                        row.newsId(),
                        row.categoryId(),
                        row.categoryName(),
                        row.title(),
                        row.summary(),
                        row.publisher(),
                        row.publishedAt(),
                        row.thumbnailUrl(),
                        row.viewCount()))
                .toList();

        String nextCursor = null;
        if (hasNext) {
            NewsSummaryRow last = pageRows.get(pageRows.size() - 1);
            nextCursor = newsCursorCodec.encode(last.publishedAt(), last.newsId());
        }

        return new NewsListResponse(items, nextCursor, hasNext);
    }

    /** 한국 시간 기준 당일 수집된 공개 뉴스 중 최신 발행순 상위 3건을 조회한다. */
    @Transactional(readOnly = true)
    public NewsTodayResponse getTodayNews() {
        LocalDate today = LocalDate.now(clock);
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime startOfNextDay = startOfDay.plusDays(1);

        List<NewsSummaryRow> rows =
                newsQueryRepository.findTodayPublishedNews(startOfDay, startOfNextDay, TODAY_NEWS_LIMIT);

        List<NewsTodayItem> items = new ArrayList<>(rows.size());
        for (int index = 0; index < rows.size(); index++) {
            NewsSummaryRow row = rows.get(index);
            items.add(new NewsTodayItem(
                    row.newsId(),
                    row.categoryId(),
                    row.categoryName(),
                    row.title(),
                    row.summary(),
                    row.publisher(),
                    row.publishedAt(),
                    resolveThumbnail(row, index == 0),
                    row.viewCount()));
        }

        return new NewsTodayResponse(items);
    }

    /** 뉴스 상세를 조회하며 조회수 증가와 열람 기록 갱신을 함께 수행한다. */
    @Transactional
    public NewsDetailResponse getNewsDetail(Long userId, Long newsId) {
        News news = newsRepository
                .findById(newsId)
                .filter(found -> found.getDeletedAt() == null)
                .orElseThrow(() -> new NewsException(NewsErrorCode.NEWS_NOT_FOUND));
        if (news.getStatus() != NewsStatus.PUBLISHED) {
            throw new NewsException(NewsErrorCode.NEWS_NOT_PUBLISHED);
        }

        news.increaseViewCount();
        upsertReadHistory(userId, newsId);

        boolean scrapped = scrapRepository.existsByUserIdAndNewsId(userId, newsId);
        String categoryName = categoryRepository
                .findById(news.getCategoryId())
                .map(Category::getName)
                .orElse(null);

        List<BodySegment> bodySegments = List.of(BodySegment.text(news.getContent()));

        return new NewsDetailResponse(
                news.getId(),
                news.getId(),
                news.getTitle(),
                categoryName,
                news.getViewCount(),
                news.getPublisher(),
                news.getPublishedAt(),
                news.getThumbnailUrl(),
                news.getOriginalUrl(),
                bodySegments,
                scrapped);
    }

    /** 뉴스의 12개 섹터별 영향도를 조회한다. 분석 결과가 없는 섹터는 NEUTRAL로 채운다. */
    @Transactional(readOnly = true)
    public NewsSectorImpactResponse getSectorImpacts(Long newsId) {
        News news = newsRepository
                .findById(newsId)
                .filter(found -> found.getDeletedAt() == null)
                .orElseThrow(() -> new NewsException(NewsErrorCode.NEWS_NOT_FOUND));

        Map<Long, ImpactType> impactBySectorId = loadImpactsBySectorId(news.getId());

        List<SectorImpactItem> sectorImpacts = newsQueryRepository.findActiveSectorsInDisplayOrder().stream()
                .map(sector -> new SectorImpactItem(
                        sector.sectorCode(),
                        sector.sectorName(),
                        impactBySectorId.getOrDefault(sector.sectorId(), ImpactType.NEUTRAL)))
                .toList();

        return new NewsSectorImpactResponse(news.getId(), sectorImpacts);
    }

    private Map<Long, ImpactType> loadImpactsBySectorId(Long newsId) {
        Map<Long, ImpactType> impactBySectorId = new HashMap<>();
        for (NewsSectorImpact impact : newsSectorImpactRepository.findByNewsId(newsId)) {
            impactBySectorId.putIfAbsent(impact.getSectorId(), impact.getImpact());
        }
        return impactBySectorId;
    }

    /**
     * 열람 기록을 upsert한다. 동시 조회 시 findByUserIdAndNewsId가 둘 다 빈 값을 반환해 저장이 경합할 수 있으므로,
     * {@code (user_id, news_id)} 유니크 제약 위반은 상대가 먼저 생성한 것으로 보고 그 행을 다시 조회해 갱신한다
     * ({@link com.muffin.sector.infrastructure.EtfPriceWriter#upsert}와 동일한 방어 패턴).
     */
    private void upsertReadHistory(Long userId, Long newsId) {
        Optional<ReadHistory> existing = readHistoryRepository.findByUserIdAndNewsId(userId, newsId);
        if (existing.isPresent()) {
            existing.get().updateReadAt();
            return;
        }

        try {
            readHistoryRepository.saveAndFlush(ReadHistory.create(userId, newsId));
        } catch (DataIntegrityViolationException exception) {
            readHistoryRepository
                    .findByUserIdAndNewsId(userId, newsId)
                    .orElseThrow(() -> exception)
                    .updateReadAt();
        }
    }

    /**
     * 오늘의 뉴스 썸네일 정책. ①원본 썸네일 → ②(첫 번째 뉴스) 공통 기본 이미지 → ③카테고리별 대체 이미지 순으로 반환한다.
     *
     * <p>TODO(S3): {@code isFirst}일 때 반환할 공통 기본 이미지는 추후 S3 presigned URL로 발급한다. 발급 경로가 생기기
     * 전까지는 첫 번째 뉴스도 카테고리 대체 이미지로 폴백한다.
     */
    private String resolveThumbnail(NewsSummaryRow row, boolean isFirst) {
        if (row.thumbnailUrl() != null && !row.thumbnailUrl().isBlank()) {
            return row.thumbnailUrl();
        }
        return row.categoryFallbackThumbnailUrl();
    }
}
