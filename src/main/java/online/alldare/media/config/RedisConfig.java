package online.alldare.media.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import online.alldare.media.messaging.MediaEventConsumer;

@Configuration
public class RedisConfig {

    @Bean
    public MessageListenerAdapter mediaUploadListenerAdapter(MediaEventConsumer receiver) {
        return new MessageListenerAdapter(receiver, "handleMessage");
    }

    @Bean
    public RedisMessageListenerContainer container(RedisConnectionFactory connectionFactory,
                                                    MessageListenerAdapter mediaUploadListenerAdapter) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(mediaUploadListenerAdapter, new ChannelTopic("list:uploads"));
        return container;
    }
}
