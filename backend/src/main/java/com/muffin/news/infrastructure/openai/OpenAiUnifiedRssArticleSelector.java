package com.muffin.news.infrastructure.openai;

import com.muffin.news.application.rss.CategorizedRssArticle;
import com.muffin.news.application.rss.UnifiedRssArticleSelector;
import com.muffin.news.infrastructure.retry.RetryExecutor;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 모든 카테고리 후보를 한 번에 평가해 하루 전체에서 중요도순으로 선별한다.
 *
 * <p>카테고리별 선별({@link OpenAiRssArticleSelector})과 프롬프트가 다르다. 카테고리 안에서의 상대 평가가 아니라 하루 전체를
 * 가로지르는 비교이므로, 후보마다 카테고리를 라벨로 붙여 넘기고 카테고리 균형을 맞추지 말라고 명시한다.
 */
@Component
@ConditionalOnExpression("${muffin.news.ai.enabled:false} and '${muffin.news.ai.unified-selector:OPENAI}' == 'OPENAI'")
public class OpenAiUnifiedRssArticleSelector implements UnifiedRssArticleSelector {

    private static final String INSTRUCTIONS =
            """
            당신은 금융 입문자를 위한 경제 뉴스 편집자다.

            목표:
            오늘 수집된 모든 카테고리의 후보 기사를 함께 비교해, 하루 전체에서 가장 중요한 기사를 지정된 개수만큼 선별하라.
            선별된 기사는 주식 시장 이해, 경제 흐름 학습, 뉴스 재구성, 용어 학습, 퀴즈 생성에 활용된다.

            선별 기준:
            각 후보 기사를 다음 우선순위에 따라 평가하라.

            1순위. 주식 시장에 미치는 파급력
            - 국내외 주식 시장, 주요 산업, 기업 실적, 투자 심리, 금리, 환율, 원자재, 정책, 수급에 영향을 줄 가능성이 높은가?
            - 특정 기업뿐 아니라 업종, 섹터, 시장 전반에 영향을 줄 수 있는 기사일수록 우선한다.

            2순위. 일상과의 연관성
            - 물가, 소비, 대출, 부동산, 세금, 고용, 교통비, 통신비, 생활비처럼 일반 사용자의 생활과 연결되는가?

            3순위. 학습 가치
            - 금리, 환율, 채권, ETF, 물가, 세금, 재정, 무역, 기업 실적, 산업 구조 등 경제·금융 개념을 설명하기 좋은가?

            카테고리 취급 기준:
            - 후보마다 카테고리가 붙어 있지만 이는 참고 정보일 뿐이다.
            - 카테고리별로 개수를 나누거나 균형을 맞추려 하지 마라. 특정 카테고리가 하나도 선택되지 않아도 된다.
            - 중요한 기사가 한 카테고리에 몰려 있으면 그대로 그 카테고리에서 많이 고르라.

            제외 기준:
            다음에 해당하는 기사는 선별하지 마라.
            - 지수나 종목의 등락 결과만 전하는 시황 기사. "코스피 1% 상승", "신고가 경신", "집중매수" 같은
              결과 보도보다 그 움직임의 원인이 된 사건을 고르라.
            - 같은 사건을 반복하는 중복 기사
            - 광고성 기사
            - 선정적이거나 클릭을 유도하는 기사
            - 단순 사건·사고 기사
            - 연예·스포츠 중심 기사
            - 부고, 인사, 포토, 표, 공시 기사
            - 제목만으로 경제 학습 가치가 낮은 기사

            중복 기사 처리 기준:
            - 같은 사건을 다룬 후보가 여러 개 있으면 가장 종합적이고 학습 가치가 높은 기사 1개만 선택하라.
            - 제목에 겹치는 단어가 없어도 같은 사안일 수 있다. 제목이 아니라 다루는 사안이 같은지로 판단하라.
            - 같은 산업이나 같은 나라를 다루더라도 사건이 다르면 서로 다른 기사로 취급하라.

            출력 규칙:
            - 반드시 제공된 후보 URL 중에서만 선택하라.
            - 새로운 URL을 만들지 마라.
            - 지정된 개수를 넘기지 마라. 적합한 기사가 부족하면 가능한 개수만 반환하라.
            - 중요한 순서대로 반환하라.
            - 적합한 기사가 없으면 빈 배열을 반환하라.
            - JSON 외의 설명을 출력하지 마라.

            출력 형식:
            {"selected_urls":["후보 URL 중 하나","후보 URL 중 하나"]}
            """;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final OpenAiClientProperties openAiProperties;
    private final AiSelectionProperties properties;

    public OpenAiUnifiedRssArticleSelector(
            @Qualifier("openAiRestClient") RestClient restClient,
            ObjectMapper objectMapper,
            OpenAiClientProperties openAiProperties,
            AiSelectionProperties properties) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.openAiProperties = openAiProperties;
        this.properties = properties;
    }

    /** 후보 전체를 한 번에 평가하고, 선택된 URL 순서대로 기사를 돌려준다. */
    @Override
    public List<CategorizedRssArticle> select(List<CategorizedRssArticle> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }

        String responseBody = requestSelectionWithRetry(candidates);
        JsonNode response;
        try {
            response = objectMapper.readTree(responseBody);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to parse OpenAI response", exception);
        }

        Set<String> selectedUrls = extractSelectedUrls(response);
        Map<String, CategorizedRssArticle> candidatesByUrl = new LinkedHashMap<>();
        candidates.forEach(
                candidate -> candidatesByUrl.putIfAbsent(candidate.article().url(), candidate));

        // 모델이 중요도순으로 돌려준 순서를 유지한다. 후보에 없는 URL은 버린다.
        return selectedUrls.stream()
                .map(candidatesByUrl::get)
                .filter(java.util.Objects::nonNull)
                .limit(properties.maxTotal())
                .toList();
    }

    private String requestSelectionWithRetry(List<CategorizedRssArticle> candidates) {
        return RetryExecutor.execute(
                "OpenAI unified selection request",
                "all-categories",
                "OpenAI unified selection retry wait was interrupted",
                () -> restClient
                        .post()
                        .uri(openAiProperties.endpoint())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + openAiProperties.apiKey())
                        .body(requestBody(candidates))
                        .retrieve()
                        .body(String.class),
                OpenAiUnifiedRssArticleSelector::isRetryableException);
    }

    private static boolean isRetryableException(RuntimeException exception) {
        return exception instanceof ResourceAccessException
                || exception instanceof RestClientResponseException responseException
                        && isRetryableStatus(responseException.getStatusCode());
    }

    private static boolean isRetryableStatus(HttpStatusCode statusCode) {
        int value = statusCode.value();
        return value == 408 || value == 429 || statusCode.is5xxServerError();
    }

    private Map<String, Object> requestBody(List<CategorizedRssArticle> candidates) {
        List<Map<String, String>> articles = candidates.stream()
                .filter(candidate -> candidate.article().title() != null
                        && candidate.article().url() != null)
                .map(candidate -> Map.of(
                        "category",
                        candidate.category(),
                        "title",
                        candidate.article().title(),
                        "description",
                        candidate.article().description() == null
                                ? ""
                                : candidate.article().description(),
                        "url",
                        candidate.article().url()))
                .toList();

        String input;
        try {
            input = "최대 " + properties.maxTotal() + "건을 중요한 순서대로 선택하라.\n후보: "
                    + objectMapper.writeValueAsString(articles);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to serialize RSS candidates", exception);
        }

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put(
                "properties",
                Map.of(
                        "selected_urls",
                        Map.of("type", "array", "items", Map.of("type", "string"), "maxItems", properties.maxTotal())));
        schema.put("required", List.of("selected_urls"));
        schema.put("additionalProperties", false);

        return Map.of(
                "model",
                properties.model(),
                "instructions",
                INSTRUCTIONS,
                "input",
                input,
                "text",
                Map.of(
                        "format",
                        Map.of(
                                "type",
                                "json_schema",
                                "name",
                                "unified_rss_article_selection",
                                "strict",
                                true,
                                "schema",
                                schema)));
    }

    private Set<String> extractSelectedUrls(JsonNode response) {
        if (response == null) {
            throw new IllegalStateException("OpenAI returned an empty response");
        }
        for (JsonNode output : response.path("output")) {
            for (JsonNode content : output.path("content")) {
                if ("output_text".equals(content.path("type").asText())) {
                    try {
                        JsonNode result =
                                objectMapper.readTree(content.path("text").asText());
                        Set<String> selectedUrls = new LinkedHashSet<>();
                        result.path("selected_urls").forEach(url -> selectedUrls.add(url.asText()));
                        return selectedUrls;
                    } catch (JacksonException exception) {
                        throw new IllegalStateException("Failed to parse OpenAI selection response", exception);
                    }
                }
            }
        }
        throw new IllegalStateException("OpenAI response did not contain output_text");
    }
}
