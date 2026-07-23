package com.muffin.news.infrastructure.article;

import com.muffin.news.application.exception.NewsErrorCode;
import com.muffin.news.application.exception.NewsException;
import com.muffin.news.application.reconstruction.NewsArticleContentClient;
import com.muffin.news.infrastructure.openai.NewsReconstructionProperties;
import com.muffin.news.infrastructure.retry.RetryExecutor;
import java.io.IOException;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "muffin.news.ai.enabled", havingValue = "true")
public class HttpNewsArticleContentClient implements NewsArticleContentClient {

    private final NewsReconstructionProperties properties;

    /** 요청 타임아웃 시간 20000ms */
    private static final int REQUEST_TIMEOUT_MS = 20_000;

    /** 언론사별 HTML 구조에서 기사 본문 영역을 찾기 위한 CSS 선택자 목록 */
    private static final String CONTENT_SELECTOR =
            """
        [itemprop=articleBody],
        article,
        #articleBody,
        #article_body,
        .article-body,
        .article_body,
        .news_cnt_detail_wrap,
        .art_txt,
        .view_txt
        """;

    /** 기사 본문을 추출하기 전에 광고, 메뉴, 관련 기사 등 불필요한 요소를 제거하기 위한 CSS 선택자 목록 */
    private static final String NOISE_SELECTOR =
            """
        script,
        style,
        noscript,
        iframe,
        nav,
        header,
        footer,
        aside,
        form,
        .advertisement,
        .ad,
        .reporter,
        .related-news,
        .share
        """;

    /** 원문 HTML을 조회하고 기사 본문 후보 중 가장 긴 내용을 반환한다. */
    @Override
    public String fetch(String originalUrl) {
        try {
            Document document = RetryExecutor.execute(
                    "News article request",
                    originalUrl,
                    "News article retry wait was interrupted",
                    () -> request(originalUrl),
                    HttpNewsArticleContentClient::isRetryableException);

            String content = extractContent(document);

            if (content.isBlank()) {
                throw new NewsException(NewsErrorCode.ARTICLE_CONTENT_FETCH_FAILED);
            }

            return limitLength(content, properties.maxSourceLength());
        } catch (NewsException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new NewsException(NewsErrorCode.ARTICLE_CONTENT_FETCH_FAILED, exception);
        }
    }

    /** 기사 URL에 단일 HTTP 요청을 보내 HTML 문서로 변환한다. */
    private Document request(String originalUrl) {
        try {
            return Jsoup.connect(originalUrl)
                    .userAgent("Muffin-News/1.0")
                    .timeout(REQUEST_TIMEOUT_MS)
                    .followRedirects(true)
                    .get();
        } catch (HttpStatusException exception) {
            throw new ArticleHttpStatusException(exception.getStatusCode());
        } catch (IOException exception) {
            throw new ArticleAccessException(exception);
        }
    }

    /** 불필요한 요소를 제거한 뒤 가장 가능성 높은 기사 본문을 선택한다. */
    private static String extractContent(Document document) {
        document.select(NOISE_SELECTOR).remove();

        return document.select(CONTENT_SELECTOR).stream()
                .map(Element::text)
                .map(String::strip)
                .filter(content -> !content.isBlank())
                .max(Comparator.comparingInt(String::length))
                .orElseGet(() -> document.body().text().strip());
    }

    // 테스트에서 경계 로직(음수 인덱스 방어, 서로게이트 쌍 절단)을 직접 검증하기 위해 패키지-프라이빗으로 둔다.
    static String limitLength(String content, int maxLength) {
        if (maxLength <= 0) {
            throw new IllegalArgumentException("maxLength는 1 이상이어야 합니다: " + maxLength);
        }
        if (content.length() <= maxLength) {
            return content;
        }
        int end = maxLength;
        // 이모지 같은 보충 문자(서로게이트 쌍)의 중간을 자르면 깨진 문자가 AI 요청에 섞이므로 경계를 한 칸 당긴다.
        if (Character.isHighSurrogate(content.charAt(end - 1))) {
            end--;
        }
        return content.substring(0, end);
    }

    /** 네트워크 오류 또는 재시도 가능한 HTTP 상태인지 확인한다. */
    private static boolean isRetryableException(RuntimeException exception) {
        return exception instanceof ArticleAccessException
                || exception instanceof ArticleHttpStatusException statusException
                        && isRetryableStatus(statusException.statusCode());
    }

    private static boolean isRetryableStatus(int statusCode) {
        return statusCode == 408 || statusCode == 429 || statusCode >= 500;
    }

    private static final class ArticleAccessException extends RuntimeException {

        private ArticleAccessException(IOException cause) {
            super(cause);
        }
    }

    private static final class ArticleHttpStatusException extends RuntimeException {

        private final int statusCode;

        private ArticleHttpStatusException(int statusCode) {
            this.statusCode = statusCode;
        }

        private int statusCode() {
            return statusCode;
        }
    }
}
