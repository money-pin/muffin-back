package com.muffin.auth.presentation.swagger;

import com.muffin.auth.presentation.google.dto.GoogleAuthRequest;
import com.muffin.auth.presentation.google.dto.GoogleAuthResponse;
import com.muffin.auth.presentation.login.dto.LocalLoginRequest;
import com.muffin.auth.presentation.login.dto.LoginResponse;
import com.muffin.auth.presentation.refresh.dto.RefreshResponse;
import com.muffin.auth.presentation.signup.dto.LocalSignupRequest;
import com.muffin.auth.presentation.signup.dto.SignupResponse;
import com.muffin.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@Tag(name = "Auth", description = "인증(회원가입/로그인/로그아웃/탈퇴) API")
public interface AuthApi {

    @Operation(
            summary = "로컬 회원가입 (AUTH-01-1)",
            description = "이메일/비밀번호로 회원가입하고 즉시 로그인 상태로 access token을 발급한다. refresh token은 HttpOnly Cookie로 내려간다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "가입 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "요청 값 검증 실패(COMMON_400_002), 약관 미동의(AUTH_400_002), 비밀번호 형식 오류(COMMON_400_001)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "이미 사용 중인 이메일입니다.(AUTH_409_001) 또는 최근 탈퇴한 이메일입니다.(AUTH_409_003, 탈퇴 후 30일 이내)")
    })
    ResponseEntity<ApiResponse<SignupResponse>> signupLocal(LocalSignupRequest request, HttpServletResponse response);

    @Operation(
            summary = "로컬 로그인 (AUTH-02-1)",
            description = "이메일/비밀번호로 로그인하고 access token을 발급한다. refresh token은 HttpOnly Cookie로 내려간다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그인 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "이메일 또는 비밀번호가 일치하지 않습니다. (AUTH_401_002)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "탈퇴한 계정입니다.(AUTH_403_002) 또는 정지된 계정입니다.(AUTH_403_003)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "423",
                description = "로그인 시도 횟수를 초과하여 계정이 잠겼습니다.(AUTH_423_002, 30분 후 자동 해제)")
    })
    ApiResponse<LoginResponse> loginLocal(LocalLoginRequest request, HttpServletResponse response);

    @Operation(
            summary = "Access/Refresh Token 재발급 (AUTH-02-2)",
            description = "요청 Cookie의 refresh token(refreshToken)으로 access token을 새로 발급한다. "
                    + "재발급 시 refresh token도 함께 회전(rotate)되어 새 값이 Cookie로 다시 내려간다. "
                    + "요청 바디는 없으며 Authorization 헤더도 필요 없다(만료된 access token을 대체하기 위한 API이므로).")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "재발급 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "refresh token이 없거나 유효하지 않습니다. (AUTH_401_003)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "탈퇴한 계정입니다.(AUTH_403_002) 또는 정지된 계정입니다.(AUTH_403_003)")
    })
    ApiResponse<RefreshResponse> refresh(HttpServletRequest request, HttpServletResponse response);

    @Operation(
            summary = "로그아웃 (AUTH-09-1)",
            description = "Authorization 헤더의 access token으로 식별된 계정의 refresh token을 무효화하고, "
                    + "refresh token Cookie를 만료시킨다(Max-Age=0). 이미 로그아웃된 상태에서 다시 호출해도 200을 반환한다(멱등).")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그아웃 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증이 필요합니다. (AUTH_401_001)")
    })
    ApiResponse<Void> logout(@AuthenticationPrincipal Long userId, HttpServletResponse response);

    @Operation(
            summary = "회원 탈퇴 (AUTH-09-2)",
            description = "Authorization 헤더의 access token으로 식별된 계정을 탈퇴 처리한다. 이름은 즉시 삭제되고 이메일은 복구 불가능한 값으로 대체되며, "
                    + "refresh token이 무효화되고 refresh token Cookie가 만료(Max-Age=0)된다. 투자/퀴즈 기록은 탈퇴 6개월 후 별도 배치가 삭제한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "탈퇴 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증이 필요합니다. (AUTH_401_001)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "이미 탈퇴했거나 정지된 계정이라 탈퇴할 수 없습니다. (COMMON_409_001)")
    })
    ApiResponse<Void> withdraw(@AuthenticationPrincipal Long userId, HttpServletResponse response);

    @Operation(
            summary = "구글 로그인/가입",
            description = "구글 ID Token을 검증해 기존 계정이면 로그인, 없으면 자동 가입한다(서비스 약관은 자동 동의로 간주). "
                    + "access token은 응답 바디로, refresh token은 HttpOnly Cookie로 발급한다. "
                    + "기능명세서(v3.1)에 없는 확장 기능이라 별도 명세 ID가 없다(그룹 내 정렬 기준상 ID가 있는 API들 뒤에 둔다).")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그인/가입 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "유효하지 않은 구글 로그인 토큰입니다. (AUTH_401_004)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "탈퇴한 계정입니다.(AUTH_403_002) 또는 정지된 계정입니다.(AUTH_403_003)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "최근 탈퇴한 이메일입니다.(AUTH_409_003, 신규 가입 시에만)")
    })
    ApiResponse<GoogleAuthResponse> authenticateGoogle(GoogleAuthRequest request, HttpServletResponse response);
}
