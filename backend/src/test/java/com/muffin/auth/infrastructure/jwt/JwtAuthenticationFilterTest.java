package com.muffin.auth.infrastructure.jwt;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.muffin.auth.domain.AccessTokenProvider;
import com.muffin.global.apiPayload.code.GeneralErrorCode;
import com.muffin.global.apiPayload.exception.GeneralException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private AccessTokenProvider accessTokenProvider;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("유효한 Bearer 토큰이면 SecurityContext에 userId가 채워지고 체인이 통과된다")
    void validToken_setsAuthenticationAndContinuesChain() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");
        when(accessTokenProvider.parseUserId("valid-token")).thenReturn(42L);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo(42L);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 SecurityContext는 비어있고 체인은 그대로 통과된다")
    void noHeader_leavesContextEmptyAndContinuesChain() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("토큰이 유효하지 않으면 SecurityContext는 비어있고 체인은 그대로 통과된다")
    void invalidToken_leavesContextEmptyAndContinuesChain() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer bad-token");
        when(accessTokenProvider.parseUserId("bad-token"))
                .thenThrow(new GeneralException(GeneralErrorCode.UNAUTHORIZED));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }
}
