package com.muffin.auth.infrastructure.apple;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.muffin.auth.application.AppleProperties;
import com.muffin.auth.application.withdraw.AppleAuthorizationClient;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/** 애플 토큰 교환(/auth/token)·철회(/auth/revoke) API를 호출한다. iOS 네이티브 로그인이라 redirect_uri는 보내지 않는다. */
@Component
public class AppleTokenClient implements AppleAuthorizationClient {

    private final RestClient appleApiRestClient;
    private final AppleClientSecretGenerator clientSecretGenerator;
    private final AppleProperties appleProperties;

    public AppleTokenClient(
            RestClient appleApiRestClient,
            AppleClientSecretGenerator clientSecretGenerator,
            AppleProperties appleProperties) {
        this.appleApiRestClient = appleApiRestClient;
        this.clientSecretGenerator = clientSecretGenerator;
        this.appleProperties = appleProperties;
    }

    @Override
    public AppleTokens exchange(String authorizationCode) {
        MultiValueMap<String, String> form = clientCredentials();
        form.add("code", authorizationCode);
        form.add("grant_type", "authorization_code");

        TokenResponse response = appleApiRestClient
                .post()
                .uri("/auth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);
        return new AppleTokens(response.refreshToken(), response.idToken());
    }

    @Override
    public void revoke(String refreshToken) {
        MultiValueMap<String, String> form = clientCredentials();
        form.add("token", refreshToken);
        form.add("token_type_hint", "refresh_token");

        appleApiRestClient
                .post()
                .uri("/auth/revoke")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .toBodilessEntity();
    }

    private MultiValueMap<String, String> clientCredentials() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", appleProperties.bundleId());
        form.add("client_secret", clientSecretGenerator.generate());
        return form;
    }

    // access_token은 쓰지 않아 받지 않는다(철회는 refresh_token으로 한다).
    private record TokenResponse(
            @JsonProperty("refresh_token") String refreshToken, @JsonProperty("id_token") String idToken) {}
}
