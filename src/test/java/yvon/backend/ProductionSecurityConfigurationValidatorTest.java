package yvon.backend;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import yvon.backend.auth.AuthProperties;
import yvon.backend.bootstrap.ProductionSecurityConfigurationValidator;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

class ProductionSecurityConfigurationValidatorTest {

    @Test
    void rejectsDevelopmentJwtSecretInProd() {
        AuthProperties properties = new AuthProperties();
        properties.setJwtSecret("taskflow-dev-only-secret-change-before-sharing-32");

        assertThatThrownBy(() -> new ProductionSecurityConfigurationValidator(environment(), properties).validateNow())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TASKFLOW_JWT_SECRET");
    }

    @Test
    void acceptsExplicitStrongProdSecretsAndOrigins() {
        AuthProperties properties = new AuthProperties();
        properties.setJwtSecret("prod-jwt-key-012345678901234567890123");

        assertThatCode(() -> new ProductionSecurityConfigurationValidator(environment(), properties).validateNow())
                .doesNotThrowAnyException();
    }

    @Test
    void productionCookieConfigurationRequiresSecureTransport() throws Exception {
        String properties = new String(getClass().getResourceAsStream(
                "/application-prod.properties").readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);

        assertThat(properties).contains("taskflow.auth.browser-cookie.secure=true");
        assertThat(properties).contains("taskflow.auth.browser-cookie.http-only=true");
    }

    private MockEnvironment environment() {
        return new MockEnvironment()
                .withProperty("DB_PASSWORD", "database-prod-secret-2026")
                .withProperty("RABBITMQ_PASSWORD", "rabbit-prod-secret-2026")
                .withProperty("MINIO_ACCESS_KEY", "minio-prod-access-2026")
                .withProperty("MINIO_SECRET_KEY", "minio-prod-secret-2026")
                .withProperty("TASKFLOW_BOOTSTRAP_ADMIN_ENABLED", "false")
                .withProperty("WEBSOCKET_ALLOWED_ORIGINS", "https://taskflow.example.com");
    }
}
