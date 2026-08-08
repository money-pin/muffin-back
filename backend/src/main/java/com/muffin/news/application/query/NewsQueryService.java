package com.muffin.news.application.query;

import com.muffin.global.apiPayload.code.GeneralErrorCode;
import com.muffin.global.apiPayload.exception.GeneralException;
import com.muffin.news.application.exception.NewsErrorCode;
import com.muffin.news.application.exception.NewsException;
import com.muffin.news.application.term.TermTextMatcher;
import com.muffin.news.domain.category.Category;
import com.muffin.news.domain.category.CategoryRepository;
import com.muffin.news.domain.news.News;
import com.muffin.news.domain.news.NewsRepository;
import com.muffin.news.domain.news.NewsTerm;
import com.muffin.news.domain.news.enums.NewsStatus;
import com.muffin.news.domain.readhistory.ReadHistory;
import com.muffin.news.domain.readhistory.ReadHistoryRepository;
import com.muffin.news.domain.sectorimpact.NewsSectorImpact;
import com.muffin.news.domain.sectorimpact.NewsSectorImpactRepository;
import com.muffin.news.domain.sectorimpact.enums.ImpactType;
import com.muffin.news.domain.term.TermDictionary;
import com.muffin.news.domain.term.TermDictionaryRepository;
import com.muffin.news.presentation.dto.NewsDetailResponse;
import com.muffin.news.presentation.dto.NewsDetailResponse.BodySegment;
import com.muffin.news.presentation.dto.NewsListResponse;
import com.muffin.news.presentation.dto.NewsListResponse.NewsListItem;
import com.muffin.news.presentation.dto.NewsReadResponse;
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
    private final TermDictionaryRepository termDictionaryRepository;
    private final ReadHistoryRepository readHistoryRepository;
    private final ScrapRepository scrapRepository;
    private final NewsCursorCodec newsCursorCodec;
    private final Clock clock;

    /** 공개 뉴스를 커서 기반으로 최신순 조회하고 현재 사용자의 스크랩 여부를 함께 반환한다. */
    @Transactional(readOnly = true)
    public NewsListResponse getNewsList(Long userId, String cursorParam, int size, Long categoryId) {
        if (size < MIN_PAGE_SIZE || size > MAX_PAGE_SIZE) {
            throw new GeneralException(GeneralErrorCode.BAD_REQUEST, "size: 1 이상 50 이하여야 합니다.");
        }

        NewsCursor cursor = (cursorParam == null || cursorParam.isBlank()) ? null : newsCursorCodec.decode(cursorParam);

        List<NewsSummaryRow> rows = newsQueryRepository.findPublishedNewsPage(userId, cursor, categoryId, size + 1);
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
                        originalThumbnail(row.thumbnailUrl()),
                        row.viewCount(),
                        row.isScrapped()))
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
    public NewsTodayResponse getTodayNews(Long userId) {
        LocalDate today = LocalDate.now(clock);
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime startOfNextDay = startOfDay.plusDays(1);

        List<NewsSummaryRow> rows =
                newsQueryRepository.findTodayPublishedNews(userId, startOfDay, startOfNextDay, TODAY_NEWS_LIMIT);

        if (rows.isEmpty()) {
            rows = newsQueryRepository
                    .findLatestPublishedCreatedAtBefore(startOfDay)
                    .map(latestCreatedAt -> {
                        LocalDateTime fallbackStart =
                                latestCreatedAt.toLocalDate().atStartOfDay();
                        return newsQueryRepository.findTodayPublishedNews(
                                userId, fallbackStart, fallbackStart.plusDays(1), TODAY_NEWS_LIMIT);
                    })
                    .orElseGet(List::of);
        }

        List<NewsTodayItem> items = new ArrayList<>(rows.size());
        for (NewsSummaryRow row : rows) {
            items.add(new NewsTodayItem(
                    row.newsId(),
                    row.categoryId(),
                    row.categoryName(),
                    row.title(),
                    row.summary(),
                    row.publisher(),
                    row.publishedAt(),
                    originalThumbnail(row.thumbnailUrl()),
                    row.viewCount(),
                    row.isScrapped()));
        }

        return new NewsTodayResponse(items);
    }

    /** 공개된 뉴스 상세를 부수 효과 없이 조회한다. */
    @Transactional(readOnly = true)
    public NewsDetailResponse getNewsDetail(Long userId, Long newsId) {
        News news = getPublishedNews(newsId);

        boolean scrapped = scrapRepository.existsByUserIdAndNewsId(userId, newsId);
        Optional<Category> category = categoryRepository.findById(news.getCategoryId());
        String categoryName = category.map(Category::getName).orElse(null);

        List<BodySegment> bodySegments = toBodySegments(news);

        return new NewsDetailResponse(
                news.getId(),
                news.getTitle(),
                categoryName,
                news.getViewCount(),
                news.getPublisher(),
                news.getPublishedAt(),
                originalThumbnail(news.getThumbnailUrl()),
                news.getOriginalUrl(),
                bodySegments,
                scrapped);
    }

    /** 뉴스 열람을 기록하고 호출 시점의 갱신된 조회수를 반환한다. */
    @Transactional
    public NewsReadResponse recordNewsRead(Long userId, Long newsId) {
        News news = getPublishedNewsForUpdate(newsId);
        news.increaseViewCount();
        upsertReadHistory(userId, newsId);
        return new NewsReadResponse(news.getViewCount());
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

    /** 뉴스 본문을 일반 텍스트와 용어 하이라이트 세그먼트로 분리한다. */
    private List<BodySegment> toBodySegments(News news) {
        String content = news.getContent();
        if (content == null || content.isBlank()) {
            return List.of();
        }

        List<Long> termIds = news.getTerms().stream().map(NewsTerm::getTermId).toList();
        if (termIds.isEmpty()) {
            return List.of(BodySegment.text(content));
        }

        // NewsTerm에는 termId만 있으므로 실제 표시 텍스트는 사전에서 다시 조회해 본문 위치와 매칭한다.
        Map<Long, TermDictionary> termsById = new HashMap<>();
        for (TermDictionary term : termDictionaryRepository.findAllById(termIds)) {
            termsById.put(term.getId(), term);
        }

        List<TermMatch> matches = findTermMatches(content, termsById);
        if (matches.isEmpty()) {
            return List.of(BodySegment.text(content));
        }

        return splitContentByMatches(content, matches);
    }

    /** 본문에 실제로 등장하는 매핑 용어의 모든 위치를 찾는다. 같은 시작점에서는 긴 용어가 먼저 오도록 정렬한다. */
    private List<TermMatch> findTermMatches(String content, Map<Long, TermDictionary> termsById) {
        List<TermMatch> matches = new ArrayList<>();
        for (TermDictionary term : termsById.values()) {
            String keyword = term.getTerm();
            if (keyword == null || keyword.isBlank()) {
                continue;
            }

            for (TermTextMatcher.TermTextMatch match : TermTextMatcher.findMatches(content, keyword)) {
                matches.add(new TermMatch(match.start(), match.end(), term.getId()));
            }
        }

        return matches.stream()
                .sorted((left, right) -> {
                    int startCompare = Integer.compare(left.start(), right.start());
                    if (startCompare != 0) {
                        return startCompare;
                    }
                    return Integer.compare(right.length(), left.length());
                })
                .toList();
    }

    /** 겹치는 매칭은 앞에서 확정된 긴 용어를 우선하고, 나머지 영역은 TEXT 세그먼트로 유지한다. */
    private List<BodySegment> splitContentByMatches(String content, List<TermMatch> matches) {
        List<BodySegment> segments = new ArrayList<>();
        int cursor = 0;

        for (TermMatch match : matches) {
            if (match.start() < cursor) {
                continue;
            }
            if (cursor < match.start()) {
                segments.add(BodySegment.text(content.substring(cursor, match.start())));
            }
            segments.add(BodySegment.highlight(content.substring(match.start(), match.end()), match.termId()));
            cursor = match.end();
        }

        if (cursor < content.length()) {
            segments.add(BodySegment.text(content.substring(cursor)));
        }

        return segments;
    }

    private record TermMatch(int start, int end, Long termId) {

        private int length() {
            return end - start;
        }
    }

    /** 잠근 뉴스 행의 트랜잭션 안에서 열람 기록을 생성하거나 열람 시각을 갱신한다. */
    private void upsertReadHistory(Long userId, Long newsId) {
        Optional<ReadHistory> existing = readHistoryRepository.findByUserIdAndNewsId(userId, newsId);
        if (existing.isPresent()) {
            existing.get().updateReadAt();
            return;
        }

        readHistoryRepository.save(ReadHistory.create(userId, newsId));
    }

    private News getPublishedNews(Long newsId) {
        return requirePublishedNews(newsRepository.findById(newsId));
    }

    private News getPublishedNewsForUpdate(Long newsId) {
        return requirePublishedNews(newsRepository.findByIdForUpdate(newsId));
    }

    private News requirePublishedNews(Optional<News> candidate) {
        News news = candidate
                .filter(found -> found.getDeletedAt() == null)
                .orElseThrow(() -> new NewsException(NewsErrorCode.NEWS_NOT_FOUND));
        if (news.getStatus() != NewsStatus.PUBLISHED) {
            throw new NewsException(NewsErrorCode.NEWS_NOT_PUBLISHED);
        }
        return news;
    }

    /**
     * 원본 썸네일이 있으면 그대로, 없으면 null을 반환한다. 원본이 없을 때의 기본 이미지는 백엔드가 관여하지 않고
     * 프론트가 화면/카테고리에 맞춰 자체 에셋으로 렌더링한다.
     */
    private static String originalThumbnail(String thumbnailUrl) {
        return (thumbnailUrl != null && !thumbnailUrl.isBlank()) ? thumbnailUrl : null;
    }
}
