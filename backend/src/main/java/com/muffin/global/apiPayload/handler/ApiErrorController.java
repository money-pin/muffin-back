package com.muffin.global.apiPayload.handler;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.global.apiPayload.code.GeneralErrorCode;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 서블릿 ERROR 디스패치의 종착지. 스프링 부트 기본 {@code BasicErrorController}를 대체한다.
 *
 * <p>{@link GeneralExceptionAdvice}가 DispatcherServlet 안에서 발생한 예외를 거의 다 처리하므로 여기까지 오는
 * 경우는 드물다. 남는 경로는 <b>서블릿 필터에서 터진 예외</b>처럼 {@code @RestControllerAdvice}가 닿지 않는 곳이다.
 * 기본 컨트롤러를 그대로 두면 그때만 {@code {"timestamp":..., "status":..., "error":...}} 형태가 나가 공통 응답
 * 포맷이 깨지므로, 마지막 경로까지 {@link ApiResponse}로 통일한다.
 *
 * <p>Content-Type을 JSON으로 고정하는 이유는 {@link GeneralExceptionAdvice#toResponse}와 같다.
 */
@Slf4j
@Hidden // 화면에서 호출하는 API가 아니므로 Swagger/OpenAPI 목록에 넣지 않는다.
@RestController
public class ApiErrorController implements ErrorController {

    @RequestMapping("/error")
    public ResponseEntity<ApiResponse<?>> handleError(HttpServletRequest request) {
        GeneralErrorCode errorCode = toErrorCode(resolveStatus(request));

        Object exception = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        log.warn(
                "[ErrorDispatch] status={}, path={}, exception={}",
                errorCode.getHttpStatus().value(),
                request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI),
                exception == null ? "none" : exception.getClass().getName());

        // 상태코드를 errorCode에서 다시 꺼내 쓴다. 원래 상태코드를 그대로 쓰면 대응하는 에러코드가 없을 때
        // "상태는 503인데 본문 code는 COMMON_500_001"처럼 둘이 어긋난다. 이렇게 하면 구조적으로 항상 일치한다.
        return ResponseEntity.status(errorCode.getHttpStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.onFailure(errorCode, List.of(errorCode.getMessage())));
    }

    private HttpStatus resolveStatus(HttpServletRequest request) {
        Object statusCode = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (statusCode instanceof Integer code) {
            HttpStatus resolved = HttpStatus.resolve(code);
            if (resolved != null) {
                return resolved;
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    /**
     * 상태코드에 대응하는 공통 에러코드를 찾는다. 대응하는 것이 없으면 500으로 떨어뜨린다. 여기까지 온 요청은 이미 원래
     * 예외 정보를 잃은 상태라, 상태코드만으로 표현할 수 있는 만큼만 알려준다.
     */
    private GeneralErrorCode toErrorCode(HttpStatus status) {
        return Arrays.stream(GeneralErrorCode.values())
                .filter(code -> code.getHttpStatus() == status)
                .findFirst()
                .orElse(GeneralErrorCode.INTERNAL_SERVER_ERROR);
    }
}
