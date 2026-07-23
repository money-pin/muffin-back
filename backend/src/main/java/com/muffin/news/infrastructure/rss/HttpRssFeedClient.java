package com.muffin.news.infrastructure.rss;

import com.muffin.news.application.rss.RssArticle;
import com.muffin.news.application.rss.RssFeedClient;
import com.muffin.news.infrastructure.retry.RetryExecutor;
import java.io.ByteArrayInputStream;
import java.io.IOException;
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
        HttpResponse<byte[]> response;
        try {
            response = RetryExecutor.execute(
                    "RSS request",
                    feedUrl,
                    "RSS retry wait was interrupted",
                    () -> request(feedUrl),
                    HttpRssFeedClient::isRetryableException);
        } catch (RssAccessException exception) {
            throw new IllegalStateException("Failed to fetch RSS feed", exception.getCause());
        } catch (RssHttpStatusException exception) {
            throw new IllegalStateException("RSS server returned HTTP " + exception.statusCode());
        }
        try {
            return parse(response.body());
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to parse RSS feed", exception);
        }
    }

    /** RSS 서버에 단일 HTTP 요청을 보내고 비정상 응답을 재시도 판정용 예외로 변환한다. */
    private HttpResponse<byte[]> request(String feedUrl) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(feedUrl))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "Muffin-RSS/1.0")
                .GET()
                .build();
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RssHttpStatusException(response.statusCode());
            }
            return response;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("RSS request was interrupted", exception);
        } catch (IOException exception) {
            throw new RssAccessException(exception);
        }
    }

    /** 네트워크 오류 또는 재시도 가능한 RSS HTTP 응답인지 확인한다. */
    private static boolean isRetryableException(RuntimeException exception) {
        return exception instanceof RssAccessException
                || exception instanceof RssHttpStatusException statusException
                        && isRetryableStatus(statusException.statusCode());
    }

    private static boolean isRetryableStatus(int statusCode) {
        return statusCode == 408 || statusCode == 429 || statusCode >= 500;
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
            articles.add(new RssArticle(
                    title,
                    url,
                    text(item, "description"),
                    thumbnailUrl(item),
                    parsePublishedAt(text(item, "pubDate"))));
        }
        return articles;
    }

    /** XML 요소에서 지정한 태그의 텍스트를 안전하게 추출한다. */
    private static String text(Element element, String tagName) {
        NodeList nodes = element.getElementsByTagName(tagName);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent().strip();
    }

    /** news.thumbnail_url 컬럼 길이. 넘는 URL은 잘라 저장할 수 없으므로 버린다. */
    private static final int MAX_THUMBNAIL_URL_LENGTH = 1_000;

    /**
     * item에서 대표 이미지 URL을 추출한다. 매일경제는 {@code <media:content medium="image" url="..."/>}를 쓰며,
     * 다른 피드 대비 {@code <media:thumbnail>}과 {@code <enclosure type="image/...">}도 순서대로 확인한다. 없으면 null.
     */
    private static String thumbnailUrl(Element item) {
        String imageMediaContent = imageMediaContentUrl(item);
        if (imageMediaContent != null) {
            return imageMediaContent;
        }
        String mediaThumbnail = attributeOf(item, "media:thumbnail", "url");
        if (mediaThumbnail != null) {
            return mediaThumbnail;
        }
        NodeList enclosures = item.getElementsByTagName("enclosure");
        for (int index = 0; index < enclosures.getLength(); index++) {
            Element enclosure = (Element) enclosures.item(index);
            if (enclosure.getAttribute("type").startsWith("image/")) {
                return normalizeUrl(enclosure.getAttribute("url"));
            }
        }
        return null;
    }

    /**
     * {@code media:content}는 이미지뿐 아니라 video/audio/document도 표현할 수 있으므로, {@code medium="image"}
     * 또는 {@code type="image/*"}로 이미지임이 명시된 항목만 채택한다. 비이미지(예: 앞선 video)는 건너뛰고 다음
     * 후보로 넘어가, 뒤에 오는 유효한 {@code media:thumbnail} 폴백이 막히지 않도록 한다.
     */
    private static String imageMediaContentUrl(Element item) {
        NodeList mediaContents = item.getElementsByTagName("media:content");
        for (int index = 0; index < mediaContents.getLength(); index++) {
            Element mediaContent = (Element) mediaContents.item(index);
            if (isImageMedia(mediaContent)) {
                String url = normalizeUrl(mediaContent.getAttribute("url"));
                if (url != null) {
                    return url;
                }
            }
        }
        return null;
    }

    private static boolean isImageMedia(Element mediaContent) {
        return "image".equalsIgnoreCase(mediaContent.getAttribute("medium"))
                || mediaContent.getAttribute("type").startsWith("image/");
    }

    private static String attributeOf(Element item, String tagName, String attributeName) {
        NodeList nodes = item.getElementsByTagName(tagName);
        return nodes.getLength() == 0 ? null : normalizeUrl(((Element) nodes.item(0)).getAttribute(attributeName));
    }

    private static String normalizeUrl(String url) {
        String stripped = url.strip();
        if (stripped.isEmpty() || stripped.length() > MAX_THUMBNAIL_URL_LENGTH) {
            return null;
        }
        return stripped;
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

    /** RSS 서버 연결 또는 응답 수신 중 발생한 I/O 오류를 나타낸다. */
    private static final class RssAccessException extends RuntimeException {

        private RssAccessException(IOException cause) {
            super(cause);
        }
    }

    /** RSS 서버가 반환한 비정상 HTTP 상태 코드를 재시도 판정까지 전달한다. */
    private static final class RssHttpStatusException extends RuntimeException {

        private final int statusCode;

        private RssHttpStatusException(int statusCode) {
            this.statusCode = statusCode;
        }

        private int statusCode() {
            return statusCode;
        }
    }
}
