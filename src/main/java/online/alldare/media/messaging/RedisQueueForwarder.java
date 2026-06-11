package online.alldare.media.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import java.time.Duration;

@Component
@Slf4j
@RequiredArgsConstructor
public class RedisQueueForwarder implements CommandLineRunner, DisposableBean {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private Thread thread;

    @Override
    public void run(String... args) {
        thread = new Thread(() -> {
            log.info("Started Redis queue forwarder: list:uploads (List) -> list:uploads (Pub/Sub)");
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    String message = redisTemplate.opsForList().leftPop("list:uploads", Duration.ofSeconds(5));
                    if (message != null) {
                        String cleanMessage = message;
                        try {
                            JsonNode rootNode = objectMapper.readTree(message);
                            if (rootNode.isArray() && rootNode.size() > 0) {
                                JsonNode firstElement = rootNode.get(0);
                                if (firstElement.has("Event")) {
                                    JsonNode eventNode = firstElement.get("Event");
                                    ObjectNode standardEvent = objectMapper.createObjectNode();
                                    standardEvent.set("Records", eventNode);
                                    cleanMessage = objectMapper.writeValueAsString(standardEvent);
                                }
                            }
                        } catch (Exception e) {
                            log.warn("Failed to parse/normalize MinIO event payload, forwarding raw message instead: {}", e.getMessage());
                        }

                        log.info("Forwarding message to Redis Pub/Sub: {}", cleanMessage);
                        redisTemplate.convertAndSend("list:uploads", cleanMessage);
                    }
                } catch (Exception e) {
                    // Avoid logging errors if we are interrupted/shutting down
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }
                    log.error("Error in Redis queue forwarder loop", e);
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        });
        thread.setName("redis-forwarder-thread");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void destroy() {
        log.info("Stopping Redis queue forwarder thread...");
        if (thread != null) {
            thread.interrupt();
        }
    }
}
