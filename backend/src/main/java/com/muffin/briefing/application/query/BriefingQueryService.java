package com.muffin.briefing.application.query;

import com.muffin.briefing.application.BriefingProperties;
import com.muffin.briefing.application.exception.BriefingErrorCode;
import com.muffin.briefing.application.exception.BriefingException;
import com.muffin.briefing.domain.Briefing;
import com.muffin.briefing.domain.BriefingIssue;
import com.muffin.briefing.domain.BriefingRepository;
import com.muffin.briefing.domain.BriefingSectorScore;
import com.muffin.briefing.domain.BriefingView;
import com.muffin.briefing.domain.BriefingViewRepository;
import com.muffin.briefing.domain.MarketIndicatorPrice;
import com.muffin.briefing.domain.MarketIndicatorPriceRepository;
import com.muffin.briefing.domain.enums.BriefingStatus;
import com.muffin.briefing.domain.enums.MarketIndicator;
import com.muffin.briefing.domain.enums.SectorRankType;
import com.muffin.briefing.presentation.dto.BriefingDateListResponse;
import com.muffin.briefing.presentation.dto.BriefingDateListResponse.BriefingDateItem;
import com.muffin.briefing.presentation.dto.BriefingResponse;
import com.muffin.briefing.presentation.dto.BriefingViewResponse;
import com.muffin.news.domain.news.News;
import com.muffin.news.domain.news.NewsRepository;
import com.muffin.news.domain.news.enums.NewsStatus;
import com.muffin.news.domain.sectorimpact.NewsSectorImpact;
import com.muffin.news.domain.sectorimpact.NewsSectorImpactRepository;
import com.muffin.news.domain.sectorimpact.enums.ImpactType;
import com.muffin.news.domain.term.TermDictionaryRepository;
import com.muffin.sector.domain.sector.Sector;
import com.muffin.sector.domain.sector.SectorRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 브리핑 조회. 발행된 브리핑만 사용자에게 노출한다. */
@Service
@RequiredArgsConstructor
public class BriefingQueryService {

    /** 이슈 하나에 붙일 섹터 칩 최대 개수. */
    private static final int MAX_SECTOR_CHIPS = 2;

    private static final String DISCLAIMER = "머핀 브리핑은 투자 권유가 아닌 학습용 정보예요.";

    /** 지표 수집이 붙기 전까지는 기사 출처만 담는다. */
    private static final String SOURCES = "매일경제";

    /** 영향도가 뚜렷한 순서. NEUTRAL은 칩으로 쓰지 않는다. */
    private static final Map<ImpactType, Integer> IMPACT_PRIORITY = Map.of(
            ImpactType.STRONG_POSITIVE, 0,
            ImpactType.STRONG_NEGATIVE, 1,
            ImpactType.POSITIVE, 2,
            ImpactType.NEGATIVE, 3);

    private final BriefingRepository briefingRepository;
    private final BriefingViewRepository briefingViewRepository;
    private final NewsRepository newsRepository;
    private final NewsSectorImpactRepository newsSectorImpactRepository;
    private final TermDictionaryRepository termDictionaryRepository;
    private final SectorRepository sectorRepository;
    private final MarketIndicatorPriceRepository marketIndicatorPriceRepository;
    private final BriefingProperties properties;
    private final Clock clock;

    /**
     * 오늘의 브리핑. 오늘 발행분이 없으면 최근 발행분으로 대체하고 {@code isToday=false}로 알린다. 주말에 들어오면 금요일
     * 브리핑이 보이는 동작이 이것이다.
     */
    @Transactional(readOnly = true)
    public BriefingResponse getTodayBriefing() {
        LocalDate today = LocalDate.now(clock);

        Optional<Briefing> published = briefingRepository.findByBriefingDateAndStatus(today, BriefingStatus.PUBLISHED);
        if (published.isPresent()) {
            return toResponse(published.get(), true);
        }

        // 오늘 것이 아직 공개되지 않았다면 생성 중인지 실패인지를 그대로 알려 준다. 화면은 상태로 안내 문구를 고른다.
        Optional<Briefing> inProgress = briefingRepository.findByBriefingDate(today);
        if (inProgress.isPresent()) {
            return BriefingResponse.unavailable(today, inProgress.get().getStatus(), notice());
        }

        return latestPublished()
                .map(briefing -> toResponse(briefing, false))
                .orElseGet(() -> BriefingResponse.unavailable(today, BriefingStatus.UNAVAILABLE, notice()));
    }

    /** 특정 날짜의 브리핑. 다시 보기 범위를 벗어난 날짜는 거절한다. */
    @Transactional(readOnly = true)
    public BriefingResponse getBriefing(LocalDate briefingDate) {
        LocalDate today = LocalDate.now(clock);
        if (briefingDate.isAfter(today) || briefingDate.isBefore(today.minusDays(properties.recentDays()))) {
            throw new BriefingException(BriefingErrorCode.BRIEFING_OUT_OF_RANGE);
        }

        Briefing briefing = briefingRepository
                .findByBriefingDateAndStatus(briefingDate, BriefingStatus.PUBLISHED)
                .orElseThrow(() -> new BriefingException(BriefingErrorCode.BRIEFING_NOT_FOUND));
        return toResponse(briefing, briefingDate.equals(today));
    }

    /** 날짜 선택 칩에 쓸 최근 브리핑 목록. */
    @Transactional(readOnly = true)
    public BriefingDateListResponse getRecentBriefingDates() {
        List<BriefingDateItem> items = recentPublished().stream()
                .map(briefing -> new BriefingDateItem(briefing.getBriefingDate(), briefing.getHeadline()))
                .toList();
        return new BriefingDateListResponse(items);
    }

    /** 열람 기록을 남긴다. 다시 열람하면 시각만 갱신한다. */
    @Transactional
    public BriefingViewResponse recordView(Long userId, LocalDate briefingDate) {
        briefingRepository
                .findByBriefingDateAndStatus(briefingDate, BriefingStatus.PUBLISHED)
                .orElseThrow(() -> new BriefingException(BriefingErrorCode.BRIEFING_NOT_FOUND));

        LocalDateTime viewedAt = LocalDateTime.now(clock);
        BriefingView view = briefingViewRepository
                .findByUserIdAndBriefingDate(userId, briefingDate)
                .map(existing -> {
                    existing.renew(viewedAt);
                    return existing;
                })
                .orElseGet(() -> BriefingView.create(userId, briefingDate, viewedAt));

        briefingViewRepository.save(view);
        return new BriefingViewResponse(briefingDate, viewedAt);
    }

    private Optional<Briefing> latestPublished() {
        return recentPublished().stream().findFirst();
    }

    private List<Briefing> recentPublished() {
        return briefingRepository.findByStatusOrderByBriefingDateDesc(
                BriefingStatus.PUBLISHED, PageRequest.of(0, properties.recentDays()));
    }

    private BriefingResponse toResponse(Briefing briefing, boolean isToday) {
        List<Long> newsIds =
                briefing.getIssues().stream().map(BriefingIssue::getNewsId).toList();

        Map<Long, News> newsById =
                newsRepository.findAllById(newsIds).stream().collect(Collectors.toMap(News::getId, news -> news));
        Map<Long, Sector> sectorsById =
                sectorRepository.findAll().stream().collect(Collectors.toMap(Sector::getId, sector -> sector));
        Map<Long, List<NewsSectorImpact>> impactsByNewsId = newsIds.isEmpty()
                ? Map.of()
                : newsSectorImpactRepository.findByNewsIdIn(newsIds).stream()
                        .collect(Collectors.groupingBy(NewsSectorImpact::getNewsId));

        List<BriefingResponse.Issue> issues = briefing.getIssues().stream()
                .map(issue -> new BriefingResponse.Issue(
                        issue.getIssueOrder(),
                        issue.getTitle(),
                        issue.getSummary(),
                        issue.getImpactLine(),
                        sectorChips(impactsByNewsId.getOrDefault(issue.getNewsId(), List.of()), sectorsById),
                        issue.getNewsId(),
                        isArticleAvailable(newsById.get(issue.getNewsId()))))
                .toList();

        return new BriefingResponse(
                briefing.getBriefingDate(),
                briefing.getStatus(),
                isToday,
                briefing.getHeadline(),
                marketIndices(briefing.getBriefingDate()),
                issues,
                sectorScoreboard(briefing, sectorsById),
                todayTerm(briefing),
                notice());
    }

    /**
     * 간밤의 시장 카드. 지표별로 기준일 이하의 가장 최근 정상 수신 건을 쓴다.
     *
     * <p>받아오지 못한 지표는 배열에서 빠진다. 한 지표의 수집 실패가 블록 전체를 비우면 안 되기 때문이다. 순서는 기획서의 카드 순서
     * (코스피 · 코스닥 · S&amp;P 500 · 나스닥 · 원/달러)와 같은 {@code MarketIndicator} 선언 순서를 따른다.
     */
    private List<BriefingResponse.MarketIndex> marketIndices(LocalDate briefingDate) {
        Map<MarketIndicator, MarketIndicatorPrice> pricesByIndicator =
                marketIndicatorPriceRepository.findLatestUsableOn(briefingDate).stream()
                        .collect(Collectors.toMap(MarketIndicatorPrice::getIndicator, price -> price));

        List<BriefingResponse.MarketIndex> indices = new ArrayList<>();
        for (MarketIndicator indicator : MarketIndicator.values()) {
            MarketIndicatorPrice price = pricesByIndicator.get(indicator);
            if (price == null) {
                continue;
            }
            indices.add(new BriefingResponse.MarketIndex(
                    indicator.displayName(),
                    price.getClosePrice(),
                    price.getChangeRate(),
                    price.getPriceDate(),
                    indicator.unit(),
                    indicator.referenceSymbol()));
        }
        return indices;
    }

    /**
     * 근거 기사를 열 수 있는지 판단한다. 브리핑이 뉴스 공개보다 먼저 발행될 수 있고 기사가 삭제될 수도 있어, 두 경우 모두 여기서
     * 걸러 [기사 보기] 버튼을 숨기게 한다.
     */
    private static boolean isArticleAvailable(News news) {
        return news != null && news.getStatus() == NewsStatus.PUBLISHED && news.getDeletedAt() == null;
    }

    /** 영향이 뚜렷한 섹터부터 최대 2개를 칩으로 만든다. NEUTRAL은 쓰지 않는다. */
    private static List<BriefingResponse.SectorChip> sectorChips(
            List<NewsSectorImpact> impacts, Map<Long, Sector> sectorsById) {
        return impacts.stream()
                .filter(impact -> IMPACT_PRIORITY.containsKey(impact.getImpact()))
                .sorted(Comparator.comparingInt(impact -> IMPACT_PRIORITY.get(impact.getImpact())))
                .map(impact -> sectorsById.get(impact.getSectorId()))
                .filter(Objects::nonNull)
                .map(sector -> new BriefingResponse.SectorChip(sector.getSectorCode(), sector.getName()))
                .limit(MAX_SECTOR_CHIPS)
                .toList();
    }

    private static BriefingResponse.SectorScoreboard sectorScoreboard(
            Briefing briefing, Map<Long, Sector> sectorsById) {
        List<BriefingResponse.Entry> gainers = new ArrayList<>();
        List<BriefingResponse.Entry> losers = new ArrayList<>();

        for (BriefingSectorScore score : briefing.getSectorScores()) {
            Sector sector = sectorsById.get(score.getSectorId());
            if (sector == null) {
                continue;
            }
            BriefingResponse.Entry entry =
                    new BriefingResponse.Entry(sector.getSectorCode(), sector.getName(), score.getChangeRate());
            if (score.getRankType() == SectorRankType.TOP_GAINER) {
                gainers.add(entry);
            } else {
                losers.add(entry);
            }
        }
        return new BriefingResponse.SectorScoreboard(gainers, losers);
    }

    private BriefingResponse.TodayTerm todayTerm(Briefing briefing) {
        if (briefing.getTermId() == null) {
            return null;
        }
        return termDictionaryRepository
                .findById(briefing.getTermId())
                .map(term -> new BriefingResponse.TodayTerm(term.getId(), term.getTerm(), briefing.getTermSummary()))
                .orElse(null);
    }

    private BriefingResponse.Notice notice() {
        return new BriefingResponse.Notice(SOURCES, LocalDateTime.now(clock), DISCLAIMER);
    }
}
