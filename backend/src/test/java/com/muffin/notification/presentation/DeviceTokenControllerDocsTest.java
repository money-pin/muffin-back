package com.muffin.notification.presentation;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muffin.global.apiPayload.handler.GeneralExceptionAdvice;
import com.muffin.notification.application.devicetoken.DeviceTokenCommandService;
import com.muffin.notification.domain.devicetoken.ApnsEnvironment;
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

@ExtendWith(RestDocumentationExtension.class)
class DeviceTokenControllerDocsTest {

    @BeforeEach
    void setUpAuthentication() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(1L, null, List.of()));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("푸시 기기 토큰 등록 문서화")
    void documentRegisterDeviceToken(RestDocumentationContextProvider restDocumentation) throws Exception {
        DeviceTokenCommandService commandStub =
                new DeviceTokenCommandService(
                        null,
                        null,
                        null,
                        org.mockito.Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class)) {
                    @Override
                    public void register(Long userId, String accessToken, String token, ApnsEnvironment environment) {}
                };
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new DeviceTokenController(commandStub))
                .setControllerAdvice(new GeneralExceptionAdvice())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .apply(documentationConfiguration(restDocumentation)
                        .operationPreprocessors()
                        .withRequestDefaults(prettyPrint())
                        .withResponseDefaults(prettyPrint()))
                .build();

        mockMvc.perform(put("/api/notifications/device-token")
                        .header("Authorization", "Bearer access-token-example")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"7a3f9c0e1b2d4f6a8c0e2b4d6f8a0c2e4b6d8f0a2c4e6b8d0f2a4c6e8b0d2f4a\","
                                + "\"environment\":\"PRODUCTION\"}"))
                .andExpect(status().isOk())
                .andDo(document(
                        "device-token-register",
                        requestFields(
                                fieldWithPath("token").description("APNs 기기 토큰(최대 255자)"),
                                fieldWithPath("environment")
                                        .description(
                                                "APNs 환경. Xcode 빌드면 SANDBOX, TestFlight·App Store 빌드면 PRODUCTION")),
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"))));
    }
}
