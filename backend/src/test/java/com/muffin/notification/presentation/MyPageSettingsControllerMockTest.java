package com.muffin.notification.presentation;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.muffin.global.apiPayload.handler.GeneralExceptionAdvice;
import com.muffin.notification.application.NotificationSettingsCommandService;
import com.muffin.notification.application.NotificationSettingsQueryService;
import com.muffin.notification.presentation.dto.MyPageSettingsResponse;
import com.muffin.notification.presentation.dto.NotificationSettingsItem;
import com.muffin.notification.presentation.dto.NotificationSettingsUpdateRequest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;

/** MyPageSettingsController를 서비스는 mock으로 격리해 컨트롤러 계층만 단위 테스트한다. */
@ExtendWith(MockitoExtension.class)
class MyPageSettingsControllerMockTest {

    private static final Long USER_ID = 1L;

    @Mock
    private NotificationSettingsQueryService notificationSettingsQueryService;

    @Mock
    private NotificationSettingsCommandService notificationSettingsCommandService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(USER_ID, null, List.of()));
        mockMvc = standaloneSetup(new MyPageSettingsController(
                        notificationSettingsQueryService, notificationSettingsCommandService))
                .setControllerAdvice(new GeneralExceptionAdvice())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /settings는 서비스 결과를 그대로 응답한다")
    void getSettings_delegatesToService() throws Exception {
        when(notificationSettingsQueryService.getSettings(USER_ID))
                .thenReturn(new MyPageSettingsResponse(new NotificationSettingsItem(true, false, true, false)));

        mockMvc.perform(get("/api/mypage/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.notifications.newsUpdate").value(true))
                .andExpect(jsonPath("$.result.notifications.dailyQuiz").value(false));

        verify(notificationSettingsQueryService).getSettings(USER_ID);
    }

    @Test
    @DisplayName("GET /settings/notifications도 같은 서비스를 통해 응답한다")
    void getNotificationSettings_delegatesToService() throws Exception {
        when(notificationSettingsQueryService.getSettings(USER_ID))
                .thenReturn(new MyPageSettingsResponse(new NotificationSettingsItem(true, true, true, true)));

        mockMvc.perform(get("/api/mypage/settings/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.notifications.rankingChange").value(true));

        verify(notificationSettingsQueryService).getSettings(USER_ID);
    }

    @Test
    @DisplayName("PUT /settings/notifications는 서비스 호출 결과를 응답에 매핑한다")
    void updateNotificationSettings_delegatesToService() throws Exception {
        NotificationSettingsUpdateRequest request = new NotificationSettingsUpdateRequest(false, true, false, true);
        when(notificationSettingsCommandService.updateSettings(eq(USER_ID), eq(request)))
                .thenReturn(new MyPageSettingsResponse(new NotificationSettingsItem(false, true, false, true)));

        mockMvc.perform(
                        put("/api/mypage/settings/notifications")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"newsUpdate\":false,\"dailyQuiz\":true,\"investResult\":false,\"rankingChange\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.notifications.newsUpdate").value(false))
                .andExpect(jsonPath("$.result.notifications.dailyQuiz").value(true));

        verify(notificationSettingsCommandService).updateSettings(USER_ID, request);
    }

    @Test
    @DisplayName("PUT 필드 하나라도 누락되면 서비스 호출 없이 400")
    void updateNotificationSettings_missingField_badRequestWithoutCallingService() throws Exception {
        mockMvc.perform(put("/api/mypage/settings/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newsUpdate\":true,\"dailyQuiz\":true,\"investResult\":true}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(notificationSettingsCommandService);
    }
}
