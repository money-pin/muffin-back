package com.muffin.news.infrastructure.rss;

import static org.assertj.core.api.Assertions.assertThat;

import com.muffin.news.application.rss.RssArticle;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class RssFeedTest {

    private final HttpRssFeedClient client = new HttpRssFeedClient();

    /** link가 없는 item은 건너뛰고, 유효한 item만 RssArticle로 파싱한다. */
    @Test
    void parsesRssItemsAndSkipsItemsWithoutAUrl() throws Exception {
        String xml =
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0"><channel>
                  <item>
                    <title><![CDATA[금리 인하 기대감 확대]]></title>
                    <link>https://example.com/news/1</link>
                    <description><![CDATA[기사 요약]]></description>
                    <pubDate>Sun, 12 Jul 2026 06:00:00 +0900</pubDate>
                  </item>
                  <item><title>URL 없는 기사</title></item>
                </channel></rss>
                """;

        List<RssArticle> result = client.parse(xml.getBytes(StandardCharsets.UTF_8));

        assertThat(result)
                .containsExactly(new RssArticle(
                        "금리 인하 기대감 확대", "https://example.com/news/1", "기사 요약", LocalDateTime.of(2026, 7, 12, 6, 0)));
    }
}
