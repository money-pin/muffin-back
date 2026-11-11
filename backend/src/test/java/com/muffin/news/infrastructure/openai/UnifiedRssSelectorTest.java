package com.muffin.news.infrastructure.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.muffin.news.application.rss.CategorizedRssArticle;
import com.muffin.news.application.rss.RssArticle;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class UnifiedRssSelectorTest {

    /** 응답에 실제 후보에 없는 URL(환각)이 섞여 있어도, 후보 목록에 존재하는 기사만 반환한다. */
    @Test
    void returnsOnlyCandidateArticlesSelectedByOpenAi() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiUnifiedRssArticleSelector selector = selector(builder, 15);
        CategorizedRssArticle selected = candidate("경제", "선택", "https://example.com/1");
        CategorizedRssArticle rejected = candidate("증권", "탈락", "https://example.com/2");
        server.expect(requestTo("https://api.test/responses"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andRespond(withSuccess(
                        response(
                                """
                                {"url":"https://example.com/1","reason":"기준금리 결정이라 파급력이 크다","market_impact":3,"daily_life":2,"learning_value":3},
                                {"url":"https://hallucinated.example","reason":"존재하지 않는 기사","market_impact":3,"daily_life":3,"learning_value":3}
                                """),
                        MediaType.APPLICATION_JSON));

        List<CategorizedRssArticle> result = selector.select(List.of(selected, rejected));

        assertThat(result).containsExactly(selected);
        server.verify();
    }

    /** 모델이 돌려준 중요도 순서를 유지하고 통합 선별 상한까지만 반환한다. */
    @Test
    void preservesModelOrderAndAppliesMaxTotal() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiUnifiedRssArticleSelector selector = selector(builder, 2);
        CategorizedRssArticle first = candidate("경제", "첫 번째 후보", "https://example.com/1");
        CategorizedRssArticle second = candidate("증권", "두 번째 후보", "https://example.com/2");
        CategorizedRssArticle third = candidate("세계", "세 번째 후보", "https://example.com/3");
        server.expect(requestTo("https://api.test/responses"))
                .andRespond(withSuccess(
                        response(
                                """
                                {"url":"https://example.com/3","reason":"가장 중요하다","market_impact":3,"daily_life":3,"learning_value":3},
                                {"url":"https://example.com/1","reason":"두 번째로 중요하다","market_impact":2,"daily_life":2,"learning_value":2},
                                {"url":"https://example.com/2","reason":"상한을 넘어 버려진다","market_impact":1,"daily_life":1,"learning_value":1}
                                """),
                        MediaType.APPLICATION_JSON));

        List<CategorizedRssArticle> result = selector.select(List.of(first, second, third));

        assertThat(result).containsExactly(third, first);
        server.verify();
    }

    /** 같은 URL이 두 번 오면 한 번만 반환한다. 중복을 그대로 담으면 같은 기사가 두 번 발행된다. */
    @Test
    void dropsDuplicateUrls() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiUnifiedRssArticleSelector selector = selector(builder, 15);
        CategorizedRssArticle first = candidate("경제", "첫 번째 후보", "https://example.com/1");
        CategorizedRssArticle second = candidate("증권", "두 번째 후보", "https://example.com/2");
        server.expect(requestTo("https://api.test/responses"))
                .andRespond(withSuccess(
                        response(
                                """
                                {"url":"https://example.com/1","reason":"첫 선별","market_impact":3,"daily_life":2,"learning_value":2},
                                {"url":"https://example.com/1","reason":"같은 URL 재등장","market_impact":3,"daily_life":2,"learning_value":2},
                                {"url":"https://example.com/2","reason":"다른 기사","market_impact":2,"daily_life":2,"learning_value":2}
                                """),
                        MediaType.APPLICATION_JSON));

        List<CategorizedRssArticle> result = selector.select(List.of(first, second));

        assertThat(result).containsExactly(first, second);
        server.verify();
    }

    /** 선별 결과가 빈 배열이면 빈 목록을 반환한다. 적합한 기사가 없는 날은 정상 상황이다. */
    @Test
    void returnsEmptyWhenModelSelectsNothing() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiUnifiedRssArticleSelector selector = selector(builder, 15);
        server.expect(requestTo("https://api.test/responses"))
                .andRespond(withSuccess(response(""), MediaType.APPLICATION_JSON));

        List<CategorizedRssArticle> result = selector.select(List.of(candidate("경제", "후보", "https://example.com/1")));

        assertThat(result).isEmpty();
        server.verify();
    }

    /** 후보가 없으면 모델을 호출하지 않는다. 빈 요청에 토큰을 쓸 이유가 없다. */
    @Test
    void doesNotCallOpenAiWhenThereAreNoCandidates() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiUnifiedRssArticleSelector selector = selector(builder, 15);

        List<CategorizedRssArticle> result = selector.select(List.of());

        assertThat(result).isEmpty();
        server.verify();
    }

    private static OpenAiUnifiedRssArticleSelector selector(RestClient.Builder builder, int maxTotal) {
        return new OpenAiUnifiedRssArticleSelector(
                builder.build(),
                new ObjectMapper(),
                new OpenAiClientProperties("test-key", "https://api.test/responses"),
                new AiSelectionProperties("gpt-6-luna", 5, maxTotal));
    }

    /** selections 배열 원소를 받아 Responses API 응답 봉투로 감싼다. */
    private static String response(String selections) {
        String payload = "{\"selections\":[" + selections.replace("\n", "") + "]}";
        return "{\"output\":[{\"content\":[{\"type\":\"output_text\",\"text\":"
                + new ObjectMapper().writeValueAsString(payload) + "}]}]}";
    }

    private static CategorizedRssArticle candidate(String category, String title, String url) {
        return new CategorizedRssArticle(
                category, new RssArticle(title, url, "요약", null, LocalDateTime.of(2026, 7, 12, 6, 0)));
    }
}
