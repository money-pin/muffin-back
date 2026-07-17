package com.muffin.global.apiPayload.handler;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.global.apiPayload.code.BaseErrorCode;
import com.muffin.global.apiPayload.code.GeneralErrorCode;
import com.muffin.global.apiPayload.exception.GeneralException;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GeneralExceptionAdvice {

    // 커스텀 예외
    @ExceptionHandler(GeneralException.class)
    public ResponseEntity<ApiResponse<?>> handleGeneralException(GeneralException ex) {
        BaseErrorCode ec = ex.getErrorCode();
        log.warn("[GeneralException] code={}, message={}", ec.getCode(), ex.getMessage());
        return ResponseEntity.status(ec.getHttpStatus()).body(ApiResponse.onFailure(ec, List.of(ex.getMessage())));
    }

    // @Valid DTO 검증 실패 (RequestBody, @ModelAttribute, QueryParam)
    // MethodArgumentNotValidException은 BindException의 하위 타입이므로 함께 처리
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<?>> handleBindException(BindException ex) {
        BaseErrorCode ec = GeneralErrorCode.VALIDATION_ERROR;

        List<String> detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();

        log.debug("[ValidationFail] {}", detail);
        return ResponseEntity.status(ec.getHttpStatus()).body(ApiResponse.onFailure(ec, detail));
    }

    // @Validated + PathVariable/RequestParam 검증 실패
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<?>> handleConstraintViolation(ConstraintViolationException ex) {
        BaseErrorCode ec = GeneralErrorCode.VALIDATION_ERROR;

        List<String> detail = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .toList();

        log.debug("[ConstraintViolation] {}", detail);
        return ResponseEntity.status(ec.getHttpStatus()).body(ApiResponse.onFailure(ec, detail));
    }

    // 도메인 엔티티가 직접 던지는 입력값 검증 실패(형식/범위 위반)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<?>> handleIllegalArgument(IllegalArgumentException ex) {
        BaseErrorCode ec = GeneralErrorCode.BAD_REQUEST;

        log.warn("[IllegalArgumentException] {}", ex.getMessage());
        return ResponseEntity.status(ec.getHttpStatus()).body(ApiResponse.onFailure(ec, List.of(ex.getMessage())));
    }

    // 도메인 엔티티가 직접 던지는 상태 전이 위반(이미 처리됨, 잘못된 상태에서의 요청 등)
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<?>> handleIllegalState(IllegalStateException ex) {
        BaseErrorCode ec = GeneralErrorCode.CONFLICT;

        log.warn("[IllegalStateException] {}", ex.getMessage());
        return ResponseEntity.status(ec.getHttpStatus()).body(ApiResponse.onFailure(ec, List.of(ex.getMessage())));
    }

    // 타입 미스매치
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<?>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        BaseErrorCode ec = GeneralErrorCode.VALIDATION_ERROR;

        String detail = ex.getName() + ": 타입이 올바르지 않습니다. (value=" + ex.getValue() + ")";
        log.debug("[TypeMismatch] {}", detail);
        return ResponseEntity.status(ec.getHttpStatus()).body(ApiResponse.onFailure(ec, List.of(detail)));
    }

    // 필수 RequestParam 누락
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<?>> handleMissingParam(MissingServletRequestParameterException ex) {
        BaseErrorCode ec = GeneralErrorCode.VALIDATION_ERROR;

        String detail = ex.getParameterName() + ": 필수 파라미터가 누락되었습니다.";
        log.debug("[MissingParam] {}", detail);
        return ResponseEntity.status(ec.getHttpStatus()).body(ApiResponse.onFailure(ec, List.of(detail)));
    }

    // JSON 파싱 실패 / 요청 body가 깨졌을 때
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<?>> handleNotReadable(HttpMessageNotReadableException ex) {
        BaseErrorCode ec = GeneralErrorCode.BAD_REQUEST;

        log.warn("[HttpMessageNotReadable] malformed request body");
        log.debug("[HttpMessageNotReadable] detail", ex);
        return ResponseEntity.status(ec.getHttpStatus())
                .body(ApiResponse.onFailure(ec, List.of("요청 본문(JSON)을 올바르게 작성해 주세요.")));
    }

    // 지원하지 않는 HTTP Method (405)
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<?>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        BaseErrorCode ec = GeneralErrorCode.METHOD_NOT_ALLOWED;

        String detail = "지원하지 않는 HTTP 메서드입니다: " + ex.getMethod();
        return ResponseEntity.status(ec.getHttpStatus()).body(ApiResponse.onFailure(ec, List.of(detail)));
    }

    // 지원하지 않는 Content-Type (415)
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<?>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        BaseErrorCode ec = GeneralErrorCode.UNSUPPORTED_MEDIA_TYPE;

        String detail = "지원하지 않는 Content-Type 입니다: " + ex.getContentType();
        return ResponseEntity.status(ec.getHttpStatus()).body(ApiResponse.onFailure(ec, List.of(detail)));
    }

    // 나머지 전부 (500)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleException(Exception ex) {
        log.error("Unhandled exception", ex);

        BaseErrorCode ec = GeneralErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(ec.getHttpStatus()).body(ApiResponse.onFailure(ec, List.of()));
    }
}
