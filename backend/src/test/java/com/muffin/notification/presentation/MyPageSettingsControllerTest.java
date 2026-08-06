package com.muffin.notification.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muffin.auth.domain.AccessTokenProvider;
import com.muffin.notification.domain.NotificationSettingsRepository;
import com.muffin.notification.presentation.dto.NotificationSettingsUpdateRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/** 마이페이지 설정/알림 설정 API가 실제 서비스/저장소/Security까지 엮여서 동작하는지 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MyPageSettingsControllerTest {

    private static final Long USER_ID = 100L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccessTokenProvider accessTokenProvider;

    @Autowired
    private NotificationSettingsRepository notificationSettingsRepository;

    @AfterEach
    void cleanUp() {
        notificationSettingsRepository.deleteAll();
    }

    private String bearerToken() {
        return "Bearer " + accessTokenProvider.issue(USER_ID, "USER");
    }

    @Test
    @DisplayName("최초 GET /settings 시 기본값(모두 true)이 생성되어 응답된다")
    void getSettings_createsDefault() throws Exception {
        mockMvc.perform(get("/api/mypage/settings").header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.notifications.newsUpdate", is(true)))
                .andExpect(jsonPath("$.result.notifications.dailyQuiz", is(true)))
                .andExpect(jsonPath("$.result.notifications.investResult", is(true)))
                .andExpect(jsonPath("$.result.notifications.rankingChange", is(true)));

        assertThat(notificationSettingsRepository.existsById(USER_ID)).isTrue();
    }

    @Test
    @DisplayName("GET /settings/notifications도 같은 형식으로 응답한다")
    void getNotificationSettings_sameShape() throws Exception {
        mockMvc.perform(get("/api/mypage/settings/notifications").header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.notifications.newsUpdate", is(true)));
    }

    @Test
    @DisplayName("PUT /settings/notifications로 변경 후 재조회 시 반영된다")
    void updateThenGet_reflectsChange() throws Exception {
        mockMvc.perform(put("/api/mypage/settings/notifications")
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new NotificationSettingsUpdateRequest(false, true, false, true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.notifications.newsUpdate", is(false)))
                .andExpect(jsonPath("$.result.notifications.dailyQuiz", is(true)))
                .andExpect(jsonPath("$.result.notifications.investResult", is(false)))
                .andExpect(jsonPath("$.result.notifications.rankingChange", is(true)));

        mockMvc.perform(get("/api/mypage/settings").header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.notifications.newsUpdate", is(false)))
                .andExpect(jsonPath("$.result.notifications.rankingChange", is(true)));
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 401")
    void unauthorized() throws Exception {
        mockMvc.perform(get("/api/mypage/settings")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT 필드 누락이면 400")
    void updateSettings_missingField_badRequest() throws Exception {
        mockMvc.perform(put("/api/mypage/settings/notifications")
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newsUpdate\": true, \"dailyQuiz\": true, \"investResult\": true}"))
                .andExpect(status().isBadRequest());
    }
}
