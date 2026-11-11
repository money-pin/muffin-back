package com.muffin.briefing.application.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.muffin.briefing.application.BriefingProperties;
import com.muffin.briefing.application.exception.BriefingException;
import com.muffin.briefing.domain.Briefing;
import com.muffin.briefing.domain.BriefingRepository;
import com.muffin.briefing.domain.BriefingView;
import com.muffin.briefing.domain.BriefingViewRepository;
import com.muffin.briefing.domain.MarketIndicatorPrice;
import com.muffin.briefing.domain.MarketIndicatorPriceRepository;
import com.muffin.briefing.domain.enums.BriefingStatus;
import com.muffin.briefing.domain.enums.MarketIndicator;
import com.muffin.briefing.domain.enums.MarketIndicatorUnit;
import com.muffin.briefing.presentation.dto.BriefingResponse;
import com.muffin.news.domain.news.News;
import com.muffin.news.domain.news.NewsRepository;
import com.muffin.news.domain.sectorimpact.NewsSectorImpactRepository;
import com.muffin.news.domain.term.TermDictionaryRepository;
import com.muffin.sector.domain.sector.SectorRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class BriefingQueryServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 20);
    private static final Clock CLOCK =
            Clock.fixed(LocalDateTime.of(2026, 7, 20, 8, 0).atZone(SEOUL).toInstant(), SEOUL);

    private BriefingRepository briefingRepository;
    private BriefingViewRepository briefingViewRepository;
    private NewsRepository newsRepository;
    private NewsSectorImpactRepository newsSectorImpactRepository;
    private TermDictionaryRepository termDictionaryRepository;
    private SectorRepository sectorRepository;
    private MarketIndicatorPriceRepository marketIndicatorPriceRepository;
    private BriefingQueryService queryService;

    @BeforeEach
    void setUp() {
        briefingRepository = mock(BriefingRepository.class);
        briefingViewRepository = mock(BriefingViewRepository.class);
        newsRepository = mock(NewsRepository.class);
        newsSectorImpactRepository = mock(NewsSectorImpactRepository.class);
        termDictionaryRepository = mock(TermDictionaryRepository.class);
        sectorRepository = mock(SectorRepository.class);

        when(sectorRepository.findAll()).thenReturn(List.of());
        when(newsSectorImpactRepository.findByNewsIdIn(any())).thenReturn(List.of());

        marketIndicatorPriceRepository = mock(MarketIndicatorPriceRepository.class);
        when(marketIndicatorPriceRepository.findLatestUsableOn(any())).thenReturn(List.of());

        queryService = new BriefingQueryService(
                briefingRepository,
                briefingViewRepository,
                newsRepository,
                newsSectorImpactRepository,
                termDictionaryRepository,
                sectorRepository,
                marketIndicatorPriceRepository,
                new BriefingProperties(8, 7, new BriefingProperties.Buzz(false, List.of(), 10)),
                CLOCK);
    }

    @Test
    @DisplayName("오늘 발행된 브리핑이 있으면 그대로 반환한다")
    void getTodayBriefing_returnsTodayWhenPublished() {
        givenPublishedToday();
        givenNews(publishedNews(101L), publishedNews(102L), publishedNews(103L));

        BriefingResponse response = queryService.getTodayBriefing();

        assertThat(response.isToday()).isTrue();
        assertThat(response.status()).isEqualTo(BriefingStatus.PUBLISHED);
        assertThat(response.issues()).hasSize(3);
    }

    /** 주말에 들어오면 금요일 브리핑이 보이고, 오늘 것이 아님을 isToday로 알린다. */
    @Test
    @DisplayName("오늘 발행분이 없으면 최근 발행분으로 대체하고 isToday를 false로 내린다")
    void getTodayBriefing_fallsBackToLatestPublished() {
        Briefing friday = publishedBriefing(LocalDate.of(2026, 7, 17));
        when(briefingRepository.findByBriefingDateAndStatus(TODAY, BriefingStatus.PUBLISHED))
                .thenReturn(Optional.empty());
        when(briefingRepository.findByBriefingDate(TODAY)).thenReturn(Optional.empty());
        when(briefingRepository.findByStatusOrderByBriefingDateDesc(any(), any()))
                .thenReturn(List.of(friday));
        givenNews(publishedNews(101L), publishedNews(102L), publishedNews(103L));

        BriefingResponse response = queryService.getTodayBriefing();

        assertThat(response.isToday()).isFalse();
        assertThat(response.briefingDate()).isEqualTo(LocalDate.of(2026, 7, 17));
    }

    @Test
    @DisplayName("아직 생성 중이면 상태만 내려보낸다")
    void getTodayBriefing_returnsGeneratingStatus() {
        when(briefingRepository.findByBriefingDateAndStatus(TODAY, BriefingStatus.PUBLISHED))
                .thenReturn(Optional.empty());
        when(briefingRepository.findByBriefingDate(TODAY)).thenReturn(Optional.of(Briefing.create(TODAY)));

        BriefingResponse response = queryService.getTodayBriefing();

        assertThat(response.status()).isEqualTo(BriefingStatus.GENERATING);
        assertThat(response.issues()).isEmpty();
        assertThat(response.headline()).isNull();
    }

    /** 브리핑이 뉴스보다 먼저 발행될 수 있어, 기사가 아직 공개 전이면 버튼을 숨겨야 한다. */
    @Test
    @DisplayName("근거 기사가 아직 공개 전이면 articleAvailable이 false다")
    void getTodayBriefing_marksArticleUnavailableWhenNewsIsNotPublished() {
        givenPublishedToday();
        givenNews(pendingNews(101L), publishedNews(102L), publishedNews(103L));

        BriefingResponse response = queryService.getTodayBriefing();

        assertThat(response.issues())
                .extracting(BriefingResponse.Issue::newsId, BriefingResponse.Issue::articleAvailable)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(101L, false),
                        org.assertj.core.groups.Tuple.tuple(102L, true),
                        org.assertj.core.groups.Tuple.tuple(103L, true));
    }

    @Test
    @DisplayName("근거 기사가 삭제되면 articleAvailable이 false다")
    void getTodayBriefing_marksArticleUnavailableWhenNewsIsDeleted() {
        givenPublishedToday();
        News deleted = publishedNews(101L);
        deleted.delete();
        givenNews(deleted, publishedNews(102L), publishedNews(103L));

        BriefingResponse response = queryService.getTodayBriefing();

        assertThat(response.issues().getFirst().articleAvailable()).isFalse();
    }

    @Test
    @DisplayName("수집된 지표가 없으면 시장 블록은 빈 배열이다")
    void getTodayBriefing_returnsEmptyMarketIndicesWhenNotCollected() {
        givenPublishedToday();
        givenNews(publishedNews(101L), publishedNews(102L), publishedNews(103L));

        assertThat(queryService.getTodayBriefing().marketIndices()).isEmpty();
    }

    @Test
    @DisplayName("지표 카드는 기획서 순서(코스피·코스닥·S&P 500·나스닥·원/달러)로 내려간다")
    void getTodayBriefing_returnsMarketIndicesInPlannedOrder() {
        givenPublishedToday();
        givenNews(publishedNews(101L), publishedNews(102L), publishedNews(103L));
        when(marketIndicatorPriceRepository.findLatestUsableOn(any()))
                .thenReturn(List.of(
                        indicatorPrice(MarketIndicator.USD_KRW, "1380.5000", "1375.0000"),
                        indicatorPrice(MarketIndicator.KOSPI, "2812.4500", "2798.1000"),
                        indicatorPrice(MarketIndicator.NASDAQ100, "512.3000", "518.0000")));

        assertThat(queryService.getTodayBriefing().marketIndices())
                .extracting(BriefingResponse.MarketIndex::name)
                .containsExactly("코스피", "나스닥 100", "원/달러");
    }

    /** 해외 지수는 추종 ETF 가격이라 지수 레벨과 다르다. 화면이 근거를 밝힐 수 있도록 기준 종목을 함께 내린다. */
    @Test
    @DisplayName("ETF로 대신한 지표는 기준 종목을 함께 내려보낸다")
    void getTodayBriefing_exposesReferenceSymbolForEtfBackedIndicator() {
        givenPublishedToday();
        givenNews(publishedNews(101L), publishedNews(102L), publishedNews(103L));
        when(marketIndicatorPriceRepository.findLatestUsableOn(any()))
                .thenReturn(List.of(
                        indicatorPrice(MarketIndicator.KOSPI, "2812.4500", "2798.1000"),
                        indicatorPrice(MarketIndicator.NASDAQ100, "512.3000", "518.0000")));

        assertThat(queryService.getTodayBriefing().marketIndices())
                .extracting(
                        BriefingResponse.MarketIndex::name,
                        BriefingResponse.MarketIndex::referenceSymbol,
                        BriefingResponse.MarketIndex::unit)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("코스피", null, MarketIndicatorUnit.POINT),
                        org.assertj.core.groups.Tuple.tuple("나스닥 100", "QQQ", MarketIndicatorUnit.USD));
    }

    /** 한 지표의 수집 실패가 블록 전체를 비우면 안 된다. */
    @Test
    @DisplayName("받아오지 못한 지표는 카드에서만 빠진다")
    void getTodayBriefing_omitsOnlyMissingIndicators() {
        givenPublishedToday();
        givenNews(publishedNews(101L), publishedNews(102L), publishedNews(103L));
        when(marketIndicatorPriceRepository.findLatestUsableOn(any()))
                .thenReturn(List.of(indicatorPrice(MarketIndicator.KOSPI, "2812.4500", "2798.1000")));

        assertThat(queryService.getTodayBriefing().marketIndices()).hasSize(1);
    }

    private static MarketIndicatorPrice indicatorPrice(
            MarketIndicator indicator, String closePrice, String previousClose) {
        return MarketIndicatorPrice.success(
                indicator, TODAY, new BigDecimal(closePrice), new BigDecimal(previousClose));
    }

    @Test
    @DisplayName("다시 보기 범위를 벗어난 날짜는 거절한다")
    void getBriefing_rejectsDateOutOfRange() {
        assertThatThrownBy(() -> queryService.getBriefing(TODAY.minusDays(8))).isInstanceOf(BriefingException.class);
        assertThatThrownBy(() -> queryService.getBriefing(TODAY.plusDays(1))).isInstanceOf(BriefingException.class);
    }

    @Test
    @DisplayName("해당 날짜에 발행된 브리핑이 없으면 404로 알린다")
    void getBriefing_throwsWhenNotPublished() {
        when(briefingRepository.findByBriefingDateAndStatus(TODAY, BriefingStatus.PUBLISHED))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> queryService.getBriefing(TODAY)).isInstanceOf(BriefingException.class);
    }

    @Test
    @DisplayName("처음 열람하면 기록을 만든다")
    void recordView_createsRecordOnFirstView() {
        givenPublishedToday();
        when(briefingViewRepository.findByUserIdAndBriefingDate(9L, TODAY)).thenReturn(Optional.empty());

        assertThat(queryService.recordView(9L, TODAY).briefingDate()).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("다시 열람하면 새 기록을 만들지 않고 시각만 갱신한다")
    void recordView_renewsExistingRecord() {
        givenPublishedToday();
        BriefingView existing = BriefingView.create(9L, TODAY, LocalDateTime.of(2026, 7, 20, 7, 40));
        when(briefingViewRepository.findByUserIdAndBriefingDate(9L, TODAY)).thenReturn(Optional.of(existing));

        queryService.recordView(9L, TODAY);

        assertThat(existing.getViewedAt()).isEqualTo(LocalDateTime.of(2026, 7, 20, 8, 0));
    }

    private void givenPublishedToday() {
        when(briefingRepository.findByBriefingDateAndStatus(TODAY, BriefingStatus.PUBLISHED))
                .thenReturn(Optional.of(publishedBriefing(TODAY)));
    }

    private void givenNews(News... newsList) {
        when(newsRepository.findAllById(any())).thenReturn(List.of(newsList));
    }

    private static Briefing publishedBriefing(LocalDate date) {
        Briefing briefing = Briefing.create(date);
        briefing.addIssue(1, 101L, "금리 동결", "한국은행이 기준금리를 묶었어요. 물가를 지켜보기로 했어요.", "대출 이자 부담은 그대로예요.");
        briefing.addIssue(2, 102L, "환율 상승", "원/달러 환율이 올랐어요. 달러 강세가 이어졌어요.", "해외 결제 비용이 늘 수 있어요.");
        briefing.addIssue(3, 103L, "반도체 수출 증가", "반도체 수출이 늘었어요. AI 서버용 주문이 이어졌어요.", "반도체 흐름 참고에 좋아요.");
        briefing.ready("오늘은 금리에 시선이 쏠렸어요.", null, null);
        briefing.publish(LocalDateTime.of(date, java.time.LocalTime.of(7, 30)));
        return briefing;
    }

    private static News publishedNews(Long newsId) {
        News news = pendingNews(newsId);
        news.publish();
        return news;
    }

    private static News pendingNews(Long newsId) {
        News news = News.processing(
                1L, "뉴스 " + newsId, "매일경제", LocalDateTime.of(2026, 7, 20, 6, 0), null, "https://news/" + newsId);
        news.completeReconstruction("뉴스 " + newsId, "뉴스 " + newsId + " 요약", "재구성된 본문");
        ReflectionTestUtils.setField(news, "id", newsId);
        return news;
    }
}
