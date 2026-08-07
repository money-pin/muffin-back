package com.muffin.quiz.application.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muffin.news.domain.explanation.NewsExplanation;
import com.muffin.news.domain.explanation.NewsExplanationRepository;
import com.muffin.news.domain.explanation.enums.NewsExplanationStatus;
import com.muffin.news.domain.news.News;
import com.muffin.news.domain.news.NewsRepository;
import com.muffin.news.domain.news.enums.NewsStatus;
import com.muffin.quiz.domain.quizset.Quiz;
import com.muffin.quiz.domain.quizset.QuizSet;
import com.muffin.quiz.domain.quizset.QuizSetRepository;
import com.muffin.quiz.domain.quizset.enums.QuizDifficulty;
import com.muffin.quiz.domain.quizset.enums.QuizSetStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class DailyQuizGenerationServiceTest {

    private static final LocalDate QUIZ_DATE = LocalDate.of(2026, 7, 19);

    private NewsRepository newsRepository;
    private NewsExplanationRepository newsExplanationRepository;
    private QuizSetRepository quizSetRepository;
    private DailyQuizGenerator dailyQuizGenerator;
    private PlatformTransactionManager transactionManager;
    private DailyQuizGenerationService generationService;

    @BeforeEach
    void setUp() {
        newsRepository = mock(NewsRepository.class);
        newsExplanationRepository = mock(NewsExplanationRepository.class);
        quizSetRepository = mock(QuizSetRepository.class);
        dailyQuizGenerator = mock(DailyQuizGenerator.class);
        transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(newsExplanationRepository.findTop3ByNewsIdAndStatusOrderByCardOrderAsc(
                        any(), any(NewsExplanationStatus.class)))
                .thenReturn(List.of());
        generationService = new DailyQuizGenerationService(
                newsRepository,
                newsExplanationRepository,
                quizSetRepository,
                dailyQuizGenerator,
                new TransactionTemplate(transactionManager));
    }

    @Test
    @DisplayName("재구성 완료 뉴스 3개를 바탕으로 READY 상태의 일일 퀴즈 세트를 저장한다")
    void generate_savesReadyQuizSet() {
        List<News> newsSources = List.of(
                pendingNews(1L, "뉴스1", "기준금리가 올랐습니다. 대출 이자 부담이 커졌습니다."),
                pendingNews(2L, "뉴스2", "코픽스가 상승했습니다. 주택담보대출 금리가 오를 수 있습니다."),
                pendingNews(3L, "뉴스3", "금융당국이 토스를 금융복합기업집단으로 지정했습니다."));

        when(quizSetRepository.findByQuizDate(QUIZ_DATE)).thenReturn(Optional.empty());
        when(newsRepository.findQuizCandidates(
                        NewsStatus.PENDING,
                        NewsExplanationStatus.DONE,
                        QUIZ_DATE.atStartOfDay(),
                        QUIZ_DATE.plusDays(1).atStartOfDay(),
                        PageRequest.of(0, 15)))
                .thenReturn(newsSources);
        when(dailyQuizGenerator.generate(any())).thenReturn(generationResult());

        generationService.generate(QUIZ_DATE);

        verify(quizSetRepository).saveAndFlush(any(QuizSet.class));

        ArgumentCaptor<QuizSet> captor = ArgumentCaptor.forClass(QuizSet.class);
        verify(quizSetRepository).save(captor.capture());
        QuizSet quizSet = captor.getValue();

        assertThat(quizSet.getQuizDate()).isEqualTo(QUIZ_DATE);
        assertThat(quizSet.getStatus()).isEqualTo(QuizSetStatus.READY);
        assertThat(quizSet.getQuizzes()).hasSize(3);
        assertThat(quizSet.getQuizzes()).extracting(Quiz::getNewsId).containsExactly(1L, 2L, 3L);
        assertThat(quizSet.getQuizzes().getFirst().getOptions()).hasSize(3);
        assertThat(quizSet.getQuizzes().getFirst().findCorrectOption()).isPresent();
    }

    @Test
    @DisplayName("일일 퀴즈 생성 요청에는 뉴스별 해설카드 정보가 함께 포함된다")
    void generate_includesExplanationCardsInRequest() {
        List<News> newsSources = defaultNewsSources();
        when(quizSetRepository.findByQuizDate(QUIZ_DATE)).thenReturn(Optional.empty());
        when(newsRepository.findQuizCandidates(
                        NewsStatus.PENDING,
                        NewsExplanationStatus.DONE,
                        QUIZ_DATE.atStartOfDay(),
                        QUIZ_DATE.plusDays(1).atStartOfDay(),
                        PageRequest.of(0, 15)))
                .thenReturn(newsSources);
        when(newsExplanationRepository.findTop3ByNewsIdAndStatusOrderByCardOrderAsc(1L, NewsExplanationStatus.DONE))
                .thenReturn(List.of(explanation(1L, 1, "기준금리란?", "기준금리", "중앙은행이 정하는 대표 금리입니다.")));
        when(dailyQuizGenerator.generate(any())).thenReturn(generationResult());

        generationService.generate(QUIZ_DATE);

        ArgumentCaptor<DailyQuizGenerationRequest> captor = ArgumentCaptor.forClass(DailyQuizGenerationRequest.class);
        verify(dailyQuizGenerator).generate(captor.capture());
        assertThat(captor.getValue().newsSources().getFirst().explanationCards())
                .singleElement()
                .satisfies(card -> {
                    assertThat(card.order()).isEqualTo(1);
                    assertThat(card.title()).isEqualTo("기준금리란?");
                    assertThat(card.keyTerm()).isEqualTo("기준금리");
                    assertThat(card.content()).isEqualTo("중앙은행이 정하는 대표 금리입니다.");
                });
    }

    @Test
    @DisplayName("퀴즈 출처 뉴스는 최신순만 보지 않고 카테고리 다양성과 용어 매핑 수를 기준으로 3개를 고른다")
    void generate_selectsSourceNewsByCategoryDiversityAndTermCount() {
        List<News> candidates = List.of(
                pendingNews(1L, 1L, LocalDateTime.of(2026, 7, 19, 12, 0), "뉴스1 본문", 0),
                pendingNews(2L, 1L, LocalDateTime.of(2026, 7, 19, 11, 0), "뉴스2 본문", 3),
                pendingNews(3L, 2L, LocalDateTime.of(2026, 7, 19, 10, 0), "뉴스3 본문", 2),
                pendingNews(4L, 3L, LocalDateTime.of(2026, 7, 19, 9, 0), "뉴스4 본문", 1),
                pendingNews(5L, 2L, LocalDateTime.of(2026, 7, 19, 8, 0), "뉴스5 본문", 1));

        when(quizSetRepository.findByQuizDate(QUIZ_DATE)).thenReturn(Optional.empty());
        when(newsRepository.findQuizCandidates(
                        NewsStatus.PENDING,
                        NewsExplanationStatus.DONE,
                        QUIZ_DATE.atStartOfDay(),
                        QUIZ_DATE.plusDays(1).atStartOfDay(),
                        PageRequest.of(0, 15)))
                .thenReturn(candidates);
        when(dailyQuizGenerator.generate(any()))
                .thenReturn(new DailyQuizGenerationResult(
                        List.of(question(1, 2L, "뉴스2 본문"), question(2, 3L, "뉴스3 본문"), question(3, 4L, "뉴스4 본문"))));

        generationService.generate(QUIZ_DATE);

        ArgumentCaptor<DailyQuizGenerationRequest> requestCaptor =
                ArgumentCaptor.forClass(DailyQuizGenerationRequest.class);
        verify(dailyQuizGenerator).generate(requestCaptor.capture());
        assertThat(requestCaptor.getValue().newsSources())
                .extracting(DailyQuizNewsSource::newsId)
                .containsExactly(2L, 3L, 4L);

        ArgumentCaptor<QuizSet> quizSetCaptor = ArgumentCaptor.forClass(QuizSet.class);
        verify(quizSetRepository).save(quizSetCaptor.capture());
        assertThat(quizSetCaptor.getValue().getQuizzes())
                .extracting(Quiz::getNewsId)
                .containsExactly(2L, 3L, 4L);
    }

    @Test
    @DisplayName("퀴즈 출처 뉴스는 해설카드 핵심어가 겹치지 않도록 우선 선별한다")
    void generate_selectsSourceNewsByExplanationKeyTermDiversity() {
        List<News> candidates = List.of(
                pendingNews(1L, 1L, LocalDateTime.of(2026, 7, 19, 12, 0), "뉴스1 본문", 3),
                pendingNews(2L, 2L, LocalDateTime.of(2026, 7, 19, 11, 0), "뉴스2 본문", 2),
                pendingNews(3L, 3L, LocalDateTime.of(2026, 7, 19, 10, 0), "뉴스3 본문", 1),
                pendingNews(4L, 4L, LocalDateTime.of(2026, 7, 19, 9, 0), "뉴스4 본문", 1));

        when(quizSetRepository.findByQuizDate(QUIZ_DATE)).thenReturn(Optional.empty());
        when(newsRepository.findQuizCandidates(
                        NewsStatus.PENDING,
                        NewsExplanationStatus.DONE,
                        QUIZ_DATE.atStartOfDay(),
                        QUIZ_DATE.plusDays(1).atStartOfDay(),
                        PageRequest.of(0, 15)))
                .thenReturn(candidates);
        when(newsExplanationRepository.findTop3ByNewsIdAndStatusOrderByCardOrderAsc(1L, NewsExplanationStatus.DONE))
                .thenReturn(List.of(explanation(1L, 1, "기준금리란?", "기준금리", "금리 설명")));
        when(newsExplanationRepository.findTop3ByNewsIdAndStatusOrderByCardOrderAsc(2L, NewsExplanationStatus.DONE))
                .thenReturn(List.of(explanation(2L, 1, "금리 변화란?", "기준금리", "금리 설명")));
        when(newsExplanationRepository.findTop3ByNewsIdAndStatusOrderByCardOrderAsc(3L, NewsExplanationStatus.DONE))
                .thenReturn(List.of(explanation(3L, 1, "코픽스란?", "코픽스", "코픽스 설명")));
        when(newsExplanationRepository.findTop3ByNewsIdAndStatusOrderByCardOrderAsc(4L, NewsExplanationStatus.DONE))
                .thenReturn(List.of(explanation(4L, 1, "공모주란?", "공모주", "공모주 설명")));
        when(dailyQuizGenerator.generate(any()))
                .thenReturn(new DailyQuizGenerationResult(
                        List.of(question(1, 1L, "뉴스1 본문"), question(2, 3L, "뉴스3 본문"), question(3, 4L, "뉴스4 본문"))));

        generationService.generate(QUIZ_DATE);

        ArgumentCaptor<DailyQuizGenerationRequest> requestCaptor =
                ArgumentCaptor.forClass(DailyQuizGenerationRequest.class);
        verify(dailyQuizGenerator).generate(requestCaptor.capture());
        assertThat(requestCaptor.getValue().newsSources())
                .extracting(DailyQuizNewsSource::newsId)
                .containsExactly(1L, 3L, 4L);
    }

    @Test
    @DisplayName("이전 실패로 UNAVAILABLE 퀴즈 세트가 있으면 삭제 후 다시 생성한다")
    void generate_retriesWhenUnavailableQuizSetExists() {
        QuizSet unavailableQuizSet = QuizSet.create(QUIZ_DATE);
        unavailableQuizSet.unavailable();
        List<News> newsSources = defaultNewsSources();

        when(quizSetRepository.findByQuizDate(QUIZ_DATE)).thenReturn(Optional.of(unavailableQuizSet));
        when(newsRepository.findQuizCandidates(
                        NewsStatus.PENDING,
                        NewsExplanationStatus.DONE,
                        QUIZ_DATE.atStartOfDay(),
                        QUIZ_DATE.plusDays(1).atStartOfDay(),
                        PageRequest.of(0, 15)))
                .thenReturn(newsSources);
        when(dailyQuizGenerator.generate(any())).thenReturn(generationResult());

        generationService.generate(QUIZ_DATE);

        verify(quizSetRepository).delete(unavailableQuizSet);
        verify(quizSetRepository).flush();
        verify(quizSetRepository).saveAndFlush(any(QuizSet.class));

        ArgumentCaptor<QuizSet> captor = ArgumentCaptor.forClass(QuizSet.class);
        verify(quizSetRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(QuizSetStatus.READY);
    }

    @Test
    @DisplayName("이미 유효한 퀴즈 세트가 있으면 재시도 스케줄러가 호출해도 생성을 건너뛴다")
    void generate_skipsWhenNonRetryableQuizSetExists() {
        QuizSet readyQuizSet = readyQuizSet(QUIZ_DATE);

        when(quizSetRepository.findByQuizDate(QUIZ_DATE)).thenReturn(Optional.of(readyQuizSet));

        generationService.generate(QUIZ_DATE);

        verify(dailyQuizGenerator, never()).generate(any());
        verify(quizSetRepository, never()).delete(any());
        verify(quizSetRepository, never()).saveAndFlush(any());
        verify(quizSetRepository, never()).save(any());
    }

    @Test
    @DisplayName("AI 근거 문장과 뉴스 본문의 공백 차이는 정규화해서 검증한다")
    void generate_normalizesSourceSentenceWhitespace() {
        List<News> newsSources = List.of(
                pendingNews(1L, "뉴스1", "기준금리가 올랐습니다.\n\n대출 이자 부담이 커졌습니다."),
                pendingNews(2L, "뉴스2", "코픽스가 상승했습니다. 주택담보대출 금리가 오를 수 있습니다."),
                pendingNews(3L, "뉴스3", "금융당국이 토스를 금융복합기업집단으로 지정했습니다."));

        when(quizSetRepository.findByQuizDate(QUIZ_DATE)).thenReturn(Optional.empty());
        when(newsRepository.findQuizCandidates(
                        NewsStatus.PENDING,
                        NewsExplanationStatus.DONE,
                        QUIZ_DATE.atStartOfDay(),
                        QUIZ_DATE.plusDays(1).atStartOfDay(),
                        PageRequest.of(0, 15)))
                .thenReturn(newsSources);
        when(dailyQuizGenerator.generate(any()))
                .thenReturn(new DailyQuizGenerationResult(List.of(
                        question(1, 1L, "기준금리가 올랐습니다. 대출 이자 부담이 커졌습니다."),
                        question(2, 2L, "코픽스가 상승했습니다."),
                        question(3, 3L, "금융당국이 토스를 금융복합기업집단으로 지정했습니다."))));

        generationService.generate(QUIZ_DATE);

        ArgumentCaptor<QuizSet> captor = ArgumentCaptor.forClass(QuizSet.class);
        verify(quizSetRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(QuizSetStatus.READY);
    }

    @Test
    @DisplayName("같은 날짜 퀴즈 생성이 다시 호출되면 DB 예약 세트로 중복 AI 생성을 막는다")
    void generate_skipsWhenGenerationReservationExists() {
        List<News> newsSources = defaultNewsSources();
        AtomicBoolean reserved = new AtomicBoolean(false);

        when(quizSetRepository.findByQuizDate(QUIZ_DATE))
                .thenAnswer(invocation -> reserved.get() ? Optional.of(QuizSet.create(QUIZ_DATE)) : Optional.empty());
        when(newsRepository.findQuizCandidates(
                        NewsStatus.PENDING,
                        NewsExplanationStatus.DONE,
                        QUIZ_DATE.atStartOfDay(),
                        QUIZ_DATE.plusDays(1).atStartOfDay(),
                        PageRequest.of(0, 15)))
                .thenReturn(newsSources);
        when(dailyQuizGenerator.generate(any())).thenReturn(generationResult());
        when(quizSetRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            reserved.set(true);
            return invocation.getArgument(0);
        });

        generationService.generate(QUIZ_DATE);
        generationService.generate(QUIZ_DATE);

        verify(dailyQuizGenerator, times(1)).generate(any());
    }

    @Test
    @DisplayName("DB 예약 생성이 유니크 제약에 걸리면 AI 호출 없이 생성을 건너뛴다")
    void generate_skipsWhenReservationInsertConflicts() {
        when(quizSetRepository.findByQuizDate(QUIZ_DATE)).thenReturn(Optional.empty());
        when(quizSetRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate quizDate"));

        generationService.generate(QUIZ_DATE);

        verify(dailyQuizGenerator, never()).generate(any());
        verify(quizSetRepository, never()).save(any());
    }

    @Test
    @DisplayName("재구성 완료 뉴스가 3개보다 적으면 퀴즈 생성을 건너뛰고 AI를 호출하지 않는다")
    void generate_skipsWhenSourceNewsIsNotEnough() {
        when(quizSetRepository.findByQuizDate(QUIZ_DATE)).thenReturn(Optional.empty());
        when(newsRepository.findQuizCandidates(
                        NewsStatus.PENDING,
                        NewsExplanationStatus.DONE,
                        QUIZ_DATE.atStartOfDay(),
                        QUIZ_DATE.plusDays(1).atStartOfDay(),
                        PageRequest.of(0, 15)))
                .thenReturn(List.of(pendingNews(1L, "뉴스1", "기준금리가 올랐습니다.")));

        generationService.generate(QUIZ_DATE);

        verify(dailyQuizGenerator, never()).generate(any());
        verify(quizSetRepository).saveAndFlush(any(QuizSet.class));
        verify(quizSetRepository).delete(any(QuizSet.class));
        verify(quizSetRepository, never()).save(any());
    }

    @Test
    @DisplayName("AI 결과의 근거 문장이 뉴스 본문에 없으면 UNAVAILABLE 퀴즈 세트를 저장한다")
    void generate_savesUnavailableWhenSourceSentenceIsInvalid() {
        List<News> newsSources = List.of(
                pendingNews(1L, "뉴스1", "기준금리가 올랐습니다. 대출 이자 부담이 커졌습니다."),
                pendingNews(2L, "뉴스2", "코픽스가 상승했습니다. 주택담보대출 금리가 오를 수 있습니다."),
                pendingNews(3L, "뉴스3", "금융당국이 토스를 금융복합기업집단으로 지정했습니다."));

        when(quizSetRepository.findByQuizDate(QUIZ_DATE)).thenReturn(Optional.empty());
        when(newsRepository.findQuizCandidates(
                        NewsStatus.PENDING,
                        NewsExplanationStatus.DONE,
                        QUIZ_DATE.atStartOfDay(),
                        QUIZ_DATE.plusDays(1).atStartOfDay(),
                        PageRequest.of(0, 15)))
                .thenReturn(newsSources);
        when(dailyQuizGenerator.generate(any())).thenReturn(invalidSourceSentenceResult());

        generationService.generate(QUIZ_DATE);

        ArgumentCaptor<QuizSet> captor = ArgumentCaptor.forClass(QuizSet.class);
        verify(quizSetRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(QuizSetStatus.UNAVAILABLE);
    }

    @Test
    @DisplayName("AI 결과의 근거 문장이 해설카드에 있으면 READY 퀴즈 세트를 저장한다")
    void generate_savesReadyWhenSourceSentenceExistsInExplanationCard() {
        List<News> newsSources = List.of(
                pendingNews(1L, "뉴스1", "본문에는 해설카드 문장이 없습니다."),
                pendingNews(2L, "뉴스2", "코픽스가 상승했습니다. 주택담보대출 금리가 오를 수 있습니다."),
                pendingNews(3L, "뉴스3", "금융당국이 토스를 금융복합기업집단으로 지정했습니다."));

        when(quizSetRepository.findByQuizDate(QUIZ_DATE)).thenReturn(Optional.empty());
        when(newsRepository.findQuizCandidates(
                        NewsStatus.PENDING,
                        NewsExplanationStatus.DONE,
                        QUIZ_DATE.atStartOfDay(),
                        QUIZ_DATE.plusDays(1).atStartOfDay(),
                        PageRequest.of(0, 15)))
                .thenReturn(newsSources);
        when(newsExplanationRepository.findTop3ByNewsIdAndStatusOrderByCardOrderAsc(1L, NewsExplanationStatus.DONE))
                .thenReturn(List.of(explanation(1L, 1, "기준금리란?", "기준금리", "기준금리는 중앙은행이 정하는 대표 금리입니다.")));
        when(dailyQuizGenerator.generate(any()))
                .thenReturn(new DailyQuizGenerationResult(List.of(
                        question(1, 1L, "기준금리는 중앙은행이 정하는 대표 금리입니다."),
                        question(2, 2L, "코픽스가 상승했습니다."),
                        question(3, 3L, "금융당국이 토스를 금융복합기업집단으로 지정했습니다."))));

        generationService.generate(QUIZ_DATE);

        ArgumentCaptor<QuizSet> captor = ArgumentCaptor.forClass(QuizSet.class);
        verify(quizSetRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(QuizSetStatus.READY);
    }

    @Test
    @DisplayName("AI 결과가 3문항이 아니면 UNAVAILABLE 퀴즈 세트를 저장한다")
    void generate_savesUnavailableWhenQuestionCountIsInvalid() {
        List<News> newsSources = defaultNewsSources();

        when(quizSetRepository.findByQuizDate(QUIZ_DATE)).thenReturn(Optional.empty());
        when(newsRepository.findQuizCandidates(
                        NewsStatus.PENDING,
                        NewsExplanationStatus.DONE,
                        QUIZ_DATE.atStartOfDay(),
                        QUIZ_DATE.plusDays(1).atStartOfDay(),
                        PageRequest.of(0, 15)))
                .thenReturn(newsSources);
        when(dailyQuizGenerator.generate(any()))
                .thenReturn(new DailyQuizGenerationResult(
                        List.of(question(1, 1L, "기준금리가 올랐습니다."), question(2, 2L, "코픽스가 상승했습니다."))));

        generationService.generate(QUIZ_DATE);

        assertUnavailableQuizSetSaved();
    }

    @Test
    @DisplayName("AI 결과가 같은 뉴스에 여러 문항을 만들면 UNAVAILABLE 퀴즈 세트를 저장한다")
    void generate_savesUnavailableWhenQuestionNewsIsDuplicated() {
        List<News> newsSources = defaultNewsSources();

        when(quizSetRepository.findByQuizDate(QUIZ_DATE)).thenReturn(Optional.empty());
        when(newsRepository.findQuizCandidates(
                        NewsStatus.PENDING,
                        NewsExplanationStatus.DONE,
                        QUIZ_DATE.atStartOfDay(),
                        QUIZ_DATE.plusDays(1).atStartOfDay(),
                        PageRequest.of(0, 15)))
                .thenReturn(newsSources);
        when(dailyQuizGenerator.generate(any()))
                .thenReturn(new DailyQuizGenerationResult(List.of(
                        question(1, 1L, "기준금리가 올랐습니다."),
                        question(2, 1L, "대출 이자 부담이 커졌습니다."),
                        question(3, 3L, "금융당국이 토스를 금융복합기업집단으로 지정했습니다."))));

        generationService.generate(QUIZ_DATE);

        assertUnavailableQuizSetSaved();
    }

    @Test
    @DisplayName("AI 결과의 선택지가 3개가 아니면 UNAVAILABLE 퀴즈 세트를 저장한다")
    void generate_savesUnavailableWhenOptionCountIsInvalid() {
        List<News> newsSources = defaultNewsSources();

        when(quizSetRepository.findByQuizDate(QUIZ_DATE)).thenReturn(Optional.empty());
        when(newsRepository.findQuizCandidates(
                        NewsStatus.PENDING,
                        NewsExplanationStatus.DONE,
                        QUIZ_DATE.atStartOfDay(),
                        QUIZ_DATE.plusDays(1).atStartOfDay(),
                        PageRequest.of(0, 15)))
                .thenReturn(newsSources);
        when(dailyQuizGenerator.generate(any()))
                .thenReturn(new DailyQuizGenerationResult(List.of(
                        question(
                                1,
                                1L,
                                "기준금리가 올랐습니다.",
                                List.of(new DailyQuizOptionResult(1, "보기 1"), new DailyQuizOptionResult(2, "보기 2"))),
                        question(2, 2L, "코픽스가 상승했습니다."),
                        question(3, 3L, "금융당국이 토스를 금융복합기업집단으로 지정했습니다."))));

        generationService.generate(QUIZ_DATE);

        assertUnavailableQuizSetSaved();
    }

    @Test
    @DisplayName("AI 결과의 선택지 순서가 유효하지 않으면 UNAVAILABLE 퀴즈 세트를 저장한다")
    void generate_savesUnavailableWhenOptionOrderIsInvalid() {
        List<News> newsSources = defaultNewsSources();

        when(quizSetRepository.findByQuizDate(QUIZ_DATE)).thenReturn(Optional.empty());
        when(newsRepository.findQuizCandidates(
                        NewsStatus.PENDING,
                        NewsExplanationStatus.DONE,
                        QUIZ_DATE.atStartOfDay(),
                        QUIZ_DATE.plusDays(1).atStartOfDay(),
                        PageRequest.of(0, 15)))
                .thenReturn(newsSources);
        when(dailyQuizGenerator.generate(any()))
                .thenReturn(new DailyQuizGenerationResult(List.of(
                        question(
                                1,
                                1L,
                                "기준금리가 올랐습니다.",
                                List.of(
                                        new DailyQuizOptionResult(1, "보기 1"),
                                        new DailyQuizOptionResult(2, "보기 2"),
                                        new DailyQuizOptionResult(2, "보기 3"))),
                        question(2, 2L, "코픽스가 상승했습니다."),
                        question(3, 3L, "금융당국이 토스를 금융복합기업집단으로 지정했습니다."))));

        generationService.generate(QUIZ_DATE);

        assertUnavailableQuizSetSaved();
    }

    @Test
    @DisplayName("AI 결과가 공백으로 우회한 단순 수치 암기형 문항이면 UNAVAILABLE 퀴즈 세트를 저장한다")
    void generate_savesUnavailableWhenQuestionAsksNumericRecall() {
        List<News> newsSources = defaultNewsSources();

        when(quizSetRepository.findByQuizDate(QUIZ_DATE)).thenReturn(Optional.empty());
        when(newsRepository.findQuizCandidates(
                        NewsStatus.PENDING,
                        NewsExplanationStatus.DONE,
                        QUIZ_DATE.atStartOfDay(),
                        QUIZ_DATE.plusDays(1).atStartOfDay(),
                        PageRequest.of(0, 15)))
                .thenReturn(newsSources);
        when(dailyQuizGenerator.generate(any()))
                .thenReturn(new DailyQuizGenerationResult(List.of(
                        question(1, 1L, "이번 금리 인상은 몇  년 만에 처음 있었던 일인가요?", "기준금리가 올랐습니다."),
                        question(2, 2L, "코픽스가 상승했습니다."),
                        question(3, 3L, "금융당국이 토스를 금융복합기업집단으로 지정했습니다."))));

        generationService.generate(QUIZ_DATE);

        assertUnavailableQuizSetSaved();
    }

    @Test
    @DisplayName("AI 호출 자체가 실패하면 UNAVAILABLE 퀴즈 세트를 저장한다")
    void generate_savesUnavailableWhenGeneratorFails() {
        List<News> newsSources = defaultNewsSources();

        when(quizSetRepository.findByQuizDate(QUIZ_DATE)).thenReturn(Optional.empty());
        when(newsRepository.findQuizCandidates(
                        NewsStatus.PENDING,
                        NewsExplanationStatus.DONE,
                        QUIZ_DATE.atStartOfDay(),
                        QUIZ_DATE.plusDays(1).atStartOfDay(),
                        PageRequest.of(0, 15)))
                .thenReturn(newsSources);
        when(dailyQuizGenerator.generate(any())).thenThrow(new IllegalStateException("OpenAI failed"));

        generationService.generate(QUIZ_DATE);

        assertUnavailableQuizSetSaved();
    }

    @Test
    private void assertUnavailableQuizSetSaved() {
        ArgumentCaptor<QuizSet> captor = ArgumentCaptor.forClass(QuizSet.class);
        verify(quizSetRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(QuizSetStatus.UNAVAILABLE);
    }

    private static List<News> defaultNewsSources() {
        return List.of(
                pendingNews(1L, "뉴스1", "기준금리가 올랐습니다. 대출 이자 부담이 커졌습니다."),
                pendingNews(2L, "뉴스2", "코픽스가 상승했습니다. 주택담보대출 금리가 오를 수 있습니다."),
                pendingNews(3L, "뉴스3", "금융당국이 토스를 금융복합기업집단으로 지정했습니다."));
    }

    private static News pendingNews(Long newsId, String title, String content) {
        News news =
                News.processing(1L, title, "매일경제", LocalDateTime.of(2026, 7, 19, 8, 0), null, "https://news/" + newsId);
        ReflectionTestUtils.setField(news, "id", newsId);
        news.completeReconstruction(title + " 요약", content);
        return news;
    }

    private static News pendingNews(
            Long newsId, Long categoryId, LocalDateTime publishedAt, String content, int termCount) {
        News news = News.processing(categoryId, "뉴스" + newsId, "매일경제", publishedAt, null, "https://news/" + newsId);
        ReflectionTestUtils.setField(news, "id", newsId);
        news.completeReconstruction("뉴스" + newsId + " 요약", content);
        for (long termId = 1; termId <= termCount; termId++) {
            news.addTerm(termId);
        }
        return news;
    }

    private static QuizSet readyQuizSet(LocalDate quizDate) {
        QuizSet quizSet = QuizSet.create(quizDate);
        quizSet.addQuiz(1L, "질문1", "해설1", 100L, 1, "근거 문장1", QuizDifficulty.EASY);
        quizSet.addQuiz(2L, "질문2", "해설2", 100L, 2, "근거 문장2", QuizDifficulty.EASY);
        quizSet.addQuiz(3L, "질문3", "해설3", 100L, 3, "근거 문장3", QuizDifficulty.MEDIUM);
        quizSet.ready();
        return quizSet;
    }

    private static NewsExplanation explanation(
            Long newsId, int cardOrder, String title, String keyTerm, String content) {
        NewsExplanation explanation = NewsExplanation.create(newsId, cardOrder, title, content, keyTerm);
        explanation.complete();
        return explanation;
    }

    private static DailyQuizGenerationResult generationResult() {
        return new DailyQuizGenerationResult(List.of(
                question(1, 1L, "기준금리가 올랐습니다."),
                question(2, 2L, "코픽스가 상승했습니다."),
                question(3, 3L, "금융당국이 토스를 금융복합기업집단으로 지정했습니다.")));
    }

    private static DailyQuizGenerationResult invalidSourceSentenceResult() {
        return new DailyQuizGenerationResult(List.of(
                question(1, 1L, "본문에 없는 문장입니다."),
                question(2, 2L, "코픽스가 상승했습니다."),
                question(3, 3L, "금융당국이 토스를 금융복합기업집단으로 지정했습니다.")));
    }

    private static DailyQuizQuestionResult question(int order, Long newsId, String sourceSentence) {
        return question(order, newsId, "문제 " + order, sourceSentence);
    }

    private static DailyQuizQuestionResult question(
            int order, Long newsId, String questionText, String sourceSentence) {
        return question(
                order,
                newsId,
                questionText,
                sourceSentence,
                List.of(
                        new DailyQuizOptionResult(1, "보기 1"),
                        new DailyQuizOptionResult(2, "보기 2"),
                        new DailyQuizOptionResult(3, "보기 3")));
    }

    private static DailyQuizQuestionResult question(
            int order, Long newsId, String sourceSentence, List<DailyQuizOptionResult> options) {
        return question(order, newsId, "문제 " + order, sourceSentence, options);
    }

    private static DailyQuizQuestionResult question(
            int order, Long newsId, String questionText, String sourceSentence, List<DailyQuizOptionResult> options) {
        return new DailyQuizQuestionResult(
                order, newsId, questionText, options, 1, "해설 " + order, sourceSentence, QuizDifficulty.EASY);
    }
}
