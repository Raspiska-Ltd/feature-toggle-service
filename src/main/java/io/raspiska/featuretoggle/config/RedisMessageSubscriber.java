package io.raspiska.featuretoggle.config;

import io.raspiska.featuretoggle.service.FeatureToggleCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisMessageSubscriber implements MessageListener {

    private final FeatureToggleCacheService cacheService;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String featureName = new String(message.getBody());
        log.info("Received cache invalidation for feature: {}", featureName);
        
        if ("*".equals(featureName)) {
            cacheService.evictAllFromLocalCache();
        } else {
            cacheService.evictFromLocalCache(featureName);
        }
    }
}
