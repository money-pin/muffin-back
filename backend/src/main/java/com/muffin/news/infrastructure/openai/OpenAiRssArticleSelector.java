package com.muffin.news.infrastructure.openai;

import com.muffin.news.application.rss.RssArticle;
import com.muffin.news.application.rss.RssArticleSelector;
import com.muffin.news.infrastructure.retry.RetryExecutor;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(name = "muffin.news.ai-selection.enabled", havingValue = "true")
public class OpenAiRssArticleSelector implements RssArticleSelector {

    /** 금융 입문자용 기사 선별 기준과 JSON 출력 규칙을 정의한다. */
    private static final String INSTRUCTIONS =
            """
            당신은 금융 입문자를 위한 경제 뉴스 편집자다.

            목표:
            입력으로 제공된 현재 카테고리의 후보 기사 중 최대 5개의 기사를 선별하라.
            선별된 기사는 주식 시장 이해, 경제 흐름 학습, 뉴스 재구성, 용어 학습, 퀴즈 생성에 활용된다.

            선별 기준:
            각 후보 기사를 다음 우선순위에 따라 평가하라.

            1순위. 주식 시장에 미치는 파급력
            - 해당 정보가 국내외 주식 시장, 주요 산업, 기업 실적, 투자 심리, 금리, 환율, 원자재, 정책, 수급에 영향을 줄 가능성이 높은가?
            - 특정 기업뿐 아니라 업종, 섹터, 시장 전반에 영향을 줄 수 있는 기사일수록 우선한다.

            2순위. 일상과의 연관성
            - 물가, 소비, 대출, 부동산, 세금, 고용, 교통비, 통신비, 생활비처럼 일반 사용자의 생활과 연결되는가?
            - 금융 입문자가 자신의 일상과 연결해 이해할 수 있는 기사일수록 우선한다.

            3순위. 학습 가치
            - 금리, 환율, 채권, ETF, 물가, 세금, 재정, 무역, 기업 실적, 산업 구조 등 경제·금융 개념을 설명하기 좋은가?
            - 뉴스 재구성, 용어 하이라이트, 퀴즈 생성에 활용하기 좋은 기사일수록 우선한다.

            제외 기준:
            다음에 해당하는 기사는 선별하지 마라.
            - 같은 사건을 반복하는 중복 기사
            - 광고성 기사
            - 선정적이거나 클릭을 유도하는 기사
            - 단순 사건·사고 기사
            - 연예·스포츠 중심 기사
            - 부고, 인사, 포토, 표, 공시, 단순 시황 정리 기사
            - 제목만으로 경제 학습 가치가 낮은 기사

            중복 기사 처리 기준:
            - 같은 사건을 다룬 후보가 여러 개 있으면 가장 종합적이고 학습 가치가 높은 기사 1개만 선택하라.
            - 단순히 제목이 다른 같은 내용의 기사를 여러 개 선택하지 마라.

            출력 규칙:
            - 반드시 제공된 후보 URL 중에서만 선택하라.
            - 새로운 URL을 만들지 마라.
            - 최대 5개를 선택하라.
            - 적합한 기사가 5개보다 부족하면 가능한 개수만 반환하라.
            - 적합한 기사가 없으면 빈 배열을 반환하라.
            - JSON 외의 설명을 출력하지 마라.
            - selected_urls 배열에는 선택한 URL 문자열만 넣어라.

            출력 형식:
            {"selected_urls":["후보 URL 중 하나","후보 URL 중 하나"]}
            """;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AiSelectionProperties properties;

    public OpenAiRssArticleSelector(
            @Qualifier("openAiRestClient") RestClient restClient,
            ObjectMapper objectMapper,
            AiSelectionProperties properties) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /** 제목과 RSS 요약을 OpenAI에 전달하고 선택된 URL에 해당하는 기사만 반환한다. */
    @Override
    public List<RssArticle> select(String category, List<RssArticle> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        String responseBody = requestSelectionWithRetry(category, candidates);
        JsonNode response;
        try {
            response = objectMapper.readTree(responseBody);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to parse OpenAI response", exception);
        }
        Set<String> selectedUrls = extractSelectedUrls(response);
        Map<String, RssArticle> candidatesByUrl = new LinkedHashMap<>();
        candidates.forEach(article -> candidatesByUrl.putIfAbsent(article.url(), article));
        return selectedUrls.stream()
                .map(candidatesByUrl::get)
                .filter(java.util.Objects::nonNull)
                .limit(properties.maxPerCategory())
                .toList();
    }

    /** RSS 후보 선별 요청을 공통 재시도 정책으로 실행한다. */
    private String requestSelectionWithRetry(String category, List<RssArticle> candidates) {
        return RetryExecutor.execute(
                "OpenAI request",
                category,
                "OpenAI retry wait was interrupted",
                () -> restClient
                        .post()
                        .uri(properties.endpoint())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + properties.apiKey())
                        .body(requestBody(category, candidates))
                        .retrieve()
                        .body(String.class),
                OpenAiRssArticleSelector::isRetryableException);
    }

    /** 네트워크 오류 또는 재시도 가능한 OpenAI HTTP 응답인지 확인한다. */
    private static boolean isRetryableException(RuntimeException exception) {
        return exception instanceof ResourceAccessException
                || exception instanceof RestClientResponseException responseException
                        && isRetryableStatus(responseException.getStatusCode());
    }

    /** 재시도 가능한 상태코드인지 검증(408, 429, 5xx) */
    private static boolean isRetryableStatus(HttpStatusCode statusCode) {
        int value = statusCode.value();
        return value == 408 || value == 429 || statusCode.is5xxServerError();
    }

    /** 후보 기사와 selected_urls JSON Schema를 OpenAI Responses API 요청 형식으로 구성한다. */
    private Map<String, Object> requestBody(String category, List<RssArticle> candidates) {
        List<Map<String, String>> articles = candidates.stream()
                .filter(article -> article.title() != null && article.url() != null)
                .map(article -> Map.of(
                        "title", article.title(),
                        "description", article.description() == null ? "" : article.description(),
                        "url", article.url()))
                .toList();
        String input;
        try {
            input = "카테고리: " + category + "\n최대 " + properties.maxPerCategory() + "건을 선택하라.\n후보: "
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
                        Map.of(
                                "type", "array",
                                "items", Map.of("type", "string"),
                                "maxItems", properties.maxPerCategory())));
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
                                "rss_article_selection",
                                "strict",
                                true,
                                "schema",
                                schema)));
    }

    /** 응답에서 선별기사 URL을 추출하며 output_text가 없으면 실패 처리한다. */
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
