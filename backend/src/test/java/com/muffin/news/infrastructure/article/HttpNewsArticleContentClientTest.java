package com.muffin.news.infrastructure.article;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.muffin.news.application.exception.NewsErrorCode;
import com.muffin.news.application.exception.NewsException;
import com.muffin.news.infrastructure.openai.NewsReconstructionProperties;
import org.junit.jupiter.api.Test;

class HttpNewsArticleContentClientTest {

    @Test
    void fetchFailureUsesArticleContentErrorCode() {
        HttpNewsArticleContentClient client =
                new HttpNewsArticleContentClient(new NewsReconstructionProperties("test-model", 1_000));

        assertThatThrownBy(() -> client.fetch("not-a-valid-url"))
                .isInstanceOfSatisfying(NewsException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(NewsErrorCode.ARTICLE_CONTENT_FETCH_FAILED);
                    assertThat(exception.getCause()).isNotNull();
                });
    }

    @Test
    void limitLengthRejectsNonPositiveMax() {
        assertThatThrownBy(() -> HttpNewsArticleContentClient.limitLength("본문", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HttpNewsArticleContentClient.limitLength("본문", -5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void limitLengthTruncatesToMax() {
        assertThat(HttpNewsArticleContentClient.limitLength("abcdef", 3)).isEqualTo("abc");
        assertThat(HttpNewsArticleContentClient.limitLength("abc", 10)).isEqualTo("abc");
    }

    @Test
    void limitLengthDoesNotSplitSurrogatePair() {
        // "😀" 는 서로게이트 쌍(2 char). 경계가 쌍의 중간(1)에 걸리면 깨진 문자 대신 앞에서 잘라야 한다.
        String emoji = "😀"; // 😀
        assertThat(HttpNewsArticleContentClient.limitLength("a" + emoji, 2)).isEqualTo("a");
    }
}
