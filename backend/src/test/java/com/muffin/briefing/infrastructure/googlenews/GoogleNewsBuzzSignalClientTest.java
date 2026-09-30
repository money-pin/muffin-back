package com.muffin.briefing.infrastructure.googlenews;

import static org.assertj.core.api.Assertions.assertThat;

import com.muffin.briefing.application.BriefingProperties;
import com.muffin.briefing.application.generation.BuzzSignal.BuzzHeadline;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GoogleNewsBuzzSignalClientTest {

    private final GoogleNewsBuzzSignalClient client = new GoogleNewsBuzzSignalClient(
            new BriefingProperties(8, 7, new BriefingProperties.Buzz(true, List.of("증시"), 10)));

    @Test
    @DisplayName("RSS에서 제목과 출처를 뽑고 제목 끝의 매체명은 떼어 낸다")
    void parse_extractsTitleAndOutlet() throws Exception {
        String xml =
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0"><channel>
                  <item>
                    <title>코스피 2600선 회복 - 머니투데이</title>
                    <link>https://news.google.com/rss/articles/1</link>
                    <source url="https://mt.co.kr">머니투데이</source>
                  </item>
                </channel></rss>
                """;

        List<BuzzHeadline> result = client.parse(xml.getBytes(StandardCharsets.UTF_8));

        assertThat(result).singleElement().satisfies(headline -> {
            assertThat(headline.title()).isEqualTo("코스피 2600선 회복");
            assertThat(headline.outlet()).isEqualTo("머니투데이");
        });
    }

    @Test
    @DisplayName("source가 없어도 제목만으로 수집한다")
    void parse_handlesMissingSource() throws Exception {
        String xml =
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0"><channel>
                  <item>
                    <title>환율 급등</title>
                    <link>https://news.google.com/rss/articles/2</link>
                  </item>
                </channel></rss>
                """;

        List<BuzzHeadline> result = client.parse(xml.getBytes(StandardCharsets.UTF_8));

        assertThat(result).singleElement().satisfies(headline -> {
            assertThat(headline.title()).isEqualTo("환율 급등");
            assertThat(headline.outlet()).isEmpty();
        });
    }

    @Test
    @DisplayName("제목이 없는 item은 건너뛴다")
    void parse_skipsItemWithoutTitle() throws Exception {
        String xml =
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0"><channel>
                  <item><link>https://news.google.com/rss/articles/3</link></item>
                  <item>
                    <title>금리 동결 - 이데일리</title>
                    <source url="https://edaily.co.kr">이데일리</source>
                  </item>
                </channel></rss>
                """;

        List<BuzzHeadline> result = client.parse(xml.getBytes(StandardCharsets.UTF_8));

        assertThat(result).extracting(BuzzHeadline::title).containsExactly("금리 동결");
    }

    /** 신호를 끄면 네트워크를 타지 않고 빈 값을 돌려줘야 한다. */
    @Test
    @DisplayName("화제도 신호가 꺼져 있으면 조회하지 않고 빈 신호를 반환한다")
    void fetch_returnsEmptyWhenDisabled() {
        GoogleNewsBuzzSignalClient disabled = new GoogleNewsBuzzSignalClient(
                new BriefingProperties(8, 7, new BriefingProperties.Buzz(false, List.of("증시"), 10)));

        assertThat(disabled.fetch().isEmpty()).isTrue();
    }

    @Test
    @DisplayName("검색어가 비어 있으면 조회하지 않고 빈 신호를 반환한다")
    void fetch_returnsEmptyWhenNoQuery() {
        GoogleNewsBuzzSignalClient noQuery = new GoogleNewsBuzzSignalClient(
                new BriefingProperties(8, 7, new BriefingProperties.Buzz(true, List.of(), 10)));

        assertThat(noQuery.fetch().isEmpty()).isTrue();
    }
}
