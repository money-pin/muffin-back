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
    private static final int MAX_GENERATION_ATTEMPTS = 2;
    private static final Set<String> FORBIDDEN_QUESTION_PHRASES =
            Set.of("투자해야", "베팅", "유리할까요", "추천", "사야 할까요", "팔아야 할까요", "오를까요", "내릴까요");

    private static final String INSTRUCTIONS =
            """
        당신은 경제·금융 교육 퀴즈를 출제하는 전문가입니다.
        퀴즈 수강자는 금융에 관심을 가지기 시작한 20~30대입니다.

        [퀴즈 출제 원칙]
        1. 문항은 반드시 제공된 뉴스 본문에서만 근거를 가져온다. 외부 지식으로 출제하지 않는다.
        2. 정답은 본문에 명확하게 서술된 사실이어야 한다.
        3. 오답 보기는 그럴듯하지만 본문에서 틀린 것으로 확인 가능해야 한다.
        4. 뉴스 1개당 1문항만 출제한다. 뉴스 3개면 총 3문항이다.
        5. 정답 위치가 항상 특정 번호에 편중되지 않도록 1, 2, 3번에 분산한다.
        6. source_sentence에는 정답의 근거가 된 뉴스 본문의 정확한 한 문장을 그대로 넣는다.
        7. question_text의 정답은 source_sentence 한 문장만 읽어도 판단 가능해야 한다.
        8. explanation은 source_sentence에서 확인되는 사실만 설명한다.
        9. 본문에 없는 수치, 날짜, 기업명, 기관명을 만들지 않는다.
        10. 투자 판단, 의견, 예측을 묻는 문항을 만들지 않는다.
        11. 문항 유형은 용어형과 뉴스 연결형을 섞는다. 최소 1문항은 용어형으로 만든다.
        12. "아닌 것은?", "틀린 것은?"처럼 부정형으로 묻는 문항은 피한다.
        13. 단순 날짜, 기간, 수치, 금액만 맞히는 문항은 만들지 않는다.
        14. "몇 년 만인가요?", "금리는 얼마인가요?", "자산은 몇 조 원인가요?"처럼 숫자 자체가 정답인 문항은 금지한다.
        15. 정답 선택지는 숫자나 기간만 다르게 바꾼 보기로 구성하지 않는다.
        16. source_sentence가 수치 문장이어도 문항은 경제 개념, 변화 방향, 원인과 영향의 의미를 묻는다.
        17. 오답 보기는 정답과 같은 범주의 보기로 만든다. 너무 엉뚱한 범주나 쉽게 배제되는 보기는 만들지 않는다.
        18. question_text는 앱 화면에 어울리는 자연스러운 말투로 작성한다.
        19. 출력은 반드시 지정된 JSON 형식만 반환한다.
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

    /** 재구성 완료 뉴스 3개를 OpenAI에 전달하고 일일 퀴즈 생성 결과를 반환한다. */
    @Override
    public DailyQuizGenerationResult generate(DailyQuizGenerationRequest request) {
        InvalidDailyQuizResponseException lastInvalidResponseException = null;

        for (int attempt = 1; attempt <= MAX_GENERATION_ATTEMPTS; attempt++) {
            try {
                String responseBody = requestWithRetry(request, retryInstruction(attempt));
                return parseResponse(request, responseBody);
            } catch (InvalidDailyQuizResponseException exception) {
                lastInvalidResponseException = exception;
            }
        }

        throw lastInvalidResponseException;
    }

    private String requestWithRetry(DailyQuizGenerationRequest request, String retryInstruction) {
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
                            .body(requestBody(request, retryInstruction))
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

    /** 재구성 본문과 뉴스 제목만 전달해 본문 밖 정보로 출제될 가능성을 줄인다. */
    private Map<String, Object> requestBody(DailyQuizGenerationRequest request, String retryInstruction) {
        String input;

        try {
            input = objectMapper.writeValueAsString(Map.of(
                    "quiz_date",
                    request.quizDate().toString(),
                    "news",
                    request.newsSources().stream()
                            .map(source -> Map.of(
                                    "title", source.title(),
                                    "rewritten_body", source.rewrittenBody()))
                            .toList()));
        } catch (JacksonException exception) {
            throw new IllegalStateException("OpenAI daily quiz request body serialization failed", exception);
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
                                "daily_quiz",
                                "strict",
                                true,
                                "schema",
                                responseSchema())));
    }

    private String prompt(String input, String retryInstruction) {
        return """
                다음 3개의 뉴스 본문을 바탕으로 퀴즈 3문항을 출제해 주세요.

                [뉴스 입력]
                %s

                [작성 규칙]
                - 각 뉴스에서 반드시 1문항씩, 총 3문항을 출제하라.
                - related_news_title에는 입력으로 받은 뉴스 제목을 그대로 작성하라.
                - question_text는 초보자가 뉴스 속 경제 개념이나 뉴스와 연결된 흐름을 이해하도록 작성하라.
                - 3문항 중 최소 1문항은 용어형으로 작성하라. 예: "오늘 뉴스에 나온 코픽스는 무엇을 뜻할까요?"
                - 3문항 중 가능하면 1문항은 뉴스 연결형으로 작성하라. 예: "이 뉴스와 가장 직접적으로 연결된 업종/금융 영역은 무엇일까요?"
                - 뉴스 연결형은 본문에 나온 업종, 금융 영역, 정책 효과만 다룬다. 투자할 섹터를 고르게 하지 않는다.
                - "아닌 것은?", "틀린 것은?"처럼 오답을 고르는 부정형 문항은 만들지 않는다.
                - 단순히 날짜, 기간, 수치, 금액만 맞히는 문제는 만들지 않는다.
                - "몇 년 만인가요?", "금리는 얼마인가요?", "자산은 몇 조 원인가요?"처럼 숫자 자체가 정답인 문제는 금지한다.
                - 정답 선택지를 "1년/3년/5년", "3조/10조/41조"처럼 숫자만 바꾼 보기로 만들지 않는다.
                - 수치가 필요하다면 그 수치 자체보다 경제적 의미, 변화 방향, 원인과 영향을 묻게 하라.
                - 오답 보기는 정답과 같은 범주의 보기로 만든다. 예를 들어 정책이면 다른 정책/상태, 금융 지표면 다른 금융 지표, 기업 지정 사유면 다른 지정 사유를 보기로 둔다.
                - 오답 보기에 뉴스 주제와 무관한 엉뚱한 범주를 넣지 않는다. 예: 금융복합기업집단 문항에 "무역 회사로 지정됨" 같은 보기 금지.
                - 오답은 너무 쉽게 탈락하지 않도록 자연스럽게 작성하되, 본문을 보면 틀렸다고 판단 가능해야 한다.
                - question_text는 딱딱한 시험 문장보다 앱 화면에 자연스럽게 들어갈 말투로 작성하라.
                - source_sentence 필드에는 정답의 근거가 된 뉴스 본문의 정확한 문장을 그대로 포함하라.
                - question_text의 정답은 source_sentence 한 문장만 보고도 고를 수 있어야 한다.
                - explanation은 source_sentence에 직접 포함된 사실을 쉬운 말로 풀어쓴다.
                - 여러 문장을 합쳐야만 답이 되는 질문은 만들지 않는다.
                - 오답 보기는 본문 내용을 바탕으로 틀렸다고 판단할 수 있어야 한다.
                - 투자 판단, 매수·매도 판단, 가격 전망, 미래 가능성 예측을 묻지 않는다.
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
                      "options": [
                        { "order": 1, "text": "보기1" },
                        { "order": 2, "text": "보기2" },
                        { "order": 3, "text": "보기3" }
                      ],
                      "correct_option_order": 1,
                      "explanation": "해설 텍스트",
                      "source_sentence": "본문에서 정답의 근거가 된 문장 원문 그대로",
                      "difficulty": "EASY"
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
                이전 응답은 JSON 형식, 문항 수, 선택지 수, source_sentence, 문항 유형, 또는 투자 조언 금지 조건을 만족하지 못했다.
                각 뉴스에서 정확히 1문항씩 다시 만들고, 최소 1문항은 용어형으로 작성하라.
                날짜, 기간, 금액, 비율처럼 숫자 자체를 맞히는 문항은 만들지 말고, 경제 개념이나 뉴스 속 변화의 의미를 묻는 문항으로 작성하라.
                정답 선택지를 숫자만 다르게 바꾼 보기로 구성하지 마라.
                오답 보기는 정답과 같은 범주 안에서 자연스럽게 다시 작성하라.
                source_sentence는 뉴스 본문에 존재하는 한 문장을 그대로 복사하라.
                정답과 해설은 source_sentence 한 문장만으로 확인 가능한 내용으로 다시 작성하라.
                """;
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

    private DailyQuizGenerationResult parseResponse(DailyQuizGenerationRequest request, String responseBody) {
        try {
            if (responseBody == null || responseBody.isBlank()) {
                throw new IllegalStateException("OpenAI returned an empty response");
            }

            JsonNode response = objectMapper.readTree(responseBody);
            for (JsonNode output : response.path("output")) {
                for (JsonNode content : output.path("content")) {
                    if ("output_text".equals(content.path("type").asText())) {
                        return parseOutputText(request, content.path("text").asText());
                    }
                }
            }
            throw new IllegalStateException("OpenAI response did not contain output_text");
        } catch (RuntimeException exception) {
            throw new InvalidDailyQuizResponseException(exception);
        }
    }

    private DailyQuizGenerationResult parseOutputText(DailyQuizGenerationRequest request, String outputText)
            throws JacksonException {
        Map<String, DailyQuizNewsSource> sourceByTitle = request.newsSources().stream()
                .collect(Collectors.toMap(source -> normalizeText(source.title()), Function.identity()));

        List<DailyQuizQuestionResult> questions = objectMapper
                .readTree(outputText)
                .path("questions")
                .valueStream()
                .map(question -> toQuestionResult(sourceByTitle, question))
                .toList();

        validateQuestions(questions, sourceByTitle);

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
                QuizDifficulty.valueOf(question.path("difficulty").asText()));
    }

    private void validateQuestions(
            List<DailyQuizQuestionResult> questions, Map<String, DailyQuizNewsSource> sourceByTitle) {
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

        for (DailyQuizQuestionResult question : questions) {
            validateQuestion(question, sourceById.get(question.newsId()));
        }
    }

    private void validateQuestion(DailyQuizQuestionResult question, DailyQuizNewsSource source) {
        if (isBlank(question.questionText())
                || isBlank(question.explanation())
                || isBlank(question.sourceSentence())
                || containsForbiddenQuestionPhrase(question.questionText())) {
            throw new IllegalStateException("OpenAI returned invalid quiz question");
        }
        if (!source.rewrittenBody().contains(question.sourceSentence())) {
            throw new IllegalStateException("OpenAI returned source_sentence that is not in news body");
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

    private static boolean containsForbiddenQuestionPhrase(String questionText) {
        return FORBIDDEN_QUESTION_PHRASES.stream().anyMatch(questionText::contains)
                || QuizQuestionPolicy.NUMERIC_RECALL_QUESTION_PHRASES.stream().anyMatch(questionText::contains);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private static class InvalidDailyQuizResponseException extends IllegalStateException {

        InvalidDailyQuizResponseException(Throwable cause) {
            super("OpenAI daily quiz response is invalid", cause);
        }
    }
}
