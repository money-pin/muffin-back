package com.muffin.scrap.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class ScrapSecurityIntegrationTest {

    private static final long MISSING_NEWS_ID = 999_999L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccessTokenProvider accessTokenProvider;

    @Test
    @DisplayName("스크랩 API는 JWT 인증이 없으면 401을 반환한다")
    void scrap_requiresAuthentication() throws Exception {
        mockMvc.perform(put("/api/news/{newsId}/scrap", MISSING_NEWS_ID)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("스크랩 해제 API는 JWT 인증이 없으면 401을 반환한다")
    void unscrap_requiresAuthentication() throws Exception {
        mockMvc.perform(delete("/api/news/{newsId}/scrap", MISSING_NEWS_ID)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("인증된 요청은 보안 필터를 통과해 컨트롤러에 도달한다(없는 뉴스면 404)")
    void scrap_authenticatedReachesController() throws Exception {
        String bearer = "Bearer " + accessTokenProvider.issue(1L, "USER");

        mockMvc.perform(put("/api/news/{newsId}/scrap", MISSING_NEWS_ID).header("Authorization", bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONTENT_404_001"));
    }
}
