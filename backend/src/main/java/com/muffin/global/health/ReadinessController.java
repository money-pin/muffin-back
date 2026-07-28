package com.muffin.global.health;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 배포 환경 readiness probe. 인증/공통 응답 포맷과 무관한 인프라용 엔드포인트라 plain text로 응답한다. */
@Tag(name = "Health", description = "배포 환경 상태 확인용 엔드포인트")
@RestController
@RequestMapping("/api/health")
public class ReadinessController {

    private static final String READY = "READY";
    private static final String NOT_READY = "NOT_READY";

    private final JdbcOperations jdbcOperations;

    public ReadinessController(JdbcOperations jdbcOperations) {
        this.jdbcOperations = jdbcOperations;
    }

    @Operation(summary = "Readiness probe", description = "DB 연결 상태를 확인해 READY/NOT_READY를 plain text로 반환한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "DB 연결 정상. 본문은 \"READY\" 고정 문자열(text/plain).",
                content = @Content(mediaType = MediaType.TEXT_PLAIN_VALUE, schema = @Schema(example = "READY"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "503",
                description = "DB 연결 실패. 본문은 \"NOT_READY\" 고정 문자열(text/plain).",
                content = @Content(mediaType = MediaType.TEXT_PLAIN_VALUE, schema = @Schema(example = "NOT_READY")))
    })
    @GetMapping(value = "/readiness", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> readiness() {
        try {
            Integer result = jdbcOperations.queryForObject("SELECT 1", Integer.class);
            if (Integer.valueOf(1).equals(result)) {
                return ResponseEntity.ok(READY);
            }
        } catch (DataAccessException ignored) {
            // Readiness failures are represented by the response status and body.
        }

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(NOT_READY);
    }
}
