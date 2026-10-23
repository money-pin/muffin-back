package com.muffin.auth.infrastructure.kakao;

import com.muffin.auth.application.KakaoProperties;
import com.muffin.auth.application.withdraw.KakaoAccountUnlinker;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/** 카카오 연결 끊기 API를 어드민 키로 호출한다. */
@Component
public class KakaoUnlinkClient implements KakaoAccountUnlinker {

    private final RestClient kakaoApiRestClient;
    private final String authorization;

    public KakaoUnlinkClient(RestClient kakaoApiRestClient, KakaoProperties kakaoProperties) {
        this.kakaoApiRestClient = kakaoApiRestClient;
        this.authorization = "KakaoAK " + kakaoProperties.adminKey();
    }

    @Override
    public void unlink(String kakaoUserId) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("target_id_type", "user_id");
        form.add("target_id", kakaoUserId);

        kakaoApiRestClient
                .post()
                .uri("/v1/user/unlink")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .toBodilessEntity();
    }
}
