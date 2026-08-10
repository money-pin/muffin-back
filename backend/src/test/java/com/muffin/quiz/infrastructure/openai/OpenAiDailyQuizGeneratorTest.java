package com.muffin.quiz.infrastructure.openai;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.muffin.news.infrastructure.openai.OpenAiClientProperties;
import com.muffin.quiz.application.generation.DailyQuizExplanationCardSource;
import com.muffin.quiz.application.generation.DailyQuizGenerationRequest;
import com.muffin.quiz.application.generation.DailyQuizNewsSource;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class OpenAiDailyQuizGeneratorTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void generate_rejectsCardOnlyEvidenceOnFallbackAttempt() throws Exception {
        TestContext context = context();
        context.server().expect(request -> {}).andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));
        context.server().expect(request -> {}).andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));
        context.server()
                .expect(request -> {})
                .andRespond(withSuccess(
                        openAiResponse(outputText(List.of(
                                validQuestion(
                                        1,
                                        "뉴스1",
                                        "은행들이 돈을 빌려오는 평균 비용을 보여주는 지표는 무엇일까요?",
                                        "코픽스",
                                        "코픽스는 은행들이 돈을 빌려오는 평균 비용을 보여주는 지표입니다."),
                                validQuestion(
                                        2,
                                        "뉴스2",
                                        "환율을 안정시키기 위해 통화를 사고파는 조치는 무엇인가요?",
                                        "외환시장 개입",
                                        "외환시장 개입은 정부나 중앙은행이 환율을 안정시키기 위해 외환 시장에서 통화를 사고파는 조치입니다."),
                                validQuestion(
                                        3,
                                        "뉴스3",
                                        "주가 하락 뒤 매수 여력이 줄어 추가 하락이 이어지는 현상은 무엇인가요?",
                                        "손실 나선",
                                        "손실 나선은 주가 하락으로 손실이 발생하면 다음 매수 여력이 줄어 주가가 더 하락하는 악순환입니다.")))),
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> context.generator().generate(fallbackEvidenceRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("OpenAI daily quiz response is invalid");
        context.server().verify();
    }

    private static TestContext context() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiDailyQuizGenerator generator = new OpenAiDailyQuizGenerator(
                builder.build(),
                OBJECT_MAPPER,
                new OpenAiClientProperties("test-key", "https://api.test/responses"),
                new DailyQuizGenerationProperties("test-model"));
        return new TestContext(generator, server);
    }

    private static DailyQuizGenerationRequest request() {
        return new DailyQuizGenerationRequest(
                LocalDate.of(2026, 8, 10),
                List.of(
                        source(
                                1L,
                                "뉴스1",
                                "코픽스는 은행들이 돈을 빌려오는 평균 비용을 보여주는 지표입니다.",
                                "코픽스",
                                "코픽스는 은행들이 돈을 빌려오는 평균 비용을 보여주는 지표입니다."),
                        source(
                                2L,
                                "뉴스2",
                                "외환시장 개입은 정부나 중앙은행이 환율을 안정시키기 위해 외환 시장에서 통화를 사고파는 조치입니다.",
                                "외환시장 개입",
                                "외환시장 개입은 정부나 중앙은행이 환율을 안정시키기 위해 외환 시장에서 통화를 사고파는 조치입니다."),
                        source(
                                3L,
                                "뉴스3",
                                "손실 나선은 주가 하락으로 손실이 발생하면 다음 매수 여력이 줄어 주가가 더 하락하는 악순환입니다.",
                                "손실 나선",
                                "손실 나선은 주가 하락으로 손실이 발생하면 다음 매수 여력이 줄어 주가가 더 하락하는 악순환입니다.")));
    }

    private static DailyQuizGenerationRequest fallbackEvidenceRequest() {
        return new DailyQuizGenerationRequest(
                LocalDate.of(2026, 8, 10),
                List.of(
                        source(1L, "뉴스1", "뉴스1 재구성 본문입니다.", "코픽스", "코픽스는 은행들이 돈을 빌려오는 평균 비용을 보여주는 지표입니다."),
                        source(
                                2L,
                                "뉴스2",
                                "뉴스2 재구성 본문입니다.",
                                "외환시장 개입",
                                "외환시장 개입은 정부나 중앙은행이 환율을 안정시키기 위해 외환 시장에서 통화를 사고파는 조치입니다."),
                        source(
                                3L,
                                "뉴스3",
                                "뉴스3 재구성 본문입니다.",
                                "손실 나선",
                                "손실 나선은 주가 하락으로 손실이 발생하면 다음 매수 여력이 줄어 주가가 더 하락하는 악순환입니다.")));
    }

    private static DailyQuizNewsSource source(
            Long newsId, String title, String rewrittenBody, String keyTerm, String content) {
        return new DailyQuizNewsSource(
                newsId,
                title,
                rewrittenBody,
                List.of(new DailyQuizExplanationCardSource(1, keyTerm + "이란?", keyTerm, content)));
    }

    private static Map<String, Object> validQuestion(
            int order, String relatedNewsTitle, String questionText, String correctAnswer, String sourceSentence) {
        return question(
                order,
                relatedNewsTitle,
                questionText,
                correctAnswer,
                sourceSentence,
                List.of(correctAnswer, "기준금리", "시장 변동성"));
    }

    private static Map<String, Object> question(
            int order,
            String relatedNewsTitle,
            String questionText,
            String correctAnswer,
            String sourceSentence,
            List<String> options) {
        return Map.of(
                "order",
                order,
                "related_news_title",
                relatedNewsTitle,
                "question_text",
                questionText,
                "question_topic",
                correctAnswer,
                "options",
                List.of(
                        Map.of("order", 1, "text", options.get(0)),
                        Map.of("order", 2, "text", options.get(1)),
                        Map.of("order", 3, "text", options.get(2))),
                "correct_option_order",
                1,
                "explanation",
                correctAnswer + "는 source_sentence에서 설명한 개념입니다.",
                "source_sentence",
                sourceSentence,
                "difficulty",
                "EASY");
    }

    private static String outputText(List<Map<String, Object>> questions) throws Exception {
        return OBJECT_MAPPER.writeValueAsString(Map.of("questions", questions));
    }

    private static String openAiResponse(String outputText) throws Exception {
        return OBJECT_MAPPER.writeValueAsString(Map.of(
                "output", List.of(Map.of("content", List.of(Map.of("type", "output_text", "text", outputText))))));
    }

    private record TestContext(OpenAiDailyQuizGenerator generator, MockRestServiceServer server) {}
}
