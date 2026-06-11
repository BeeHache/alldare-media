package online.alldare.media.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.alldare.media.messaging.MetaDataUpdateEventListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;

import java.time.Duration;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class RedisStreamConfig {

    private final MetaDataUpdateEventListener metaDataUpdateEventListener;

    @Bean
    public StreamMessageListenerContainer<String, ObjectRecord<String, String>> streamMessageListenerContainer(
            RedisConnectionFactory connectionFactory) {

        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, ObjectRecord<String, String>> containerOptions =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
                        .batchSize(10)
                        .pollTimeout(Duration.ofSeconds(1))
                        .targetType(String.class)
                        .build();

        StreamMessageListenerContainer<String, ObjectRecord<String, String>> container =
                StreamMessageListenerContainer.create(connectionFactory, containerOptions);

        String streamKey = "stream:media";
        String groupName = "alldare-media-group";
        String consumerName = "media-worker-1";

        try {
            connectionFactory.getConnection().streamCommands().xGroupCreate(streamKey.getBytes(), groupName, ReadOffset.from("0-0"), true);
        } catch (Exception e) {
            log.debug("Consumer group {} already exists or stream {} not initialized", groupName, streamKey);
        }

        container.receive(
                Consumer.from(groupName, consumerName),
                StreamOffset.create(streamKey, ReadOffset.lastConsumed()),
                metaDataUpdateEventListener
        );

        container.start();
        return container;
    }
}
