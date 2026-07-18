package com.muffin.news.infrastructure.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.muffin.news.application.exception.NewsErrorCode;
import com.muffin.news.application.exception.NewsException;
import com.muffin.news.application.reconstruction.NewsReconstructionRequest;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class OpenAiRewriterExceptionTest {

    @Test
    void requestFailureUsesReconstructionRequestErrorCode() {
        TestContext context = context();
        context.server().expect(request -> {}).andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> context.rewriter().rewrite(request()))
                .isInstanceOfSatisfying(NewsException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(NewsErrorCode.NEWS_RECONSTRUCTION_REQUEST_FAILED);
                    assertThat(exception.getCause()).isNotNull();
                });
        context.server().verify();
    }

    @Test
    void invalidResponseUsesReconstructionResponseErrorCode() {
        TestContext context = context();
        context.server().expect(request -> {}).andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> context.rewriter().rewrite(request()))
                .isInstanceOfSatisfying(NewsException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(NewsErrorCode.NEWS_RECONSTRUCTION_RESPONSE_INVALID);
                    assertThat(exception.getCause()).isNotNull();
                });
        context.server().verify();
    }

    private static TestContext context() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiRewriter rewriter = new OpenAiRewriter(
                builder.build(),
                new ObjectMapper(),
                new OpenAiClientProperties("test-key", "https://api.test/responses"),
                new NewsReconstructionProperties("test-model", 1_000));
        return new TestContext(rewriter, server);
    }

    private static NewsReconstructionRequest request() {
        return new NewsReconstructionRequest("경제 뉴스", "매일경제", LocalDateTime.of(2026, 7, 18, 6, 0), "뉴스 원문");
    }

    private record TestContext(OpenAiRewriter rewriter, MockRestServiceServer server) {}
}
