package com.muffin.news.infrastructure.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.muffin.news.application.exception.NewsErrorCode;
import com.muffin.news.application.exception.NewsException;
import com.muffin.news.application.explanation.NewsExplanationGenerationRequest;
import com.muffin.news.application.explanation.NewsExplanationGenerationResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class OpenAiNewsExplanationGeneratorTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void generate_retriesWhenFirstResponseContainsInvalidCard() throws Exception {
        TestContext context = context();
        context.server()
                .expect(request -> {})
                .andRespond(withSuccess(
                        openAiResponse(outputText(List.of(
                                card(1, "짧은 카드", "짧습니다.", "금리"), card(2, "기준금리란?", validBody("기준금리"), "기준금리")))),
                        MediaType.APPLICATION_JSON));
        context.server()
                .expect(request -> {})
                .andRespond(withSuccess(
                        openAiResponse(outputText(List.of(
                                card(1, "기준금리란?", validBody("기준금리"), "기준금리"),
                                card(2, "통화정책은 무엇?", validBody("통화정책"), "통화정책")))),
                        MediaType.APPLICATION_JSON));

        NewsExplanationGenerationResult result = context.generator().generate(request());

        assertThat(result.cards()).hasSize(2);
        assertThat(result.cards()).extracting("order").containsExactly(1, 2);
        assertThat(result.cards()).extracting("title").containsExactly("기준금리란?", "통화정책은 무엇?");
        context.server().verify();
    }

    @Test
    void generate_savesOnlyUsableCardsWhenRetryResponseStillContainsInvalidCard() throws Exception {
        TestContext context = context();
        context.server().expect(request -> {}).andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));
        context.server()
                .expect(request -> {})
                .andRespond(withSuccess(
                        openAiResponse(outputText(List.of(
                                card(1, "투자 조언 카드", validBody("투자하세요"), "투자"),
                                card(2, "기준금리란?", validBody("기준금리"), "기준금리")))),
                        MediaType.APPLICATION_JSON));

        NewsExplanationGenerationResult result = context.generator().generate(request());

        assertThat(result.cards()).singleElement().satisfies(card -> {
            assertThat(card.order()).isEqualTo(2);
            assertThat(card.title()).isEqualTo("기준금리란?");
        });
        context.server().verify();
    }

    @Test
    void generate_rejectsDuplicatedOrderEvenOnRetryResponse() throws Exception {
        TestContext context = context();
        context.server().expect(request -> {}).andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));
        context.server()
                .expect(request -> {})
                .andRespond(withSuccess(
                        openAiResponse(outputText(List.of(
                                card(1, "기준금리란?", validBody("기준금리"), "기준금리"),
                                card(1, "통화정책은 무엇?", validBody("통화정책"), "통화정책")))),
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> context.generator().generate(request()))
                .isInstanceOfSatisfying(NewsException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(NewsErrorCode.NEWS_EXPLANATION_RESPONSE_INVALID));
        context.server().verify();
    }

    @Test
    void generate_returnsCardsByImportanceOrder() throws Exception {
        TestContext context = context();
        context.server()
                .expect(content().string(containsString("cards 배열과 order는 중요도 순서로 작성한다")))
                .andRespond(withSuccess(
                        openAiResponse(outputText(List.of(
                                card(2, "가계대출은 무엇?", validBody("가계대출"), "가계대출"),
                                card(1, "통화정책은 무엇?", validBody("통화정책"), "통화정책")))),
                        MediaType.APPLICATION_JSON));

        NewsExplanationGenerationResult result = context.generator().generate(request());

        assertThat(result.cards()).extracting("order").containsExactly(1, 2);
        assertThat(result.cards()).extracting("title").containsExactly("통화정책은 무엇?", "가계대출은 무엇?");
        context.server().verify();
    }

    private static TestContext context() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiNewsExplanationGenerator generator = new OpenAiNewsExplanationGenerator(
                builder.build(),
                OBJECT_MAPPER,
                new OpenAiClientProperties("test-key", "https://api.test/responses"),
                new NewsExplanationProperties("test-model"));
        return new TestContext(generator, server);
    }

    private static NewsExplanationGenerationRequest request() {
        return new NewsExplanationGenerationRequest(
                1L, "한국은행 기준금리 인상", "한국은행이 물가 안정을 위해 기준금리를 인상했다.", "한국은행은 물가 상승과 금융 불안을 이유로 기준금리를 올렸습니다.", List.of());
    }

    private static Map<String, Object> card(int order, String title, String body, String keyTerm) {
        return Map.of("order", order, "title", title, "body", body, "key_term", keyTerm);
    }

    private static String outputText(List<Map<String, Object>> cards) throws Exception {
        return OBJECT_MAPPER.writeValueAsString(Map.of("cards", cards));
    }

    private static String openAiResponse(String outputText) throws Exception {
        return OBJECT_MAPPER.writeValueAsString(Map.of(
                "output", List.of(Map.of("content", List.of(Map.of("type", "output_text", "text", outputText))))));
    }

    private static String validBody(String keyTerm) {
        return "**%s**는 뉴스에서 돈의 흐름과 비용 변화를 이해하게 해 주는 핵심 개념입니다. 생활비가 오르면 지출을 다시 살피듯, 금리와 물가 변화는 대출과 저축 환경에 이어집니다. 이번 뉴스에서는 경제 안정 흐름을 읽는 배경으로 연결되며, 물가와 금융 불안을 함께 이해하는 기준이 됩니다."
                .formatted(keyTerm);
    }

    private record TestContext(OpenAiNewsExplanationGenerator generator, MockRestServiceServer server) {}
}
