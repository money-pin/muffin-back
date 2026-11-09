package com.muffin.notification.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muffin.auth.domain.AccessTokenProvider;
import com.muffin.notification.domain.devicetoken.ApnsEnvironment;
import com.muffin.notification.domain.devicetoken.DeviceToken;
import com.muffin.notification.domain.devicetoken.DeviceTokenRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DeviceTokenControllerTest {

    private static final Long USER_A = 100L;
    private static final Long USER_B = 200L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccessTokenProvider accessTokenProvider;

    @Autowired
    private DeviceTokenRepository deviceTokenRepository;

    @AfterEach
    void cleanUp() {
        deviceTokenRepository.deleteAll();
    }

    private ResultActions register(Long userId, String body) throws Exception {
        return mockMvc.perform(put("/api/notifications/device-token")
                .header("Authorization", "Bearer " + accessTokenProvider.issue(userId, "USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
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
    @DisplayName("Authorization 헤더가 없으면 401")
    void register_unauthorized() throws Exception {
        mockMvc.perform(put("/api/notifications/device-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"token-1\",\"environment\":\"PRODUCTION\"}"))
                .andExpect(status().isUnauthorized());
    }
}
