package io.raspiska.featuretoggle.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Service
public class MetricsService {

    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, Counter> featureCheckCounters = new ConcurrentHashMap<>();
    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;
    private final Timer featureCheckTimer;

    public MetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        this.cacheHitCounter = Counter.builder("feature_toggle_cache_hits")
                .description("Number of cache hits for feature toggle checks")
                .register(meterRegistry);
        
        this.cacheMissCounter = Counter.builder("feature_toggle_cache_misses")
                .description("Number of cache misses for feature toggle checks")
                .register(meterRegistry);
        
        this.featureCheckTimer = Timer.builder("feature_toggle_check_duration")
                .description("Time taken to check feature toggle")
                .register(meterRegistry);
    }

    public void recordFeatureCheck(String featureName, boolean enabled) {
        String result = enabled ? "enabled" : "disabled";
        featureCheckCounters.computeIfAbsent(
                featureName + "_" + result,
                key -> Counter.builder("feature_toggle_checks")
                        .tag("feature", featureName)
                        .tag("result", result)
                        .description("Number of feature toggle checks")
                        .register(meterRegistry)
        ).increment();
    }

    public void recordCacheHit() {
        cacheHitCounter.increment();
    }

    public void recordCacheMiss() {
        cacheMissCounter.increment();
    }

    public <T> T timeFeatureCheck(Supplier<T> supplier) {
        return featureCheckTimer.record(supplier);
    }
}
