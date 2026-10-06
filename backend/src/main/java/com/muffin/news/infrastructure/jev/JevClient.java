package com.muffin.news.infrastructure.jev;

import com.muffin.news.infrastructure.retry.RetryExecutor;
import java.util.LinkedHashMap;
import java.util.Map;
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
 * 구조화 결정 모델 호출 클라이언트.
 *
 * <p>평가 대상({@code state})과 이름이 붙은 질문 맵을 보내면 질문마다 타입이 정해진 답을 돌려준다. 문자열을 생성하지 않으므로 목록에
 * 없는 값이 나올 수 없고, 답마다 확률과 신뢰도가 함께 온다.
 */
@Component
@ConditionalOnExpression("${muffin.news.ai.enabled:false} and '${muffin.news.ai.unified-selector:OPENAI}' == 'JEV'")
public class JevClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final JevProperties properties;

    public JevClient(
            @Qualifier("jevRestClient") RestClient restClient, ObjectMapper objectMapper, JevProperties properties) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * 평가 대상 하나에 대해 여러 질문을 한 번에 묻는다.
     *
     * @param label 재시도 로그에 남길 식별자
     * @param state 평가 대상. 문자열이나 맵 모두 가능하다
     * @param questions 질문 이름 → 질문 정의
     * @return 질문 이름 → 답변
     */
    public Map<String, JevAnswer> evaluate(String label, Object state, Map<String, Object> questions) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("state", state);
        body.put("model", properties.model());
        body.put("questions", questions);

        String responseBody = RetryExecutor.execute(
                "Jev evaluation request",
                label,
                "Jev retry wait was interrupted",
                () -> restClient
                        .post()
                        .uri(properties.endpoint())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + properties.apiKey())
                        .body(body)
                        .retrieve()
                        .body(String.class),
                JevClient::isRetryableException);

        return parseAnswers(responseBody);
    }

    /** 429(레이트리밋)와 529(과부하)는 문서가 지수 백오프를 권한다. 네트워크 오류와 5xx도 재시도 대상이다. */
    private static boolean isRetryableException(RuntimeException exception) {
        return exception instanceof ResourceAccessException
                || exception instanceof RestClientResponseException responseException
                        && isRetryableStatus(responseException.getStatusCode());
    }

    private static boolean isRetryableStatus(HttpStatusCode statusCode) {
        int value = statusCode.value();
        return value == 429 || value == 529 || statusCode.is5xxServerError();
    }

    private Map<String, JevAnswer> parseAnswers(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new IllegalStateException("Jev returned an empty response");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(responseBody);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to parse Jev response", exception);
        }

        JsonNode answers = root.path("answers");
        if (answers.isMissingNode() || !answers.isObject()) {
            throw new IllegalStateException("Jev response did not contain answers");
        }

        Map<String, JevAnswer> parsed = new LinkedHashMap<>();
        answers.propertyStream().forEach(entry -> {
            JsonNode answer = entry.getValue();
            parsed.put(
                    entry.getKey(),
                    new JevAnswer(
                            answer.path("type").asString(""),
                            answer.path("score").asDouble(0),
                            answer.path("noul").asDouble(0),
                            answer.path("confidence").asDouble(0)));
        });
        return parsed;
    }
}
