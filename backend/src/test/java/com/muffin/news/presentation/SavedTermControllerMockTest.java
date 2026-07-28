package com.muffin.news.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.muffin.global.apiPayload.handler.GeneralExceptionAdvice;
import com.muffin.news.application.term.TermQueryService;
import com.muffin.news.presentation.dto.response.SavedTermListResponse;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;

/** SavedTermController를 서비스는 mock으로 격리해 컨트롤러 계층(쿼리 파라미터 바인딩/기본값)만 단위 테스트한다. */
@ExtendWith(MockitoExtension.class)
class SavedTermControllerMockTest {

    private static final Long USER_ID = 1L;

    @Mock
    private TermQueryService termQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(USER_ID, null, List.of()));
        mockMvc = standaloneSetup(new TermController(termQueryService, null))
                .setControllerAdvice(new GeneralExceptionAdvice())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("page/size/sort를 생략하면 기본값(0, 20, null)으로 서비스를 호출한다")
    void getSavedTerms_defaultsApplied() throws Exception {
        when(termQueryService.getSavedTerms(eq(USER_ID), any(), isNull()))
                .thenReturn(new SavedTermListResponse(List.of(), 0, 20, false));

        mockMvc.perform(get("/api/mypage/saved-terms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(20));

        verify(termQueryService).getSavedTerms(eq(USER_ID), eq(PageRequest.of(0, 20)), isNull());
    }

    @Test
    @DisplayName("page/size/sort를 지정하면 그대로 서비스에 전달한다")
    void getSavedTerms_customParamsPassedThrough() throws Exception {
        when(termQueryService.getSavedTerms(eq(USER_ID), any(), eq("alphabetical")))
                .thenReturn(new SavedTermListResponse(List.of(), 2, 5, true));

        mockMvc.perform(get("/api/mypage/saved-terms")
                        .param("page", "2")
                        .param("size", "5")
                        .param("sort", "alphabetical"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.page").value(2))
                .andExpect(jsonPath("$.result.size").value(5))
                .andExpect(jsonPath("$.result.hasNext").value(true));

        verify(termQueryService).getSavedTerms(eq(USER_ID), eq(PageRequest.of(2, 5)), eq("alphabetical"));
    }
}
