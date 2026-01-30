package io.raspiska.featuretoggle.client;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.web.client.RestTemplate;

@AutoConfiguration
@EnableConfigurationProperties(FeatureToggleClientProperties.class)
@ConditionalOnProperty(prefix = "feature-toggle.client", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FeatureToggleAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "featureToggleRestTemplate")
    public RestTemplate featureToggleRestTemplate() {
        return new RestTemplate();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(RedisTemplate.class)
    public RedisTemplate<String, Object> featureToggleRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        return template;
    }

    @Bean
    @ConditionalOnMissingBean
    public FeatureToggleClient featureToggleClient(
            RestTemplate featureToggleRestTemplate,
            RedisTemplate<String, Object> featureToggleRedisTemplate,
            FeatureToggleClientProperties properties) {
        return new FeatureToggleClient(featureToggleRestTemplate, featureToggleRedisTemplate, properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "feature-toggle.client.redis", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ChannelTopic featureToggleClientTopic(FeatureToggleClientProperties properties) {
        return new ChannelTopic(properties.getRedis().getChannel());
    }

    @Bean
    @ConditionalOnProperty(prefix = "feature-toggle.client.redis", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RedisMessageListenerContainer featureToggleListenerContainer(
            RedisConnectionFactory connectionFactory,
            FeatureToggleClient featureToggleClient,
            ChannelTopic featureToggleClientTopic) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(new MessageListenerAdapter(featureToggleClient, "onMessage"), featureToggleClientTopic);
        return container;
    }
}
