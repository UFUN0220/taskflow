package yvon.backend;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opt-in infrastructure smoke test. Run with -Dtaskflow.integration=true and Docker available.
 * The default unit-test command deliberately does not require a local Docker daemon.
 */
@Tag("integration")
@EnabledIfSystemProperty(named = "taskflow.integration", matches = "true")
@Testcontainers(disabledWithoutDocker = true)
class Stage14ContainerEnvironmentTest {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("taskflow")
            .withUsername("taskflow")
            .withPassword("taskflow_test");

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7.4-alpine")
            .withExposedPorts(6379);

    @Container
    static final RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management");

    @Container
    static final GenericContainer<?> minio = new GenericContainer<>("minio/minio:RELEASE.2024-12-18T13-15-44Z")
            .withEnv("MINIO_ROOT_USER", "minioadmin")
            .withEnv("MINIO_ROOT_PASSWORD", "minioadmin_local")
            .withExposedPorts(9000)
            .withCommand("server", "/data", "--console-address", ":9001");

    @Test
    void startsRequiredInfrastructureAndMigratesFreshMySqlSchema() throws Exception {
        Flyway.configure()
                .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        assertThat(mysql.isRunning()).isTrue();
        assertThat(redis.execInContainer("redis-cli", "PING").getStdout()).contains("PONG");
        assertThat(rabbit.getAmqpUrl()).startsWith("amqp://");
        assertThat(minio.getMappedPort(9000)).isPositive();
    }
}
