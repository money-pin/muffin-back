package com.muffin.global.health;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.test.web.servlet.MockMvc;

class ReadinessControllerTest {

    private JdbcOperations jdbcOperations;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        jdbcOperations = Mockito.mock(JdbcOperations.class);
        mockMvc = standaloneSetup(new ReadinessController(jdbcOperations)).build();
    }

    @Test
    void returnsReadyWhenDatabaseIsAvailable() throws Exception {
        when(jdbcOperations.queryForObject("SELECT 1", Integer.class)).thenReturn(1);

        mockMvc.perform(get("/api/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(content().string("READY"));
    }

    @Test
    void returnsServiceUnavailableWhenDatabaseIsUnavailable() throws Exception {
        when(jdbcOperations.queryForObject("SELECT 1", Integer.class))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        mockMvc.perform(get("/api/health/readiness"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string("NOT_READY"));
    }
}
