package yvon.backend.bootstrap;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import yvon.backend.auth.AuthProperties;

import java.util.Locale;

/**
 * Rejects development credentials when the application explicitly runs with the prod profile.
 * Missing placeholders are rejected by application-prod.properties before this validator runs.
 */
@Component
@Profile("prod")
public class ProductionSecurityConfigurationValidator {

    private final Environment environment;
    private final AuthProperties authProperties;

    public ProductionSecurityConfigurationValidator(Environment environment, AuthProperties authProperties) {
        this.environment = environment;
        this.authProperties = authProperties;
    }

    @PostConstruct
    public void validate() {
        validateNow();
    }

    public void validateNow() {
        requireStrong("TASKFLOW_JWT_SECRET", authProperties.getJwtSecret());
        requireStrong("DB_PASSWORD", environment.getProperty("DB_PASSWORD"));
        requireStrong("RABBITMQ_PASSWORD", environment.getProperty("RABBITMQ_PASSWORD"));
        requireStrong("MINIO_ACCESS_KEY", environment.getProperty("MINIO_ACCESS_KEY"));
        requireStrong("MINIO_SECRET_KEY", environment.getProperty("MINIO_SECRET_KEY"));

        if (Boolean.parseBoolean(environment.getProperty("TASKFLOW_BOOTSTRAP_ADMIN_ENABLED", "false"))) {
            requireStrong("TASKFLOW_BOOTSTRAP_ADMIN_PASSWORD",
                    environment.getProperty("TASKFLOW_BOOTSTRAP_ADMIN_PASSWORD"));
        }

        String origins = environment.getProperty("WEBSOCKET_ALLOWED_ORIGINS", "");
        if (origins.isBlank() || origins.contains("*")) {
            throw new IllegalStateException("WEBSOCKET_ALLOWED_ORIGINS must explicitly list trusted origins in prod");
        }
    }

    private void requireStrong(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be explicitly configured in prod");
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        if (value.length() < 16 || normalized.contains("change-me") || normalized.contains("replace-with")
                || normalized.contains("development") || normalized.contains("dev-only")
                || normalized.contains("before-sharing") || normalized.contains("local")
                || normalized.contains("default") || normalized.equals("guest")
                || normalized.equals("password")) {
            throw new IllegalStateException(name + " must not use a development or weak value in prod");
        }
    }
}
