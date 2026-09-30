package com.muffin.briefing.application.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muffin.briefing.application.BriefingProperties;
import com.muffin.briefing.application.exception.BriefingGenerationException;
import com.muffin.briefing.application.generation.BriefingGenerationResult.IssueResult;
import com.muffin.briefing.application.generation.BriefingGenerationSummary.Outcome;
import com.muffin.briefing.application.generation.SectorScoreboard.SectorScore;
import com.muffin.briefing.domain.Briefing;
import com.muffin.briefing.domain.BriefingIssue;
import com.muffin.briefing.domain.BriefingRepository;
import com.muffin.briefing.domain.enums.BriefingStatus;
import com.muffin.news.domain.category.Category;
import com.muffin.news.domain.category.CategoryRepository;
import com.muffin.news.domain.explanation.NewsExplanationRepository;
import com.muffin.news.domain.news.News;
import com.muffin.news.domain.news.NewsRepository;
import com.muffin.news.domain.term.TermDictionaryRepository;
import com.muffin.sector.domain.sector.SectorRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class BriefingGenerationServiceTest {

    private static final LocalDate BRIEFING_DATE = LocalDate.of(2026, 7, 20);
    private static final Long BRIEFING_ID = 1L;

    private BriefingRepository briefingRepository;
    private NewsRepository newsRepository;
    private NewsExplanationRepository newsExplanationRepository;
    private CategoryRepository categoryRepository;
    private TermDictionaryRepository termDictionaryRepository;
    private SectorRepository sectorRepository;
    private SectorScoreboardCalculator sectorScoreboardCalculator;
    private BuzzSignalClient buzzSignalClient;
    private BriefingGenerator briefingGenerator;
    private BriefingGenerationService generationService;

    private Briefing reservation;

    @BeforeEach
    void setUp() {
        briefingRepository = mock(BriefingRepository.class);
        newsRepository = mock(NewsRepository.class);
        newsExplanationRepository = mock(NewsExplanationRepository.class);
        categoryRepository = mock(CategoryRepository.class);
        termDictionaryRepository = mock(TermDictionaryRepository.class);
        sectorRepository = mock(SectorRepository.class);
        sectorScoreboardCalculator = mock(SectorScoreboardCalculator.class);
        buzzSignalClient = mock(BuzzSignalClient.class);
        briefingGenerator = mock(BriefingGenerator.class);

        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        generationService = new BriefingGenerationService(
                briefingRepository,
                newsRepository,
                newsExplanationRepository,
                categoryRepository,
                termDictionaryRepository,
                sectorRepository,
                sectorScoreboardCalculator,
                buzzSignalClient,
                briefingGenerator,
                new BriefingProperties(3, 7, new BriefingProperties.Buzz(true, List.of("증시"), 10)),
                new TransactionTemplate(transactionManager));

        reservation = Briefing.create(BRIEFING_DATE);
        ReflectionTestUtils.setField(reservation, "id", BRIEFING_ID);

        when(briefingRepository.findByBriefingDate(BRIEFING_DATE)).thenReturn(Optional.empty());
        when(briefingRepository.saveAndFlush(any(Briefing.class))).thenReturn(reservation);
        when(briefingRepository.findById(BRIEFING_ID)).thenReturn(Optional.of(reservation));
        when(categoryRepository.findAll()).thenReturn(List.of(category()));
        when(newsExplanationRepository.findByNewsIdInAndStatus(any(), any())).thenReturn(List.of());
        when(newsRepository.findAllById(any())).thenReturn(List.of());
        when(sectorRepository.findAll()).thenReturn(List.of());
        when(termDictionaryRepository.findAllById(any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("후보가 모이면 이슈 3건을 담은 발행 대기 브리핑을 저장한다")
    void generate_savesReadyBriefing() {
        givenCandidates(3);
        when(sectorScoreboardCalculator.calculate(BRIEFING_DATE))
                .thenReturn(new SectorScoreboard(
                        List.of(new SectorScore(10L, new BigDecimal("1.20"))),
                        List.of(new SectorScore(20L, new BigDecimal("-2.30")))));
        when(buzzSignalClient.fetch()).thenReturn(BuzzSignal.empty());
        when(briefingGenerator.generate(any())).thenReturn(result());

        BriefingGenerationSummary summary = generationService.generate(BRIEFING_DATE);

        assertThat(summary.outcome()).isEqualTo(Outcome.GENERATED);
        assertThat(summary.issueCount()).isEqualTo(3);
        assertThat(reservation.getStatus()).isEqualTo(BriefingStatus.READY);
        assertThat(reservation.getHeadline()).isEqualTo("오늘은 금리에 시선이 쏠렸어요.");
        assertThat(reservation.getIssues()).extracting(BriefingIssue::getNewsId).containsExactly(101L, 102L, 103L);
        assertThat(reservation.getSectorScores()).hasSize(2);
        verify(briefingRepository).save(reservation);
    }

    /**
     * 외부 신호는 순위를 돕는 재료일 뿐 필수 입력이 아니다. Google News 장애로 아침 브리핑이 통째로 빠지면 안 된다.
     */
    @Test
    @DisplayName("화제도 조회가 실패해도 브리핑은 정상 생성된다")
    void generate_succeedsWhenBuzzSignalFails() {
        givenCandidates(3);
        when(sectorScoreboardCalculator.calculate(BRIEFING_DATE)).thenReturn(SectorScoreboard.empty());
        when(buzzSignalClient.fetch()).thenThrow(new IllegalStateException("google news down"));
        when(briefingGenerator.generate(any())).thenReturn(result());

        BriefingGenerationSummary summary = generationService.generate(BRIEFING_DATE);

        assertThat(summary.outcome()).isEqualTo(Outcome.GENERATED);
        assertThat(reservation.getStatus()).isEqualTo(BriefingStatus.READY);
    }

    @Test
    @DisplayName("섹터 계산이 실패해도 성적표만 비운 채 브리핑은 생성된다")
    void generate_succeedsWhenSectorCalculationFails() {
        givenCandidates(3);
        when(sectorScoreboardCalculator.calculate(BRIEFING_DATE)).thenThrow(new IllegalStateException("db down"));
        when(buzzSignalClient.fetch()).thenReturn(BuzzSignal.empty());
        when(briefingGenerator.generate(any())).thenReturn(result());

        BriefingGenerationSummary summary = generationService.generate(BRIEFING_DATE);

        assertThat(summary.outcome()).isEqualTo(Outcome.GENERATED);
        assertThat(reservation.getSectorScores()).isEmpty();
    }

    @Test
    @DisplayName("후보가 최소 개수에 못 미치면 예약을 풀고 다음 시도를 기다린다")
    void generate_releasesReservationWhenCandidatesAreNotEnough() {
        givenCandidates(2);

        BriefingGenerationSummary summary = generationService.generate(BRIEFING_DATE);

        assertThat(summary.outcome()).isEqualTo(Outcome.INSUFFICIENT_NEWS);
        verify(briefingRepository).delete(reservation);
        verify(briefingGenerator, never()).generate(any());
    }

    @Test
    @DisplayName("이미 생성 중이거나 만들어진 브리핑이 있으면 건너뛴다")
    void generate_skipsWhenAlreadyReserved() {
        when(briefingRepository.findByBriefingDate(BRIEFING_DATE)).thenReturn(Optional.of(reservation));

        BriefingGenerationSummary summary = generationService.generate(BRIEFING_DATE);

        assertThat(summary.outcome()).isEqualTo(Outcome.ALREADY_RESERVED);
        verify(briefingRepository, never()).saveAndFlush(any());
    }

    /** 이전 실패로 남은 기록이 다음 재시도를 영영 막으면 안 된다. */
    @Test
    @DisplayName("이전 실패로 남은 이용 불가 브리핑은 지우고 다시 예약한다")
    void generate_clearsUnavailableBriefingBeforeRetry() {
        Briefing unavailable = Briefing.create(BRIEFING_DATE);
        unavailable.unavailable();
        when(briefingRepository.findByBriefingDate(BRIEFING_DATE)).thenReturn(Optional.of(unavailable));
        givenCandidates(3);
        when(sectorScoreboardCalculator.calculate(BRIEFING_DATE)).thenReturn(SectorScoreboard.empty());
        when(buzzSignalClient.fetch()).thenReturn(BuzzSignal.empty());
        when(briefingGenerator.generate(any())).thenReturn(result());

        BriefingGenerationSummary summary = generationService.generate(BRIEFING_DATE);

        assertThat(summary.outcome()).isEqualTo(Outcome.GENERATED);
        verify(briefingRepository).delete(unavailable);
    }

    @Test
    @DisplayName("AI 생성이 실패하면 이용 불가 상태로 저장한다")
    void generate_savesUnavailableWhenGenerationFails() {
        givenCandidates(3);
        when(sectorScoreboardCalculator.calculate(BRIEFING_DATE)).thenReturn(SectorScoreboard.empty());
        when(buzzSignalClient.fetch()).thenReturn(BuzzSignal.empty());
        when(briefingGenerator.generate(any())).thenThrow(new BriefingGenerationException("후보에 없는 뉴스를 골랐습니다"));

        BriefingGenerationSummary summary = generationService.generate(BRIEFING_DATE);

        assertThat(summary.outcome()).isEqualTo(Outcome.FAILED);
        assertThat(reservation.getStatus()).isEqualTo(BriefingStatus.UNAVAILABLE);
    }

    private void givenCandidates(int count) {
        List<News> newsList = List.of(news(101L), news(102L), news(103L)).subList(0, count);
        when(newsRepository.findBriefingCandidates(any(), any(), any(), any())).thenReturn(newsList);
    }

    private static BriefingGenerationResult result() {
        return new BriefingGenerationResult(
                "오늘은 금리에 시선이 쏠렸어요.",
                List.of(
                        new IssueResult(101L, "금리 동결", "한국은행이 기준금리를 묶었어요. 물가 흐름을 지켜보기로 했어요.", "대출 이자 부담은 그대로예요."),
                        new IssueResult(102L, "환율 상승", "원/달러 환율이 올랐어요. 달러 강세가 이어졌어요.", "해외 결제 비용이 늘 수 있어요."),
                        new IssueResult(103L, "반도체 수출 증가", "반도체 수출이 늘었어요. AI 서버용 주문이 이어졌어요.", "반도체 흐름 참고에 좋아요.")),
                null,
                null);
    }

    private static News news(Long newsId) {
        News news = News.processing(
                1L, "뉴스 " + newsId, "매일경제", LocalDateTime.of(2026, 7, 20, 6, 0), null, "https://news/" + newsId);
        news.completeReconstruction("뉴스 " + newsId, "뉴스 " + newsId + " 요약", "재구성된 본문");
        ReflectionTestUtils.setField(news, "id", newsId);
        return news;
    }

    private static Category category() {
        Category category = Category.create("경제", null);
        ReflectionTestUtils.setField(category, "id", 1L);
        return category;
    }
}
