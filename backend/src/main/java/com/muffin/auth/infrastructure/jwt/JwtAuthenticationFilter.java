package com.muffin.auth.infrastructure.jwt;

import com.muffin.auth.domain.AccessTokenProvider;
import com.muffin.global.apiPayload.exception.GeneralException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * {@code Authorization: Bearer <accessToken>} 헤더를 검증해 SecurityContext에 인증 정보를 채워 넣는다. 토큰이
 * 없거나 유효하지 않아도 여기서 요청을 막지 않고 그대로 통과시킨다 — 실제로 막을지는 {@code SecurityConfig}의 인가 규칙과
 * {@code ApiAuthenticationEntryPoint}가 결정한다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AccessTokenProvider accessTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        extractToken(request).ifPresent(this::authenticate);
        filterChain.doFilter(request, response);
    }

    private Optional<String> extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return Optional.of(header.substring(BEARER_PREFIX.length()));
        }
        return Optional.empty();
    }

    private void authenticate(String accessToken) {
        try {
            Long userId = accessTokenProvider.parseUserId(accessToken);
            Authentication authentication = new UsernamePasswordAuthenticationToken(userId, null, List.of());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (GeneralException e) {
            // 토큰이 유효하지 않으면 인증 정보를 채우지 않고 넘어간다.
        }
    }
}
