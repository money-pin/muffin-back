package com.muffin.notification.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muffin.auth.application.RefreshTokenIssuer;
import com.muffin.auth.application.refresh.RefreshCommandService;
import com.muffin.auth.domain.AccessTokenProvider;
import com.muffin.auth.domain.refreshtoken.RefreshTokenRepository;
import com.muffin.notification.domain.devicetoken.ApnsEnvironment;
import com.muffin.notification.domain.devicetoken.DeviceToken;
import com.muffin.notification.domain.devicetoken.DeviceTokenRepository;
import com.muffin.user.domain.User;
import com.muffin.user.domain.UserRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DeviceTokenControllerTest {

    private static final Long USER_A = 100L;
    private static final Long USER_B = 200L;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccessTokenProvider accessTokenProvider;

    @Autowired
    private DeviceTokenRepository deviceTokenRepository;

    @Autowired
    private RefreshTokenIssuer refreshTokenIssuer;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RefreshCommandService refreshCommandService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void cleanUp() {
        deviceTokenRepository.deleteAll();
        refreshTokenRepository.deleteAll();
    }

    /** 로그인과 같은 순서로 세션을 시작한 뒤 access token을 발급한다. */
    private String loggedIn(Long userId) {
        refreshTokenIssuer.startSession(userId);
        return accessTokenProvider.issue(
                userId,
                "USER",
                refreshTokenRepository.findByUserId(userId).orElseThrow().getSessionId());
    }

    private ResultActions register(Long userId, String body) throws Exception {
        return registerWith(loggedIn(userId), body);
    }

    private ResultActions registerWith(String accessToken, String body) throws Exception {
        return mockMvc.perform(put("/api/notifications/device-token")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static String body(String token) {
        return "{\"token\":\"" + token + "\",\"environment\":\"PRODUCTION\"}";
    }

    private ResultActions register(Long userId, String token, String environment) throws Exception {
        return register(userId, "{\"token\":\"" + token + "\",\"environment\":\"" + environment + "\"}");
    }

    @Test
    @DisplayName("처음 등록하면 토큰이 저장된다")
    void register_new() throws Exception {
        register(USER_A, "token-1", "PRODUCTION").andExpect(status().isOk());

        DeviceToken saved = deviceTokenRepository.findByToken("token-1").orElseThrow();
        assertThat(saved.getUserId()).isEqualTo(USER_A);
        assertThat(saved.getEnvironment()).isEqualTo(ApnsEnvironment.PRODUCTION);
    }

    @Test
    @DisplayName("같은 토큰을 다시 보내도 행은 1개다")
    void register_sameTokenAgain_idempotent() throws Exception {
        register(USER_A, "token-1", "PRODUCTION").andExpect(status().isOk());
        register(USER_A, "token-1", "PRODUCTION").andExpect(status().isOk());

        assertThat(deviceTokenRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("새 토큰을 보내면 옛 토큰은 지워지고 새 토큰만 남는다")
    void register_newToken_replacesOld() throws Exception {
        register(USER_A, "token-old", "PRODUCTION").andExpect(status().isOk());
        register(USER_A, "token-new", "SANDBOX").andExpect(status().isOk());

        assertThat(deviceTokenRepository.findByToken("token-old")).isEmpty();
        assertThat(deviceTokenRepository.findByToken("token-new").orElseThrow().getEnvironment())
                .isEqualTo(ApnsEnvironment.SANDBOX);
        assertThat(deviceTokenRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("다른 기기에서 로그인해 새 토큰을 등록해도, 곧 로그아웃될 이전 기기의 토큰은 남는다")
    void register_newDevice_keepsLeavingDeviceToken() throws Exception {
        register(USER_A, "old-device", "PRODUCTION").andExpect(status().isOk());
        endAt("old-device", LocalDateTime.now(KST).plusMinutes(30));

        register(USER_A, "new-device", "PRODUCTION").andExpect(status().isOk());

        assertThat(deviceTokenRepository.findByToken("old-device")).isPresent();
        assertThat(deviceTokenRepository.findByToken("new-device").orElseThrow().getExpiresAt())
                .isNull();
    }

    @Test
    @DisplayName("같은 기기에서 다시 로그인해 같은 토큰을 등록하면 종료 시각이 지워진다")
    void register_sameDeviceAfterRelogin_clearsEnd() throws Exception {
        register(USER_A, "my-device", "PRODUCTION").andExpect(status().isOk());
        endAt("my-device", LocalDateTime.now(KST).plusMinutes(30));

        register(USER_A, "my-device", "PRODUCTION").andExpect(status().isOk());

        assertThat(deviceTokenRepository.findByToken("my-device").orElseThrow().getExpiresAt())
                .isNull();
    }

    /** 다른 기기 로그인 때 서버가 적는 종료 시각을 흉내 낸다. */
    private void endAt(String token, LocalDateTime endsAt) {
        jdbcTemplate.update("update device_token set expires_at = ? where token = ?", endsAt, token);
    }

    @Test
    @DisplayName("같은 기기에서 다른 계정으로 로그인하면 토큰의 주인이 옮겨가고, 그 계정의 옛 토큰은 지워진다")
    void register_tokenOfOtherUser_movesOwner() throws Exception {
        register(USER_A, "shared-device", "PRODUCTION").andExpect(status().isOk());
        register(USER_B, "b-old-device", "PRODUCTION").andExpect(status().isOk());

        register(USER_B, "shared-device", "PRODUCTION").andExpect(status().isOk());

        assertThat(deviceTokenRepository
                        .findByToken("shared-device")
                        .orElseThrow()
                        .getUserId())
                .isEqualTo(USER_B);
        assertThat(deviceTokenRepository.findByToken("b-old-device")).isEmpty();
        assertThat(deviceTokenRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("token이 비어 있으면 400 COMMON_400_002")
    void register_blankToken_badRequest() throws Exception {
        register(USER_A, " ", "PRODUCTION")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400_002"));
    }

    @Test
    @DisplayName("environment가 SANDBOX/PRODUCTION이 아니면 400 COMMON_400_001")
    void register_unknownEnvironment_badRequest() throws Exception {
        register(USER_A, "token-1", "PROD")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400_001"));
    }

    @Test
    @DisplayName("다른 기기에서 로그인하기 전에 발급된 access token이면 401 AUTH_401_005이고 현재 기기 토큰을 덮어쓰지 않는다")
    void register_staleSession_rejected() throws Exception {
        String oldDeviceAccessToken = loggedIn(USER_A);
        register(USER_A, "new-device", "PRODUCTION").andExpect(status().isOk());
        refreshTokenIssuer.startSession(USER_A);

        registerWith(oldDeviceAccessToken, body("old-device"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_401_005"));

        assertThat(deviceTokenRepository.findByToken("new-device")).isPresent();
        assertThat(deviceTokenRepository.findByToken("old-device")).isEmpty();
    }

    @Test
    @DisplayName("로그아웃·탈퇴로 세션이 없으면 남은 access token으로 등록할 수 없다(401 AUTH_401_005)")
    void register_noSession_rejected() throws Exception {
        String accessToken = loggedIn(USER_A);
        refreshTokenRepository.deleteAll();

        registerWith(accessToken, body("token-1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_401_005"));
        assertThat(deviceTokenRepository.count()).isZero();
    }

    @Test
    @DisplayName("재발급으로 받은 access token은 같은 세션이라 등록할 수 있다")
    void register_afterRefresh_allowed() throws Exception {
        User user = userRepository.save(User.register(null, UUID.randomUUID().toString(), "홍길동", null));
        String rawRefreshToken = refreshTokenIssuer.startSession(user.getUserId());
        String refreshedAccessToken =
                refreshCommandService.refresh(rawRefreshToken).accessToken();

        registerWith(refreshedAccessToken, body("token-1")).andExpect(status().isOk());

        userRepository.delete(user);
    }

    @Test
    @DisplayName("environment가 없으면 400 COMMON_400_002")
    void register_missingEnvironment_badRequest() throws Exception {
        register(USER_A, "{\"token\":\"token-1\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400_002"));
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 401")
    void register_unauthorized() throws Exception {
        mockMvc.perform(put("/api/notifications/device-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"token-1\",\"environment\":\"PRODUCTION\"}"))
                .andExpect(status().isUnauthorized());
    }
}
