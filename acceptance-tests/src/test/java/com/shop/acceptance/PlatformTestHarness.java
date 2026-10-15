package com.shop.acceptance;

import com.redis.testcontainers.RedisContainer;
import com.shop.events.EventTopics;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.concurrent.TimeUnit;

public final class PlatformTestHarness {

    private static final Pattern TOMCAT_PORT = Pattern.compile("Tomcat started on port (\\d+)");

    public final PostgreSQLContainer<?> postgres;
    public final KafkaContainer kafka;
    public final RedisContainer redis;

    public Process inventoryProcess;
    public Process paymentProcess;
    public Process orderProcess;

    public int orderPort;
    public int inventoryPort;
    public int paymentPort;

    private Path inventoryLog;
    private Path paymentLog;
    private Path orderLog;

    public String ordersJdbcUrl;
    public String inventoryJdbcUrl;
    public String paymentsJdbcUrl;

    private final Path repoRoot;

    public PlatformTestHarness() {
        repoRoot = Path.of(System.getProperty("acceptance.repo.root", "..")).toAbsolutePath().normalize();
        postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                .withDatabaseName("shop")
                .withUsername("shop")
                .withPassword("shop")
                .withInitScript("init-databases.sql");
        // Legacy TC KafkaContainer maps broker advertised listener to host:mappedPort (required for forked JVM services).
        kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));
        redis = new RedisContainer("redis:7-alpine");
    }

    public void startAll() throws Exception {
        startAll(Map.of(), Map.of());
    }

    public void startAll(Map<String, String> orderExtraEnv, Map<String, String> inventoryExtraEnv) throws Exception {
        postgres.start();
        kafka.start();
        redis.start();
        createKafkaTopics();

        String pgHost = postgres.getHost();
        int pgPort = postgres.getMappedPort(5432);
        ordersJdbcUrl = "jdbc:postgresql://" + pgHost + ":" + pgPort + "/orders";
        inventoryJdbcUrl = "jdbc:postgresql://" + pgHost + ":" + pgPort + "/inventory";
        paymentsJdbcUrl = "jdbc:postgresql://" + pgHost + ":" + pgPort + "/payments";

        Map<String, String> common = baseEnv(pgHost, pgPort);
        Started inventory = startJar("inventory-service", merge(common, inventoryExtraEnv), inventoryJdbcUrl, List.of());
        inventoryProcess = inventory.process();
        inventoryPort = inventory.port();
        inventoryLog = inventory.log();

        Started payment = startJar("payment-service", common, paymentsJdbcUrl, List.of());
        paymentProcess = payment.process();
        paymentPort = payment.port();
        paymentLog = payment.log();

        Map<String, String> orderEnv = merge(common, orderExtraEnv);
        orderEnv.put("INVENTORY_SERVICE_URL", "http://localhost:" + inventoryPort);
        orderEnv.put("PAYMENT_SERVICE_URL", "http://localhost:" + paymentPort);
        Started order = startJar("order-service", orderEnv, ordersJdbcUrl, List.of());
        orderProcess = order.process();
        orderPort = order.port();
        orderLog = order.log();

        awaitTable(inventoryJdbcUrl, "inventory");
        awaitTable(ordersJdbcUrl, "orders");
        awaitTable(paymentsJdbcUrl, "payments");
    }

    public void stopAll() {
        stop(orderProcess);
        stop(paymentProcess);
        stop(inventoryProcess);
        redis.stop();
        kafka.stop();
        postgres.stop();
    }

    public void restartOrder(Map<String, String> extraEnv, List<String> extraArgs) throws Exception {
        stop(orderProcess);
        Map<String, String> env = merge(baseEnv(postgres.getHost(), postgres.getMappedPort(5432)), extraEnv);
        env.put("INVENTORY_SERVICE_URL", "http://localhost:" + inventoryPort);
        env.put("PAYMENT_SERVICE_URL", "http://localhost:" + paymentPort);
        Started order = startJar("order-service", env, ordersJdbcUrl, extraArgs);
        orderProcess = order.process();
        orderPort = order.port();
        orderLog = order.log();
    }

    public void ensureAllRunning() throws Exception {
        if (!isHealthy(inventoryPort)) {
            restartInventory(Map.of());
        }
        if (!isHealthy(paymentPort)) {
            Map<String, String> env = baseEnv(postgres.getHost(), postgres.getMappedPort(5432));
            Started payment = startJar("payment-service", env, paymentsJdbcUrl, List.of());
            paymentProcess = payment.process();
            paymentPort = payment.port();
            paymentLog = payment.log();
        }
        if (!isHealthy(orderPort)) {
            restartOrder(Map.of(), List.of());
        }
    }

    public void restartInventory(Map<String, String> extraEnv) throws Exception {
        stop(inventoryProcess);
        Map<String, String> env = merge(baseEnv(postgres.getHost(), postgres.getMappedPort(5432)), extraEnv);
        Started inventory = startJar("inventory-service", env, inventoryJdbcUrl, List.of());
        inventoryProcess = inventory.process();
        inventoryPort = inventory.port();
        inventoryLog = inventory.log();
    }

    private Map<String, String> baseEnv(String pgHost, int pgPort) {
        Map<String, String> env = new HashMap<>();
        env.put("POSTGRES_HOST", pgHost);
        env.put("POSTGRES_PORT", String.valueOf(pgPort));
        env.put("POSTGRES_USER", "shop");
        env.put("POSTGRES_PASSWORD", "shop");
        env.put("ORDERS_DB", "orders");
        env.put("INVENTORY_DB", "inventory");
        env.put("PAYMENTS_DB", "payments");
        env.put("KAFKA_BOOTSTRAP_SERVERS", kafkaBootstrapServers());
        env.put("REDIS_HOST", redis.getHost());
        env.put("REDIS_PORT", String.valueOf(redis.getMappedPort(6379)));
        env.put("PAYMENT_FAILURE_RATE", "0");
        return env;
    }

    private Started startJar(String module, Map<String, String> extraEnv, String jdbcUrl, List<String> extraArgs)
            throws Exception {
        Path jar = repoRoot.resolve(module).resolve("target").resolve(module + "-0.1.0-SNAPSHOT-boot.jar");
        if (!jar.toFile().exists()) {
            throw new IllegalStateException("Missing boot jar (run mvn package first): " + jar);
        }
        Path logDir = repoRoot.resolve("acceptance-tests/target/acceptance-logs");
        Files.createDirectories(logDir);
        Path log = logDir.resolve(module + "-" + System.nanoTime() + ".log");
        String java = Path.of(System.getProperty("java.home")).resolve("bin/java").toString();
        List<String> command = new java.util.ArrayList<>();
        command.add(java);
        command.add("-Xms64m");
        command.add("-Xmx256m");
        command.add("-jar");
        command.add(jar.toString());
        command.add("--server.port=0");
        command.add("--spring.datasource.url=" + jdbcUrl);
        command.add("--spring.datasource.username=shop");
        command.add("--spring.datasource.password=shop");
        command.add("--spring.kafka.bootstrap-servers=" + extraEnv.get("KAFKA_BOOTSTRAP_SERVERS"));
        // Idempotent acks=all can hang until delivery.timeout against single-broker Testcontainers Kafka.
        command.add("--spring.kafka.producer.acks=1");
        command.add("--spring.kafka.producer.properties.enable.idempotence=false");
        command.add("--spring.kafka.producer.delivery-timeout-ms=15000");
        command.add("--spring.kafka.producer.request-timeout-ms=10000");
        command.add("--spring.data.redis.host=" + extraEnv.get("REDIS_HOST"));
        command.add("--spring.data.redis.port=" + extraEnv.get("REDIS_PORT"));
        if (extraEnv.containsKey("INVENTORY_SERVICE_URL")) {
            command.add("--inventory.base-url=" + extraEnv.get("INVENTORY_SERVICE_URL"));
        }
        if (extraEnv.containsKey("PAYMENT_SERVICE_URL")) {
            command.add("--payment.base-url=" + extraEnv.get("PAYMENT_SERVICE_URL"));
        }
        if (extraEnv.containsKey("OUTBOX_ENABLED")) {
            command.add("--outbox.enabled=" + extraEnv.get("OUTBOX_ENABLED"));
        }
        for (Map.Entry<String, String> e : extraEnv.entrySet()) {
            if (e.getKey().contains(".")) {
                command.add("--" + e.getKey() + "=" + e.getValue());
            }
        }
        command.addAll(extraArgs);
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.environment().putAll(extraEnv);
        pb.redirectErrorStream(true);
        pb.redirectOutput(log.toFile());
        Process process = pb.start();
        int port = awaitListeningPort(process, log, module);
        awaitHealthy(port, log, module);
        return new Started(process, port, log);
    }

    private record Started(Process process, int port, Path log) {}

    private static Map<String, String> merge(Map<String, String> base, Map<String, String> extra) {
        Map<String, String> merged = new HashMap<>(base);
        merged.putAll(extra);
        return merged;
    }

    private static void stop(Process process) {
        if (process != null && process.isAlive()) {
            process.destroy();
            try {
                process.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private static boolean isHealthy(int port) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/actuator/health"))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 && response.body().contains("UP");
        } catch (Exception e) {
            return false;
        }
    }

    private String kafkaBootstrapServers() {
        String raw = kafka.getBootstrapServers();
        if (raw != null && raw.startsWith("PLAINTEXT://")) {
            return raw.substring("PLAINTEXT://".length());
        }
        return raw;
    }

    private void createKafkaTopics() throws Exception {
        java.util.Properties props = new java.util.Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers());
        try (AdminClient admin = AdminClient.create(props)) {
            var topics = java.util.List.of(
                    new NewTopic(EventTopics.ORDERS, 6, (short) 1),
                    new NewTopic(EventTopics.INVENTORY, 6, (short) 1),
                    new NewTopic(EventTopics.PAYMENTS, 6, (short) 1),
                    new NewTopic(EventTopics.dlt(EventTopics.ORDERS), 6, (short) 1),
                    new NewTopic(EventTopics.dlt(EventTopics.INVENTORY), 6, (short) 1),
                    new NewTopic(EventTopics.dlt(EventTopics.PAYMENTS), 6, (short) 1));
            admin.createTopics(topics).all().get();
        }
    }

    private static int awaitListeningPort(Process process, Path log, String module) throws Exception {
        long deadline = System.currentTimeMillis() + 120_000;
        while (System.currentTimeMillis() < deadline) {
            if (!process.isAlive()) {
                throw new IllegalStateException(module + " exited before listening:\n" + tail(log));
            }
            String text = Files.exists(log) ? Files.readString(log) : "";
            Matcher matcher = TOMCAT_PORT.matcher(text);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
            Thread.sleep(200);
        }
        throw new IllegalStateException(module + " did not log a listen port:\n" + tail(log));
    }

    private static void awaitHealthy(int port, Path log, String module) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/actuator/health"))
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build();
        long deadline = System.currentTimeMillis() + 120_000;
        while (System.currentTimeMillis() < deadline) {
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200 && response.body().contains("UP")) {
                    return;
                }
            } catch (Exception ignored) {
                // retry
            }
            Thread.sleep(500);
        }
        throw new IllegalStateException(module + " on port " + port + " not healthy:\n" + tail(log));
    }

    private static String tail(Path log) {
        try {
            if (!Files.exists(log)) {
                return "(no log)";
            }
            String text = Files.readString(log);
            int start = Math.max(0, text.length() - 4000);
            return text.substring(start);
        } catch (Exception e) {
            return "(could not read log: " + e.getMessage() + ")";
        }
    }

    private static void awaitTable(String jdbcUrl, String table) throws Exception {
        long deadline = System.currentTimeMillis() + 120_000;
        while (System.currentTimeMillis() < deadline) {
            try (Connection c = DriverManager.getConnection(jdbcUrl, "shop", "shop");
                    Statement st = c.createStatement()) {
                st.executeQuery("SELECT 1 FROM " + table + " LIMIT 1");
                return;
            } catch (Exception ignored) {
                Thread.sleep(500);
            }
        }
        throw new IllegalStateException("Table " + table + " not ready for " + jdbcUrl);
    }
}
