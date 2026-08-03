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
        1. 문항은 뉴스 사건 암기 문제가 아니라, 뉴스에서 뽑은 경제·금융 개념을 이해했는지 확인하는 학습 문제로 만든다.
        2. 사용자가 당일 뉴스 전체를 모두 읽지 않아도 문제 문장과 보기만으로 풀 수 있는 자기완결형 문항이어야 한다.
        3. 문항은 경제 상식 해설카드처럼 "개념의 뜻", "작동 방식", "경제 흐름에서의 역할", "원인과 영향 관계"를 묻는다.
        4. 정답과 explanation은 source_sentence 한 문장에 직접 포함된 사실, 정의, 원인, 결과, 역할에서 도출되어야 한다.
        5. source_sentence에는 정답의 근거가 된 뉴스 본문의 정확한 한 문장을 그대로 넣는다.
        6. 본문에 없는 수치, 날짜, 기업명, 기관명을 만들지 않는다.
        7. 투자 판단, 의견, 예측을 묻는 문항을 만들지 않는다.
        8. 뉴스 1개당 1문항만 출제한다. 뉴스 3개면 총 3문항이다.
        9. 정답 위치가 항상 특정 번호에 편중되지 않도록 1, 2, 3번에 분산한다.
        10. 문항은 피그마 예시처럼 용어·개념 정의형을 최우선으로 만든다. 예: "ETF는 무엇의 약자일까요?", "파운드리란 무엇을 의미하나요?"
        11. 용어 정의형은 source_sentence 한 문장에 해당 용어의 뜻, 특징, 역할 또는 작동 원리가 직접 설명된 경우에만 만든다.
        12. 정의형 근거가 부족할 때만 맥락 이해형으로 만들고, 이때도 투자 판단이 아니라 원인·결과·역할·영향을 묻는다.
        13. "아닌 것은?", "틀린 것은?"처럼 부정형으로 묻는 문항은 피한다.
        14. 단순 날짜, 기간, 수치, 금액만 맞히는 문항은 만들지 않는다.
        15. "몇 년 만인가요?", "금리는 얼마인가요?", "자산은 몇 조 원인가요?"처럼 숫자 자체가 정답인 문항은 금지한다.
        16. 정답 선택지는 숫자나 기간만 다르게 바꾼 보기로 구성하지 않는다.
        17. source_sentence가 수치 문장이어도 문항은 경제 개념, 변화 방향, 원인과 영향의 의미를 묻는다.
        18. 오답 보기는 정답과 같은 범주의 보기로 만든다. 약자 문제면 다른 금융 약자, 회사 유형 문제면 다른 회사 유형, 지표 문제면 다른 지표를 보기로 둔다.
        19. "오늘 뉴스에 나온", "이 뉴스에서", "본문에 따르면"처럼 뉴스를 읽었다는 전제가 필요한 표현을 쓰지 않는다.
        20. "어떤 섹터에 투자/베팅하는 게 유리할까요?" 같은 섹터 선택형·투자 판단형 문항은 만들지 않는다.
        21. 선택지는 버튼 안에 들어가는 짧은 문구로 작성한다. "~하려고", "~하기 위해서" 같은 어색한 구어체보다 명사형 또는 간결한 구 형태를 우선한다.
        22. question_text는 앱 화면에 어울리는 자연스러운 말투로 작성한다.
        23. 출력은 반드시 지정된 JSON 형식만 반환한다.
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
                - question_text는 초보자가 뉴스 속 경제 개념이나 뉴스와 연결된 흐름을 이해하도록 작성하되, 뉴스 본문을 읽지 않아도 문제 문장과 보기만으로 풀 수 있게 만든다.
                - 문제는 뉴스 사건을 맞히는 퀴즈가 아니라 경제 상식 해설카드에서 이어지는 짧은 학습 확인 문제처럼 작성한다.
                - 사용자가 당일 뉴스 15개를 모두 읽었다는 전제로 묻지 않는다. 문제 자체에 필요한 개념 설명이나 상황 단서를 포함한다.
                - "오늘 뉴스에 나온", "뉴스에서 언급된", "이 뉴스에서", "기사에 따르면", "이 기사에서", "뉴스 본문을 보면", "본문에 따르면"처럼 출처를 먼저 말하거나 본문을 읽었다는 전제가 필요한 문항은 만들지 않는다.
                - 문항은 기사 내용 확인 문제가 아니라, 재구성 본문에서 뽑은 경제·금융 개념 학습 문제여야 한다.
                - 묻는 대상은 특정 날짜의 사건보다 오래 남는 개념이어야 한다. 예: 기준금리, 코픽스, 금융복합기업집단, ESS, 레버리지, 변동성, 무역수지, 배당 ETF.
                - 문항은 피그마 예시처럼 용어·개념 정의형을 최우선으로 작성하라.
                - 용어 정의형은 "무엇의 약자일까요?", "무엇을 의미하나요?", "무엇을 뜻할까요?", "어떤 지표일까요?", "어떤 차이일까요?" 같은 형태를 우선 사용한다.
                - 용어 정의형은 source_sentence 한 문장에 해당 용어의 뜻, 특징, 역할 또는 작동 원리가 직접 설명된 경우에만 작성한다.
                - 정의형 근거가 부족할 때만 맥락 이해형을 작성한다. 이때도 특정 기사 사건을 그대로 묻지 말고, source_sentence 한 문장에 담긴 원인·결과·역할·영향을 바탕으로 경제 흐름을 이해했는지 묻는다.
                - source_sentence가 용어를 단순히 언급만 하고 설명하지 않으면, 그 용어의 정의를 묻지 말고 원인·결과·역할·영향을 묻는 맥락 이해형으로 출제하라.
                - 좋은 문항은 "무엇을 뜻할까요?", "왜 영향을 줄까요?", "어떤 역할을 할까요?", "어떤 관계가 있을까요?"처럼 개념 이해를 확인한다.
                - 좋은 피그마 스타일 예: "ETF는 무엇의 약자일까요?"
                - 좋은 피그마 스타일 예: "반도체 산업에서 파운드리란 무엇을 의미하나요?"
                - 좋은 피그마 스타일 예: "반도체 업황을 나타내는 대표적인 지표는 무엇일까요?"
                - 좋은 피그마 스타일 예: "메모리 반도체와 시스템 반도체의 차이는 무엇일까요?"
                - 좋은 용어 정의형 예: "한 번에 미리 지불하는 방식으로 장기 거래에 유리한 수수료 방식은 무엇일까요?"
                - 좋은 용어 정의형 예: "은행들이 자금을 조달할 때 드는 평균 비용을 나타내는 지표는 무엇일까요?"
                - 좋은 용어 정의형 예: "중앙은행이 시중 금리에 영향을 주기 위해 정하는 대표 금리는 무엇일까요?"
                - 좋은 용어 정의형 예: "코픽스는 무엇을 뜻할까요?"
                - 좋은 맥락 이해형 예: "물가 상승이 이어질 때 중앙은행이 기준금리를 올리는 이유로 가장 가까운 것은 무엇일까요?"
                - 좋은 맥락 이해형 예: "코픽스가 오르면 변동금리 대출자의 부담이 커질 수 있는 이유는 무엇일까요?"
                - 좋은 맥락 이해형 예: "여러 금융업을 함께 운영하는 기업집단에 감독 기준이 필요한 이유는 무엇일까요?"
                - 나쁜 기사 확인형 예: "한국은행이 이번에 내린 결정은 무엇인가요?"
                - 나쁜 기사 확인형 예: "토스가 금융당국의 감독을 받게 된 이유는 무엇인가요?"
                - 나쁜 기사 확인형 예: "6월 한국 수출은 얼마를 넘었나요?"
                - 나쁜 기사 확인형 예: "어느 회사의 ETF 순자산이 4조원을 넘었나요?"
                - 나쁜 투자 판단형 예: "반도체 수출 실적이 좋아졌다면 어떤 섹터에 베팅하는 게 유리할까요?"
                - 나쁜 투자 판단형 예: "어떤 업종에 투자하는 것이 좋을까요?"
                - 특정 기사에서 어떤 일이 있었는지만 확인하는 문항은 만들지 않는다.
                - 본문 속 상황은 정답 검증 근거로 사용하되, 사용자가 경제 개념이나 흐름을 이해하도록 묻는다.
                - "아닌 것은?", "틀린 것은?"처럼 오답을 고르는 부정형 문항은 만들지 않는다.
                - 단순히 날짜, 기간, 수치, 금액만 맞히는 문제는 만들지 않는다.
                - "몇 년 만인가요?", "금리는 얼마인가요?", "자산은 몇 조 원인가요?"처럼 숫자 자체가 정답인 문제는 금지한다.
                - 정답 선택지를 "1년/3년/5년", "3조/10조/41조"처럼 숫자만 바꾼 보기로 만들지 않는다.
                - 수치가 필요하다면 그 수치 자체보다 경제적 의미, 변화 방향, 원인과 영향을 묻게 하라.
                - 오답 보기는 피그마 예시처럼 정답과 같은 범주의 보기로 만든다.
                - 약자 문제의 오답은 다른 금융 약자나 상품명으로, 회사 유형 문제의 오답은 다른 회사 유형으로, 지표 문제의 오답은 다른 지표로, 차이 문제의 오답은 다른 차이 유형으로 구성한다.
                - 예: ETF 약자 문제의 보기는 "상장지수펀드", "개인종합자산관리계좌", "퇴직연금"처럼 모두 금융 상품·제도 범주로 맞춘다.
                - 예: 파운드리 의미 문제의 보기는 "반도체 설계 전문 회사", "반도체 생산 전문 회사", "반도체 유통 회사"처럼 모두 회사 유형 범주로 맞춘다.
                - 선택지는 버튼 안에 들어가는 짧고 단정한 문구로 작성한다.
                - 선택지는 "~하려고", "~하기 위해서", "~때문에요"처럼 어색한 구어체를 피하고 명사형 또는 간결한 구 형태를 우선한다.
                - 좋은 선택지 예: "물가 상승·금융 불안 완화", "자금 조달 비용 지표", "위험 확산 방지", "반도체 생산 전문 회사"
                - 나쁜 선택지 예: "물가 상승과 금융 불안을 막으려고", "수출을 증가시키려고", "위험을 줄이기 위해서"
                - 오답 보기에 뉴스 주제와 무관한 엉뚱한 범주를 넣지 않는다. 예: 금융복합기업집단 문항에 "무역 회사로 지정됨" 같은 보기 금지.
                - 오답은 너무 쉽게 탈락하지 않도록 자연스럽게 작성하되, 본문을 보면 틀렸다고 판단 가능해야 한다.
                - question_text는 딱딱한 시험 문장보다 앱 화면에 자연스럽게 들어갈 말투로 작성하라.
                - source_sentence 필드에는 정답의 근거가 된 뉴스 본문의 정확한 문장을 그대로 포함하라.
                - source_sentence는 서버 검증과 해설 근거용이다. 정답과 explanation은 source_sentence 한 문장에서 직접 확인되어야 한다.
                - explanation은 source_sentence에 직접 포함된 사실, 정의, 원인, 결과, 역할을 쉬운 말로 풀어쓴다.
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
                각 뉴스에서 정확히 1문항씩 다시 만들고, 피그마 예시처럼 용어·개념 정의형을 최우선으로 작성하라.
                "오늘 뉴스에 나온", "뉴스에서 언급된", "이 뉴스에서", "기사에 따르면", "이 기사에서", "뉴스 본문을 보면", "본문에 따르면"처럼 출처를 먼저 말하거나 본문을 읽었다는 전제가 필요한 문항은 만들지 마라.
                기사 내용 확인 문제가 아니라, 경제 상식 해설카드에서 이어지는 짧은 학습 확인 문제처럼 작성하라.
                사용자가 당일 뉴스 전체를 읽었다는 전제 없이, 문제 문장과 보기만으로 풀 수 있는 자기완결형 문항으로 작성하라.
                특정 날짜의 사건보다 오래 남는 경제·금융 개념의 뜻, 작동 방식, 역할, 원인과 영향 관계를 묻도록 작성하라.
                "무엇의 약자일까요?", "무엇을 의미하나요?", "무엇을 뜻할까요?", "어떤 지표일까요?", "어떤 차이일까요?" 같은 피그마 스타일 질문을 우선 사용하라.
                용어 정의형은 source_sentence 한 문장에 해당 용어의 뜻, 특징, 역할 또는 작동 원리가 직접 설명된 경우에만 작성하라.
                맥락 이해형은 특정 기사 사건을 묻지 말고, source_sentence 한 문장에 담긴 원인·결과·역할·영향을 바탕으로 경제 흐름을 이해했는지 묻도록 작성하라.
                source_sentence가 용어를 단순히 언급만 하고 설명하지 않으면, 그 용어의 정의를 묻지 말고 원인·결과·역할·영향을 묻는 맥락 이해형으로 출제하라.
                섹터 선택형, 투자 판단형, 베팅형 문항은 만들지 마라.
                question_text는 뉴스 본문을 읽지 않아도 문제 문장과 보기만으로 풀 수 있는 자기완결형 문항이어야 한다.
                날짜, 기간, 금액, 비율처럼 숫자 자체를 맞히는 문항은 만들지 말고, 경제 개념이나 뉴스 속 변화의 의미를 묻는 문항으로 작성하라.
                정답 선택지를 숫자만 다르게 바꾼 보기로 구성하지 마라.
                오답 보기는 정답과 같은 범주 안에서 자연스럽게 다시 작성하라. 약자, 회사 유형, 지표, 차이 유형처럼 보기의 결을 맞춰라.
                선택지는 버튼 문구처럼 짧고 단정하게 작성하고, "~하려고", "~하기 위해서" 같은 어색한 구어체를 명사형 또는 간결한 구 형태로 바꿔라.
                source_sentence는 뉴스 본문에 존재하는 한 문장을 그대로 복사하라.
                정답과 해설은 source_sentence 한 문장만으로 확인 가능한 사실, 정의, 원인, 결과, 역할로 다시 작성하라.
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
        if (!normalizeText(source.rewrittenBody()).contains(normalizeText(question.sourceSentence()))) {
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
        validateCorrectAnswerMentionedInSourceSentence(question);
    }

    private void validateCorrectAnswerMentionedInSourceSentence(DailyQuizQuestionResult question) {
        DailyQuizOptionResult correctOption = question.options().stream()
                .filter(option -> option.order() == question.correctOptionOrder())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("OpenAI returned missing correct option"));

        String normalizedSource = normalizeText(question.sourceSentence());
        String normalizedAnswer = normalizeText(correctOption.text());
        if (!containsAnswerToken(normalizedSource, normalizedAnswer)) {
            throw new IllegalStateException("OpenAI returned question answer not supported by source_sentence");
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
        String normalizedQuestion = normalizeForPhraseCheck(questionText);
        return FORBIDDEN_QUESTION_PHRASES.stream()
                        .map(OpenAiDailyQuizGenerator::normalizeForPhraseCheck)
                        .anyMatch(normalizedQuestion::contains)
                || QuizQuestionPolicy.NUMERIC_RECALL_QUESTION_PHRASES.stream().anyMatch(questionText::contains);
    }

    private static boolean containsAnswerToken(String sourceSentence, String answer) {
        String normalizedSource = normalizeForPhraseCheck(sourceSentence);
        String normalizedAnswer = normalizeForPhraseCheck(answer);
        if (normalizedSource.contains(normalizedAnswer)) {
            return true;
        }
        for (String token : answer.split("[^가-힣A-Za-z0-9]+")) {
            if (token.length() >= 2 && normalizedSource.contains(normalizeForPhraseCheck(token))) {
                return true;
            }
        }
        return false;
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
