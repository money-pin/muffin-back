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
}
