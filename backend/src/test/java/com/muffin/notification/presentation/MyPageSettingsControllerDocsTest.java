package com.muffin.notification.presentation;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.http.MediaType;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

/** 마이페이지 설정/알림 설정 API의 REST Docs 스니펫을 생성한다. */
@ExtendWith(RestDocumentationExtension.class)
class MyPageSettingsControllerDocsTest {

    private static final Long USER_ID = 1L;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUpAuthentication() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(USER_ID, null, List.of()));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("마이페이지 설정 조회 문서화")
    void documentGetSettings(RestDocumentationContextProvider restDocumentation) throws Exception {
        NotificationSettingsQueryService queryStub = new NotificationSettingsQueryService(null) {
            @Override
            public MyPageSettingsResponse getSettings(Long userId) {
                return new MyPageSettingsResponse(new NotificationSettingsItem(true, true, true, false));
            }
        };
        MockMvc mockMvc = mockMvcOf(queryStub, null, restDocumentation);

        mockMvc.perform(get("/api/mypage/settings"))
                .andExpect(status().isOk())
                .andDo(document(
                        "mypage-settings-get",
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("result.notifications.newsUpdate").description("뉴스 업데이트 알림"),
                                fieldWithPath("result.notifications.dailyQuiz").description("일일 퀴즈 알림"),
                                fieldWithPath("result.notifications.investResult")
                                        .description("투자 결과 알림"),
                                fieldWithPath("result.notifications.rankingChange")
                                        .description("랭킹 변동 알림"))));
    }

    @Test
    @DisplayName("알림 설정 조회 문서화")
    void documentGetNotificationSettings(RestDocumentationContextProvider restDocumentation) throws Exception {
        NotificationSettingsQueryService queryStub = new NotificationSettingsQueryService(null) {
            @Override
            public MyPageSettingsResponse getSettings(Long userId) {
                return new MyPageSettingsResponse(new NotificationSettingsItem(true, true, true, false));
            }
        };
        MockMvc mockMvc = mockMvcOf(queryStub, null, restDocumentation);

        mockMvc.perform(get("/api/mypage/settings/notifications"))
                .andExpect(status().isOk())
                .andDo(document(
                        "mypage-settings-notifications-get",
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("result.notifications.newsUpdate").description("뉴스 업데이트 알림"),
                                fieldWithPath("result.notifications.dailyQuiz").description("일일 퀴즈 알림"),
                                fieldWithPath("result.notifications.investResult")
                                        .description("투자 결과 알림"),
                                fieldWithPath("result.notifications.rankingChange")
                                        .description("랭킹 변동 알림"))));
    }

    @Test
    @DisplayName("알림 설정 변경 문서화")
    void documentUpdateNotificationSettings(RestDocumentationContextProvider restDocumentation) throws Exception {
        NotificationSettingsCommandService commandStub = new NotificationSettingsCommandService(null) {
            @Override
            public MyPageSettingsResponse updateSettings(Long userId, NotificationSettingsUpdateRequest request) {
                return new MyPageSettingsResponse(new NotificationSettingsItem(
                        request.newsUpdate(), request.dailyQuiz(), request.investResult(), request.rankingChange()));
            }
        };
        MockMvc mockMvc = mockMvcOf(null, commandStub, restDocumentation);

        mockMvc.perform(put("/api/mypage/settings/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new NotificationSettingsUpdateRequest(true, false, true, false))))
                .andExpect(status().isOk())
                .andDo(document(
                        "mypage-settings-notifications-update",
                        requestFields(
                                fieldWithPath("newsUpdate").description("뉴스 업데이트 알림"),
                                fieldWithPath("dailyQuiz").description("일일 퀴즈 알림"),
                                fieldWithPath("investResult").description("투자 결과 알림"),
                                fieldWithPath("rankingChange").description("랭킹 변동 알림")),
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("result.notifications.newsUpdate").description("뉴스 업데이트 알림"),
                                fieldWithPath("result.notifications.dailyQuiz").description("일일 퀴즈 알림"),
                                fieldWithPath("result.notifications.investResult")
                                        .description("투자 결과 알림"),
                                fieldWithPath("result.notifications.rankingChange")
                                        .description("랭킹 변동 알림"))));
    }

    private MockMvc mockMvcOf(
            NotificationSettingsQueryService queryStub,
            NotificationSettingsCommandService commandStub,
            RestDocumentationContextProvider restDocumentation) {
        return MockMvcBuilders.standaloneSetup(new MyPageSettingsController(queryStub, commandStub))
                .setControllerAdvice(new GeneralExceptionAdvice())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .apply(documentationConfiguration(restDocumentation)
                        .operationPreprocessors()
                        .withRequestDefaults(prettyPrint())
                        .withResponseDefaults(prettyPrint()))
                .build();
    }
}
