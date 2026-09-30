package com.muffin.briefing.infrastructure.openai;

import com.muffin.briefing.application.exception.BriefingGenerationException;
import com.muffin.briefing.application.generation.BriefingGenerationRequest;
import com.muffin.briefing.application.generation.BriefingGenerationResult;
import com.muffin.briefing.application.generation.BriefingGenerationResult.IssueResult;
import com.muffin.briefing.application.generation.BriefingGenerator;
import com.muffin.briefing.application.generation.BriefingIssueCandidate;
import com.muffin.briefing.application.generation.BriefingSectorLine;
import com.muffin.briefing.application.generation.BriefingTermCandidate;
import com.muffin.briefing.domain.Briefing;
import com.muffin.news.infrastructure.openai.OpenAiClientProperties;
import com.muffin.news.infrastructure.retry.RetryExecutor;
import java.util.ArrayList;
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

/**
 * OpenAI Responses API로 브리핑 문장을 생성한다.
 *
 * <p>한 줄 요약과 이슈 3건을 한 번의 호출로 받는다. 따로 부르면 한 줄 요약이 이슈와 어긋날 수 있고 비용도 늘어난다.
 *
 * <p>후보 뉴스 ID를 JSON Schema의 {@code enum}으로 못박아, 모델이 목록에 없는 뉴스를 지어내는 경우를 응답 단계에서 차단한다.
 */
@Component
@ConditionalOnProperty(name = "muffin.news.ai.enabled", havingValue = "true")
public class OpenAiBriefingGenerator implements BriefingGenerator {

    private static final int MAX_HEADLINE_LENGTH = 40;
    private static final int MAX_ISSUE_TITLE_LENGTH = 20;
    private static final int MAX_ISSUE_SUMMARY_LENGTH = 90;
    private static final int MAX_IMPACT_LINE_LENGTH = 50;
    private static final int MAX_TERM_SUMMARY_LENGTH = 30;

    private static final String INSTRUCTIONS =
            """
            당신은 금융 입문자를 위한 아침 경제 브리핑 편집자다.

            제공된 뉴스 후보 중에서 오늘의 이슈 3건을 고르고, 브리핑 문장을 작성하라.

            [이슈 선정 기준]

            - 주식 시장과 경제 흐름에 미치는 파급력이 큰 순서로 고르라.
            - buzz_topics와 buzz_headlines는 다른 매체들이 오늘 무엇을 크게 다뤘는지 보여주는 참고 자료다.
              여러 매체가 반복해 다룬 주제와 맞닿은 후보를 우선하라.
            - 서로 다른 사건 3건을 고르라. 같은 사건을 다룬 후보가 여러 개면 그중 하나만 고르라.
            - 반드시 후보로 주어진 news_id 중에서만 고르라.

            [headline 작성 규칙]

            - 오늘 경제 흐름 전체를 한 문장으로 요약하라.
            - 40자 이내로 작성하라.
            - 해요체로 끝맺어라.
            - 고른 이슈 3건과 어긋나지 않아야 한다.

            [이슈 title 작성 규칙]

            - 20자 이내로 작성하라.
            - 무슨 일이 있었는지 명사형으로 끝맺어라.
            - 과장어와 낚시성 표현을 쓰지 마라.

            [이슈 summary 작성 규칙]

            - 정확히 2문장으로, 합계 90자 이내로 작성하라.
            - 첫 문장은 무슨 일이 있었는지, 둘째 문장은 왜 그런 일이 생겼는지 쓰라.
            - 해요체로 끝맺어라.

            [이슈 impact_line 작성 규칙]

            - 1문장, 50자 이내로 작성하라.
            - 이 소식이 사용자의 생활, 지갑 또는 관심 섹터와 어떻게 이어지는지 쓰라.
            - 특정 종목이나 상품의 매수, 매도를 권유하지 마라.
            - 수익을 단정하거나 보장하는 표현을 쓰지 마라.

            [오늘의 용어 작성 규칙]

            - term_candidates 중 브리핑 내용과 가장 관련 있는 용어 하나를 고르라.
            - term_summary는 30자 이내로, 용어의 뜻을 쉬운 말로 풀어 쓰라.

            [공통 원칙]

            - 후보 뉴스에 없는 사실, 수치, 전망을 지어내지 마라.
            - sector_lines의 숫자는 이미 계산된 값이다. 이 숫자를 바꾸거나 새로운 수치를 만들지 마라.
            - 결과에 Markdown 문법을 쓰지 마라.
            - 반드시 지정된 JSON 형식으로만 응답하라.
            """;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final OpenAiClientProperties openAiProperties;
    private final BriefingGenerationProperties properties;

    public OpenAiBriefingGenerator(
            @Qualifier("openAiRestClient") RestClient restClient,
            ObjectMapper objectMapper,
            OpenAiClientProperties openAiProperties,
            BriefingGenerationProperties properties) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.openAiProperties = openAiProperties;
        this.properties = properties;
    }

    @Override
    public BriefingGenerationResult generate(BriefingGenerationRequest request) {
        if (request.candidates().size() < Briefing.DAILY_ISSUE_COUNT) {
            throw new BriefingGenerationException(
                    "브리핑 이슈 후보가 부족합니다: " + request.candidates().size());
        }
        String responseBody = requestWithRetry(request);
        return parseResponse(responseBody, request);
    }

    private String requestWithRetry(BriefingGenerationRequest request) {
        try {
            return RetryExecutor.execute(
                    "OpenAI briefing request",
                    request.briefingDate().toString(),
                    "OpenAI briefing retry wait was interrupted",
                    () -> restClient
                            .post()
                            .uri(openAiProperties.endpoint())
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + openAiProperties.apiKey())
                            .body(requestBody(request))
                            .retrieve()
                            .body(String.class),
                    OpenAiBriefingGenerator::isRetryableException);
        } catch (BriefingGenerationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BriefingGenerationException("브리핑 생성 요청에 실패했습니다.", exception);
        }
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

    private Map<String, Object> requestBody(BriefingGenerationRequest request) {
        String input;
        try {
            input = objectMapper.writeValueAsString(inputPayload(request));
        } catch (JacksonException exception) {
            throw new BriefingGenerationException("브리핑 생성 입력 직렬화에 실패했습니다.", exception);
        }

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
                        Map.of("type", "json_schema", "name", "briefing", "strict", true, "schema", schema(request))));
    }

    private static Map<String, Object> inputPayload(BriefingGenerationRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("briefing_date", request.briefingDate().toString());
        payload.put(
                "candidates",
                request.candidates().stream()
                        .map(OpenAiBriefingGenerator::candidatePayload)
                        .toList());
        payload.put(
                "buzz_topics",
                request.buzzSignal().topics().stream()
                        .map(topic -> Map.of(
                                "keyword", topic.keyword(),
                                "mention_count", topic.mentionCount(),
                                "outlet_count", topic.outletCount()))
                        .toList());
        payload.put(
                "buzz_headlines",
                request.buzzSignal().headlines().stream()
                        .map(headline -> Map.of("title", headline.title(), "outlet", headline.outlet()))
                        .toList());
        payload.put(
                "sector_lines",
                request.sectorLines().stream()
                        .map(OpenAiBriefingGenerator::sectorPayload)
                        .toList());
        payload.put(
                "term_candidates",
                request.termCandidates().stream()
                        .map(term -> Map.of(
                                "term_id", term.termId(),
                                "term", term.term(),
                                "content", term.content()))
                        .toList());
        return payload;
    }

    private static Map<String, Object> candidatePayload(BriefingIssueCandidate candidate) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("news_id", candidate.newsId());
        payload.put("title", candidate.title());
        payload.put("summary", candidate.summary());
        payload.put("category", candidate.categoryName());
        payload.put("key_terms", candidate.keyTerms());
        return payload;
    }

    private static Map<String, Object> sectorPayload(BriefingSectorLine line) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sector", line.sectorName());
        payload.put("change_rate", line.changeRate().toPlainString());
        return payload;
    }

    /** 후보 뉴스 ID와 용어 ID를 enum으로 고정해 모델이 목록 밖의 값을 내놓지 못하게 한다. */
    private static Map<String, Object> schema(BriefingGenerationRequest request) {
        List<Long> candidateIds = request.candidates().stream()
                .map(BriefingIssueCandidate::newsId)
                .toList();

        Map<String, Object> issueProperties = new LinkedHashMap<>();
        issueProperties.put("news_id", Map.of("type", "integer", "enum", candidateIds));
        issueProperties.put("title", Map.of("type", "string", "maxLength", MAX_ISSUE_TITLE_LENGTH));
        issueProperties.put("summary", Map.of("type", "string", "maxLength", MAX_ISSUE_SUMMARY_LENGTH));
        issueProperties.put("impact_line", Map.of("type", "string", "maxLength", MAX_IMPACT_LINE_LENGTH));

        Map<String, Object> issueSchema = new LinkedHashMap<>();
        issueSchema.put("type", "object");
        issueSchema.put("properties", issueProperties);
        issueSchema.put("required", List.of("news_id", "title", "summary", "impact_line"));
        issueSchema.put("additionalProperties", false);

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("headline", Map.of("type", "string", "maxLength", MAX_HEADLINE_LENGTH));
        properties.put(
                "issues",
                Map.of(
                        "type",
                        "array",
                        "items",
                        issueSchema,
                        "minItems",
                        Briefing.DAILY_ISSUE_COUNT,
                        "maxItems",
                        Briefing.DAILY_ISSUE_COUNT));

        List<String> required = new ArrayList<>(List.of("headline", "issues"));
        // 용어 후보가 없으면 고를 대상이 없다. strict 스키마는 정의한 키를 모두 required에 넣어야 하므로 아예 뺀다.
        if (!request.termCandidates().isEmpty()) {
            List<Long> termIds = request.termCandidates().stream()
                    .map(BriefingTermCandidate::termId)
                    .toList();
            properties.put("term_id", Map.of("type", "integer", "enum", termIds));
            properties.put("term_summary", Map.of("type", "string", "maxLength", MAX_TERM_SUMMARY_LENGTH));
            required.add("term_id");
            required.add("term_summary");
        }

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return schema;
    }

    private BriefingGenerationResult parseResponse(String responseBody, BriefingGenerationRequest request) {
        try {
            if (responseBody == null || responseBody.isBlank()) {
                throw new IllegalStateException("OpenAI returned an empty response");
            }
            JsonNode response = objectMapper.readTree(responseBody);
            for (JsonNode output : response.path("output")) {
                for (JsonNode content : output.path("content")) {
                    if ("output_text".equals(content.path("type").asText())) {
                        return parseOutputText(content.path("text").asText(), request);
                    }
                }
            }
            throw new IllegalStateException("OpenAI response did not contain output_text");
        } catch (BriefingGenerationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BriefingGenerationException("브리핑 생성 응답을 해석하지 못했습니다.", exception);
        }
    }

    private BriefingGenerationResult parseOutputText(String outputText, BriefingGenerationRequest request)
            throws JacksonException {
        JsonNode result = objectMapper.readTree(outputText);

        String headline = result.path("headline").asText();
        if (headline.isBlank()) {
            throw new BriefingGenerationException("브리핑 headline이 비어 있습니다.");
        }

        Set<Long> candidateIds = new LinkedHashSet<>(request.candidates().stream()
                .map(BriefingIssueCandidate::newsId)
                .toList());

        List<IssueResult> issues = new ArrayList<>();
        Set<Long> usedNewsIds = new LinkedHashSet<>();
        for (JsonNode issue : result.path("issues")) {
            Long newsId = issue.path("news_id").asLong();
            if (!candidateIds.contains(newsId)) {
                throw new BriefingGenerationException("후보에 없는 뉴스를 이슈로 골랐습니다: " + newsId);
            }
            if (!usedNewsIds.add(newsId)) {
                throw new BriefingGenerationException("같은 뉴스를 이슈로 두 번 골랐습니다: " + newsId);
            }
            issues.add(new IssueResult(
                    newsId,
                    issue.path("title").asText(),
                    issue.path("summary").asText(),
                    issue.path("impact_line").asText()));
        }
        if (issues.size() != Briefing.DAILY_ISSUE_COUNT) {
            throw new BriefingGenerationException(
                    "브리핑 이슈는 " + Briefing.DAILY_ISSUE_COUNT + "건이어야 합니다: " + issues.size());
        }

        Long termId = null;
        String termSummary = null;
        if (result.has("term_id")) {
            Long selectedTermId = result.path("term_id").asLong();
            boolean known = request.termCandidates().stream()
                    .anyMatch(candidate -> candidate.termId().equals(selectedTermId));
            if (!known) {
                throw new BriefingGenerationException("후보에 없는 용어를 골랐습니다: " + selectedTermId);
            }
            termId = selectedTermId;
            termSummary = result.path("term_summary").asText();
        }

        return new BriefingGenerationResult(headline, issues, termId, termSummary);
    }
}
