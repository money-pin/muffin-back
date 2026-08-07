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
        2. 기사 사건 암기보다 경제·금융 개념 이해를 묻는다.
        3. 문제와 보기만으로 풀 수 있는 자기완결형 문항으로 작성한다.
        4. 해설카드의 key_term, title, content를 우선 근거로 사용해 용어·개념 정의형 또는 개념 이해형 문항을 만든다.
        5. 해설카드에 용어의 뜻·특징·역할 설명이 있으면 반드시 용어·개념 정의형을 우선하고, 근거가 부족할 때만 뉴스 본문의 원인·결과·영향을 묻는다.
        6. 정답과 explanation은 source_sentence 한 문장에서 직접 도출되어야 하며, source_sentence는 해설카드 content 또는 뉴스 본문 문장 그대로 쓴다.
        7. 본문 밖 수치·날짜·기업명·기관명은 만들지 않는다.
        8. 투자 판단, 의견, 예측, 섹터 선택, 정책·군사 사건의 목적 확인, 부정형("아닌 것은?") 문항은 금지한다.
        9. 날짜·기간·금액·비율 같은 숫자 자체를 맞히게 하지 않는다.
        10. 오답 보기는 정답과 같은 범주로 자연스럽게 만들고, 선택지는 source_sentence에서 직접 확인 가능한 구체 명사형/구 형태로 쓴다.
        11. "오늘 뉴스에 나온", "본문에 따르면"처럼 본문을 읽었다는 전제 표현은 쓰지 않는다.
        12. explanation은 source_sentence를 그대로 반복하지 말고, 정답이 왜 맞는지 쉬운 말로 풀어쓴다.
        13. 세 문항의 question_topic은 서로 겹치지 않게 작성한다.
        14. 정답 위치는 1, 2, 3번에 분산하고, JSON 형식만 반환한다.
        """;
    private static final String FALLBACK_INSTRUCTIONS =
            """
        당신은 경제·금융 교육 퀴즈를 출제하는 전문가입니다.
        퀴즈 수강자는 금융에 관심을 가지기 시작한 20~30대입니다.

        [최종 fallback 출제 원칙]
        1. 뉴스 1개당 1문항씩 총 3문항을 만든다.
        2. rewritten_body만 근거로 사용해 경제·금융 개념 이해를 묻는다.
        3. 문제와 보기만으로 풀 수 있는 자기완결형 문항으로 작성한다.
        4. 정답과 explanation은 source_sentence 한 문장에서 직접 도출되어야 하며, source_sentence는 뉴스 본문 문장 그대로 쓴다.
        5. 투자 판단, 의견, 예측, 섹터 선택, 정책·군사 사건의 목적 확인, 부정형("아닌 것은?") 문항은 금지한다.
        6. 날짜·기간·금액·비율 같은 숫자 자체를 맞히게 하지 않는다.
        7. 오답 보기는 정답과 같은 범주로 자연스럽게 만들고, 선택지는 source_sentence에서 직접 확인 가능한 구체 명사형/구 형태로 쓴다.
        8. "오늘 뉴스에 나온", "본문에 따르면"처럼 본문을 읽었다는 전제 표현은 쓰지 않는다.
        9. explanation은 source_sentence를 그대로 반복하지 말고, 정답이 왜 맞는지 쉬운 말로 풀어쓴다.
        10. 세 문항의 question_topic은 서로 겹치지 않게 작성한다.
        11. 정답 위치는 1, 2, 3번에 분산하고, JSON 형식만 반환한다.
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
                String responseBody = requestWithRetry(request, retryInstruction(attempt), isFallbackAttempt(attempt));
                return parseResponse(request, responseBody);
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

    private String prompt(String input, String retryInstruction) {
        return """
                다음 3개의 뉴스 본문과 해설카드를 바탕으로 퀴즈 3문항을 출제해 주세요.

                [뉴스 입력]
                %s

                [작성 규칙]
                - 각 뉴스에서 반드시 1문항씩, 총 3문항을 출제하라.
                - related_news_title에는 입력으로 받은 뉴스 제목을 그대로 작성하라.
                - 해설카드의 key_term, title, content를 먼저 참고해 학습 가치가 높은 개념을 고른다.
                - 각 문항의 핵심 주제를 question_topic에 짧은 명사형으로 작성한다. 예: "코픽스", "공모주", "금융 시장 불확실성"
                - 세 문항의 question_topic은 서로 달라야 한다. 같은 용어 또는 같은 개념을 반복 출제하지 않는다.
                - 사용자가 당일 뉴스 전체를 읽었다고 가정하지 말고, 문제와 보기만으로 풀 수 있게 개념 설명이나 상황 단서를 포함하라.
                - question_text는 초보자가 뉴스 속 경제 개념이나 뉴스와 연결된 흐름을 이해하도록 작성하되, 뉴스 본문을 읽지 않아도 문제 문장과 보기만으로 풀 수 있게 만든다.
                - 해설카드 content에 용어의 뜻·특징·역할 설명이 있으면 그 내용을 근거로 용어·개념 정의형을 우선 작성한다.
                - 정답 근거인 source_sentence는 explanation_cards.content 또는 rewritten_body 안에서 그대로 가져온다.
                - 해설카드 content에 정의 근거가 부족할 때만 rewritten_body의 경제·금융 관점 원인·결과·역할·영향을 묻는 맥락 이해형으로 작성하라.
                - rewritten_body가 용어를 단순히 언급만 하고 설명하지 않으면, 그 용어의 정의를 묻지 말고 해설카드에 설명된 다른 개념을 고른다.
                - 좋은 예: "은행들이 자금을 조달할 때 드는 평균 비용을 나타내는 지표는 무엇일까요?"
                - 좋은 예: "코픽스가 오르면 변동금리 대출자의 부담이 커질 수 있는 이유는 무엇일까요?"
                - 좋은 예: "메모리 반도체와 시스템 반도체의 차이는 무엇일까요?"
                - 좋은 예: "반도체 산업에서 파운드리란 무엇을 의미하나요?"
                - 좋은 예: "ETF는 무엇의 약자일까요?"
                - 정책·군사·국제 사건은 "왜 했나요?", "주목적은 무엇인가요?"처럼 사건 목적을 묻지 말고, 물가·금리·전력비·운임·불확실성 등 경제 영향으로 바꿔 묻는다.
                - 나쁜 예: "한국은행이 이번에 내린 결정은 무엇인가요?", "뉴욕주가 데이터센터 건설을 유예한 이유는 무엇인가요?", "미군의 공습과 봉쇄 강화의 주목적은 무엇인가요?", "어떤 업종에 투자하는 것이 좋을까요?"
                - 나쁜 예: "새로운 공모주 청약 제도에서 투자자가 받을 수 있는 혜택은 무엇인가요?"
                - 좋은 예: "전력 사용이 큰 데이터센터가 늘어날 때 소비자에게 생길 수 있는 부담은 무엇일까요?"
                - 좋은 예: "군사적 긴장이 커질 때 금융 시장에서 함께 커질 수 있는 것은 무엇일까요?"
                - 좋은 예: "공모주는 무엇을 의미하나요?"
                - 출처 전제 표현("오늘 뉴스에 나온", "기사에 따르면", "본문에 따르면" 등), 부정형("아닌 것은?"), 투자 판단, 가격 전망은 금지한다.
                - 숫자 자체를 맞히는 문제와 숫자만 바꾼 선택지는 금지한다.
                - 선택지는 정답과 같은 범주의 짧은 명사형/구 형태로 쓴다. 예: "자금 조달 비용 지표", "위험 확산 방지"
                - 정답 선택지는 source_sentence에서 직접 확인 가능한 구체 명사형으로 작성한다.
                - 정답 선택지는 source_sentence의 핵심 표현을 그대로 쓰거나, 의미가 바뀌지 않는 짧은 명사형으로만 바꾼다.
                - 정답 선택지를 너무 일반적인 표현으로 만들지 말고, 무엇이 어떻게 되는지 드러나게 작성한다. 예: "전기요금 인상", "자금 조달 비용 지표", "시장 불확실성 증가"
                - 오답도 정답과 같은 범주의 구체적인 명사형으로 작성한다.
                - "긍정적인 영향", "부정적인 영향", "경제적 변화", "시장 변화", "위험 증가", "부담 증가"처럼 범위가 넓고 추상적인 표현은 정답/오답 보기로 쓰지 않는다.
                - source_sentence는 해설카드 content 또는 뉴스 본문 문장 그대로 쓰고, 정답과 explanation은 그 한 문장에서 확인 가능해야 한다.
                - explanation은 source_sentence를 그대로 반복하지 말고, 정답이 왜 맞는지 초보자가 이해할 수 있게 1~2문장으로 풀어쓴다.
                - 특정 기사에서 어떤 일이 있었는지나 정책·군사 조치의 목적만 확인하는 문항은 만들지 않는다.
                - 본문 속 상황은 정답 검증 근거로 사용하되, 사용자가 경제 개념이나 흐름을 이해하도록 묻는다.
                - 단순히 날짜, 기간, 수치, 금액만 맞히는 문제는 만들지 않는다.
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
                      "source_sentence": "해설카드 또는 본문에서 정답의 근거가 된 문장 원문 그대로",
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
        if (attempt == MAX_GENERATION_ATTEMPTS) {
            return """

                    [최종 재생성 지시: 재구성 본문 기반 fallback]
                    앞선 해설카드 기반 응답이 검증 조건을 만족하지 못했다.
                    이번 시도에서는 해설카드가 아니라 rewritten_body를 주 근거로 사용해 각 뉴스에서 정확히 1문항씩 다시 만들어라.
                    단, 기사 사건을 확인하는 문제가 아니라 rewritten_body에 설명된 경제·금융 개념, 지표, 원인·결과·영향을 묻는 자기완결형 문항으로 작성하라.
                    source_sentence는 반드시 rewritten_body 안에 실제로 존재하는 문장 그대로 써라.
                    정답과 해설은 source_sentence 한 문장에서 확인 가능해야 한다.
                    출처 전제 표현, 부정형, 투자 판단, 숫자 암기형, 숫자만 바꾼 선택지는 금지한다.
                    세 문항의 question_topic은 서로 다르게 작성하고, 같은 용어 또는 같은 개념을 반복하지 마라.
                    오답은 정답과 같은 범주로, 선택지는 짧은 명사형/구 형태로 작성하라.
                    정답/오답 선택지는 source_sentence에서 직접 확인 가능한 구체 명사형으로 작성하고, "부정적인 영향", "시장 변화" 같은 추상 표현은 쓰지 마라.
                    explanation은 source_sentence를 그대로 반복하지 말고, 정답이 왜 맞는지 쉬운 말로 풀어써라.
                    """;
        }

        return """

                [재생성 지시]
                이전 응답은 JSON 형식, 문항 수, 선택지 수, source_sentence, 문항 유형, 또는 투자 조언 금지 조건을 만족하지 못했다.
                각 뉴스에서 정확히 1문항씩 다시 만들고, 기사 확인형이 아닌 자기완결형 개념 학습 문제로 작성하라.
                본문에 용어 설명이 있으면 정의형을 우선하고, 근거가 부족할 때만 경제·금융 관점의 원인·결과·역할·영향을 묻는다.
                정책·군사·국제 사건의 목적 확인형은 만들지 말고, 물가·금리·비용·운임·불확실성 같은 경제 영향형으로 바꿔라.
                출처 전제 표현, 부정형, 투자 판단, 숫자 암기형, 숫자만 바꾼 선택지는 금지한다.
                세 문항의 question_topic은 서로 다르게 작성하고, 같은 용어 또는 같은 개념을 반복하지 마라.
                오답은 정답과 같은 범주로, 선택지는 짧은 명사형/구 형태로 작성하라.
                정답/오답 선택지는 source_sentence에서 직접 확인 가능한 구체 명사형으로 작성하고, "부정적인 영향", "시장 변화" 같은 추상 표현은 쓰지 마라.
                source_sentence는 해설카드 content 또는 본문 문장 그대로 쓰고, 정답과 해설은 그 한 문장에서 확인 가능해야 한다.
                explanation은 source_sentence를 그대로 반복하지 말고, 정답이 왜 맞는지 쉬운 말로 풀어써라.
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
                normalizeText(question.path("question_topic").asText()),
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
        if (hasDuplicatedQuestionTopic(questions)) {
            throw new IllegalStateException("OpenAI returned duplicated question_topic");
        }

        for (DailyQuizQuestionResult question : questions) {
            validateQuestion(question, sourceById.get(question.newsId()));
        }
    }

    private void validateQuestion(DailyQuizQuestionResult question, DailyQuizNewsSource source) {
        if (isBlank(question.questionText())
                || isBlank(question.explanation())
                || isBlank(question.sourceSentence())
                || isBlank(question.questionTopic())
                || containsForbiddenQuestionPhrase(question.questionText())) {
            throw new IllegalStateException("OpenAI returned invalid quiz question");
        }
        if (!containsEvidenceSentence(source, question.sourceSentence())) {
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

    private static boolean containsEvidenceSentence(DailyQuizNewsSource source, String sourceSentence) {
        String normalizedSourceSentence = normalizeForPhraseCheck(sourceSentence);
        return normalizeForPhraseCheck(source.rewrittenBody()).contains(normalizedSourceSentence)
                || source.explanationCards().stream()
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
