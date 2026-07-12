package com.muffin.news.application.rss.selection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.muffin.news.application.rss.RssArticle;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RssSelectorTest {

    /** OpenAI 응답에 실제 후보에 없는 URL(환각)이 섞여 있어도, 후보 목록에 존재하는 기사만 반환한다. */
    @Test
    void returnsOnlyCandidateArticlesSelectedByOpenAi() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiRssArticleSelector selector = new OpenAiRssArticleSelector(
                builder.build(),
                new ObjectMapper(),
                new AiSelectionProperties("test-key", "gpt-5-mini", "https://api.test/responses", 5));
        RssArticle selected = article("선택", "https://example.com/1");
        RssArticle rejected = article("탈락", "https://example.com/2");
        server.expect(requestTo("https://api.test/responses"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andRespond(withSuccess(
                        """
                        {
                          "output": [{
                            "content": [{
                              "type": "output_text",
                              "text": "{\\\"selected_urls\\\":[\\\"https://example.com/1\\\",\\\"https://hallucinated.example\\\"]}"
                            }]
                          }]
                        }
                        """,
                        MediaType.APPLICATION_JSON));

        List<RssArticle> result = selector.select("경제", List.of(selected, rejected));

        assertThat(result).containsExactly(selected);
        server.verify();
    }

    private static RssArticle article(String title, String url) {
        return new RssArticle(title, url, "요약", LocalDateTime.of(2026, 7, 12, 6, 0));
    }
}
