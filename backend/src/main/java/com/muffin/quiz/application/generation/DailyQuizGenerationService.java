package com.muffin.quiz.application.generation;

import com.muffin.news.domain.news.News;
import com.muffin.news.domain.news.NewsRepository;
import com.muffin.news.domain.news.enums.NewsStatus;
import com.muffin.quiz.domain.quizset.Quiz;
import com.muffin.quiz.domain.quizset.QuizQuestionPolicy;
import com.muffin.quiz.domain.quizset.QuizSet;
import com.muffin.quiz.domain.quizset.QuizSetRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "muffin.news.ai.enabled", havingValue = "true")
public class DailyQuizGenerationService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int DAILY_QUIZ_COUNT = 3;
    private static final int OPTION_COUNT = 3;
    private static final long REWARD_MONEY = 100000L;

    private final NewsRepository newsRepository;
    private final QuizSetRepository quizSetRepository;
    private final DailyQuizGenerator dailyQuizGenerator;
    private final TransactionTemplate transactionTemplate;
    private final ConcurrentMap<LocalDate, Object> generationLocks = new ConcurrentHashMap<>();

    /** 오늘 날짜 기준으로 발행 대기 뉴스 3개를 골라 하루치 퀴즈를 생성한다. */
    public void generateToday() {
        generate(LocalDate.now(KST));
    }

    /**
     * 지정한 날짜의 퀴즈 세트를 생성한다.
     *
     * <p>뉴스는 사용자 공개 전이라도 재구성까지 완료된 PENDING 상태를 사용한다. 이벤트 기반으로 여러 번 호출될 수 있으므로 뉴스가 3개
     * 미만이면 아직 생성 시점이 아니라고 보고 건너뛴다. 뉴스 3개가 모인 뒤 생성에 실패하면 조회 API가 UNAVAILABLE로 응답할 수 있도록
     * 빈 퀴즈 세트를 이용 불가 상태로 저장한다.
     */
    public void generate(LocalDate quizDate) {
        Object lock = generationLocks.computeIfAbsent(quizDate, ignored -> new Object());
        synchronized (lock) {
            // 날짜별 락 객체를 유지해 대기 중인 호출과 새 호출이 서로 다른 락을 잡는 상황을 막는다.
            generateLocked(quizDate);
        }
    }

    /** 같은 날짜 퀴즈 생성은 하나의 JVM 안에서 순차 실행해 OpenAI 중복 호출과 중복 저장을 방지한다. */
    private void generateLocked(LocalDate quizDate) {
        if (quizSetExists(quizDate)) {
            log.info("Daily quiz generation skipped: quizDate={} already exists", quizDate);
            return;
        }

        // 재구성 이벤트는 뉴스마다 발생하므로, 아직 3개가 모이지 않은 호출은 정상적인 중간 상태로 본다.
        List<News> newsSources = transactionTemplate.execute(status -> findQuizSourceNews(quizDate));
        if (newsSources.size() < DAILY_QUIZ_COUNT) {
            log.info("Daily quiz generation skipped: quizDate={} sourceNewsCount={}", quizDate, newsSources.size());
            return;
        }

        try {
            // 외부 API 대기 중 DB 커넥션을 오래 잡지 않도록 OpenAI 호출은 트랜잭션 밖에서 실행한다.
            DailyQuizGenerationResult result = dailyQuizGenerator.generate(toRequest(quizDate, newsSources));
            QuizSet quizSet = toQuizSet(quizDate, newsSources, result);
            transactionTemplate.executeWithoutResult(status -> quizSetRepository.save(quizSet));
            log.info(
                    "Daily quiz generation completed: quizDate={} questionCount={}",
                    quizDate,
                    result.questions().size());
        } catch (RuntimeException exception) {
            if (quizSetExists(quizDate)) {
                log.info("Daily quiz unavailable save skipped: quizDate={} already exists", quizDate);
            } else {
                saveUnavailableQuizSet(quizDate);
            }
            log.error("Daily quiz generation failed: quizDate={}", quizDate, exception);
        }
    }

    /** 이미 오늘 퀴즈 세트가 있으면 중복 생성하지 않는다. */
    private boolean quizSetExists(LocalDate quizDate) {
        return Boolean.TRUE.equals(transactionTemplate.execute(
                status -> quizSetRepository.findByQuizDate(quizDate).isPresent()));
    }

    /** 사용자 공개 전이라도 재구성 결과가 있는 발행 대기 뉴스 3개를 퀴즈 출처로 사용한다. */
    private List<News> findQuizSourceNews(LocalDate quizDate) {
        LocalDateTime startInclusive = quizDate.atStartOfDay();
        LocalDateTime endExclusive = quizDate.plusDays(1).atStartOfDay();

        return newsRepository.findQuizCandidates(NewsStatus.PENDING, startInclusive, endExclusive).stream()
                .filter(News::hasReconstructionResult)
                .limit(DAILY_QUIZ_COUNT)
                .toList();
    }

    private DailyQuizGenerationRequest toRequest(LocalDate quizDate, List<News> newsSources) {
        List<DailyQuizNewsSource> sources = newsSources.stream()
                .map(news -> new DailyQuizNewsSource(news.getId(), news.getTitle(), news.getContent()))
                .toList();
        return new DailyQuizGenerationRequest(quizDate, sources);
    }

    /** AI 응답을 검증한 뒤 QuizSet 애그리거트 루트를 통해 문제와 선택지를 구성한다. */
    private QuizSet toQuizSet(LocalDate quizDate, List<News> newsSources, DailyQuizGenerationResult result) {
        validateResult(newsSources, result);

        QuizSet quizSet = QuizSet.create(quizDate);
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
        return quizSet;
    }

    /** 하루 3문항, 뉴스당 1문항이라는 일일 퀴즈 생성 정책을 저장 전에 검증한다. */
    private void validateResult(List<News> newsSources, DailyQuizGenerationResult result) {
        if (result.questions().size() != DAILY_QUIZ_COUNT) {
            throw new IllegalStateException("일일 퀴즈는 3문항이어야 합니다.");
        }
        if (hasDuplicatedQuestionOrder(result.questions())) {
            throw new IllegalStateException("퀴즈 문항 순서가 중복되었습니다.");
        }

        Map<Long, News> newsById = newsSources.stream().collect(Collectors.toMap(News::getId, Function.identity()));
        Set<Long> questionNewsIds =
                result.questions().stream().map(DailyQuizQuestionResult::newsId).collect(Collectors.toSet());
        if (!questionNewsIds.equals(newsById.keySet())) {
            throw new IllegalStateException("각 뉴스마다 정확히 1문항씩 생성되어야 합니다.");
        }

        for (DailyQuizQuestionResult question : result.questions()) {
            validateQuestion(newsById.get(question.newsId()), question);
        }
    }

    /** 문항별 선택지 수, 정답 번호, 근거 문장이 올바른지 확인한다. */
    private void validateQuestion(News news, DailyQuizQuestionResult question) {
        if (question.order() < 1 || question.order() > DAILY_QUIZ_COUNT) {
            throw new IllegalStateException("퀴즈 문항 순서는 1부터 3 사이여야 합니다.");
        }
        if (question.options().size() != OPTION_COUNT) {
            throw new IllegalStateException("퀴즈 선택지는 3개여야 합니다.");
        }
        if (hasInvalidOptionOrder(question.options())) {
            throw new IllegalStateException("퀴즈 선택지 순서는 1부터 3까지 중복 없이 존재해야 합니다.");
        }
        if (question.correctOptionOrder() < 1 || question.correctOptionOrder() > OPTION_COUNT) {
            throw new IllegalStateException("정답 선택지 번호는 1부터 3 사이여야 합니다.");
        }
        if (containsNumericRecallPhrase(question.questionText())) {
            throw new IllegalStateException("단순 수치 암기형 문항은 저장할 수 없습니다.");
        }
        if (question.options().stream().noneMatch(option -> option.order() == question.correctOptionOrder())) {
            throw new IllegalStateException("정답 선택지 번호에 해당하는 선택지가 없습니다.");
        }
        if (!news.getContent().contains(question.sourceSentence())) {
            throw new IllegalStateException("sourceSentence는 뉴스 본문에 존재해야 합니다.");
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
        return QuizQuestionPolicy.NUMERIC_RECALL_QUESTION_PHRASES.stream().anyMatch(questionText::contains);
    }

    /** AI 생성 실패가 사용자 조회 API에서 명확히 드러나도록 이용 불가 상태의 빈 퀴즈 세트를 저장한다. */
    private void saveUnavailableQuizSet(LocalDate quizDate) {
        transactionTemplate.executeWithoutResult(status -> {
            QuizSet quizSet = QuizSet.create(quizDate);
            quizSet.unavailable();
            quizSetRepository.save(quizSet);
        });
    }
}
