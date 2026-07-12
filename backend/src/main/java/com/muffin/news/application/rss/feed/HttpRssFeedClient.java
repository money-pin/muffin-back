package com.muffin.news.application.rss.feed;

import com.muffin.news.application.rss.RssArticle;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

@Component
public class HttpRssFeedClient implements RssFeedClient {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    /** RSS 서버에 HTTP 요청을 보내고 응답 XML을 기사 목록으로 반환한다. */
    @Override
    public List<RssArticle> fetch(String feedUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(feedUrl))
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "Muffin-RSS/1.0")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("RSS server returned HTTP " + response.statusCode());
            }
            return parse(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("RSS request was interrupted", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to fetch RSS feed", exception);
        }
    }

    /** RSS XML에서 불필요한 요소를 제거한 내용을 articles 객체로 변환한다. */
    List<RssArticle> parse(byte[] xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        NodeList items = factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml))
                .getElementsByTagName("item");
        List<RssArticle> articles = new ArrayList<>();
        for (int index = 0; index < items.getLength(); index++) {
            Element item = (Element) items.item(index);
            String title = text(item, "title");
            String url = text(item, "link");
            if (title.isBlank() || url.isBlank()) {
                continue;
            }
            articles.add(
                    new RssArticle(title, url, text(item, "description"), parsePublishedAt(text(item, "pubDate"))));
        }
        return articles;
    }

    /** XML 요소에서 지정한 태그의 텍스트를 안전하게 추출한다. */
    private static String text(Element element, String tagName) {
        NodeList nodes = element.getElementsByTagName(tagName);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent().strip();
    }

    /** RSS 발행 시각을 KST 기준으로 변환하고 파싱 실패 시 현재 시각을 사용한다. */
    private static LocalDateTime parsePublishedAt(String value) {
        if (value.isBlank()) {
            return LocalDateTime.now(SEOUL);
        }
        try {
            return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
                    .withZoneSameInstant(SEOUL)
                    .toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(value).atZoneSameInstant(SEOUL).toLocalDateTime();
            } catch (DateTimeParseException alsoIgnored) {
                return LocalDateTime.now(SEOUL);
            }
        }
    }
}
