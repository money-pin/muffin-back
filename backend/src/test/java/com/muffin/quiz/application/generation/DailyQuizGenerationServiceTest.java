package com.muffin.quiz.application.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class DailyQuizGenerationServiceTest {

    private static final LocalDate QUIZ_DATE = LocalDate.of(2026, 7, 19);

    private NewsRepository newsRepository;
    private QuizSetRepository quizSetRepository;
    private DailyQuizGenerator dailyQuizGenerator;
    private PlatformTransactionManager transactionManager;
    private DailyQuizGenerationService generationService;

    @BeforeEach
    void setUp() {
        newsRepository = mock(NewsRepository.class);
        quizSetRepository = mock(QuizSetRepository.class);
        dailyQuizGenerator = mock(DailyQuizGenerator.class);
        transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        generationService = new DailyQuizGenerationService(
                newsRepository, quizSetRepository, dailyQuizGenerator, new TransactionTemplate(transactionManager));
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
                        QUIZ_DATE.atStartOfDay(),
                        QUIZ_DATE.plusDays(1).atStartOfDay()))
                .thenReturn(newsSources);
        when(dailyQuizGenerator.generate(any())).thenReturn(generationResult());

        generationService.generate(QUIZ_DATE);

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
    @DisplayName("같은 날짜 퀴즈 생성이 동시에 호출되어도 AI 생성은 한 번만 실행한다")
    void generate_serializesSameDateGeneration() throws Exception {
        List<News> newsSources = defaultNewsSources();
        AtomicBoolean saved = new AtomicBoolean(false);
        CountDownLatch generatorStarted = new CountDownLatch(1);
        CountDownLatch releaseGenerator = new CountDownLatch(1);

        when(quizSetRepository.findByQuizDate(QUIZ_DATE))
                .thenAnswer(invocation -> saved.get() ? Optional.of(QuizSet.create(QUIZ_DATE)) : Optional.empty());
        when(newsRepository.findQuizCandidates(
                        NewsStatus.PENDING,
                        QUIZ_DATE.atStartOfDay(),
                        QUIZ_DATE.plusDays(1).atStartOfDay()))
                .thenReturn(newsSources);
        when(dailyQuizGenerator.generate(any())).thenAnswer(invocation -> {
            generatorStarted.countDown();
            assertThat(releaseGenerator.await(1, TimeUnit.SECONDS)).isTrue();
            return generationResult();
        });
        when(quizSetRepository.save(any())).thenAnswer(invocation -> {
            saved.set(true);
            return invocation.getArgument(0);
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> generationService.generate(QUIZ_DATE));
            assertThat(generatorStarted.await(1, TimeUnit.SECONDS)).isTrue();

            Future<?> second = executor.submit(() -> generationService.generate(QUIZ_DATE));
            releaseGenerator.countDown();

            first.get(1, TimeUnit.SECONDS);
            second.get(1, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        verify(dailyQuizGenerator, times(1)).generate(any());
    }

    @Test
    @DisplayName("재구성 완료 뉴스가 3개보다 적으면 퀴즈 생성을 건너뛰고 AI를 호출하지 않는다")
    void generate_skipsWhenSourceNewsIsNotEnough() {
        when(quizSetRepository.findByQuizDate(QUIZ_DATE)).thenReturn(Optional.empty());
        when(newsRepository.findQuizCandidates(
                        NewsStatus.PENDING,
                        QUIZ_DATE.atStartOfDay(),
                        QUIZ_DATE.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(pendingNews(1L, "뉴스1", "기준금리가 올랐습니다.")));

        generationService.generate(QUIZ_DATE);

        verify(quizSetRepository, never()).save(any());
        verify(dailyQuizGenerator, never()).generate(any());
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
                        QUIZ_DATE.atStartOfDay(),
                        QUIZ_DATE.plusDays(1).atStartOfDay()))
                .thenReturn(newsSources);
        when(dailyQuizGenerator.generate(any())).thenReturn(invalidSourceSentenceResult());

        generationService.generate(QUIZ_DATE);

        ArgumentCaptor<QuizSet> captor = ArgumentCaptor.forClass(QuizSet.class);
        verify(quizSetRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(QuizSetStatus.UNAVAILABLE);
    }

    @Test
    @DisplayName("AI 결과가 3문항이 아니면 UNAVAILABLE 퀴즈 세트를 저장한다")
    void generate_savesUnavailableWhenQuestionCountIsInvalid() {
        List<News> newsSources = defaultNewsSources();

        when(quizSetRepository.findByQuizDate(QUIZ_DATE)).thenReturn(Optional.empty());
        when(newsRepository.findQuizCandidates(
                        NewsStatus.PENDING,
                        QUIZ_DATE.atStartOfDay(),
                        QUIZ_DATE.plusDays(1).atStartOfDay()))
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
                        QUIZ_DATE.atStartOfDay(),
                        QUIZ_DATE.plusDays(1).atStartOfDay()))
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
                        QUIZ_DATE.atStartOfDay(),
                        QUIZ_DATE.plusDays(1).atStartOfDay()))
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
    @DisplayName("AI 결과의 정답 번호에 해당하는 선택지가 없으면 UNAVAILABLE 퀴즈 세트를 저장한다")
    void generate_savesUnavailableWhenCorrectOptionDoesNotExist() {
        List<News> newsSources = defaultNewsSources();

        when(quizSetRepository.findByQuizDate(QUIZ_DATE)).thenReturn(Optional.empty());
        when(newsRepository.findQuizCandidates(
                        NewsStatus.PENDING,
                        QUIZ_DATE.atStartOfDay(),
                        QUIZ_DATE.plusDays(1).atStartOfDay()))
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
    @DisplayName("AI 결과가 단순 수치 암기형 문항이면 UNAVAILABLE 퀴즈 세트를 저장한다")
    void generate_savesUnavailableWhenQuestionAsksNumericRecall() {
        List<News> newsSources = defaultNewsSources();

        when(quizSetRepository.findByQuizDate(QUIZ_DATE)).thenReturn(Optional.empty());
        when(newsRepository.findQuizCandidates(
                        NewsStatus.PENDING,
                        QUIZ_DATE.atStartOfDay(),
                        QUIZ_DATE.plusDays(1).atStartOfDay()))
                .thenReturn(newsSources);
        when(dailyQuizGenerator.generate(any()))
                .thenReturn(new DailyQuizGenerationResult(List.of(
                        question(1, 1L, "이번 금리 인상은 몇 년 만에 처음 있었던 일인가요?", "기준금리가 올랐습니다."),
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
                        QUIZ_DATE.atStartOfDay(),
                        QUIZ_DATE.plusDays(1).atStartOfDay()))
                .thenReturn(newsSources);
        when(dailyQuizGenerator.generate(any())).thenThrow(new IllegalStateException("OpenAI failed"));

        generationService.generate(QUIZ_DATE);

        assertUnavailableQuizSetSaved();
    }

    @Test
    @DisplayName("AI 실패 후 이미 퀴즈 세트가 생성되어 있으면 UNAVAILABLE 저장을 건너뛴다")
    void generate_skipsUnavailableSaveWhenQuizSetWasCreatedConcurrently() {
        List<News> newsSources = defaultNewsSources();
        QuizSet existingQuizSet = QuizSet.create(QUIZ_DATE);

        when(quizSetRepository.findByQuizDate(QUIZ_DATE))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existingQuizSet));
        when(newsRepository.findQuizCandidates(
                        NewsStatus.PENDING,
                        QUIZ_DATE.atStartOfDay(),
                        QUIZ_DATE.plusDays(1).atStartOfDay()))
                .thenReturn(newsSources);
        when(dailyQuizGenerator.generate(any())).thenThrow(new IllegalStateException("OpenAI failed"));

        generationService.generate(QUIZ_DATE);

        verify(quizSetRepository, never()).save(any());
    }

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
