package com.muffin.global.apiPayload.handler;

import static org.assertj.core.api.Assertions.assertThat;

import com.muffin.global.apiPayload.ApiResponse;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * 서블릿 ERROR 디스패치의 종착지가 공통 응답 포맷을 유지하는지 검증한다. MockMvc는 컨테이너의 ERROR 디스패치를
 * 재현하지 않으므로 컨트롤러를 직접 호출해 확인한다.
 */
class ApiErrorControllerTest {

    private final ApiErrorController apiErrorController = new ApiErrorController();

    private MockHttpServletRequest errorRequest(Integer statusCode) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/api/news");
        if (statusCode != null) {
            request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, statusCode);
        }
        return request;
    }

    @Test
    @DisplayName("에러 디스패치 응답은 공통 포맷과 JSON Content-Type을 유지한다")
    void handleError_returnsCommonFormatAsJson() {
        ResponseEntity<ApiResponse<?>> response = apiErrorController.handleError(errorRequest(500));

        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getCode()).isEqualTo("COMMON_500_001");
    }

    @Test
    @DisplayName("상태코드에 대응하는 에러코드가 있으면 그 상태코드를 그대로 쓴다")
    void handleError_keepsStatusWhenErrorCodeExists() {
        ResponseEntity<ApiResponse<?>> response = apiErrorController.handleError(errorRequest(404));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("COMMON_404_001");
    }

    @Test
    @DisplayName("대응하는 에러코드가 없는 상태코드는 500으로 떨어뜨려 상태와 본문 코드를 일치시킨다")
    void handleError_fallsBackToInternalServerErrorWhenUnmapped() {
        ResponseEntity<ApiResponse<?>> response = apiErrorController.handleError(errorRequest(503));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("COMMON_500_001");
    }

    @Test
    @DisplayName("상태코드 정보가 없으면 500으로 응답한다")
    void handleError_defaultsToInternalServerErrorWithoutStatusAttribute() {
        ResponseEntity<ApiResponse<?>> response = apiErrorController.handleError(errorRequest(null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("COMMON_500_001");
    }
}
