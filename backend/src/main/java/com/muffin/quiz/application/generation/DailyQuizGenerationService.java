package com.muffin.quiz.application.generation;

import com.muffin.news.domain.explanation.NewsExplanation;
import com.muffin.news.domain.explanation.NewsExplanationRepository;
import com.muffin.news.domain.explanation.enums.NewsExplanationStatus;
import com.muffin.news.domain.news.News;
import com.muffin.news.domain.news.NewsRepository;
import com.muffin.news.domain.news.enums.NewsStatus;
import com.muffin.quiz.application.generation.DailyQuizGenerationSummary.Outcome;
import com.muffin.quiz.domain.quizset.Quiz;
import com.muffin.quiz.domain.quizset.QuizQuestionPolicy;
import com.muffin.quiz.domain.quizset.QuizSet;
import com.muffin.quiz.domain.quizset.QuizSetRepository;
import com.muffin.quiz.domain.quizset.enums.QuizSetStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "muffin.news.ai.enabled", havingValue = "true")
public class DailyQuizGenerationService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int DAILY_QUIZ_COUNT = 3;
    private static final int QUIZ_SOURCE_CANDIDATE_COUNT = 15;
    private static final int OPTION_COUNT = 3;
    private static final long REWARD_MONEY = 100000L;

    private final NewsRepository newsRepository;
    private final NewsExplanationRepository newsExplanationRepository;
    private final QuizSetRepository quizSetRepository;
    private final DailyQuizGenerator dailyQuizGenerator;
    private final TransactionTemplate transactionTemplate;

    /** 오늘 날짜 기준으로 해설카드까지 생성된 발행 대기 뉴스 3개를 골라 하루치 퀴즈를 생성한다. */
    public DailyQuizGenerationSummary generateToday() {
        return generate(LocalDate.now(KST));
    }

    /**
     * 지정한 날짜의 퀴즈 세트를 생성한다.
     *
     * <p>뉴스는 사용자 공개 전이라도 재구성과 해설카드 생성까지 완료된 PENDING 상태를 사용한다. 이벤트 기반으로 여러 번 호출될 수 있으므로
     * 뉴스가 3개 미만이면 아직 생성 시점이 아니라고 보고 건너뛴다. 뉴스 3개가 모인 뒤 생성에 실패하면 조회 API가 UNAVAILABLE로 응답할 수
     * 있도록 빈 퀴즈 세트를 이용 불가 상태로 저장한다.
     */
    public DailyQuizGenerationSummary generate(LocalDate quizDate) {
        Optional<QuizSet> reservation = reserveGeneration(quizDate);
        if (reservation.isEmpty()) {
            return DailyQuizGenerationSummary.of(Outcome.ALREADY_RESERVED);
        }
        QuizSet quizSet = reservation.get();

        try {
            // 외부 API 대기 중 DB 커넥션을 오래 잡지 않도록 OpenAI 호출은 트랜잭션 밖에서 실행한다.
            List<News> newsSources = transactionTemplate.execute(status -> findQuizSourceNews(quizDate));
            if (newsSources.size() < DAILY_QUIZ_COUNT) {
                releaseGenerationReservation(quizSet);
                return DailyQuizGenerationSummary.of(Outcome.INSUFFICIENT_NEWS);
            }

            DailyQuizGenerationRequest request =
                    transactionTemplate.execute(status -> toRequest(quizDate, newsSources));
            DailyQuizGenerationResult result = dailyQuizGenerator.generate(request);
            completeQuizSet(quizSet, newsSources, result);
            return DailyQuizGenerationSummary.generated(result.questions().size());
        } catch (DailyQuizGenerationException exception) {
            saveUnavailableQuizSetSafely(quizSet);
            log.error(
                    "Daily quiz generation validation failed: quizDate={} reason={}",
                    quizDate,
                    exception.getReason(),
                    exception);
            return DailyQuizGenerationSummary.of(Outcome.FAILED);
        } catch (RuntimeException exception) {
            saveUnavailableQuizSetSafely(quizSet);
            log.error("Daily quiz generation failed: quizDate={}", quizDate, exception);
            return DailyQuizGenerationSummary.of(Outcome.FAILED);
        }
    }

    /**
     * 생성 시작 전 GENERATING 상태의 예약 세트를 DB에 먼저 저장한다.
     *
     * <p>quiz_date 유니크 제약이 인스턴스 간 동시 생성을 막는 락 역할을 한다. 이전 실패로 남은 UNAVAILABLE 세트는 삭제 후 다시
     * 예약한다.
     */
    private Optional<QuizSet> reserveGeneration(LocalDate quizDate) {
        try {
            return transactionTemplate.execute(status -> quizSetRepository
                    .findByQuizDate(quizDate)
                    .map(existingQuizSet -> reserveAfterExistingCheck(quizDate, existingQuizSet))
                    .orElseGet(() -> Optional.of(saveGenerationReservation(quizDate))));
        } catch (DataIntegrityViolationException exception) {
            log.info("Daily quiz generation skipped: quizDate={} reservation already exists", quizDate);
            return Optional.empty();
        }
    }

    private Optional<QuizSet> reserveAfterExistingCheck(LocalDate quizDate, QuizSet existingQuizSet) {
        if (existingQuizSet.getStatus() != QuizSetStatus.UNAVAILABLE) {
            log.info("Daily quiz generation skipped: quizDate={} status={}", quizDate, existingQuizSet.getStatus());
            return Optional.empty();
        }

        quizSetRepository.delete(existingQuizSet);
        quizSetRepository.flush();
        log.info("Daily quiz unavailable set cleared for retry: quizDate={}", quizDate);
        return Optional.of(saveGenerationReservation(quizDate));
    }

    private QuizSet saveGenerationReservation(LocalDate quizDate) {
        QuizSet quizSet = QuizSet.create(quizDate);
        quizSetRepository.saveAndFlush(quizSet);
        log.info("Daily quiz generation reserved: quizDate={}", quizDate);
        return quizSet;
    }

    /** 뉴스가 아직 3개 모이지 않은 경우 다음 이벤트나 재시도 스케줄러가 다시 예약할 수 있도록 예약 세트를 해제한다. */
    private void releaseGenerationReservation(QuizSet quizSet) {
        transactionTemplate.executeWithoutResult(status -> {
            quizSetRepository.delete(quizSet);
            quizSetRepository.flush();
        });
    }

    /** 사용자 공개 전이라도 재구성 결과와 DONE 해설카드가 있는 발행 대기 뉴스 후보 중 퀴즈에 적합한 3개를 고른다. */
    private List<News> findQuizSourceNews(LocalDate quizDate) {
        LocalDateTime startInclusive = quizDate.atStartOfDay();
        LocalDateTime endExclusive = quizDate.plusDays(1).atStartOfDay();

        List<News> candidates = newsRepository.findQuizCandidates(
                NewsStatus.PENDING,
                NewsExplanationStatus.DONE,
                startInclusive,
                endExclusive,
                PageRequest.of(0, QUIZ_SOURCE_CANDIDATE_COUNT));

        return selectQuizSourceNews(candidates);
    }

    /**
     * 최신 뉴스만 3개 고르면 같은 주제에 치우칠 수 있어, 해설카드 핵심어와 카테고리 다양성을 우선해 선별한다.
     *
     * <p>1차로 서로 다른 해설카드 핵심어를 가진 뉴스를 고르고, 부족한 경우 카테고리 다양성과 용어 수, 최신순 기준으로 채운다.
     */
    private List<News> selectQuizSourceNews(List<News> candidates) {
        List<News> rankedCandidates = candidates.stream()
                .sorted(Comparator.comparingInt(DailyQuizGenerationService::termCount)
                        .reversed()
                        .thenComparing(News::getPublishedAt, Comparator.reverseOrder()))
                .toList();

        List<News> selectedNews = new ArrayList<>();
        Set<String> selectedExplanationKeyTerms = new HashSet<>();

        for (News news : rankedCandidates) {
            if (selectedNews.size() == DAILY_QUIZ_COUNT) {
                break;
            }
            String keyTerm = primaryExplanationKeyTerm(news.getId());
            if (!keyTerm.isBlank() && selectedExplanationKeyTerms.add(keyTerm)) {
                selectedNews.add(news);
            }
        }

        Set<Long> selectedCategoryIds =
                selectedNews.stream().map(News::getCategoryId).collect(Collectors.toSet());
        for (News news : rankedCandidates) {
            if (selectedNews.size() == DAILY_QUIZ_COUNT) {
                break;
            }
            if (!selectedNews.contains(news) && selectedCategoryIds.add(news.getCategoryId())) {
                selectedNews.add(news);
            }
        }

        for (News news : rankedCandidates) {
            if (selectedNews.size() == DAILY_QUIZ_COUNT) {
                break;
            }
            if (!selectedNews.contains(news)) {
                selectedNews.add(news);
            }
        }

        return selectedNews;
    }

    private String primaryExplanationKeyTerm(Long newsId) {
        return toExplanationCardSources(newsId).stream()
                .map(DailyQuizExplanationCardSource::keyTerm)
                .filter(keyTerm -> keyTerm != null && !keyTerm.isBlank())
                .findFirst()
                .map(DailyQuizGenerationService::normalizeForPhraseCheck)
                .orElse("");
    }

    private static int termCount(News news) {
        return news.getTerms().size();
    }

    private DailyQuizGenerationRequest toRequest(LocalDate quizDate, List<News> newsSources) {
        List<DailyQuizNewsSource> sources = newsSources.stream()
                .map(news -> new DailyQuizNewsSource(
                        news.getId(), news.getTitle(), news.getContent(), toExplanationCardSources(news.getId())))
                .toList();
        return new DailyQuizGenerationRequest(quizDate, sources);
    }

    private List<DailyQuizExplanationCardSource> toExplanationCardSources(Long newsId) {
        return newsExplanationRepository
                .findTop3ByNewsIdAndStatusOrderByCardOrderAsc(newsId, NewsExplanationStatus.DONE)
                .stream()
                .map(DailyQuizGenerationService::toExplanationCardSource)
                .toList();
    }

    private static DailyQuizExplanationCardSource toExplanationCardSource(NewsExplanation explanation) {
        return new DailyQuizExplanationCardSource(
                explanation.getCardOrder(), explanation.getTitle(), explanation.getKeyTerm(), explanation.getContent());
    }

    /** AI 응답을 검증한 뒤 예약해 둔 QuizSet 애그리거트 루트에 문제와 선택지를 구성한다. */
    private void completeQuizSet(QuizSet quizSet, List<News> newsSources, DailyQuizGenerationResult result) {
        transactionTemplate.executeWithoutResult(status -> {
            validateResult(newsSources, result);

            for (DailyQuizQuestionResult question : result.questions()) {
                Quiz quiz = quizSet.addQuiz(
                        question.newsId(),
                        question.questionText(),
                        question.explanation(),
                        REWARD_MONEY,
                        question.order(),
                        question.sourceSentence(),
                        question.difficulty());
                for (DailyQuizOptionResult option : question.options()) {
                    quiz.addOption(option.order(), option.text(), option.order() == question.correctOptionOrder());
                }
            }
            quizSet.ready();
            quizSetRepository.save(quizSet);
        });
    }

    /** 하루 3문항, 뉴스당 1문항이라는 일일 퀴즈 생성 정책을 저장 전에 검증한다. */
    private void validateResult(List<News> newsSources, DailyQuizGenerationResult result) {
        if (result.questions().size() != DAILY_QUIZ_COUNT) {
            throw generationException(DailyQuizGenerationFailureReason.INVALID_QUESTION_COUNT, "일일 퀴즈는 3문항이어야 합니다.");
        }
        if (hasDuplicatedQuestionOrder(result.questions())) {
            throw generationException(DailyQuizGenerationFailureReason.DUPLICATED_QUESTION_ORDER, "퀴즈 문항 순서가 중복되었습니다.");
        }

        Map<Long, News> newsById = newsSources.stream().collect(Collectors.toMap(News::getId, Function.identity()));
        Set<Long> questionNewsIds =
                result.questions().stream().map(DailyQuizQuestionResult::newsId).collect(Collectors.toSet());
        if (!questionNewsIds.equals(newsById.keySet())) {
            throw generationException(
                    DailyQuizGenerationFailureReason.QUESTION_NEWS_MISMATCH, "각 뉴스마다 정확히 1문항씩 생성되어야 합니다.");
        }

        for (DailyQuizQuestionResult question : result.questions()) {
            validateQuestion(newsById.get(question.newsId()), question);
        }
    }

    /** 문항별 선택지 수, 정답 번호, 근거 문장이 올바른지 확인한다. */
    private void validateQuestion(News news, DailyQuizQuestionResult question) {
        if (question.order() < 1 || question.order() > DAILY_QUIZ_COUNT) {
            throw generationException(
                    DailyQuizGenerationFailureReason.INVALID_QUESTION_ORDER, "퀴즈 문항 순서는 1부터 3 사이여야 합니다.");
        }
        if (question.options().size() != OPTION_COUNT) {
            throw generationException(DailyQuizGenerationFailureReason.INVALID_OPTION_COUNT, "퀴즈 선택지는 3개여야 합니다.");
        }
        if (hasInvalidOptionOrder(question.options())) {
            throw generationException(
                    DailyQuizGenerationFailureReason.INVALID_OPTION_ORDER, "퀴즈 선택지 순서는 1부터 3까지 중복 없이 존재해야 합니다.");
        }
        if (question.correctOptionOrder() < 1 || question.correctOptionOrder() > OPTION_COUNT) {
            throw generationException(
                    DailyQuizGenerationFailureReason.INVALID_CORRECT_OPTION_ORDER, "정답 선택지 번호는 1부터 3 사이여야 합니다.");
        }
        if (containsNumericRecallPhrase(question.questionText())) {
            throw generationException(
                    DailyQuizGenerationFailureReason.NUMERIC_RECALL_QUESTION, "단순 수치 암기형 문항은 저장할 수 없습니다.");
        }
        if (question.options().stream().noneMatch(option -> option.order() == question.correctOptionOrder())) {
            throw generationException(
                    DailyQuizGenerationFailureReason.CORRECT_OPTION_NOT_FOUND, "정답 선택지 번호에 해당하는 선택지가 없습니다.");
        }
        if (!containsEvidenceSentence(news, question.sourceSentence())) {
            throw generationException(
                    DailyQuizGenerationFailureReason.SOURCE_SENTENCE_NOT_FOUND,
                    "sourceSentence는 뉴스 본문 또는 해설카드에 존재해야 합니다.");
        }
    }

    private static boolean hasDuplicatedQuestionOrder(List<DailyQuizQuestionResult> questions) {
        return questions.stream()
                        .mapToInt(DailyQuizQuestionResult::order)
                        .distinct()
                        .count()
                != questions.size();
    }

    private static boolean hasInvalidOptionOrder(List<DailyQuizOptionResult> options) {
        Set<Integer> optionOrders =
                options.stream().map(DailyQuizOptionResult::order).collect(Collectors.toSet());
        return optionOrders.size() != OPTION_COUNT || !optionOrders.containsAll(List.of(1, 2, 3));
    }

    private static boolean containsNumericRecallPhrase(String questionText) {
        String normalizedQuestion = normalizeForPhraseCheck(questionText);
        return QuizQuestionPolicy.NUMERIC_RECALL_QUESTION_PHRASES.stream()
                .map(DailyQuizGenerationService::normalizeForPhraseCheck)
                .anyMatch(normalizedQuestion::contains);
    }

    private boolean containsEvidenceSentence(News news, String sourceSentence) {
        String normalizedSourceSentence = normalizeForPhraseCheck(sourceSentence);
        if (normalizedSourceSentence.isEmpty()) {
            return false;
        }
        return normalizeForPhraseCheck(news.getContent()).contains(normalizedSourceSentence)
                || toExplanationCardSources(news.getId()).stream()
                        .map(DailyQuizExplanationCardSource::content)
                        .map(DailyQuizGenerationService::normalizeForPhraseCheck)
                        .anyMatch(content -> content.contains(normalizedSourceSentence));
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private static String normalizeForPhraseCheck(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "");
    }

    private static DailyQuizGenerationException generationException(
            DailyQuizGenerationFailureReason reason, String message) {
        return new DailyQuizGenerationException(reason, message);
    }

    /** AI 생성 실패가 사용자 조회 API에서 명확히 드러나도록 예약 세트를 이용 불가 상태로 저장한다. */
    private void saveUnavailableQuizSetSafely(QuizSet quizSet) {
        try {
            saveUnavailableQuizSet(quizSet);
        } catch (Exception exception) {
            log.warn(
                    "Daily quiz unavailable save failed: quizDate={} quizSetId={}",
                    quizSet.getQuizDate(),
                    quizSet.getId(),
                    exception);
        }
    }

    private void saveUnavailableQuizSet(QuizSet quizSet) {
        transactionTemplate.executeWithoutResult(status -> {
            quizSet.unavailable();
            quizSetRepository.save(quizSet);
        });
    }
}
