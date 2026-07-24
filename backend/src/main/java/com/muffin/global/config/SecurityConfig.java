package com.muffin.global.config;

import com.muffin.auth.infrastructure.jwt.JwtAuthenticationFilter;
import com.muffin.global.apiPayload.handler.ApiAccessDeniedHandler;
import com.muffin.global.apiPayload.handler.ApiAuthenticationEntryPoint;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * 세션을 쓰지 않는 stateless API 서버 구성. 로그인 없이는 쓸 수 없는 앱이라 로그인/회원가입/구글 로그인 등
 * 퍼블릭 엔드포인트만 "/auth/**"로 분리해 permitAll로 열어두고, 그 외 "/api/**"를 포함한 나머지 요청은 전부
 * authenticated()가 기본값이다. 뉴스 목록(GET /api/news)은 로그인 전 미리보기 목적으로 예외적으로 permitAll이다.
 * logout/탈퇴/이메일 인증처럼 인증이 필요한 엔드포인트는 별도 화이트리스트 없이 이 기본값에 자연히 포함되도록
 * "/api/auth/**" 아래 그대로 둔다.
 *
 * <p>{@code HttpSecurity}는 서블릿 웹 컨텍스트에서만 존재하는 빈이라, {@code webEnvironment = NONE}으로 뜨는
 * 서비스 계층 테스트에서도 이 설정이 로딩을 시도하지 않도록 웹 애플리케이션일 때만 활성화한다.
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ApiAuthenticationEntryPoint apiAuthenticationEntryPoint;
    private final ApiAccessDeniedHandler apiAccessDeniedHandler;
    private final CorsProperties corsProperties;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers(
                                "/auth/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/docs/**",
                                "/api/health/readiness")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/news")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(apiAuthenticationEntryPoint)
                        .accessDeniedHandler(apiAccessDeniedHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * refreshToken을 HttpOnly 쿠키로 주고받기 때문에 allowCredentials(true)가 필수이고, 이 경우 allowedOrigins에
     * "*"를 쓸 수 없어 muffin.cors.allowed-origins에 명시된 origin만 허용한다.
     */
    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
