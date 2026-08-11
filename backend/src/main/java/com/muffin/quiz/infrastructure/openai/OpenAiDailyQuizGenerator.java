package com.muffin.quiz.infrastructure.openai;

import com.muffin.news.infrastructure.openai.OpenAiClientProperties;
import com.muffin.news.infrastructure.retry.RetryExecutor;
import com.muffin.quiz.application.generation.DailyQuizGenerationRequest;
import com.muffin.quiz.application.generation.DailyQuizGenerationResult;
import com.muffin.quiz.application.generation.DailyQuizGenerator;
import com.muffin.quiz.application.generation.DailyQuizNewsSource;
import com.muffin.quiz.application.generation.DailyQuizOptionResult;
import com.muffin.quiz.application.generation.DailyQuizQuestionResult;
import com.muffin.quiz.domain.quizset.QuizQuestionPolicy;
import com.muffin.quiz.domain.quizset.enums.QuizDifficulty;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
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
public class OpenAiDailyQuizGenerator implements DailyQuizGenerator {

    private static final int QUESTION_COUNT = 3;
    private static final int OPTION_COUNT = 3;
    private static final int MAX_GENERATION_ATTEMPTS = 3;
    private static final Set<String> FORBIDDEN_QUESTION_PHRASES = Set.of(
            "오늘 뉴스에 나온",
            "오늘 뉴스에서",
            "뉴스에서 언급된",
            "이 뉴스에서",
            "기사에 따르면",
            "이 기사에서",
            "뉴스 본문을 보면",
            "본문에 따르면",
            "투자해야",
            "베팅",
            "유리할까요",
            "추천",
            "사야 할까요",
            "팔아야 할까요",
            "오를까요",
            "내릴까요");
    private static final String INSTRUCTIONS =
            """
        당신은 경제·금융 교육 퀴즈를 출제하는 전문가입니다.
        퀴즈 수강자는 금융에 관심을 가지기 시작한 20~30대입니다.

        [퀴즈 출제 원칙]
        1. 뉴스 1개당 1문항씩 총 3문항을 만든다.
        2. 뉴스를 읽고 학습한 사용자가 경제·금융 의미를 이해했는지 확인하는 문항을 만든다.
        3. 단순 기사 사실 암기보다 뉴스 속 개념, 원인·결과, 영향, 위험, 제도·지표의 역할을 묻는다.
        4. 날짜·기간·금액·비율 같은 숫자 자체를 맞히게 하지 않는다.
        5. 투자 판단, 의견, 예측, 섹터 선택, 부정형("아닌 것은?") 문항은 금지한다.
        6. 정답은 경제·금융 학습에 의미 있는 용어명, 지표명, 제도명, 상품명, 개념명 또는 뉴스의 경제적 의미를 담은 구체 표현이어야 한다.
        7. 정답은 지나치게 넓은 추상어, 정답을 풀어쓴 설명구, 단순 상태 변화 표현이 아니어야 한다.
        8. 해설카드의 key_term, title, content를 우선 참고하되, 뉴스 본문과 함께 판단한다.
        9. source_sentence는 문항의 근거가 되는 해설카드 content 또는 뉴스 본문 문장 그대로 쓴다.
        10. 정답과 explanation은 해설카드 content 또는 뉴스 본문 전체 맥락에서 자연스럽게 도출되어야 한다.
        11. question_text에는 정답 선택지 문구를 그대로 쓰지 않는다.
        12. "오늘 뉴스에 나온", "본문에 따르면"처럼 본문을 읽었다는 전제 표현은 쓰지 않는다.
        13. explanation은 source_sentence를 반복하지 말고, 정답의 의미와 뉴스 맥락을 3~4문장으로 설명한다.
        14. 세 문항의 question_topic은 서로 겹치지 않게 작성한다.
        15. 정답 위치는 1, 2, 3번에 분산하고, JSON 형식만 반환한다.
        """;
    private static final String FALLBACK_INSTRUCTIONS =
            """
        당신은 경제·금융 교육 퀴즈를 출제하는 전문가입니다.
        퀴즈 수강자는 금융에 관심을 가지기 시작한 20~30대입니다.

        [최종 fallback 출제 원칙]
        1. 뉴스 1개당 1문항씩 총 3문항을 만든다.
        2. rewritten_body를 읽고 학습한 사용자가 경제·금융 의미를 이해했는지 확인하는 문항을 만든다.
        3. 단순 기사 사실 암기보다 뉴스 속 개념, 원인·결과, 영향, 위험, 제도·지표의 역할을 묻는다.
        4. 날짜·기간·금액·비율 같은 숫자 자체를 맞히게 하지 않는다.
        5. 투자 판단, 의견, 예측, 섹터 선택, 부정형("아닌 것은?") 문항은 금지한다.
        6. 정답은 rewritten_body 전체 맥락에서 자연스럽게 도출되는 용어명, 지표명, 제도명, 상품명, 개념명 또는 뉴스의 경제적 의미를 담은 구체 표현이어야 한다.
        7. 정답은 지나치게 넓은 추상어, 정답을 풀어쓴 설명구, 단순 상태 변화 표현이 아니어야 한다.
        8. source_sentence는 뉴스 본문 문장 그대로 쓴다.
        9. question_text에는 정답 선택지 문구를 그대로 쓰지 않는다.
        10. "오늘 뉴스에 나온", "본문에 따르면"처럼 본문을 읽었다는 전제 표현은 쓰지 않는다.
        11. explanation은 source_sentence를 반복하지 말고, 정답의 의미와 뉴스 맥락을 3~4문장으로 설명한다.
        12. 세 문항의 question_topic은 서로 겹치지 않게 작성한다.
        13. 정답 위치는 1, 2, 3번에 분산하고, JSON 형식만 반환한다.
        """;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final OpenAiClientProperties openAiProperties;
    private final DailyQuizGenerationProperties properties;

    public OpenAiDailyQuizGenerator(
            @Qualifier("openAiRestClient") RestClient restClient,
            ObjectMapper objectMapper,
            OpenAiClientProperties openAiProperties,
            DailyQuizGenerationProperties properties) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.openAiProperties = openAiProperties;
        this.properties = properties;
    }

    /** 재구성 완료 뉴스 3개와 해설카드를 OpenAI에 전달하고 일일 퀴즈 생성 결과를 반환한다. */
    @Override
    public DailyQuizGenerationResult generate(DailyQuizGenerationRequest request) {
        InvalidDailyQuizResponseException lastInvalidResponseException = null;

        for (int attempt = 1; attempt <= MAX_GENERATION_ATTEMPTS; attempt++) {
            try {
                boolean fallbackAttempt = isFallbackAttempt(attempt);
                String responseBody = requestWithRetry(request, retryInstruction(attempt), fallbackAttempt);
                return parseResponse(request, responseBody, fallbackAttempt);
            } catch (InvalidDailyQuizResponseException exception) {
                lastInvalidResponseException = exception;
            }
        }

        throw lastInvalidResponseException;
    }

    private String requestWithRetry(
            DailyQuizGenerationRequest request, String retryInstruction, boolean fallbackAttempt) {
        try {
            return RetryExecutor.execute(
                    "OpenAI daily quiz request",
                    request.quizDate().toString(),
                    "OpenAI daily quiz retry wait was interrupted",
                    () -> restClient
                            .post()
                            .uri(openAiProperties.endpoint())
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + openAiProperties.apiKey())
                            .body(requestBody(request, retryInstruction, fallbackAttempt))
                            .retrieve()
                            .body(String.class),
                    OpenAiDailyQuizGenerator::isRetryableException);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IllegalStateException("OpenAI daily quiz request failed", exception);
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

    /** 재구성 본문과 해설카드를 함께 전달해 해설카드 중심으로 퀴즈 주제와 근거를 고른다. */
    private Map<String, Object> requestBody(
            DailyQuizGenerationRequest request, String retryInstruction, boolean fallbackAttempt) {
        String input;

        try {
            input = objectMapper.writeValueAsString(Map.of(
                    "quiz_date",
                    request.quizDate().toString(),
                    "news",
                    request.newsSources().stream()
                            .map(source -> toNewsInput(source, fallbackAttempt))
                            .toList()));
        } catch (JacksonException exception) {
            throw new IllegalStateException("OpenAI daily quiz request body serialization failed", exception);
        }

        return Map.of(
                "model",
                properties.model(),
                "instructions",
                fallbackAttempt ? FALLBACK_INSTRUCTIONS : INSTRUCTIONS,
                "input",
                prompt(input, retryInstruction, fallbackAttempt),
                "text",
                Map.of(
                        "format",
                        Map.of(
                                "type",
                                "json_schema",
                                "name",
                                "daily_quiz",
                                "strict",
                                true,
                                "schema",
                                responseSchema())));
    }

    private static Map<String, Object> toNewsInput(DailyQuizNewsSource source, boolean fallbackAttempt) {
        if (fallbackAttempt) {
            return Map.of("title", source.title(), "rewritten_body", source.rewrittenBody());
        }
        return Map.of(
                "title",
                source.title(),
                "rewritten_body",
                source.rewrittenBody(),
                "explanation_cards",
                toExplanationCardInputs(source));
    }

    private String prompt(String input, String retryInstruction, boolean fallbackAttempt) {
        String sourceDescription = fallbackAttempt ? "뉴스 본문" : "뉴스 본문과 해설카드";
        String sourceReferenceRule = fallbackAttempt
                ? "rewritten_body만 참고해 학습 가치가 높은 경제·금융 개념을 고른다."
                : "해설카드의 key_term, title, content를 우선 참고하되, key_term을 무조건 정답으로 쓰지 않는다.";
        String sourceSentenceRule = fallbackAttempt
                ? "source_sentence는 rewritten_body 안에 실제로 존재하는 문장 그대로 작성한다."
                : "source_sentence는 explanation_cards.content 또는 rewritten_body 안에 실제로 존재하는 문장 그대로 작성한다.";
        String answerDerivationRule = fallbackAttempt
                ? "정답과 explanation은 source_sentence 한 문장에만 갇히지 않고, rewritten_body 전체 맥락에서 자연스럽게 도출 가능해야 한다."
                : "정답과 explanation은 source_sentence 한 문장에만 갇히지 않고, 해설카드 content 또는 뉴스 본문 전체 맥락에서 자연스럽게 도출 가능해야 한다.";
        String sourceSentenceDescription =
                fallbackAttempt ? "본문에서 정답의 근거가 된 문장 원문 그대로" : "해설카드 또는 본문에서 정답의 근거가 된 문장 원문 그대로";
        return """
                다음 3개의 %s를 바탕으로 퀴즈 3문항을 출제해 주세요.

                [뉴스 입력]
                %s

                [작성 규칙]
                - 각 뉴스에서 반드시 1문항씩, 총 3문항을 출제하라.
                - related_news_title에는 입력으로 받은 뉴스 제목을 그대로 작성하라.
                - 뉴스를 읽고 학습한 사용자가 경제·금융 의미를 이해했는지 확인하는 문항으로 작성한다.
                - 기사에 나온 숫자, 날짜, 금액, 비율 자체를 맞히게 하지 말고 그 변화의 의미, 원인·결과, 영향, 위험, 제도·지표의 역할을 묻는다.
                - 정답은 경제·금융 학습에 의미 있는 용어명, 지표명, 제도명, 상품명, 개념명 또는 뉴스의 경제적 의미를 담은 구체 표현으로 작성한다.
                - 정답은 지나치게 넓은 추상어, 정답을 풀어쓴 설명구, 단순 상태 변화 표현이 아니어야 한다.
                - 해설카드나 본문에 명확한 경제·금융 용어, 지표, 제도, 상품명이 있으면 그 명칭을 정답 후보로 우선 검토한다.
                - %s
                - question_text에는 정답 선택지 문구를 그대로 쓰지 않는다.
                - question_topic에는 문항의 핵심 개념을 짧은 명사형으로 작성하고, 세 문항의 question_topic은 서로 달라야 한다.
                - 오답은 정답과 같은 세부 유형의 그럴듯한 용어·지표·제도·개념으로 작성하고, 경제·금융과 무관한 단어를 넣지 않는다.
                - 오답에는 다른 문항의 정답 후보나 question_topic을 재사용하지 않는다.
                - source_sentence는 문항의 출처가 되는 근거 문장으로 사용한다.
                - %s
                - %s
                - source_sentence가 숫자나 사건을 포함하더라도, 질문은 그 숫자 자체가 아니라 경제적 의미를 묻는다.
                - explanation은 source_sentence를 그대로 반복하지 말고, 정답의 의미와 뉴스 맥락을 3~4문장으로 풀어쓴다.
                - 출처 전제 표현("오늘 뉴스에 나온", "기사에 따르면", "본문에 따르면" 등), 부정형("아닌 것은?"), 투자 판단, 가격 전망, 섹터 선택은 금지한다.
                - 단순히 날짜, 기간, 수치, 금액, 비율 자체를 맞히는 문제는 만들지 않는다.
                - 정답 번호는 1, 2, 3번에 가능하면 한 번씩 분산하라.
                - 한글 단어 중간에 불필요한 공백을 넣지 않는다.
                %s

                반드시 아래 JSON 형식으로만 응답하라:
                {
                  "questions": [
                    {
                      "order": 1,
                      "related_news_title": "출처 뉴스 제목",
                      "question_text": "문제 텍스트",
                      "question_topic": "문항 핵심 주제",
                      "options": [
                        { "order": 1, "text": "보기1" },
                        { "order": 2, "text": "보기2" },
                        { "order": 3, "text": "보기3" }
                      ],
                      "correct_option_order": 1,
                      "explanation": "해설 텍스트",
                      "source_sentence": "%s",
                      "difficulty": "EASY"
                    }
                  ]
                }
                """
                .formatted(
                        sourceDescription,
                        input,
                        sourceReferenceRule,
                        sourceSentenceRule,
                        answerDerivationRule,
                        retryInstruction,
                        sourceSentenceDescription);
    }

    private String retryInstruction(int attempt) {
        if (attempt == 1) {
            return "";
        }
        if (attempt == MAX_GENERATION_ATTEMPTS) {
            return """

                    [최종 재생성 지시: 재구성 본문 기반 fallback]
                    앞선 해설카드 기반 응답이 검증 조건을 만족하지 못했다.
                    이번 시도에서는 해설카드가 아니라 rewritten_body를 주 근거로 사용해 각 뉴스에서 정확히 1문항씩 다시 만들어라.
                    rewritten_body를 읽고 학습한 사용자가 경제·금융 의미를 이해했는지 확인하는 문항으로 작성하라.
                    날짜·기간·금액·비율 같은 숫자 자체를 맞히게 하지 말고, 그 변화의 의미, 원인·결과, 영향, 위험, 제도·지표의 역할을 묻는다.
                    source_sentence는 반드시 rewritten_body 안에 실제로 존재하는 문장 그대로 써라.
                    정답과 explanation은 source_sentence 한 문장에만 갇히지 않고, rewritten_body 전체 맥락에서 자연스럽게 도출 가능해야 한다.
                    출처 전제 표현, 부정형, 투자 판단, 숫자 암기형, 숫자만 바꾼 선택지는 금지한다.
                    세 문항의 question_topic은 서로 다르게 작성하고, 같은 용어 또는 같은 개념을 반복하지 마라.
                    question_text에는 정답 선택지 문구를 그대로 쓰지 마라.
                    정답은 경제·금융 학습에 의미 있는 용어명, 지표명, 제도명, 상품명, 개념명 또는 뉴스의 경제적 의미를 담은 구체 표현으로 작성하라.
                    정답은 지나치게 넓은 추상어, 정답을 풀어쓴 설명구, 단순 상태 변화 표현이 아니어야 한다.
                    rewritten_body에 명확한 경제·금융 용어, 지표, 제도, 상품명이 있으면 그 명칭을 정답 후보로 우선 검토하라.
                    정책·제도·국제 이슈 문항은 단순 목적 확인보다 경제적 의미나 시장 영향으로 연결해 묻는다.
                    오답은 source_sentence에 없어도 되지만, 정답과 같은 세부 유형의 그럴듯한 용어·지표·제도·개념으로 작성하라.
                    오답에는 다른 문항의 정답 후보나 question_topic을 재사용하지 마라.
                    explanation은 source_sentence를 그대로 반복하지 말고, 정답의 의미와 뉴스 맥락을 3~4문장으로 풀어써라.
                    """;
        }

        return """

                [재생성 지시]
                이전 응답은 JSON 형식, 문항 수, 선택지 수, source_sentence, 문항 유형, 또는 투자 조언 금지 조건을 만족하지 못했다.
                각 뉴스에서 정확히 1문항씩 다시 만들고, 뉴스를 읽고 학습한 사용자가 경제·금융 의미를 이해했는지 확인하는 문항으로 작성하라.
                기사에 나온 숫자, 날짜, 금액, 비율 자체를 맞히게 하지 말고 그 변화의 의미, 원인·결과, 영향, 위험, 제도·지표의 역할을 묻는다.
                정답 선택지 문구를 question_text에 그대로 쓰지 마라.
                정답은 경제·금융 학습에 의미 있는 용어명, 지표명, 제도명, 상품명, 개념명 또는 뉴스의 경제적 의미를 담은 구체 표현으로 작성하라.
                정답은 지나치게 넓은 추상어, 정답을 풀어쓴 설명구, 단순 상태 변화 표현이 아니어야 한다.
                입력에 명확한 경제·금융 용어, 지표, 제도, 상품명이 있으면 그 명칭을 정답 후보로 우선 검토하라.
                정책·제도·국제 이슈 문항은 단순 목적 확인보다 경제적 의미나 시장 영향으로 연결해 묻는다.
                출처 전제 표현, 부정형, 투자 판단, 숫자 암기형, 숫자만 바꾼 선택지는 금지한다.
                세 문항의 question_topic은 서로 다르게 작성하고, 같은 용어 또는 같은 개념을 반복하지 마라.
                정답과 explanation은 source_sentence 한 문장에만 갇히지 않고, 입력 전체 맥락에서 자연스럽게 도출 가능해야 한다.
                오답은 source_sentence에 없어도 되지만, 정답과 같은 세부 유형의 그럴듯한 용어·지표·제도·개념으로 작성하라.
                오답에는 다른 문항의 정답 후보나 question_topic을 재사용하지 마라.
                source_sentence는 해설카드 content 또는 본문 문장 그대로 쓴다.
                explanation은 source_sentence를 그대로 반복하지 말고, 정답의 의미와 뉴스 맥락을 3~4문장으로 풀어써라.
                """;
    }

    private static boolean isFallbackAttempt(int attempt) {
        return attempt == MAX_GENERATION_ATTEMPTS;
    }

    private static List<Map<String, Object>> toExplanationCardInputs(DailyQuizNewsSource source) {
        return source.explanationCards().stream()
                .map(card -> Map.<String, Object>of(
                        "order", card.order(),
                        "title", card.title(),
                        "key_term", card.keyTerm(),
                        "content", card.content()))
                .toList();
    }

    private static Map<String, Object> responseSchema() {
        Map<String, Object> optionSchema = new LinkedHashMap<>();
        optionSchema.put("type", "object");
        optionSchema.put(
                "properties",
                Map.of(
                        "order", Map.of("type", "integer", "minimum", 1, "maximum", OPTION_COUNT),
                        "text", Map.of("type", "string")));
        optionSchema.put("required", List.of("order", "text"));
        optionSchema.put("additionalProperties", false);

        Map<String, Object> questionSchema = new LinkedHashMap<>();
        questionSchema.put("type", "object");
        questionSchema.put(
                "properties",
                Map.of(
                        "order", Map.of("type", "integer", "minimum", 1, "maximum", QUESTION_COUNT),
                        "related_news_title", Map.of("type", "string"),
                        "question_text", Map.of("type", "string"),
                        "question_topic", Map.of("type", "string"),
                        "options",
                                Map.of(
                                        "type",
                                        "array",
                                        "items",
                                        optionSchema,
                                        "minItems",
                                        OPTION_COUNT,
                                        "maxItems",
                                        OPTION_COUNT),
                        "correct_option_order", Map.of("type", "integer", "minimum", 1, "maximum", OPTION_COUNT),
                        "explanation", Map.of("type", "string"),
                        "source_sentence", Map.of("type", "string"),
                        "difficulty", Map.of("type", "string", "enum", List.of("EASY", "MEDIUM"))));
        questionSchema.put(
                "required",
                List.of(
                        "order",
                        "related_news_title",
                        "question_text",
                        "question_topic",
                        "options",
                        "correct_option_order",
                        "explanation",
                        "source_sentence",
                        "difficulty"));
        questionSchema.put("additionalProperties", false);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put(
                "properties",
                Map.of(
                        "questions",
                        Map.of(
                                "type",
                                "array",
                                "items",
                                questionSchema,
                                "minItems",
                                QUESTION_COUNT,
                                "maxItems",
                                QUESTION_COUNT)));
        schema.put("required", List.of("questions"));
        schema.put("additionalProperties", false);

        return schema;
    }

    private DailyQuizGenerationResult parseResponse(
            DailyQuizGenerationRequest request, String responseBody, boolean fallbackAttempt) {
        try {
            if (responseBody == null || responseBody.isBlank()) {
                throw new IllegalStateException("OpenAI returned an empty response");
            }

            JsonNode response = objectMapper.readTree(responseBody);
            for (JsonNode output : response.path("output")) {
                for (JsonNode content : output.path("content")) {
                    if ("output_text".equals(content.path("type").asText())) {
                        return parseOutputText(request, content.path("text").asText(), fallbackAttempt);
                    }
                }
            }
            throw new IllegalStateException("OpenAI response did not contain output_text");
        } catch (RuntimeException exception) {
            throw new InvalidDailyQuizResponseException(exception);
        }
    }

    private DailyQuizGenerationResult parseOutputText(
            DailyQuizGenerationRequest request, String outputText, boolean fallbackAttempt) throws JacksonException {
        Map<String, DailyQuizNewsSource> sourceByTitle = request.newsSources().stream()
                .collect(Collectors.toMap(source -> normalizeText(source.title()), Function.identity()));

        List<DailyQuizQuestionResult> questions = objectMapper
                .readTree(outputText)
                .path("questions")
                .valueStream()
                .map(question -> toQuestionResult(sourceByTitle, question))
                .toList();

        validateQuestions(questions, sourceByTitle, fallbackAttempt);

        return new DailyQuizGenerationResult(questions);
    }

    private DailyQuizQuestionResult toQuestionResult(
            Map<String, DailyQuizNewsSource> sourceByTitle, JsonNode question) {
        String relatedNewsTitle =
                normalizeText(question.path("related_news_title").asText());
        DailyQuizNewsSource source = sourceByTitle.get(relatedNewsTitle);
        if (source == null) {
            throw new IllegalStateException("OpenAI returned unknown related_news_title");
        }

        List<DailyQuizOptionResult> options = question.path("options")
                .valueStream()
                .map(option -> new DailyQuizOptionResult(
                        option.path("order").asInt(),
                        normalizeText(option.path("text").asText())))
                .toList();

        return new DailyQuizQuestionResult(
                question.path("order").asInt(),
                source.newsId(),
                normalizeText(question.path("question_text").asText()),
                options,
                question.path("correct_option_order").asInt(),
                normalizeText(question.path("explanation").asText()),
                normalizeText(question.path("source_sentence").asText()),
                normalizeText(question.path("question_topic").asText()),
                QuizDifficulty.valueOf(question.path("difficulty").asText()));
    }

    private void validateQuestions(
            List<DailyQuizQuestionResult> questions,
            Map<String, DailyQuizNewsSource> sourceByTitle,
            boolean fallbackAttempt) {
        if (questions.size() != QUESTION_COUNT || hasDuplicatedQuestionOrder(questions)) {
            throw new IllegalStateException("OpenAI returned invalid question count or duplicated order");
        }

        Map<Long, DailyQuizNewsSource> sourceById = sourceByTitle.values().stream()
                .collect(Collectors.toMap(DailyQuizNewsSource::newsId, Function.identity()));
        Set<Long> questionNewsIds =
                questions.stream().map(DailyQuizQuestionResult::newsId).collect(Collectors.toSet());
        if (!questionNewsIds.equals(sourceById.keySet())) {
            throw new IllegalStateException("OpenAI must generate one question per news");
        }
        if (hasDuplicatedQuestionTopic(questions)) {
            throw new IllegalStateException("OpenAI returned duplicated question_topic");
        }

        for (DailyQuizQuestionResult question : questions) {
            validateQuestion(question, sourceById.get(question.newsId()), fallbackAttempt);
        }
    }

    private void validateQuestion(
            DailyQuizQuestionResult question, DailyQuizNewsSource source, boolean fallbackAttempt) {
        if (isBlank(question.questionText())
                || isBlank(question.explanation())
                || isBlank(question.sourceSentence())
                || isBlank(question.questionTopic())
                || containsForbiddenQuestionPhrase(question.questionText())) {
            throw new IllegalStateException("OpenAI returned invalid quiz question");
        }
        if (!containsEvidenceSentence(source, question.sourceSentence(), fallbackAttempt)) {
            throw new IllegalStateException("OpenAI returned source_sentence that is not in quiz evidence");
        }
        if (question.options().size() != OPTION_COUNT || hasDuplicatedOptionOrder(question.options())) {
            throw new IllegalStateException("OpenAI returned invalid quiz options");
        }
        if (question.correctOptionOrder() < 1 || question.correctOptionOrder() > OPTION_COUNT) {
            throw new IllegalStateException("OpenAI returned invalid correct option order");
        }
        if (question.options().stream().anyMatch(option -> isBlank(option.text()))) {
            throw new IllegalStateException("OpenAI returned blank quiz option");
        }
        if (isSourceSentenceRepeatedAsExplanation(question)) {
            throw new IllegalStateException("OpenAI returned explanation that repeats source_sentence");
        }
    }

    private static boolean hasDuplicatedQuestionOrder(List<DailyQuizQuestionResult> questions) {
        return questions.stream()
                        .mapToInt(DailyQuizQuestionResult::order)
                        .distinct()
                        .count()
                != questions.size();
    }

    private static boolean hasDuplicatedOptionOrder(List<DailyQuizOptionResult> options) {
        return options.stream()
                        .mapToInt(DailyQuizOptionResult::order)
                        .distinct()
                        .count()
                != options.size();
    }

    private static boolean hasDuplicatedQuestionTopic(List<DailyQuizQuestionResult> questions) {
        return questions.stream()
                        .map(DailyQuizQuestionResult::questionTopic)
                        .map(OpenAiDailyQuizGenerator::normalizeForPhraseCheck)
                        .distinct()
                        .count()
                != questions.size();
    }

    private static boolean isSourceSentenceRepeatedAsExplanation(DailyQuizQuestionResult question) {
        String normalizedSource = normalizeForPhraseCheck(question.sourceSentence());
        String normalizedExplanation = normalizeForPhraseCheck(question.explanation());
        return !normalizedSource.isBlank()
                && (normalizedExplanation.equals(normalizedSource)
                        || normalizedSource.contains(normalizedExplanation)
                        || normalizedExplanation.contains(normalizedSource));
    }

    private static boolean containsEvidenceSentence(
            DailyQuizNewsSource source, String sourceSentence, boolean fallbackAttempt) {
        String normalizedSourceSentence = normalizeForPhraseCheck(sourceSentence);
        if (normalizedSourceSentence.isBlank()) {
            return false;
        }
        if (normalizeForPhraseCheck(source.rewrittenBody()).contains(normalizedSourceSentence)) {
            return true;
        }
        return !fallbackAttempt
                && source.explanationCards().stream()
                        .map(card -> normalizeForPhraseCheck(card.content()))
                        .anyMatch(content -> content.contains(normalizedSourceSentence));
    }

    private static boolean containsForbiddenQuestionPhrase(String questionText) {
        String normalizedQuestion = normalizeForPhraseCheck(questionText);
        return FORBIDDEN_QUESTION_PHRASES.stream()
                        .map(OpenAiDailyQuizGenerator::normalizeForPhraseCheck)
                        .anyMatch(normalizedQuestion::contains)
                || QuizQuestionPolicy.NUMERIC_RECALL_QUESTION_PHRASES.stream()
                        .map(OpenAiDailyQuizGenerator::normalizeForPhraseCheck)
                        .anyMatch(normalizedQuestion::contains);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private static String normalizeForPhraseCheck(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "");
    }

    private static class InvalidDailyQuizResponseException extends IllegalStateException {

        InvalidDailyQuizResponseException(Throwable cause) {
            super("OpenAI daily quiz response is invalid", cause);
        }
    }
}
