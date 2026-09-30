package com.muffin.briefing.application.generation;

import com.muffin.briefing.application.BriefingProperties;
import com.muffin.briefing.application.generation.BriefingGenerationSummary.Outcome;
import com.muffin.briefing.domain.Briefing;
import com.muffin.briefing.domain.BriefingRepository;
import com.muffin.briefing.domain.enums.BriefingStatus;
import com.muffin.briefing.domain.enums.SectorRankType;
import com.muffin.news.domain.category.Category;
import com.muffin.news.domain.category.CategoryRepository;
import com.muffin.news.domain.explanation.NewsExplanation;
import com.muffin.news.domain.explanation.NewsExplanationRepository;
import com.muffin.news.domain.explanation.enums.NewsExplanationStatus;
import com.muffin.news.domain.news.News;
import com.muffin.news.domain.news.NewsRepository;
import com.muffin.news.domain.news.NewsTerm;
import com.muffin.news.domain.news.enums.NewsStatus;
import com.muffin.news.domain.term.TermDictionaryRepository;
import com.muffin.sector.domain.sector.Sector;
import com.muffin.sector.domain.sector.SectorRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 모닝 브리핑을 생성한다.
 *
 * <p>원칙은 하나다 — 숫자는 계산하고 문장만 AI가 쓴다. 섹터 등락률은 {@link SectorScoreboardCalculator}가 계산해 넘기고,
 * AI는 그 숫자를 바꾸지 않는다.
 *
 * <p>섹터 계산과 화제도 조회는 실패해도 브리핑을 중단시키지 않는다. 둘 다 순위와 맥락을 돕는 재료일 뿐 필수 입력이 아니어서,
 * 외부 신호 장애로 아침 브리핑이 통째로 빠지는 상황을 만들면 안 되기 때문이다.
 *
 * <p>외부 호출(AI, RSS)은 트랜잭션 밖에서 실행한다. 느린 I/O를 기다리는 동안 DB 커넥션을 붙잡지 않기 위한 것으로,
 * {@code DailyQuizGenerationService}가 지키는 규칙과 같다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "muffin.news.ai.enabled", havingValue = "true")
public class BriefingGenerationService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** AI에 넘길 후보 상한. 넓게 보되 프롬프트 크기는 묶어 둔다. */
    private static final int CANDIDATE_LIMIT = 40;

    /**
     * 후보를 찾을 때 거슬러 올라가는 일수. 아침 브리핑은 "간밤"의 소식을 다루는데 발행 시각 기준 당일 기사만 보면 새벽 기사 몇 건밖에
     * 남지 않는다. 전날 기사까지 포함해야 어제와 오늘의 흐름을 다룰 수 있다.
     */
    private static final int CANDIDATE_LOOKBACK_DAYS = 1;

    private static final int MAX_TERM_CANDIDATES = 10;

    private static final List<NewsStatus> CANDIDATE_STATUSES = List.of(NewsStatus.PENDING, NewsStatus.PUBLISHED);

    private final BriefingRepository briefingRepository;
    private final NewsRepository newsRepository;
    private final NewsExplanationRepository newsExplanationRepository;
    private final CategoryRepository categoryRepository;
    private final TermDictionaryRepository termDictionaryRepository;
    private final SectorRepository sectorRepository;
    private final SectorScoreboardCalculator sectorScoreboardCalculator;
    private final BuzzSignalClient buzzSignalClient;
    private final BriefingGenerator briefingGenerator;
    private final BriefingProperties properties;
    private final TransactionTemplate transactionTemplate;

    /** 오늘 날짜 기준으로 브리핑을 생성한다. */
    public BriefingGenerationSummary generateToday() {
        return generate(LocalDate.now(KST));
    }

    /** 지정한 날짜의 브리핑을 생성한다. 이벤트와 재시도 스케줄러가 여러 번 호출할 수 있으므로 멱등하게 동작한다. */
    public BriefingGenerationSummary generate(LocalDate briefingDate) {
        Optional<Briefing> reservation = reserveGeneration(briefingDate);
        if (reservation.isEmpty()) {
            return BriefingGenerationSummary.of(Outcome.ALREADY_RESERVED);
        }
        Long briefingId = reservation.get().getId();

        try {
            List<BriefingIssueCandidate> candidates =
                    transactionTemplate.execute(status -> findCandidates(briefingDate));
            if (candidates.size() < properties.minCandidateNews()) {
                releaseReservation(briefingId);
                log.info(
                        "Briefing generation deferred: briefingDate={} candidates={} required={}",
                        briefingDate,
                        candidates.size(),
                        properties.minCandidateNews());
                return BriefingGenerationSummary.of(Outcome.INSUFFICIENT_NEWS);
            }

            SectorScoreboard scoreboard = calculateScoreboardSafely(briefingDate);
            BuzzSignal buzzSignal = fetchBuzzSignalSafely();

            Map<Long, String> sectorNames = transactionTemplate.execute(status -> sectorNamesById());
            List<BriefingTermCandidate> termCandidates =
                    transactionTemplate.execute(status -> findTermCandidates(candidates));

            BriefingGenerationResult result = briefingGenerator.generate(new BriefingGenerationRequest(
                    briefingDate, candidates, buzzSignal, toSectorLines(scoreboard, sectorNames), termCandidates));

            completeBriefing(briefingId, result, scoreboard);
            return BriefingGenerationSummary.generated(result.issues().size());
        } catch (RuntimeException exception) {
            saveUnavailableSafely(briefingId);
            log.error("Briefing generation failed: briefingDate={}", briefingDate, exception);
            return BriefingGenerationSummary.of(Outcome.FAILED);
        }
    }

    /**
     * 생성 시작 전에 GENERATING 상태의 예약을 먼저 저장한다. briefing_date 유니크 제약이 인스턴스 간 동시 생성을 막는 락 역할을
     * 한다. 이전 실패로 남은 UNAVAILABLE 브리핑은 지우고 다시 예약한다.
     */
    private Optional<Briefing> reserveGeneration(LocalDate briefingDate) {
        try {
            return transactionTemplate.execute(status -> briefingRepository
                    .findByBriefingDate(briefingDate)
                    .map(existing -> reserveAfterExistingCheck(briefingDate, existing))
                    .orElseGet(() -> Optional.of(saveReservation(briefingDate))));
        } catch (DataIntegrityViolationException exception) {
            log.info("Briefing generation skipped: briefingDate={} reservation already exists", briefingDate);
            return Optional.empty();
        }
    }

    private Optional<Briefing> reserveAfterExistingCheck(LocalDate briefingDate, Briefing existing) {
        if (existing.getStatus() != BriefingStatus.UNAVAILABLE) {
            log.info("Briefing generation skipped: briefingDate={} status={}", briefingDate, existing.getStatus());
            return Optional.empty();
        }
        briefingRepository.delete(existing);
        briefingRepository.flush();
        log.info("Briefing unavailable record cleared for retry: briefingDate={}", briefingDate);
        return Optional.of(saveReservation(briefingDate));
    }

    private Briefing saveReservation(LocalDate briefingDate) {
        Briefing briefing = briefingRepository.saveAndFlush(Briefing.create(briefingDate));
        log.info("Briefing generation reserved: briefingDate={}", briefingDate);
        return briefing;
    }

    /** 후보가 아직 모이지 않았으면 다음 이벤트나 재시도가 다시 예약할 수 있도록 예약을 풀어 준다. */
    private void releaseReservation(Long briefingId) {
        transactionTemplate.executeWithoutResult(
                status -> briefingRepository.findById(briefingId).ifPresent(briefingRepository::delete));
    }

    /** 섹터 계산 실패는 성적표 블록만 비우고 넘어간다. */
    private SectorScoreboard calculateScoreboardSafely(LocalDate briefingDate) {
        try {
            return sectorScoreboardCalculator.calculate(briefingDate);
        } catch (RuntimeException exception) {
            log.warn("Sector scoreboard calculation failed: briefingDate={}", briefingDate, exception);
            return SectorScoreboard.empty();
        }
    }

    /** 화제도 조회 실패는 신호 없이 진행한다. 브리핑 실패가 아니다. */
    private BuzzSignal fetchBuzzSignalSafely() {
        try {
            return buzzSignalClient.fetch();
        } catch (RuntimeException exception) {
            log.warn("Buzz signal fetch failed, continuing without it", exception);
            return BuzzSignal.empty();
        }
    }

    /** 당일과 전날 발행된 재구성 완료 뉴스를 후보로 모은다. */
    private List<BriefingIssueCandidate> findCandidates(LocalDate briefingDate) {
        LocalDateTime startInclusive =
                briefingDate.minusDays(CANDIDATE_LOOKBACK_DAYS).atStartOfDay();
        LocalDateTime endExclusive = briefingDate.plusDays(1).atStartOfDay();

        List<News> newsList = newsRepository.findBriefingCandidates(
                CANDIDATE_STATUSES, startInclusive, endExclusive, PageRequest.of(0, CANDIDATE_LIMIT));
        if (newsList.isEmpty()) {
            return List.of();
        }

        Map<Long, String> categoryNames =
                categoryRepository.findAll().stream().collect(Collectors.toMap(Category::getId, Category::getName));
        Map<Long, List<String>> keyTermsByNewsId =
                keyTermsByNewsId(newsList.stream().map(News::getId).toList());

        return newsList.stream()
                .map(news -> new BriefingIssueCandidate(
                        news.getId(),
                        news.getTitle(),
                        news.getSummary(),
                        categoryNames.getOrDefault(news.getCategoryId(), ""),
                        keyTermsByNewsId.getOrDefault(news.getId(), List.of())))
                .toList();
    }

    private Map<Long, List<String>> keyTermsByNewsId(List<Long> newsIds) {
        return newsExplanationRepository.findByNewsIdInAndStatus(newsIds, NewsExplanationStatus.DONE).stream()
                .collect(Collectors.groupingBy(
                        NewsExplanation::getNewsId,
                        Collectors.mapping(NewsExplanation::getKeyTerm, Collectors.toList())));
    }

    /** 후보 뉴스에 연결된 용어를 오늘의 용어 후보로 삼는다. 연결된 용어가 없으면 용어 블록 없이 생성된다. */
    private List<BriefingTermCandidate> findTermCandidates(List<BriefingIssueCandidate> candidates) {
        List<Long> newsIds =
                candidates.stream().map(BriefingIssueCandidate::newsId).toList();

        Set<Long> termIds = new LinkedHashSet<>();
        for (News news : newsRepository.findAllById(newsIds)) {
            news.getTerms().stream().map(NewsTerm::getTermId).forEach(termIds::add);
        }
        if (termIds.isEmpty()) {
            return List.of();
        }

        return termDictionaryRepository.findAllById(termIds).stream()
                .limit(MAX_TERM_CANDIDATES)
                .map(term -> new BriefingTermCandidate(term.getId(), term.getTerm(), term.getContent()))
                .toList();
    }

    private Map<Long, String> sectorNamesById() {
        return sectorRepository.findAll().stream().collect(Collectors.toMap(Sector::getId, Sector::getName));
    }

    private static List<BriefingSectorLine> toSectorLines(SectorScoreboard scoreboard, Map<Long, String> sectorNames) {
        List<BriefingSectorLine> lines = new ArrayList<>();
        scoreboard
                .gainers()
                .forEach(score -> lines.add(
                        new BriefingSectorLine(sectorNames.getOrDefault(score.sectorId(), ""), score.changeRate())));
        scoreboard
                .losers()
                .forEach(score -> lines.add(
                        new BriefingSectorLine(sectorNames.getOrDefault(score.sectorId(), ""), score.changeRate())));
        return lines;
    }

    /** 생성 결과를 한 트랜잭션으로 저장하고 발행 대기 상태로 만든다. */
    private void completeBriefing(Long briefingId, BriefingGenerationResult result, SectorScoreboard scoreboard) {
        transactionTemplate.executeWithoutResult(status -> {
            Briefing briefing = briefingRepository
                    .findById(briefingId)
                    .orElseThrow(() -> new IllegalStateException("Briefing not found: " + briefingId));

            int issueOrder = 1;
            for (BriefingGenerationResult.IssueResult issue : result.issues()) {
                briefing.addIssue(issueOrder++, issue.newsId(), issue.title(), issue.summary(), issue.impactLine());
            }

            int scoreOrder = 1;
            for (SectorScoreboard.SectorScore score : scoreboard.gainers()) {
                briefing.addSectorScore(score.sectorId(), score.changeRate(), SectorRankType.TOP_GAINER, scoreOrder++);
            }
            for (SectorScoreboard.SectorScore score : scoreboard.losers()) {
                briefing.addSectorScore(score.sectorId(), score.changeRate(), SectorRankType.TOP_LOSER, scoreOrder++);
            }

            briefing.ready(result.headline(), result.termId(), result.termSummary());
            briefingRepository.save(briefing);
            log.info(
                    "Briefing generated: briefingDate={} issues={}",
                    briefing.getBriefingDate(),
                    result.issues().size());
        });
    }

    /** 실패한 브리핑을 이용 불가로 저장한다. 이 저장까지 실패해도 다음 재시도가 처리하므로 예외를 밖으로 던지지 않는다. */
    private void saveUnavailableSafely(Long briefingId) {
        try {
            transactionTemplate.executeWithoutResult(status -> briefingRepository
                    .findById(briefingId)
                    .filter(briefing -> briefing.getStatus() != BriefingStatus.PUBLISHED)
                    .ifPresent(briefing -> {
                        briefing.unavailable();
                        briefingRepository.save(briefing);
                    }));
        } catch (RuntimeException exception) {
            log.error("Failed to mark briefing as unavailable: briefingId={}", briefingId, exception);
        }
    }
}
