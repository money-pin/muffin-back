package com.muffin.auth.infrastructure.kakao;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.muffin.auth.application.KakaoProperties;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

class KakaoUnlinkClientTest {

    private static final String BASE_URL = "http://kakao.test";

    private MockRestServiceServer server;
    private KakaoUnlinkClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        KakaoProperties properties = new KakaoProperties(
                "app-key",
                "unused",
                "unused",
                "test-admin-key",
                BASE_URL,
                Duration.ofSeconds(3),
                Duration.ofSeconds(5));
        client = new KakaoUnlinkClient(builder.build(), properties);
    }

    @Test
    @DisplayName("어드민 키 헤더와 회원번호를 form으로 담아 연결 끊기 API를 호출한다")
    void unlink_sendsAdminKeyAndTargetId() {
        server.expect(requestTo(BASE_URL + "/v1/user/unlink"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "KakaoAK test-admin-key"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().formDataContains(Map.of("target_id_type", "user_id", "target_id", "3812345678")))
                .andRespond(withSuccess("{\"id\":3812345678}", MediaType.APPLICATION_JSON));

        client.unlink("3812345678");

        server.verify();
    }

    @Test
    @DisplayName("카카오가 에러를 응답하면 예외를 던진다(처리는 호출하는 리스너가 맡음)")
    void unlink_throwsOnErrorResponse() {
        server.expect(requestTo(BASE_URL + "/v1/user/unlink")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.unlink("3812345678")).isInstanceOf(RestClientResponseException.class);
    }
}
