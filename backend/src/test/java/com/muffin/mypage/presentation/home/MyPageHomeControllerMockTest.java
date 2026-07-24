package com.muffin.mypage.presentation.home;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.muffin.character.domain.enums.MuffinType;
import com.muffin.global.apiPayload.handler.GeneralExceptionAdvice;
import com.muffin.mypage.application.home.MyPageHomeQueryService;
import com.muffin.mypage.presentation.home.dto.MyPageHomeResponse;
import com.muffin.mypage.presentation.home.dto.MyPageHomeResponse.CharacterSummary;
import com.muffin.mypage.presentation.home.dto.MyPageHomeResponse.StreakSummary;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;

/** MyPageHomeController를 서비스는 mock으로 격리해 컨트롤러 계층만 단위 테스트한다. */
@ExtendWith(MockitoExtension.class)
class MyPageHomeControllerMockTest {

    private static final Long USER_ID = 1L;

    @Mock
    private MyPageHomeQueryService myPageHomeQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(USER_ID, null, List.of()));
        mockMvc = standaloneSetup(new MyPageHomeController(myPageHomeQueryService))
                .setControllerAdvice(new GeneralExceptionAdvice())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /home은 access token의 userId로 서비스를 호출하고 결과를 그대로 응답한다")
    void getHome_delegatesToServiceWithAuthenticatedUserId() throws Exception {
        when(myPageHomeQueryService.getHome(USER_ID))
                .thenReturn(new MyPageHomeResponse(
                        "길동이",
                        new CharacterSummary(1L, MuffinType.PLAIN, "플레인 머핀", "http://image"),
                        new StreakSummary(3, 5, List.of()),
                        List.of()));

        mockMvc.perform(get("/api/mypage/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.nickname").value("길동이"))
                .andExpect(jsonPath("$.result.streak.currentStreak").value(3));

        verify(myPageHomeQueryService).getHome(USER_ID);
    }
}
