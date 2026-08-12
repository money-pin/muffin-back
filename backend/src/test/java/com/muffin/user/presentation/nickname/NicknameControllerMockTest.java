package com.muffin.user.presentation.nickname;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.muffin.global.apiPayload.handler.GeneralExceptionAdvice;
import com.muffin.user.application.nickname.NicknameCommandService;
import com.muffin.user.application.nickname.NicknameQueryService;
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

/** NicknameController를 서비스는 mock으로 격리해 컨트롤러 계층(요청 매핑/바인딩/검증)만 단위 테스트한다. */
@ExtendWith(MockitoExtension.class)
class NicknameControllerMockTest {

    private static final Long USER_ID = 1L;

    @Mock
    private NicknameQueryService nicknameQueryService;

    @Mock
    private NicknameCommandService nicknameCommandService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(USER_ID, null, List.of()));
        mockMvc = standaloneSetup(new NicknameController(nicknameQueryService, nicknameCommandService))
                .setControllerAdvice(new GeneralExceptionAdvice())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /nickname/check는 서비스 결과를 그대로 응답한다")
    void checkNickname_delegatesToService() throws Exception {
        when(nicknameQueryService.isAvailable("길동이")).thenReturn(true);

        mockMvc.perform(get("/api/mypage/nicknames/availability").param("nickname", "길동이"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.available").value(true));

        verify(nicknameQueryService).isAvailable("길동이");
    }

    @Test
    @DisplayName("nickname 파라미터가 없으면 서비스 호출 없이 400")
    void checkNickname_missingParam_badRequest() throws Exception {
        mockMvc.perform(get("/api/mypage/nicknames/availability")).andExpect(status().isBadRequest());

        verifyNoInteractions(nicknameQueryService);
    }

    @Test
    @DisplayName("PATCH /nickname은 서비스 호출 결과를 응답에 매핑한다")
    void changeNickname_delegatesToService() throws Exception {
        when(nicknameCommandService.changeNickname(eq(USER_ID), eq("새닉네임"))).thenReturn("새닉네임");

        mockMvc.perform(patch("/api/mypage/nickname")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"새닉네임\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.nickname").value("새닉네임"));

        verify(nicknameCommandService).changeNickname(USER_ID, "새닉네임");
    }

    @Test
    @DisplayName("nickname이 blank면 서비스 호출 없이 400")
    void changeNickname_blankNickname_badRequestWithoutCallingService() throws Exception {
        mockMvc.perform(patch("/api/mypage/nickname")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(nicknameCommandService);
    }

    @Test
    @DisplayName("요청 본문이 없으면 서비스 호출 없이 400")
    void changeNickname_missingBody_badRequest() throws Exception {
        mockMvc.perform(patch("/api/mypage/nickname").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(nicknameCommandService);
    }

    @Test
    @DisplayName("GET에 잘못된 HTTP 메서드(POST)를 쓰면 405")
    void wrongHttpMethod_notAllowed() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                "/api/mypage/nicknames/availability")
                        .param("nickname", "길동이"))
                .andExpect(status().isMethodNotAllowed());
    }
}
