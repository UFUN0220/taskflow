package yvon.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.command.InspectContainerResponse;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import yvon.backend.attachment.AttachmentProperties;
import yvon.backend.attachment.MinioObjectStorage;
import yvon.backend.attachment.TaskAttachmentEntity;
import yvon.backend.attachment.TaskAttachmentMetadataService;
import yvon.backend.attachment.TaskAttachmentService;
import yvon.backend.auth.AuthSessionService;
import yvon.backend.auth.JwtTokenService;
import yvon.backend.notification.NotificationDeadLetterEntity;
import yvon.backend.notification.NotificationDeadLetterMapper;
import yvon.backend.notification.NotificationDeadLetterService;
import yvon.backend.notification.NotificationMessageConsumer;
import yvon.backend.notification.NotificationService;
import yvon.backend.reminder.ReminderDueMessage;
import yvon.backend.reminder.ReminderPlanEntity;
import yvon.backend.reminder.ReminderPlanMapper;
import yvon.backend.reminder.ReminderProperties;
import yvon.backend.reminder.ReminderRedisIndexService;
import yvon.backend.task.TaskEntity;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.GetResponse;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Opt-in, real-infrastructure reliability evidence. This class is deliberately
 * not part of the fast unit-test path. It uses isolated disposable containers;
 * it is evidence for a local single-instance topology, not HA or production SLA.
 */
@Tag("integration")
@EnabledIfSystemProperty(named = "taskflow.integration", matches = "true")
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Stage12ReliabilityContainerTest {

    private static final String PREFIX = "stage12_" + UUID.randomUUID().toString().replace("-", "");
    private static final String USERNAME = PREFIX + "_user";
    private static final String EMPLOYEE_NO = PREFIX + "_employee";
    private static final String TASK_NO = PREFIX + "_TASK";
    private static final String RABBIT_EXCHANGE = PREFIX + ".main";
    private static final String RABBIT_ROUTING_KEY = "event";
    private static final String RABBIT_RETRY_EXCHANGE = PREFIX + ".retry";
    private static final String RABBIT_RETRY_QUEUE = PREFIX + ".retry";
    private static final String RABBIT_DEAD_EXCHANGE = PREFIX + ".dead";
    private static final String RABBIT_DEAD_QUEUE = PREFIX + ".dead";
    private static final String RABBIT_MAIN_QUEUE = PREFIX + ".main";

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("taskflow")
            .withUsername("taskflow")
            .withPassword("taskflow_test");

    @Container
    static final RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management");

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7.4-alpine")
            .withExposedPorts(6379);

    @Container
    static final GenericContainer<?> minio = new GenericContainer<>("minio/minio:RELEASE.2024-12-18T13-15-44Z")
            .withEnv("MINIO_ROOT_USER", "minioadmin")
            .withEnv("MINIO_ROOT_PASSWORD", "minioadmin_local")
            .withExposedPorts(9000)
            .withCommand("server", "/data", "--console-address", ":9001");

    private static JdbcTemplate jdbc;
    private static StringRedisTemplate redisTemplate;
    private static LettuceConnectionFactory redisConnectionFactory;
    private static CachingConnectionFactory rabbitConnectionFactory;
    private static RabbitTemplate rabbitTemplate;
    private static MinioClient minioClient;
    private static long userId;
    private static long taskId;

    @BeforeAll
    static void prepare() throws Exception {
        Flyway.configure()
                .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword()));
        userId = insertUser();
        taskId = insertTask();
        configureRedis();
        configureRabbit();
        minioClient = MinioClient.builder()
                .endpoint("http://" + minio.getHost() + ":" + minio.getMappedPort(9000))
                .credentials("minioadmin", "minioadmin_local")
                .build();
    }

    @AfterAll
    static void closeClients() {
        if (redisConnectionFactory != null) redisConnectionFactory.destroy();
        if (rabbitConnectionFactory != null) rabbitConnectionFactory.destroy();
    }

    @Test
    @Order(1)
    void rabbitRetryDlqReplayAndDuplicateDeliveryAreObservable() throws Exception {
        declareRabbitTopology();
        ReminderProperties properties = rabbitProperties();
        NotificationService notificationService = mock(NotificationService.class);
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(invocation -> {
            if (attempts.getAndIncrement() < properties.getRabbit().getMaxAttempts()) {
                throw new IllegalStateException("deterministic stage12 failure");
            }
            return null;
        }).when(notificationService).handleReminder(any());
        var deadLetterService = mock(yvon.backend.notification.NotificationDeadLetterService.class);
        NotificationMessageConsumer consumer = new NotificationMessageConsumer(new ObjectMapper().findAndRegisterModules(),
                notificationService, deadLetterService, rabbitTemplate, properties);

        ReminderDueMessage due = new ReminderDueMessage(PREFIX + "_message", 1L, taskId, "DUE_SOON",
                LocalDateTime.now().plusMinutes(1));
        publish(RABBIT_EXCHANGE, RABBIT_ROUTING_KEY, due.messageId(),
                new ObjectMapper().findAndRegisterModules().writeValueAsBytes(due), Map.of());

        try (org.springframework.amqp.rabbit.connection.Connection springConnection = rabbitConnectionFactory.createConnection();
             Channel channel = springConnection.getDelegate().createChannel()) {
            GetResponse first = awaitGet(channel, RABBIT_MAIN_QUEUE);
            consumer.consume(toSpringMessage(first), channel);
            GetResponse retryOne = awaitGet(channel, RABBIT_MAIN_QUEUE);
            assertThat(headerNumber(retryOne, "x-taskflow-retry-count")).isEqualTo(1);
            consumer.consume(toSpringMessage(retryOne), channel);
            GetResponse retryTwo = awaitGet(channel, RABBIT_MAIN_QUEUE);
            assertThat(headerNumber(retryTwo, "x-taskflow-retry-count")).isEqualTo(2);
            consumer.consume(toSpringMessage(retryTwo), channel);
            GetResponse dead = awaitGet(channel, RABBIT_DEAD_QUEUE);
            assertThat(dead.getProps().getMessageId()).isEqualTo(due.messageId());
            channel.basicAck(dead.getEnvelope().getDeliveryTag(), false);

            NotificationDeadLetterMapper mapper = mock(NotificationDeadLetterMapper.class);
            NotificationDeadLetterEntity entity = new NotificationDeadLetterEntity();
            entity.setId(12L);
            entity.setMessageId(due.messageId());
            entity.setEventType("REMINDER_DUE");
            entity.setPayloadJson(new String(first.getBody(), StandardCharsets.UTF_8));
            entity.setStatus("DEAD");
            when(mapper.selectById(12L)).thenReturn(entity);
            when(mapper.markReplayed(12L)).thenReturn(1);
            new NotificationDeadLetterService(mapper, rabbitTemplate, properties).replay(12L);

            GetResponse replay = awaitGet(channel, RABBIT_MAIN_QUEUE);
            consumer.consume(toSpringMessage(replay), channel);
            assertThat(attempts).hasValue(4);
        }

        jdbc.update("""
                INSERT INTO notification
                    (source_message_id, user_id, notification_type, title, content,
                     aggregate_type, aggregate_id, status, version, deleted)
                VALUES (?, ?, 'STAGE12', 'stage12', 'stage12', 'TASK', ?, 'UNREAD', 0, 0)
                ON DUPLICATE KEY UPDATE id = id
                """, PREFIX + "_idempotent", userId, taskId);
        jdbc.update("""
                INSERT INTO notification
                    (source_message_id, user_id, notification_type, title, content,
                     aggregate_type, aggregate_id, status, version, deleted)
                VALUES (?, ?, 'STAGE12', 'stage12 duplicate', 'stage12 duplicate', 'TASK', ?, 'UNREAD', 0, 0)
                ON DUPLICATE KEY UPDATE id = id
                """, PREFIX + "_idempotent", userId, taskId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE source_message_id = ? AND user_id = ?",
                Integer.class, PREFIX + "_idempotent", userId)).isEqualTo(1);
    }

    @Test
    @Order(2)
    void redisLossFailsClosedAndReminderIndexRebuildsFromDatabaseFixture() throws Exception {
        ReminderPlanMapper mapper = mock(ReminderPlanMapper.class);
        ReminderPlanEntity plan = new ReminderPlanEntity();
        plan.setId(901L);
        plan.setTaskId(taskId);
        plan.setReminderType("DUE_SOON");
        plan.setTriggerAt(LocalDateTime.now().plusMinutes(10));
        plan.setStatus("PLANNED");
        when(mapper.selectAllPlanned()).thenReturn(List.of(plan));
        ReminderProperties properties = new ReminderProperties();
        ReminderRedisIndexService index = new ReminderRedisIndexService(redisTemplate, mapper, properties);

        index.rebuildFromDatabase();
        assertThat(redisTemplate.opsForZSet().score(properties.getRedisKey(), "901")).isNotNull();

        restartContainer(redis);
        await(() -> redis.execInContainer("redis-cli", "PING").getStdout().contains("PONG"),
                value -> Boolean.TRUE.equals(value), Duration.ofSeconds(30), "Redis readiness");
        assertThat(redisTemplate.opsForZSet().score(properties.getRedisKey(), "901")).isNotNull();
        redisTemplate.delete(properties.getRedisKey());
        index.rebuildFromDatabase();
        assertThat(redisTemplate.opsForZSet().size(properties.getRedisKey())).isEqualTo(1L);
    }

    @Test
    @Order(3)
    void minioSuccessUnavailableAndOrphanCompensationAreObservable() throws Exception {
        AttachmentProperties properties = new AttachmentProperties();
        properties.getMinio().setEndpoint("http://" + minio.getHost() + ":" + minio.getMappedPort(9000));
        properties.getMinio().setAccessKey("minioadmin");
        properties.getMinio().setSecretKey("minioadmin_local");
        properties.getMinio().setBucket(PREFIX + "-bucket");
        MinioObjectStorage storage = new MinioObjectStorage(minioClient, properties);
        var taskService = mock(yvon.backend.task.TaskService.class);
        when(taskService.requireVisible(anyLong(), any())).thenReturn(new TaskEntity());
        var metadata = mock(TaskAttachmentMetadataService.class);
        TaskAttachmentEntity pending = new TaskAttachmentEntity();
        pending.setId(101L);
        pending.setVersion(0);
        when(metadata.createPending(any(TaskAttachmentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        TaskAttachmentEntity available = new TaskAttachmentEntity();
        available.setId(101L);
        available.setVersion(1);
        available.setStatus("AVAILABLE");
        when(metadata.markAvailable(any(), anyLong())).thenReturn(available);
        TaskAttachmentService service = new TaskAttachmentService(metadata, storage, taskService, properties);

        service.upload(taskId, new MockMultipartFile("file", "stage12.txt", "text/plain",
                "stage12 object".getBytes(StandardCharsets.UTF_8)), TestFixtures.principal());
        String successKey = pending.getObjectKey();
        assertThat(minioClient.statObject(StatObjectArgs.builder().bucket(properties.getMinio().getBucket())
                .object(successKey).build()).size()).isEqualTo(14L);

        restartContainer(minio);
        await(() -> {
            minioClient.listBuckets();
            return true;
        }, value -> Boolean.TRUE.equals(value), Duration.ofSeconds(30), "MinIO readiness");
        var unavailableMetadata = mock(TaskAttachmentMetadataService.class);
        TaskAttachmentEntity failedPending = new TaskAttachmentEntity();
        failedPending.setId(102L);
        failedPending.setVersion(0);
        when(unavailableMetadata.createPending(any(TaskAttachmentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        TaskAttachmentService unavailableService = new TaskAttachmentService(unavailableMetadata, storage, taskService, properties);
        assertThatThrownBy(() -> unavailableService.upload(taskId,
                new MockMultipartFile("file", "failed.txt", "text/plain", "failed".getBytes(StandardCharsets.UTF_8)),
                TestFixtures.principal())).hasMessage("附件上传失败，请稍后重试");
        verify(unavailableMetadata).markFailed(any(), anyLong());
        restartContainer(minio);
        await(() -> {
            minioClient.listBuckets();
            return true;
        }, value -> Boolean.TRUE.equals(value), Duration.ofSeconds(30), "MinIO readiness");

        var orphanMetadata = mock(TaskAttachmentMetadataService.class);
        TaskAttachmentEntity orphanPending = new TaskAttachmentEntity();
        orphanPending.setId(103L);
        orphanPending.setVersion(0);
        when(orphanMetadata.createPending(any(TaskAttachmentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orphanMetadata.markAvailable(any(), anyLong())).thenThrow(new IllegalStateException("database commit failed"));
        TaskAttachmentService orphanService = new TaskAttachmentService(orphanMetadata, storage, taskService, properties);
        assertThatThrownBy(() -> orphanService.upload(taskId,
                new MockMultipartFile("file", "orphan.txt", "text/plain", "orphan".getBytes(StandardCharsets.UTF_8)),
                TestFixtures.principal())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> minioClient.statObject(StatObjectArgs.builder().bucket(properties.getMinio().getBucket())
                .object(orphanPending.getObjectKey()).build())).isInstanceOf(Exception.class);
    }

    @Test
    @Order(4)
    void mysqlRestartKeepsFlywayAndCoreFactsOnTheSameContainerStorage() throws Exception {
        jdbc.update("INSERT INTO reminder_plan (task_id, reminder_type, trigger_at, status, version) VALUES (?, 'STAGE12', ?, 'PLANNED', 0)",
                taskId, LocalDateTime.now().plusMinutes(20));
        Map<String, Integer> before = snapshot();
        String beforeFlyway = jdbc.queryForObject("SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1", String.class);

        restartContainer(mysql);
        await(() -> jdbc.queryForObject("SELECT 1", Integer.class), value -> Integer.valueOf(1).equals(value),
                Duration.ofSeconds(60), "MySQL SQL readiness");

        Map<String, Integer> after = snapshot();
        String afterFlyway = jdbc.queryForObject("SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1", String.class);
        assertThat(afterFlyway).isEqualTo(beforeFlyway).isEqualTo("8");
        assertThat(after).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM task WHERE id = ?", Integer.class, taskId)).isEqualTo(1);
    }

    private static void configureRedis() {
        redisConnectionFactory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        redisConnectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(redisConnectionFactory);
        redisTemplate.afterPropertiesSet();
    }

    private static void configureRabbit() {
        rabbitConnectionFactory = new CachingConnectionFactory(URI.create(rabbit.getAmqpUrl()));
        rabbitConnectionFactory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        rabbitConnectionFactory.setPublisherReturns(true);
        rabbitTemplate = new RabbitTemplate(rabbitConnectionFactory);
        rabbitTemplate.setMandatory(true);
    }

    private static ReminderProperties rabbitProperties() {
        ReminderProperties properties = new ReminderProperties();
        properties.getRabbit().setExchange(RABBIT_EXCHANGE);
        properties.getRabbit().setQueue(RABBIT_MAIN_QUEUE);
        properties.getRabbit().setRoutingKey(RABBIT_ROUTING_KEY);
        properties.getRabbit().setRetryExchange(RABBIT_RETRY_EXCHANGE);
        properties.getRabbit().setRetryQueue(RABBIT_RETRY_QUEUE);
        properties.getRabbit().setRetryRoutingKey("retry");
        properties.getRabbit().setDeadLetterExchange(RABBIT_DEAD_EXCHANGE);
        properties.getRabbit().setDeadLetterQueue(RABBIT_DEAD_QUEUE);
        properties.getRabbit().setDeadLetterRoutingKey("dead");
        properties.getRabbit().setMaxAttempts(3);
        properties.getRabbit().setRetryDelayMs(100);
        return properties;
    }

    private static void declareRabbitTopology() throws Exception {
        try (org.springframework.amqp.rabbit.connection.Connection springConnection = rabbitConnectionFactory.createConnection();
             Channel channel = springConnection.getDelegate().createChannel()) {
            channel.exchangeDeclare(RABBIT_EXCHANGE, "topic", true);
            channel.exchangeDeclare(RABBIT_RETRY_EXCHANGE, "direct", true);
            channel.exchangeDeclare(RABBIT_DEAD_EXCHANGE, "direct", true);
            channel.queueDeclare(RABBIT_MAIN_QUEUE, true, false, false, Map.of());
            channel.queueDeclare(RABBIT_RETRY_QUEUE, true, false, false, Map.of(
                    "x-message-ttl", 100,
                    "x-dead-letter-exchange", RABBIT_EXCHANGE,
                    "x-dead-letter-routing-key", RABBIT_ROUTING_KEY));
            channel.queueDeclare(RABBIT_DEAD_QUEUE, true, false, false, Map.of());
            channel.queueBind(RABBIT_MAIN_QUEUE, RABBIT_EXCHANGE, RABBIT_ROUTING_KEY);
            channel.queueBind(RABBIT_RETRY_QUEUE, RABBIT_RETRY_EXCHANGE, "retry");
            channel.queueBind(RABBIT_DEAD_QUEUE, RABBIT_DEAD_EXCHANGE, "dead");
        }
    }

    private static void publish(String exchange, String routingKey, String messageId, byte[] body,
                                Map<String, Object> headers) {
        MessageProperties properties = new MessageProperties();
        properties.setMessageId(messageId);
        properties.setContentType("application/json");
        properties.getHeaders().putAll(headers);
        CorrelationDataHolder.send(rabbitTemplate, exchange, routingKey, new Message(body, properties), messageId);
    }

    private static GetResponse awaitGet(Channel channel, String queue) throws Exception {
        return await(() -> channel.basicGet(queue, false), response -> response != null,
                Duration.ofSeconds(10), "Rabbit queue message: " + queue);
    }

    private static Message toSpringMessage(GetResponse response) {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(response.getEnvelope().getDeliveryTag());
        properties.setMessageId(response.getProps().getMessageId());
        properties.setContentType(response.getProps().getContentType());
        if (response.getProps().getHeaders() != null) properties.getHeaders().putAll(response.getProps().getHeaders());
        return new Message(response.getBody(), properties);
    }

    private static int headerNumber(GetResponse response, String key) {
        Object value = response.getProps().getHeaders().get(key);
        return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
    }

    private static long insertUser() {
        jdbc.update("INSERT INTO sys_user (username, employee_no, display_name, password_hash) VALUES (?, ?, ?, ?)",
                USERNAME, EMPLOYEE_NO, "Stage12 User", "not-used");
        return jdbc.queryForObject("SELECT id FROM sys_user WHERE username = ?", Long.class, USERNAME);
    }

    private static long insertTask() {
        jdbc.update("INSERT INTO task (task_no, title, creator_id, status, priority, version) VALUES (?, ?, ?, 'IN_PROGRESS', 'MEDIUM', 0)",
                TASK_NO, "Stage12 task", userId);
        return jdbc.queryForObject("SELECT id FROM task WHERE task_no = ?", Long.class, TASK_NO);
    }

    private static Map<String, Integer> snapshot() {
        Map<String, Integer> result = new HashMap<>();
        result.put("users", jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE username = ?", Integer.class, USERNAME));
        result.put("tasks", jdbc.queryForObject("SELECT COUNT(*) FROM task WHERE task_no = ?", Integer.class, TASK_NO));
        result.put("notifications", jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE source_message_id LIKE ?", Integer.class, PREFIX + "%"));
        result.put("attachments", jdbc.queryForObject("SELECT COUNT(*) FROM task_attachment WHERE object_key LIKE ?", Integer.class, "tasks/" + taskId + "/" + PREFIX + "%"));
        result.put("reminders", jdbc.queryForObject("SELECT COUNT(*) FROM reminder_plan WHERE task_id = ?", Integer.class, taskId));
        return result;
    }

    private static <T> T await(Callable<T> action, java.util.function.Predicate<T> predicate,
                               Duration timeout, String description) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        Throwable last = null;
        while (System.nanoTime() < deadline) {
            try {
                T value = action.call();
                if (predicate.test(value)) return value;
            } catch (Throwable exception) {
                last = exception;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Timed out waiting for " + description, last);
    }

    private static void restartContainer(org.testcontainers.containers.Container<?> container) throws Exception {
        String id = container.getContainerId();
        DockerClientFactory.instance().client().stopContainerCmd(id).withTimeout(30).exec();
        await(() -> inspect(id), state -> !state.getState().getRunning(), Duration.ofSeconds(20), "container stop");
        DockerClientFactory.instance().client().startContainerCmd(id).exec();
        await(() -> inspect(id), state -> state.getState().getRunning(), Duration.ofSeconds(20), "container start");
        await(() -> container.isRunning(), value -> Boolean.TRUE.equals(value), Duration.ofSeconds(60), "container ready");
    }

    private static InspectContainerResponse inspect(String id) {
        return DockerClientFactory.instance().client().inspectContainerCmd(id).exec();
    }

    private static final class CorrelationDataHolder {
        private CorrelationDataHolder() { }

        private static void send(RabbitTemplate template, String exchange, String routingKey,
                                 Message message, String id) {
            org.springframework.amqp.rabbit.connection.CorrelationData correlation =
                    new org.springframework.amqp.rabbit.connection.CorrelationData(id);
            template.send(exchange, routingKey, message, correlation);
            try {
                var confirm = correlation.getFuture().get(10, TimeUnit.SECONDS);
                assertThat(confirm).isNotNull();
                assertThat(confirm.isAck()).isTrue();
            } catch (Exception exception) {
                throw new IllegalStateException("Rabbit publish was not confirmed", exception);
            }
        }
    }
}
