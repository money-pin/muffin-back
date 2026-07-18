package com.muffin.news.infrastructure.openai;

import com.muffin.news.application.exception.NewsErrorCode;
import com.muffin.news.application.exception.NewsException;
import com.muffin.news.application.explanation.NewsExplanationCardResult;
import com.muffin.news.application.explanation.NewsExplanationGenerationRequest;
import com.muffin.news.application.explanation.NewsExplanationGenerationResult;
import com.muffin.news.application.explanation.NewsExplanationGenerator;
import com.muffin.news.infrastructure.retry.RetryExecutor;
import java.util.LinkedHashMap;
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
@ConditionalOnProperty(name = "muffin.news.ai.enabled", havingValue = "true")
public class OpenAiNewsExplanationGenerator implements NewsExplanationGenerator {

    private static final int MIN_BODY_LENGTH = 150;
    private static final int MAX_BODY_LENGTH = 200;
    private static final int MAX_CARD_COUNT = 3;
    private static final int MAX_GENERATION_ATTEMPTS = 2;
    private static final Set<String> INVESTMENT_ADVICE_KEYWORDS = Set.of(
            "매수",
            "매도",
            "수익 보장",
            "무조건 오른",
            "반드시 오른",
            "투자하세요",
            "추천합니다",
            "고려해야 합니다",
            "유의해야 합니다",
            "염두에 두어야 합니다",
            "꼭 확인해야 합니다");

    private static final String INSTRUCTIONS =
            """
        당신은 금융 입문자를 위한 교육 콘텐츠 전문 작가입니다.
        독자는 금융 지식이 거의 없는 20~30대 직장인입니다.

        [해설 카드 작성 원칙]
        1. 해설카드는 뉴스에 나온 개념을 초보자가 이해하도록 돕는 짧은 경제 상식 카드다.
        2. 제목은 "~이란?", "~는 무엇?" 형식을 사용할 수 있고, 뉴스 흐름이 더 잘 보이면 "왜 ~가 중요할까?" 같은 질문형도 사용할 수 있다.
        3. 본문 첫 문장은 개념을 쉬운 말로 짚되, 이 뉴스에서 왜 등장했는지 함께 설명한다.
        4. 두 번째 문장은 일상 비유나 사례로 이해를 돕는다.
        5. 세 번째 문장은 뉴스 본문 속 사건이 독자의 소비, 저축, 대출, 투자 환경과 어떻게 연결되는지 설명한다.
        6. 경제금융용어 700선 후보 용어는 정확한 개념 선택을 위한 참고 자료로 사용하되, 본문은 뉴스 상황에 맞게 새로 풀어쓴다.
        7. 핵심 단어 1~2개에 **단어** 형식으로 볼드 마크업을 삽입한다.
        8. 잘못된 정의, 과장, 추측을 작성하지 않는다.
        9. 투자 조언, 수익 보장 표현, 사용자에게 특정 행동을 권하는 문장을 포함하지 않는다.
        10. 카드 수는 최소 1개, 최대 3개이며 뉴스 이해에 가장 도움이 되는 개념과 배경을 우선한다.
        11. 설명할 만한 주제가 충분하면 서로 다른 관점의 카드 2~3개를 작성한다.
        12. 각 카드는 개념 자체, 발생 원인, 생활 또는 시장에 이어지는 영향 중 서로 다른 역할을 맡는다.
        13. 한글 단어 중간에 불필요한 공백을 넣지 않는다.
        14. 출력은 반드시 지정된 JSON 형식만 반환한다.
        """;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final OpenAiClientProperties openAiProperties;
    private final NewsExplanationProperties properties;

    public OpenAiNewsExplanationGenerator(
            @Qualifier("openAiRestClient") RestClient restClient,
            ObjectMapper objectMapper,
            OpenAiClientProperties openAiProperties,
            NewsExplanationProperties properties) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.openAiProperties = openAiProperties;
        this.properties = properties;
    }

    /** 재구성된 뉴스 본문과 후보 용어를 OpenAI에 전달하고 해설카드 결과를 반환한다. */
    @Override
    public NewsExplanationGenerationResult generate(NewsExplanationGenerationRequest request) {
        NewsException lastInvalidResponseException = null;

        for (int attempt = 1; attempt <= MAX_GENERATION_ATTEMPTS; attempt++) {
            try {
                String responseBody = requestWithRetry(request, retryInstruction(attempt));
                return parseResponse(responseBody);
            } catch (NewsException exception) {
                if (exception.getErrorCode() != NewsErrorCode.NEWS_EXPLANATION_RESPONSE_INVALID) {
                    throw exception;
                }
                lastInvalidResponseException = exception;
            }
        }

        throw lastInvalidResponseException;
    }

    private String requestWithRetry(NewsExplanationGenerationRequest request, String retryInstruction) {
        try {
            return RetryExecutor.execute(
                    "OpenAI explanation request",
                    String.valueOf(request.newsId()),
                    "OpenAI explanation retry wait was interrupted",
                    () -> restClient
                            .post()
                            .uri(openAiProperties.endpoint())
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + openAiProperties.apiKey())
                            .body(requestBody(request, retryInstruction))
                            .retrieve()
                            .body(String.class),
                    OpenAiNewsExplanationGenerator::isRetryableException);
        } catch (NewsException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new NewsException(NewsErrorCode.NEWS_EXPLANATION_REQUEST_FAILED, exception);
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

    /** 해설 후보 용어와 재구성 본문만 전달해 토큰 사용량을 제한한다. */
    private Map<String, Object> requestBody(NewsExplanationGenerationRequest request, String retryInstruction) {
        String input;

        try {
            input = objectMapper.writeValueAsString(Map.of(
                    "title",
                    request.title(),
                    "summary",
                    request.summary(),
                    "rewritten_body",
                    request.rewrittenBody(),
                    "matched_terms",
                    request.terms()));
        } catch (JacksonException exception) {
            throw new NewsException(NewsErrorCode.NEWS_EXPLANATION_REQUEST_FAILED, exception);
        }

        return Map.of(
                "model",
                properties.model(),
                "instructions",
                INSTRUCTIONS,
                "input",
                prompt(input, retryInstruction),
                "text",
                Map.of(
                        "format",
                        Map.of(
                                "type",
                                "json_schema",
                                "name",
                                "news_explanation_cards",
                                "strict",
                                true,
                                "schema",
                                responseSchema())));
    }

    private String prompt(String input, String retryInstruction) {
        return """
                다음 뉴스를 바탕으로 독자가 알아야 할 경제·금융 배경지식을 해설 카드로 만들어 주세요.

                [뉴스 본문 및 해설 후보 용어]
                %s

                [작성 방향]
                - 해설 후보 용어는 카드 주제를 고르는 참고 자료로 사용한다.
                - 각 카드는 짧은 정의, 쉬운 비유, 뉴스와의 연결이 자연스럽게 이어지도록 작성한다.
                - 제목은 독자가 뉴스를 읽으며 떠올릴 만한 궁금증을 담되, 필요하면 "~이란?", "~는 무엇?" 형식을 사용해도 된다.
                - 본문은 용어 자체보다 이 뉴스에서 그 개념이 왜 중요하게 등장했는지에 무게를 둔다.
                - 예를 들어 "코픽스란?"이라고 쓰더라도, 은행 조달 비용이 주택담보대출 이자로 이어지는 흐름까지 설명한다.
                - 설명할 만한 개념이 2개 이상이면 가능하면 2~3개 카드를 작성한다.
                - 카드 주제는 서로 겹치지 않게 나눈다: 개념의 의미, 변화가 생긴 이유, 소비자나 시장에 이어지는 영향.
                - "고려해야 합니다", "유의해야 합니다", "꼭 염두에 두어야 합니다"처럼 직접 조언하는 문장은 쓰지 않는다.
                - 문장 끝은 "영향을 줄 수 있습니다", "부담으로 이어질 수 있습니다", "안정성 관리와 연결됩니다"처럼 설명형으로 쓴다.
                - key_term은 띄어쓰기나 줄바꿈이 깨지지 않은 자연스러운 핵심어로 작성한다.
                - 각 카드 본문은 160자 이상 190자 이하를 목표로 작성한다.
                - 최종 저장 기준은 150자 이상 200자 이하이므로, 문장을 중간에 끊지 말고 190자 안팎에서 자연스럽게 끝맺는다.
                - 한글 단어 중간에 공백을 넣지 않는다. 예: "코픽 스", "토스 의", "금 융"처럼 쓰지 않는다.
                - body에는 중요한 단어 또는 문장 일부를 **단어** 형식으로 자연스럽게 강조하라.
                - 특정 상품 추천이나 수익을 보장하는 표현은 포함하지 않는다.
                %s

                반드시 아래 JSON 형식으로만 응답하라:
                {
                  "cards": [
                    {
                      "order": 1,
                      "title": "카드 제목",
                      "body": "해설 본문 (150~200자, **볼드** 마크업 포함)",
                      "key_term": "이 카드의 핵심 주제 1개"
                    }
                  ]
                }
                """
                .formatted(input, retryInstruction);
    }

    private String retryInstruction(int attempt) {
        if (attempt == 1) {
            return "";
        }

        return """

                [재생성 지시]
                이전 응답은 JSON 형식, 본문 길이, 또는 투자 조언 금지 조건을 만족하지 못했다.
                모든 카드 body를 160자 이상 190자 이하를 목표로 다시 작성하고, 투자 조언 표현과 행동 권유 문장을 제거하라.
                한글 단어 중간 공백 없이 완결된 문장으로 끝내라.
                """;
    }

    private static Map<String, Object> responseSchema() {
        Map<String, Object> cardSchema = new LinkedHashMap<>();
        cardSchema.put("type", "object");
        cardSchema.put(
                "properties",
                Map.of(
                        "order", Map.of("type", "integer", "minimum", 1, "maximum", MAX_CARD_COUNT),
                        "title", Map.of("type", "string"),
                        "body", Map.of("type", "string", "minLength", MIN_BODY_LENGTH),
                        "key_term", Map.of("type", "string")));
        cardSchema.put("required", List.of("order", "title", "body", "key_term"));
        cardSchema.put("additionalProperties", false);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put(
                "properties",
                Map.of(
                        "cards",
                        Map.of("type", "array", "items", cardSchema, "minItems", 1, "maxItems", MAX_CARD_COUNT)));
        schema.put("required", List.of("cards"));
        schema.put("additionalProperties", false);

        return schema;
    }

    /** Responses API 응답에서 output_text만 꺼내 도메인 결과로 변환한다. */
    private NewsExplanationGenerationResult parseResponse(String responseBody) {
        try {
            if (responseBody == null || responseBody.isBlank()) {
                throw new IllegalStateException("OpenAI returned an empty response");
            }

            JsonNode response = objectMapper.readTree(responseBody);
            for (JsonNode output : response.path("output")) {
                for (JsonNode content : output.path("content")) {
                    if ("output_text".equals(content.path("type").asText())) {
                        return parseOutputText(content.path("text").asText());
                    }
                }
            }
            throw new IllegalStateException("OpenAI response did not contain output_text");
        } catch (RuntimeException exception) {
            throw new NewsException(NewsErrorCode.NEWS_EXPLANATION_RESPONSE_INVALID, exception);
        }
    }

    private NewsExplanationGenerationResult parseOutputText(String outputText) throws JacksonException {
        JsonNode result = objectMapper.readTree(outputText);
        List<NewsExplanationCardResult> cards = result.path("cards")
                .valueStream()
                .map(card -> new NewsExplanationCardResult(
                        card.path("order").asInt(),
                        normalizeText(card.path("title").asText()),
                        normalizeText(card.path("body").asText()),
                        normalizeText(card.path("key_term").asText())))
                .filter(OpenAiNewsExplanationGenerator::isUsableCard)
                .toList();

        if (cards.isEmpty() || cards.size() > MAX_CARD_COUNT) {
            throw new IllegalStateException("OpenAI returned invalid explanation cards");
        }

        return new NewsExplanationGenerationResult(cards);
    }

    private static boolean isUsableCard(NewsExplanationCardResult card) {
        int bodyLength = card.body().length();

        return card.order() >= 1
                && card.order() <= MAX_CARD_COUNT
                && bodyLength >= MIN_BODY_LENGTH
                && bodyLength <= MAX_BODY_LENGTH
                && endsWithCompleteSentence(card.body())
                && INVESTMENT_ADVICE_KEYWORDS.stream().noneMatch(keyword -> contains(card, keyword));
    }

    private static boolean contains(NewsExplanationCardResult card, String keyword) {
        return card.title().contains(keyword) || card.body().contains(keyword);
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private static boolean endsWithCompleteSentence(String value) {
        return value.endsWith(".")
                || value.endsWith("!")
                || value.endsWith("?")
                || value.endsWith("다")
                || value.endsWith("요")
                || value.endsWith("죠");
    }
}
