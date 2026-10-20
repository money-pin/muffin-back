package com.muffin.auth.infrastructure.apple;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.muffin.auth.application.AppleProperties;
import com.muffin.auth.application.withdraw.AppleAuthorizationClient.AppleTokens;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

class AppleTokenClientTest {

    private static final String BASE_URL = "http://apple.test";

    private MockRestServiceServer server;
    private AppleTokenClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        AppleClientSecretGenerator secretGenerator = mock(AppleClientSecretGenerator.class);
        when(secretGenerator.generate()).thenReturn("client-secret-jwt");
        AppleProperties properties =
                new AppleProperties("kr.ai.muffin", "unused", "unused", "unused", "unused", "", BASE_URL, null, null);
        client = new AppleTokenClient(builder.build(), secretGenerator, properties);
    }

    @Test
    @DisplayName("authorization code를 client_id·client_secret과 함께 보내 refresh_token과 id_token을 받는다")
    void exchange_returnsTokens() {
        server.expect(requestTo(BASE_URL + "/auth/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content()
                        .formDataContains(Map.of(
                                "client_id", "kr.ai.muffin",
                                "client_secret", "client-secret-jwt",
                                "code", "auth-code",
                                "grant_type", "authorization_code")))
                .andRespond(withSuccess(
                        """
                        {"access_token":"a","token_type":"Bearer","expires_in":3600,
                         "refresh_token":"r","id_token":"i"}
                        """,
                        MediaType.APPLICATION_JSON));

        AppleTokens tokens = client.exchange("auth-code");

        assertThat(tokens.refreshToken()).isEqualTo("r");
        assertThat(tokens.idToken()).isEqualTo("i");
        server.verify();
    }

    @Test
    @DisplayName("만료·사용된 code 등으로 애플이 400을 주면 예외를 던진다(처리는 호출부가 맡음)")
    void exchange_throwsOnBadRequest() {
        server.expect(requestTo(BASE_URL + "/auth/token"))
                .andRespond(
                        withBadRequest().body("{\"error\":\"invalid_grant\"}").contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.exchange("expired-code")).isInstanceOf(RestClientResponseException.class);
    }

    @Test
    @DisplayName("refresh token을 token_type_hint=refresh_token으로 보내 철회한다")
    void revoke_sendsRefreshToken() {
        server.expect(requestTo(BASE_URL + "/auth/revoke"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content()
                        .formDataContains(Map.of(
                                "client_id", "kr.ai.muffin",
                                "client_secret", "client-secret-jwt",
                                "token", "r",
                                "token_type_hint", "refresh_token")))
                .andRespond(withSuccess());

        client.revoke("r");

        server.verify();
    }
}
