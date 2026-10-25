package com.muffin.news.infrastructure.jev;

import com.muffin.news.application.rss.CategorizedRssArticle;
import com.muffin.news.application.rss.UnifiedRssArticleSelector;
import com.muffin.news.infrastructure.openai.AiSelectionProperties;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * 구조화 결정 모델로 하루 전체 통합 선별을 수행한다.
 *
 * <p>범용 LLM 선별과 달리 기사를 한 건씩 독립 평가해 점수를 매긴 뒤 코드가 순위를 정한다. 그래서 어떤 기준으로 몇 점을 받아 뽑혔는지
 * 로그로 추적할 수 있고, 가중치와 임계값을 프롬프트 수정 없이 설정으로 조정할 수 있다.
 *
 * <p>단계는 네 개다.
 *
 * <ol>
 *   <li>기사별 평가 — 파급력·일상 연관성·학습 가치·시황 여부·노이즈 여부
 *   <li>버리기 — 노이즈(광고·부고·연예·스포츠·시황표)와 등락 결과만 전하는 시황 기사
 *   <li>순위 — 가중합 내림차순
 *   <li>중복 제거 — 상위권 쌍을 같은 사건인지 물어 묶고 묶음마다 하나만 남김
 * </ol>
 *
 * <p>중복 제거는 기사를 한 건씩 보는 평가로는 불가능해서 쌍 단위로 따로 묻는다. #230 스파이크에서 제목에 겹치는 단어가 전혀 없는
 * 같은 사안(예: "외환위기 청구서 끝난다" / "IMF 특별기여금 종료")을 0.95로 잡아냈다.
 */
@Slf4j
@Component
@ConditionalOnExpression("${muffin.news.ai.enabled:false} and '${muffin.news.ai.unified-selector:OPENAI}' == 'JEV'")
public class JevUnifiedRssArticleSelector implements UnifiedRssArticleSelector {

    /** 기사별 평가 질문. 루브릭 문구는 기존 선별 프롬프트의 선별·제외 기준을 척도로 옮긴 것이다. */
    private static final Map<String, Object> SCORING_QUESTIONS = Map.of(
            "market_impact",
                    Map.of(
                            "type", "score",
                            "instructions", "이 기사가 국내외 주식 시장, 주요 산업, 금리, 환율, 정책에 미치는 파급력은 어느 정도인가?",
                            "criteria",
                                    List.of(
                                            "시장과 무관하거나 개별 사건·사고에 그친다",
                                            "특정 기업 한 곳에만 영향이 있다",
                                            "한 업종이나 섹터 전반에 영향이 있다",
                                            "시장 전반 또는 금리·환율·정책에 영향이 있다")),
            "daily_life",
                    Map.of(
                            "type", "score",
                            "instructions", "이 기사가 일반 사용자의 물가, 소비, 대출, 고용, 생활비와 얼마나 맞닿아 있는가?",
                            "criteria", List.of("일상과 연결점이 없다", "간접적으로 연결된다", "직접적으로 생활·지갑에 영향이 있다")),
            "learning_value",
                    Map.of(
                            "type", "score",
                            "instructions", "금융 입문자에게 경제·금융 개념을 설명하기에 적합한 기사인가?",
                            "criteria", List.of("설명할 개념이 없다", "개념이 있으나 부수적이다", "금리·환율·채권·ETF 같은 개념을 설명하기 좋다")),
            "causality",
                    Map.of(
                            "type", "score",
                            "instructions", "이 기사는 시장이 움직인 결과를 전하는 기사인가, 시장을 움직인 원인이 되는 사건을 다루는 기사인가?",
                            "criteria",
                                    List.of(
                                            "코스피·나스닥 같은 지수나 종목의 등락률, 신고가, 마감 시황 등 결과만 전한다",
                                            "등락 결과를 전하면서 원인을 한두 줄로 덧붙인다",
                                            "금리 결정, 정책 발표, 실적, 경제지표, 지정학 사건처럼 시장을 움직이는 사건 자체를 다룬다",
                                            "그 사건이 왜 일어났고 어디로 번지는지 배경과 파급 경로까지 설명한다")),
            "is_noise",
                    Map.of(
                            "type", "noul",
                            "instructions", "이 기사가 광고성, 부고·인사, 포토, 단순 시황 정리, 연예·스포츠 중심 기사인가?",
                            "criteria",
                                    Map.of(
                                            "true", "광고·홍보, 부고·인사, 포토, 표, 공시, 단순 시황 나열, 연예·스포츠 기사",
                                            "false", "경제·산업·정책의 내용을 담은 기사")));

    private static final Map<String, Object> SAME_EVENT_QUESTION = Map.of(
            "same_event",
            Map.of(
                    "type", "noul",
                    "instructions", "두 기사가 같은 하나의 사건·사안을 다루고 있는가?",
                    "criteria",
                            Map.of(
                                    "true", "같은 사건, 같은 발표, 같은 정책, 같은 통계를 다룬다. 보도 각도나 표현이 달라도 사안이 같으면 참이다.",
                                    "false", "서로 다른 사건이나 사안을 다룬다. 같은 산업이나 같은 나라를 다루더라도 사건이 다르면 거짓이다.")));

    private final JevClient jevClient;
    private final JevProperties properties;
    private final AiSelectionProperties selectionProperties;
    private final ExecutorService executor;

    public JevUnifiedRssArticleSelector(
            JevClient jevClient, JevProperties properties, AiSelectionProperties selectionProperties) {
        this.jevClient = jevClient;
        this.properties = properties;
        this.selectionProperties = selectionProperties;
        this.executor = Executors.newFixedThreadPool(properties.concurrency());
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }

    @Override
    public List<CategorizedRssArticle> select(List<CategorizedRssArticle> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }

        List<ScoredArticle> scored = scoreAll(candidates);
        if (scored.isEmpty()) {
            // 전건 실패는 선별기 장애다. 예외를 던져 전략이 카테고리별 선별로 폴백하게 한다.
            throw new IllegalStateException("Jev scoring failed for every candidate: " + candidates.size());
        }

        List<ScoredArticle> kept = scored.stream()
                .filter(article -> article.noise < properties.noiseThreshold())
                .filter(article -> article.causality >= properties.causalityCutoff())
                .sorted(Comparator.comparingDouble((ScoredArticle article) -> article.total)
                        .reversed())
                .toList();

        log.info(
                "Jev scoring finished: candidates={} scored={} kept={} droppedAsNoise={} droppedAsRecap={}",
                candidates.size(),
                scored.size(),
                kept.size(),
                scored.stream()
                        .filter(article -> article.noise >= properties.noiseThreshold())
                        .count(),
                scored.stream()
                        .filter(article -> article.noise < properties.noiseThreshold()
                                && article.causality < properties.causalityCutoff())
                        .count());

        List<ScoredArticle> deduped = removeDuplicateEvents(kept);
        return deduped.stream()
                .limit(selectionProperties.maxTotal())
                .map(article -> article.candidate)
                .toList();
    }

    /** 기사별 평가를 병렬로 돌린다. 한 건의 실패가 나머지를 버리지 않도록 건별로 막는다. */
    private List<ScoredArticle> scoreAll(List<CategorizedRssArticle> candidates) {
        List<java.util.concurrent.Future<ScoredArticle>> futures = new ArrayList<>();
        for (CategorizedRssArticle candidate : candidates) {
            futures.add(executor.submit(() -> score(candidate)));
        }

        List<ScoredArticle> scored = new ArrayList<>();
        for (java.util.concurrent.Future<ScoredArticle> future : futures) {
            try {
                scored.add(future.get());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Jev scoring was interrupted", exception);
            } catch (Exception exception) {
                log.warn("Jev scoring failed for one candidate", exception);
            }
        }
        return scored;
    }

    private ScoredArticle score(CategorizedRssArticle candidate) {
        Map<String, Object> state = Map.of(
                "category", candidate.category(),
                "title", candidate.article().title(),
                "description",
                        candidate.article().description() == null
                                ? ""
                                : candidate.article().description());

        Map<String, JevAnswer> answers = jevClient.evaluate(candidate.article().url(), state, SCORING_QUESTIONS);

        double marketImpact =
                answers.getOrDefault("market_impact", JevAnswer.empty()).score();
        double dailyLife = answers.getOrDefault("daily_life", JevAnswer.empty()).score();
        double learningValue =
                answers.getOrDefault("learning_value", JevAnswer.empty()).score();
        double causality = answers.getOrDefault("causality", JevAnswer.empty()).score();
        double noise = answers.getOrDefault("is_noise", JevAnswer.empty()).noul();

        // 원인 사건을 파급력과 같은 비중으로 둔다. 등락 결과 보도가 파급력만으로는 걸러지지 않기 때문이다.
        double total = (marketImpact * properties.marketImpactWeight()
                        + causality * properties.marketImpactWeight()
                        + dailyLife * properties.dailyLifeWeight()
                        + learningValue * properties.learningValueWeight())
                * (1 - noise);

        return new ScoredArticle(candidate, causality, noise, total);
    }

    /**
     * 상위권에서 같은 사건을 다룬 기사를 묶어 하나만 남긴다.
     *
     * <p>쌍 수가 건수의 제곱으로 늘어나므로 상위 {@code dedupPoolSize}건만 검사한다. 그 아래는 어차피 선별되지 않는다.
     */
    private List<ScoredArticle> removeDuplicateEvents(List<ScoredArticle> ranked) {
        int poolSize = Math.min(properties.dedupPoolSize(), ranked.size());
        if (poolSize < 2) {
            return ranked;
        }

        List<ScoredArticle> pool = ranked.subList(0, poolSize);
        int[] parent = new int[poolSize];
        for (int index = 0; index < poolSize; index++) {
            parent[index] = index;
        }

        for (int left = 0; left < poolSize; left++) {
            for (int right = left + 1; right < poolSize; right++) {
                if (find(parent, left) == find(parent, right)) {
                    continue; // 이미 같은 묶음이면 물어볼 필요가 없다
                }
                if (isSameEvent(pool.get(left), pool.get(right))) {
                    union(parent, left, right);
                }
            }
        }

        // 묶음마다 점수가 가장 높은 기사만 남긴다. pool은 이미 내림차순이라 먼저 만난 것이 대표다.
        Map<Integer, ScoredArticle> representatives = new LinkedHashMap<>();
        int dropped = 0;
        for (int index = 0; index < poolSize; index++) {
            if (representatives.putIfAbsent(find(parent, index), pool.get(index)) != null) {
                dropped++;
            }
        }

        if (dropped > 0) {
            log.info("Jev duplicate removal: pool={} dropped={}", poolSize, dropped);
        }

        List<ScoredArticle> result = new ArrayList<>(representatives.values());
        result.addAll(ranked.subList(poolSize, ranked.size()));
        return result;
    }

    /** 쌍 판정 실패는 중복 제거를 못 한 것일 뿐이므로, 다른 사건으로 보고 둘 다 남긴다. */
    private boolean isSameEvent(ScoredArticle left, ScoredArticle right) {
        Map<String, Object> state = Map.of(
                "article_a", articleState(left),
                "article_b", articleState(right));
        try {
            Map<String, JevAnswer> answers = jevClient.evaluate("same-event", state, SAME_EVENT_QUESTION);
            return answers.getOrDefault("same_event", JevAnswer.empty()).noul() >= properties.sameEventThreshold();
        } catch (Exception exception) {
            log.warn("Jev same-event check failed, keeping both articles", exception);
            return false;
        }
    }

    private static Map<String, String> articleState(ScoredArticle article) {
        return Map.of(
                "title",
                article.candidate.article().title(),
                "description",
                article.candidate.article().description() == null
                        ? ""
                        : article.candidate.article().description());
    }

    private static int find(int[] parent, int index) {
        while (parent[index] != index) {
            parent[index] = parent[parent[index]];
            index = parent[index];
        }
        return index;
    }

    private static void union(int[] parent, int left, int right) {
        int leftRoot = find(parent, left);
        int rightRoot = find(parent, right);
        if (leftRoot != rightRoot) {
            // 순위가 앞선 쪽(인덱스가 작은 쪽)을 대표로 둬 점수가 높은 기사가 남게 한다.
            parent[Math.max(leftRoot, rightRoot)] = Math.min(leftRoot, rightRoot);
        }
    }

    private record ScoredArticle(CategorizedRssArticle candidate, double causality, double noise, double total) {}
}
