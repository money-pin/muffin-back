package com.muffin.global.config;

import com.muffin.auth.infrastructure.jwt.JwtAuthenticationFilter;
import com.muffin.global.apiPayload.handler.ApiAccessDeniedHandler;
import com.muffin.global.apiPayload.handler.ApiAuthenticationEntryPoint;
import jakarta.servlet.DispatcherType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
 * authenticated()가 기본값이다.
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
                .authorizeHttpRequests(auth -> auth
                        // 서블릿 ERROR 디스패치는 인가 대상이 아니다. 스프링 시큐리티 6부터 필터 체인이 ERROR
                        // 디스패치에도 적용되는데, 이걸 막으면 처리되지 못한 예외가 전부 "인증이 필요합니다"(401)로
                        // 둔갑해 원래 원인(400/500 등)을 감춘다. 어떤 요청을 인증할지는 아래 REQUEST 규칙이 이미
                        // 판단했고, 여기로 오는 건 그 판단이 끝난 뒤 발생한 에러의 후처리다.
                        .dispatcherTypeMatchers(DispatcherType.ERROR)
                        .permitAll()
                        .requestMatchers(
                                "/auth/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/docs/**",
                                "/api/health/readiness",
                                // 온박스 수집기(Alloy)가 인증 없이 긁어간다. 외부 노출은 네트워크에서 막는다:
                                // EC2 보안그룹이 80/443만 열어 8080 직접 접근이 불가능하고, Nginx가 /actuator를 차단한다.
                                "/actuator/health",
                                "/actuator/prometheus")
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
