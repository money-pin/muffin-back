package com.muffin.mypage.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muffin.auth.domain.AccessTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MypageSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccessTokenProvider accessTokenProvider;

    @Test
    @DisplayName("스크랩 목록 API는 JWT 인증이 없으면 401을 반환한다")
    void getScraps_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/mypage/scraps")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("인증된 요청은 보안 필터를 통과해 컨트롤러에 도달한다(없는 사용자면 404)")
    void getScraps_authenticatedReachesController() throws Exception {
        String bearer = "Bearer " + accessTokenProvider.issue(999_999L, "USER");

        mockMvc.perform(get("/api/mypage/scraps").header("Authorization", bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MYPAGE_404_001"));
    }

    @Test
    @DisplayName("최근 읽은 뉴스 API는 JWT 인증이 없으면 401을 반환한다")
    void getRecentNews_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/mypage/recent-news")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("인증된 최근 읽은 뉴스 요청은 보안 필터를 통과해 컨트롤러에 도달한다(없는 사용자면 404)")
    void getRecentNews_authenticatedReachesController() throws Exception {
        String bearer = "Bearer " + accessTokenProvider.issue(999_999L, "USER");

        mockMvc.perform(get("/api/mypage/recent-news").header("Authorization", bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MYPAGE_404_001"));
    }
}
