package com.muffin.briefing.infrastructure.googlenews;

import com.muffin.briefing.application.BriefingProperties;
import com.muffin.briefing.application.generation.BuzzSignal;
import com.muffin.briefing.application.generation.BuzzSignal.BuzzHeadline;
import com.muffin.briefing.application.generation.BuzzSignal.BuzzTopic;
import com.muffin.briefing.application.generation.BuzzSignalClient;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Google News RSS에서 화제도를 모은다.
 *
 * <p>제목·출처·링크만 제공하는 피드라 본문을 가져오지 않는다. 여러 매체를 가로질러 같은 주제가 얼마나 반복되는지를 보고, 매경 뉴스 중
 * 무엇이 오늘 진짜 큰 이슈인지 판단하는 근거로만 쓴다.
 *
 * <p>조회 실패는 브리핑 실패가 아니다. 어떤 예외가 나도 빈 신호를 돌려주고, 브리핑은 AI 판단만으로 계속 생성된다.
 */
@Slf4j
@Component
public class GoogleNewsBuzzSignalClient implements BuzzSignalClient {

    private static final String FEED_URL = "https://news.google.com/rss/search?q=%s&hl=ko&gl=KR&ceid=KR:ko";

    /** 프롬프트 크기를 묶어 두기 위한 상한. */
    private static final int MAX_HEADLINES = 60;

    private static final int MAX_TOPICS = 20;

    /** 한 매체만 쓴 단어는 화제라고 보기 어렵다. */
    private static final int MIN_OUTLET_COUNT = 2;

    private static final int MIN_KEYWORD_LENGTH = 2;

    /** 헤드라인에 흔하지만 주제를 가리지 못하는 단어들. */
    private static final Set<String> STOP_WORDS = Set.of(
            "오늘", "내일", "어제", "올해", "작년", "지난", "이번", "관련", "대한", "위해", "통해", "전망", "속보", "단독", "종합", "그리고", "하지만",
            "이라고", "라고", "때문", "가운데", "면서", "에서", "으로", "이다", "한다", "했다", "된다", "됐다", "있다", "없다");

    private final BriefingProperties properties;
    private final HttpClient httpClient;

    public GoogleNewsBuzzSignalClient(BriefingProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.buzz().timeoutSeconds()))
                .build();
    }

    @Override
    public BuzzSignal fetch() {
        if (!properties.buzz().enabled() || properties.buzz().queries().isEmpty()) {
            return BuzzSignal.empty();
        }

        // 같은 기사가 여러 검색어에 걸리므로 제목 기준으로 한 번만 센다.
        Map<String, BuzzHeadline> headlinesByTitle = new LinkedHashMap<>();
        for (String query : properties.buzz().queries()) {
            for (BuzzHeadline headline : fetchQuerySafely(query)) {
                headlinesByTitle.putIfAbsent(headline.title(), headline);
            }
        }

        if (headlinesByTitle.isEmpty()) {
            log.warn("Buzz signal is empty: no headline collected");
            return BuzzSignal.empty();
        }

        List<BuzzHeadline> headlines =
                headlinesByTitle.values().stream().limit(MAX_HEADLINES).toList();
        return new BuzzSignal(extractTopics(headlines), headlines);
    }

    /** 검색어 하나의 조회 실패가 나머지 검색어까지 버리지 않도록 건별로 막는다. */
    private List<BuzzHeadline> fetchQuerySafely(String query) {
        try {
            return parse(request(query));
        } catch (Exception exception) {
            log.warn("Buzz signal query failed: query={}", query, exception);
            return List.of();
        }
    }

    private byte[] request(String query) throws Exception {
        String url = FEED_URL.formatted(URLEncoder.encode(query, StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(properties.buzz().timeoutSeconds()))
                .header("User-Agent", "Muffin-Briefing/1.0")
                .GET()
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Google News returned HTTP " + response.statusCode());
        }
        return response.body();
    }

    /** RSS XML에서 제목과 출처만 뽑는다. XXE 차단 설정은 기존 RSS 파서와 같다. */
    List<BuzzHeadline> parse(byte[] xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

        NodeList items = factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml))
                .getElementsByTagName("item");

        List<BuzzHeadline> headlines = new ArrayList<>();
        for (int index = 0; index < items.getLength(); index++) {
            Element item = (Element) items.item(index);
            String outlet = text(item, "source");
            String title = stripOutletSuffix(text(item, "title"), outlet);
            if (title.isBlank()) {
                continue;
            }
            headlines.add(new BuzzHeadline(title, outlet));
        }
        return headlines;
    }

    private static String text(Element element, String tagName) {
        NodeList nodes = element.getElementsByTagName(tagName);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent().strip();
    }

    /** Google News는 제목 끝에 " - 매체명"을 붙인다. 키워드 집계에 매체명이 섞이지 않도록 떼어 낸다. */
    private static String stripOutletSuffix(String title, String outlet) {
        if (outlet.isBlank()) {
            return title;
        }
        String suffix = " - " + outlet;
        return title.endsWith(suffix)
                ? title.substring(0, title.length() - suffix.length()).strip()
                : title;
    }

    /**
     * 헤드라인에서 반복되는 키워드를 센다. 형태소 분석기 없이 단순 분리하므로 조사가 붙은 형태를 어느 정도 정규화한다. 정확한 추출이
     * 목적이 아니라 "여러 매체가 같은 말을 반복하는가"를 보는 것이므로 이 정도면 충분하고, 놓친 맥락은 헤드라인 원문이 함께 전달되어 보완된다.
     */
    private static List<BuzzTopic> extractTopics(List<BuzzHeadline> headlines) {
        Map<String, Integer> mentionCounts = new HashMap<>();
        Map<String, Set<String>> outletsByKeyword = new HashMap<>();

        for (BuzzHeadline headline : headlines) {
            // 같은 제목 안에서 같은 단어가 반복돼도 기사 1건으로 센다.
            for (String keyword : new LinkedHashSet<>(keywords(headline.title()))) {
                mentionCounts.merge(keyword, 1, Integer::sum);
                outletsByKeyword
                        .computeIfAbsent(keyword, key -> new HashSet<>())
                        .add(headline.outlet());
            }
        }

        return mentionCounts.entrySet().stream()
                .map(entry -> new BuzzTopic(
                        entry.getKey(),
                        entry.getValue(),
                        outletsByKeyword.get(entry.getKey()).size()))
                .filter(topic -> topic.outletCount() >= MIN_OUTLET_COUNT)
                .sorted(Comparator.comparingInt(BuzzTopic::outletCount)
                        .thenComparingInt(BuzzTopic::mentionCount)
                        .reversed()
                        .thenComparing(BuzzTopic::keyword))
                .limit(MAX_TOPICS)
                .toList();
    }

    private static List<String> keywords(String title) {
        List<String> keywords = new ArrayList<>();
        for (String token : title.split("[^\\p{IsHangul}\\p{IsAlphabetic}\\p{IsDigit}]+")) {
            String normalized = stripParticle(token);
            if (normalized.length() >= MIN_KEYWORD_LENGTH && !STOP_WORDS.contains(normalized)) {
                keywords.add(normalized);
            }
        }
        return keywords;
    }

    /** "코스피가"와 "코스피는"이 다른 단어로 세지 않도록 흔한 조사를 떼어 낸다. 어간이 2자 이상 남을 때만 적용한다. */
    private static String stripParticle(String token) {
        for (String particle : List.of("에서는", "에서", "으로", "에게", "부터", "까지", "라고", "이라고")) {
            if (token.length() >= particle.length() + MIN_KEYWORD_LENGTH && token.endsWith(particle)) {
                return token.substring(0, token.length() - particle.length());
            }
        }
        if (token.length() >= MIN_KEYWORD_LENGTH + 1) {
            char last = token.charAt(token.length() - 1);
            if ("은는이가을를의도와과로에".indexOf(last) >= 0) {
                return token.substring(0, token.length() - 1);
            }
        }
        return token;
    }
}
