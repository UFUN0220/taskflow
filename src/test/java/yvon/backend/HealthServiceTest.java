package yvon.backend;

import org.junit.jupiter.api.Test;
import yvon.backend.bootstrap.HealthService;

import static org.assertj.core.api.Assertions.assertThat;

class HealthServiceTest {

    private final HealthService healthService = new HealthService();

    @Test
    void currentReturnsBackendHealth() {
        HealthService.HealthInfo result = healthService.current();

        assertThat(result.status()).isEqualTo("UP");
        assertThat(result.service()).isEqualTo("taskflow-backend");
    }
}
