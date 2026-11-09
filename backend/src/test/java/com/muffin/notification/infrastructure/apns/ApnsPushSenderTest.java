package com.muffin.notification.infrastructure.apns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.muffin.notification.application.dispatch.PushMessage;
import com.muffin.notification.application.dispatch.PushResult;
import com.muffin.notification.domain.devicetoken.ApnsEnvironment;
import com.muffin.notification.domain.devicetoken.DeviceToken;
import com.muffin.notification.domain.devicetoken.DeviceTokenRepository;
import java.security.interfaces.ECPrivateKey;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class ApnsPushSenderTest {

    private static final Long USER_ID = 1L;
    private static final String TOPIC = "kr.ai.muffin";
    private static final PushMessage MESSAGE = new PushMessage("제목", "본문", "muffin://news/1");
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Mock
    private DeviceTokenRepository deviceTokenRepository;

    private MockRestServiceServer server;
    private ApnsPushSender sender;

    @BeforeEach
    void setUp() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        ApnsProviderToken providerToken = new ApnsProviderToken(
                "TEAMID1234",
                "KEYID12345",
                (ECPrivateKey) ApnsTestKeys.newKeyPair().getPrivate(),
                Clock.systemUTC());
        sender = new ApnsPushSender(builder.build(), providerToken, TOPIC, deviceTokenRepository);
    }

    private DeviceToken registered(String token, ApnsEnvironment environment) {
        DeviceToken device = DeviceToken.register(USER_ID, token, environment);
        when(deviceTokenRepository.findAllByUserId(USER_ID)).thenReturn(List.of(device));
        return device;
    }

    /** 다른 기기 로그인으로 endsAt에 로그아웃될 기기의 토큰. */
    private static DeviceToken leaving(String token, LocalDateTime endsAt) {
        DeviceToken device = DeviceToken.register(USER_ID, token, ApnsEnvironment.PRODUCTION);
        ReflectionTestUtils.setField(device, "expiresAt", endsAt);
        return device;
    }

    private static String apnsError(String reason) {
        return "{\"reason\":\"" + reason + "\"}";
    }

    @Test
    @DisplayName("등록된 기기가 없으면 APNs를 부르지 않고 NO_DEVICE")
    void send_noDevice() {
        when(deviceTokenRepository.findAllByUserId(USER_ID)).thenReturn(List.of());

        assertThat(sender.send(USER_ID, MESSAGE)).isEqualTo(PushResult.NO_DEVICE);
        server.verify();
    }

    @Test
    @DisplayName("출시 빌드 토큰은 운영 서버로, 애플이 요구하는 헤더와 제목·본문·deepLink 페이로드로 보낸다")
    void send_production() {
        registered("abc123", ApnsEnvironment.PRODUCTION);
        server.expect(requestTo("https://api.push.apple.com/3/device/abc123"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("authorization", startsWith("bearer ")))
                .andExpect(header("apns-topic", TOPIC))
                .andExpect(header("apns-push-type", "alert"))
                .andExpect(jsonPath("$.aps.alert.title").value("제목"))
                .andExpect(jsonPath("$.aps.alert.body").value("본문"))
                .andExpect(jsonPath("$.deepLink").value("muffin://news/1"))
                .andRespond(withSuccess());

        assertThat(sender.send(USER_ID, MESSAGE)).isEqualTo(PushResult.SENT);
        server.verify();
    }

    @Test
    @DisplayName("Xcode 빌드 토큰은 개발용(sandbox) 서버로 보낸다")
    void send_sandbox() {
        registered("abc123", ApnsEnvironment.SANDBOX);
        server.expect(requestTo("https://api.sandbox.push.apple.com/3/device/abc123"))
                .andRespond(withSuccess());

        assertThat(sender.send(USER_ID, MESSAGE)).isEqualTo(PushResult.SENT);
        server.verify();
    }

    @Test
    @DisplayName("410(앱 삭제 등으로 토큰 무효)이면 토큰을 지우고 NO_DEVICE")
    void send_unregistered_deletesToken() {
        DeviceToken device = registered("abc123", ApnsEnvironment.PRODUCTION);
        server.expect(requestTo("https://api.push.apple.com/3/device/abc123"))
                .andRespond(withStatus(HttpStatus.GONE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(apnsError("Unregistered")));

        assertThat(sender.send(USER_ID, MESSAGE)).isEqualTo(PushResult.NO_DEVICE);
        verify(deviceTokenRepository).delete(device);
    }

    @Test
    @DisplayName("400 BadDeviceToken이면 토큰을 지우고 NO_DEVICE")
    void send_badDeviceToken_deletesToken() {
        DeviceToken device = registered("abc123", ApnsEnvironment.PRODUCTION);
        server.expect(requestTo("https://api.push.apple.com/3/device/abc123"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(apnsError("BadDeviceToken")));

        assertThat(sender.send(USER_ID, MESSAGE)).isEqualTo(PushResult.NO_DEVICE);
        verify(deviceTokenRepository).delete(device);
    }

    @Test
    @DisplayName("400 DeviceTokenNotForTopic은 우리 설정 문제라 토큰을 지우지 않고 FAILED")
    void send_wrongTopic_keepsToken() {
        DeviceToken device = registered("abc123", ApnsEnvironment.PRODUCTION);
        server.expect(requestTo("https://api.push.apple.com/3/device/abc123"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(apnsError("DeviceTokenNotForTopic")));

        assertThat(sender.send(USER_ID, MESSAGE)).isEqualTo(PushResult.FAILED);
        verify(deviceTokenRepository, never()).delete(device);
    }

    @Test
    @DisplayName("403(provider token 거절)이면 FAILED이고, 다음 발송은 새 provider token으로 보낸다")
    void send_forbidden_renewsProviderToken() {
        registered("abc123", ApnsEnvironment.PRODUCTION);
        List<String> authorizations = new ArrayList<>();
        server.expect(requestTo("https://api.push.apple.com/3/device/abc123"))
                .andExpect(request -> authorizations.add(request.getHeaders().getFirst("authorization")))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(apnsError("ExpiredProviderToken")));
        server.expect(requestTo("https://api.push.apple.com/3/device/abc123"))
                .andExpect(request -> authorizations.add(request.getHeaders().getFirst("authorization")))
                .andRespond(withSuccess());

        assertThat(sender.send(USER_ID, MESSAGE)).isEqualTo(PushResult.FAILED);
        assertThat(sender.send(USER_ID, MESSAGE)).isEqualTo(PushResult.SENT);

        server.verify();
        assertThat(authorizations.get(1)).isNotEqualTo(authorizations.get(0));
    }

    @Test
    @DisplayName("현재 기기와 곧 로그아웃될 기기 모두에 보내고, 한 기기라도 전달하면 SENT")
    void send_currentAndLeavingDevices_sendsToBoth() {
        DeviceToken current = DeviceToken.register(USER_ID, "current", ApnsEnvironment.PRODUCTION);
        DeviceToken leaving = leaving("leaving", LocalDateTime.now(KST).plusMinutes(30));
        when(deviceTokenRepository.findAllByUserId(USER_ID)).thenReturn(List.of(current, leaving));
        server.expect(requestTo("https://api.push.apple.com/3/device/current"))
                .andRespond(withStatus(HttpStatus.GONE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(apnsError("Unregistered")));
        server.expect(requestTo("https://api.push.apple.com/3/device/leaving")).andRespond(withSuccess());

        assertThat(sender.send(USER_ID, MESSAGE)).isEqualTo(PushResult.SENT);
        server.verify();
        verify(deviceTokenRepository).delete(current);
    }

    @Test
    @DisplayName("로그아웃 시각이 지난 기기는 보내지 않고 토큰을 지운다")
    void send_expiredDevice_deletedWithoutSending() {
        DeviceToken expired = leaving("expired", LocalDateTime.now(KST).minusMinutes(1));
        when(deviceTokenRepository.findAllByUserId(USER_ID)).thenReturn(List.of(expired));

        assertThat(sender.send(USER_ID, MESSAGE)).isEqualTo(PushResult.NO_DEVICE);
        server.verify();
        verify(deviceTokenRepository).delete(expired);
    }

    @Test
    @DisplayName("한 기기에서 서버 오류가 나도 다른 기기 발송은 이어진다")
    void send_serverErrorOnOneDevice_continues() {
        DeviceToken current = DeviceToken.register(USER_ID, "current", ApnsEnvironment.PRODUCTION);
        DeviceToken leaving = leaving("leaving", LocalDateTime.now(KST).plusMinutes(30));
        when(deviceTokenRepository.findAllByUserId(USER_ID)).thenReturn(List.of(current, leaving));
        server.expect(requestTo("https://api.push.apple.com/3/device/current"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo("https://api.push.apple.com/3/device/leaving")).andRespond(withSuccess());

        assertThat(sender.send(USER_ID, MESSAGE)).isEqualTo(PushResult.SENT);
        server.verify();
    }

    @Test
    @DisplayName("모든 기기 발송이 실패하면 FAILED")
    void send_allDevicesFail_failed() {
        registered("abc123", ApnsEnvironment.PRODUCTION);
        server.expect(requestTo("https://api.push.apple.com/3/device/abc123"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThat(sender.send(USER_ID, MESSAGE)).isEqualTo(PushResult.FAILED);
    }
}
