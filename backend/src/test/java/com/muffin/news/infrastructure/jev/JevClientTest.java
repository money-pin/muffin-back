package com.muffin.news.infrastructure.jev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class JevClientTest {

    private static final JevProperties PROPERTIES = new JevProperties(
            "test-key", "https://jev.test/v1/systemone", "jev-latest", 2, 3, 2, 1, 0.5, 1.0, 30, 0.85);

    private static final Map<String, Object> QUESTION = Map.of(
            "market_impact", Map.of("type", "score", "instructions", "파급력은?", "criteria", List.of("낮음", "보통", "높음")));

    @Test
    @DisplayName("state·model·questions를 담아 보내고 Bearer 인증 헤더를 붙인다")
    void evaluate_sendsStateModelAndQuestions() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        JevClient client = new JevClient(builder.build(), new ObjectMapper(), PROPERTIES);

        server.expect(requestTo("https://jev.test/v1/systemone"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(jsonPath("$.model").value("jev-latest"))
                .andExpect(jsonPath("$.state.title").value("반도체 수출 증가"))
                .andExpect(jsonPath("$.questions.market_impact.type").value("score"))
                .andRespond(withSuccess(
                        """
                        {"model":"jev-1.13.0","answers":{},"usage":{"input_tokens":10,"output_tokens":2}}
                        """,
                        MediaType.APPLICATION_JSON));

        client.evaluate("https://news/1", Map.of("title", "반도체 수출 증가"), QUESTION);

        server.verify();
    }

    /** score는 레벨 번호 × 확률의 합이라 소수로 온다. 이 연속값이 통합 랭킹의 근거다. */
    @Test
    @DisplayName("score 답변의 연속값과 신뢰도를 읽는다")
    void evaluate_parsesScoreAnswer() {
        JevClient client = clientRespondingWith(
                """
                {
                  "model": "jev-1.13.0",
                  "answers": {
                    "market_impact": {
                      "type": "score",
                      "score": 1.43,
                      "confidence": 0.35,
                      "legend": {"0": "낮음", "1": "보통", "2": "높음"},
                      "probabilities": {"0": 0.0, "1": 0.57, "2": 0.43}
                    }
                  },
                  "usage": {"input_tokens": 332, "output_tokens": 18}
                }
                """);

        Map<String, JevAnswer> answers = client.evaluate("https://news/1", Map.of("title", "제목"), QUESTION);

        assertThat(answers).containsKey("market_impact");
        assertThat(answers.get("market_impact").score()).isEqualTo(1.43);
        assertThat(answers.get("market_impact").confidence()).isEqualTo(0.35);
    }

    @Test
    @DisplayName("noul 답변의 참 확률을 읽는다")
    void evaluate_parsesNoulAnswer() {
        JevClient client = clientRespondingWith(
                """
                {
                  "model": "jev-1.13.0",
                  "answers": {"is_noise": {"type": "noul", "noul": 0.97}},
                  "usage": {"input_tokens": 120, "output_tokens": 4}
                }
                """);

        Map<String, JevAnswer> answers = client.evaluate("https://news/1", Map.of("title", "제목"), QUESTION);

        assertThat(answers.get("is_noise").noul()).isEqualTo(0.97);
    }

    @Test
    @DisplayName("answers가 없는 응답은 실패로 처리한다")
    void evaluate_throwsWhenAnswersAreMissing() {
        JevClient client =
                clientRespondingWith("""
                {"model":"jev-1.13.0","usage":{}}
                """);

        assertThatThrownBy(() -> client.evaluate("https://news/1", Map.of("title", "제목"), QUESTION))
                .isInstanceOf(IllegalStateException.class);
    }

    private static JevClient clientRespondingWith(String body) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://jev.test/v1/systemone"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        return new JevClient(builder.build(), new ObjectMapper(), PROPERTIES);
    }
}
