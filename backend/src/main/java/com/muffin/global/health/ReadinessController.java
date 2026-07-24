package com.muffin.global.health;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class ReadinessController {

    private static final String READY = "READY";
    private static final String NOT_READY = "NOT_READY";

    private final JdbcOperations jdbcOperations;

    public ReadinessController(JdbcOperations jdbcOperations) {
        this.jdbcOperations = jdbcOperations;
    }

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
