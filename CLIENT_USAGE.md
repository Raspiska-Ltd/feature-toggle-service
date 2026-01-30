# Feature Toggle Client Library

This document explains how to integrate the Feature Toggle Client library into your Spring Boot microservices.

## Quick Start

### 1. Add Dependency

First, publish the client library to your local Maven repository:

```bash
cd feature-toggle-client
./gradlew publishToMavenLocal
```

Then add the dependency to your microservice's `build.gradle`:

```gradle
dependencies {
    implementation 'io.raspiska:feature-toggle-client:1.0.0'
}
```

### 2. Configure Application

Add the following to your `application.yml`:

```yaml
feature-toggle:
  client:
    service-url: http://feature-toggle-service:8090
    cache-ttl-seconds: 30
    connect-timeout-ms: 5000
    read-timeout-ms: 5000
    defaults:
      WITHDRAW: DISABLED
      DEPOSIT: ENABLED
      NEW_FEATURE: DISABLED
```

### 3. Enable Feature Toggle

Add `@EnableFeatureToggle` to your main application class:

```java
import io.raspiska.featuretoggle.client.EnableFeatureToggle;

@SpringBootApplication
@EnableFeatureToggle
public class YourApplication {
    public static void main(String[] args) {
        SpringApplication.run(YourApplication.class, args);
    }
}
```

## Usage

### Programmatic Usage

Inject `FeatureToggleClient` and use it directly:

```java
import io.raspiska.featuretoggle.client.FeatureToggleClient;
import io.raspiska.featuretoggle.client.FeatureCheckResult;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final FeatureToggleClient featureToggle;

    public void processWithdrawal(String userId, BigDecimal amount) {
        // Check if feature is enabled for this user
        if (!featureToggle.isEnabled("WITHDRAW", userId)) {
            throw new FeatureDisabledException("WITHDRAW", "Withdrawals are currently disabled");
        }
        
        // Process withdrawal...
    }

    public void processDeposit(String userId, BigDecimal amount) {
        // Use requireEnabled to throw exception automatically
        featureToggle.requireEnabled("DEPOSIT", userId);
        
        // Process deposit...
    }

    public PaymentOptions getPaymentOptions(String userId) {
        PaymentOptions options = new PaymentOptions();
        
        // Check multiple features
        options.setWithdrawEnabled(featureToggle.isEnabled("WITHDRAW", userId));
        options.setDepositEnabled(featureToggle.isEnabled("DEPOSIT", userId));
        options.setBankTransferEnabled(featureToggle.isEnabled("BANK_TRANSFER", userId));
        
        return options;
    }
}
```

### Annotation-Based Usage

Use `@FeatureEnabled` annotation on methods:

```java
import io.raspiska.featuretoggle.client.FeatureEnabled;

@Service
public class BettingService {

    // Method will throw FeatureDisabledException if feature is disabled
    @FeatureEnabled("LIVE_BETTING")
    public void placeLiveBet(String userId, BetRequest request) {
        // This code only runs if LIVE_BETTING is enabled
    }

    // With user ID parameter - checks if feature is enabled for specific user
    @FeatureEnabled(value = "VIP_FEATURES", userIdParam = "userId")
    public void accessVipFeature(String userId, String featureId) {
        // This code only runs if VIP_FEATURES is enabled for this userId
    }

    // Return null instead of throwing exception
    @FeatureEnabled(value = "BONUS_SYSTEM", throwOnDisabled = false)
    public BonusInfo getBonusInfo(String userId) {
        return bonusRepository.findByUserId(userId);
    }
}
```

### Controller Usage

```java
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final FeatureToggleClient featureToggle;
    private final PaymentService paymentService;

    @PostMapping("/withdraw")
    public ResponseEntity<?> withdraw(@RequestBody WithdrawRequest request,
                                      @AuthenticationPrincipal User user) {
        if (!featureToggle.isEnabled("WITHDRAW", user.getId())) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Withdrawals are temporarily disabled"));
        }
        
        return ResponseEntity.ok(paymentService.processWithdrawal(user.getId(), request));
    }

    // Or use annotation on controller method
    @FeatureEnabled(value = "FAST_WITHDRAW", userIdParam = "userId")
    @PostMapping("/fast-withdraw")
    public ResponseEntity<?> fastWithdraw(@RequestBody WithdrawRequest request,
                                          @RequestParam String userId) {
        return ResponseEntity.ok(paymentService.processFastWithdrawal(userId, request));
    }
}
```

## Configuration Reference

| Property | Description | Default |
|----------|-------------|---------|
| `feature-toggle.client.service-url` | URL of the feature toggle service | `http://localhost:8090` |
| `feature-toggle.client.cache-ttl-seconds` | Local cache TTL in seconds | `30` |
| `feature-toggle.client.connect-timeout-ms` | HTTP connection timeout | `5000` |
| `feature-toggle.client.read-timeout-ms` | HTTP read timeout | `5000` |
| `feature-toggle.client.defaults.*` | Default status per feature when service unavailable | - |

## Safe Defaults

Configure safe defaults for critical features. These are used when:
- The feature toggle service is unavailable
- Redis cache is empty
- Network errors occur

```yaml
feature-toggle:
  client:
    defaults:
      # Critical financial operations - default to DISABLED for safety
      WITHDRAW: DISABLED
      BANK_TRANSFER: DISABLED
      
      # Non-critical features - can default to ENABLED
      DARK_MODE: ENABLED
      NEW_UI: ENABLED
```

## Redis Integration

### Option 1: HTTP Mode with Redis Pub/Sub (Default)

For real-time cache invalidation across multiple instances:

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}

feature-toggle:
  client:
    service-url: http://feature-toggle-service:8090
    redis:
      enabled: true
      channel: feature-toggle-updates
      direct-mode: false  # Default - uses HTTP calls
```

In this mode:
1. Feature checks make HTTP calls to the feature toggle service
2. Redis pub/sub invalidates local cache when toggles change
3. Good for when service and clients are in different networks

### Option 2: Direct Redis Mode (Zero HTTP Overhead)

**When your microservices share the same Redis instance as the feature toggle service**, you can enable direct Redis mode to eliminate HTTP calls entirely:

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}

feature-toggle:
  client:
    redis:
      enabled: true
      channel: feature-toggle-updates
      direct-mode: true  # Read directly from Redis - NO HTTP calls!
```

In this mode:
1. **No HTTP calls** - client reads directly from Redis
2. Sub-millisecond feature checks (~0.5ms vs ~5-10ms for HTTP)
3. No dependency on feature toggle service availability for reads
4. Still uses pub/sub for cache invalidation
5. Feature toggle service only needed for management (create/update/delete)

### Performance Comparison

| Mode | Latency | Network Calls | Use Case |
|------|---------|---------------|----------|
| HTTP Mode | ~5-10ms | HTTP + Redis pub/sub | Separate networks |
| Direct Redis | ~0.5ms | Redis only | Shared Redis instance |

### Architecture: Direct Redis Mode

```
┌─────────────────────────────────────────────────────────────────┐
│                     Your Microservice                           │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                  FeatureToggleClient                     │   │
│  │  ┌─────────────┐  ┌─────────────────────────────────┐   │   │
│  │  │ Local Cache │  │ Direct Redis Read (no HTTP!)    │   │   │
│  │  │ (30s TTL)   │  │ + Pub/Sub Invalidation          │   │   │
│  │  └──────┬──────┘  └──────────────┬──────────────────┘   │   │
│  └─────────┼────────────────────────┼──────────────────────┘   │
└────────────┼────────────────────────┼──────────────────────────┘
             │                        │
             │                 ┌──────▼──────┐
             │                 │    Redis    │ ◄── Shared Instance
             │                 │  (Central)  │
             │                 └──────┬──────┘
             │                        │
             │         ┌──────────────▼──────────────────┐
             │         │   Feature Toggle Service        │
             │         │   (Management API only)         │
             │         │  ┌─────────────────────────┐    │
             │         │  │   SQLite (persistence)  │    │
             │         │  └─────────────────────────┘    │
             │         └─────────────────────────────────┘
             │
    Cache Hit│ (fastest path)
             ▼
      Feature Check Result
```

### When to Use Direct Redis Mode

**Use Direct Redis Mode when:**
- All microservices share the same Redis cluster
- You need sub-millisecond feature checks
- High throughput (thousands of checks/second)
- You want to reduce load on feature toggle service

**Use HTTP Mode when:**
- Microservices are in different networks/VPCs
- Redis is not shared between services
- You need the service to handle complex logic not in Redis

## Redis High Availability Configuration

Spring Boot's Redis auto-configuration supports **Sentinel** and **Cluster** modes out of the box.

### Option 1: Standalone Redis (Default)

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      password: ${REDIS_PASSWORD:}
```

### Option 2: Redis Sentinel (High Availability)

Redis Sentinel provides automatic failover when master goes down.

```yaml
spring:
  data:
    redis:
      sentinel:
        master: mymaster
        nodes:
          - sentinel1.example.com:26379
          - sentinel2.example.com:26379
          - sentinel3.example.com:26379
      password: ${REDIS_PASSWORD:}
```

**Environment variables alternative:**
```bash
SPRING_DATA_REDIS_SENTINEL_MASTER=mymaster
SPRING_DATA_REDIS_SENTINEL_NODES=sentinel1:26379,sentinel2:26379,sentinel3:26379
SPRING_DATA_REDIS_PASSWORD=yourpassword
```

### Option 3: Redis Cluster (Sharding + HA)

Redis Cluster provides automatic sharding across multiple nodes with built-in replication.

```yaml
spring:
  data:
    redis:
      cluster:
        nodes:
          - redis1.example.com:6379
          - redis2.example.com:6379
          - redis3.example.com:6379
          - redis4.example.com:6379
          - redis5.example.com:6379
          - redis6.example.com:6379
        max-redirects: 3
      password: ${REDIS_PASSWORD:}
```

**Environment variables alternative:**
```bash
SPRING_DATA_REDIS_CLUSTER_NODES=redis1:6379,redis2:6379,redis3:6379,redis4:6379,redis5:6379,redis6:6379
SPRING_DATA_REDIS_CLUSTER_MAX_REDIRECTS=3
SPRING_DATA_REDIS_PASSWORD=yourpassword
```

### Option 4: AWS ElastiCache / Azure Cache for Redis

For managed Redis services, use the same configuration as standalone or cluster mode depending on your setup:

**ElastiCache (Cluster Mode Disabled):**
```yaml
spring:
  data:
    redis:
      host: my-cluster.xxxxx.cache.amazonaws.com
      port: 6379
      ssl:
        enabled: true
```

**ElastiCache (Cluster Mode Enabled):**
```yaml
spring:
  data:
    redis:
      cluster:
        nodes:
          - my-cluster.xxxxx.cache.amazonaws.com:6379
      ssl:
        enabled: true
```

### Connection Pool Configuration

For production, configure connection pooling:

```yaml
spring:
  data:
    redis:
      lettuce:
        pool:
          max-active: 16
          max-idle: 8
          min-idle: 4
          max-wait: 1000ms
```

### Timeout Configuration

```yaml
spring:
  data:
    redis:
      timeout: 2000ms
      connect-timeout: 1000ms
      lettuce:
        shutdown-timeout: 100ms
```

### Recommended Production Configuration

```yaml
spring:
  data:
    redis:
      sentinel:
        master: mymaster
        nodes:
          - sentinel1:26379
          - sentinel2:26379
          - sentinel3:26379
      password: ${REDIS_PASSWORD}
      timeout: 2000ms
      lettuce:
        pool:
          max-active: 16
          max-idle: 8
          min-idle: 4

feature-toggle:
  client:
    redis:
      enabled: true
      direct-mode: true  # Use direct Redis for best performance
    cache:
      ttl-seconds: 30
    defaults:
      WITHDRAW: DISABLED
      DEPOSIT: DISABLED
```

## Error Handling

### FeatureDisabledException

Thrown when `requireEnabled()` is called or `@FeatureEnabled(throwOnDisabled = true)`:

```java
try {
    featureToggle.requireEnabled("WITHDRAW", userId);
} catch (FeatureDisabledException e) {
    log.warn("Feature {} is disabled for user {}: {}", 
             e.getFeatureName(), userId, e.getReason());
    // Handle gracefully
}
```

### Global Exception Handler

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(FeatureDisabledException.class)
    public ResponseEntity<Map<String, Object>> handleFeatureDisabled(FeatureDisabledException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                    "error", "Feature unavailable",
                    "feature", e.getFeatureName(),
                    "message", e.getReason()
                ));
    }
}
```

## Best Practices

### 1. Use Meaningful Feature Names

```java
// Good - clear and descriptive
featureToggle.isEnabled("WITHDRAW_TO_BANK_ACCOUNT", userId);
featureToggle.isEnabled("LIVE_BETTING_FOOTBALL", userId);

// Bad - too generic
featureToggle.isEnabled("FEATURE_1", userId);
```

### 2. Always Provide User ID for User-Specific Features

```java
// For LIST_MODE features, always provide userId
featureToggle.isEnabled("VIP_BONUS", userId);

// For global features, userId is optional
featureToggle.isEnabled("MAINTENANCE_MODE");
```

### 3. Configure Safe Defaults for Critical Features

```yaml
feature-toggle:
  client:
    defaults:
      # Financial operations should default to DISABLED
      WITHDRAW: DISABLED
      DEPOSIT: DISABLED
      
      # UI features can be more permissive
      NEW_DASHBOARD: ENABLED
```

### 4. Use Annotations for Clean Code

```java
// Instead of this:
public void processPayment(String userId) {
    if (!featureToggle.isEnabled("PAYMENTS", userId)) {
        throw new FeatureDisabledException("PAYMENTS");
    }
    // ...
}

// Use this:
@FeatureEnabled(value = "PAYMENTS", userIdParam = "userId")
public void processPayment(String userId) {
    // ...
}
```

### 5. Log Feature Check Results for Debugging

```java
FeatureCheckResult result = featureToggle.check("WITHDRAW", userId);
log.debug("Feature check: {} for user {} = {} (reason: {})", 
          result.getFeatureName(), userId, result.isEnabled(), result.getReason());
```

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     Your Microservice                           │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                  FeatureToggleClient                     │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────────┐  │   │
│  │  │ Local Cache │  │ Redis Sub   │  │ REST Client     │  │   │
│  │  │ (30s TTL)   │  │ (Pub/Sub)   │  │ (HTTP)          │  │   │
│  │  └──────┬──────┘  └──────┬──────┘  └────────┬────────┘  │   │
│  └─────────┼────────────────┼──────────────────┼───────────┘   │
└────────────┼────────────────┼──────────────────┼───────────────┘
             │                │                  │
             │                │                  │
             │         ┌──────▼──────┐           │
             │         │    Redis    │           │
             │         │  (Pub/Sub)  │           │
             │         └──────┬──────┘           │
             │                │                  │
             │         ┌──────▼──────────────────▼───────┐
             │         │   Feature Toggle Service        │
             │         │  ┌─────────┐  ┌─────────────┐   │
             │         │  │ Redis   │  │   SQLite    │   │
             │         │  │ Cache   │  │   Database  │   │
             │         │  └─────────┘  └─────────────┘   │
             │         └─────────────────────────────────┘
             │
    Cache Hit│ (fastest path)
             ▼
      Feature Check Result
```

## Caching Strategy

1. **Local Cache (L1)**: In-memory cache with configurable TTL (default 30s)
2. **Redis Cache (L2)**: Shared cache with pub/sub invalidation
3. **Service Call (L3)**: REST API call to feature toggle service
4. **Safe Default (Fallback)**: Configured default when all else fails

## Troubleshooting

### Feature Always Returns Default

1. Check if the feature toggle service is running
2. Verify the `service-url` configuration
3. Check network connectivity between services
4. Review logs for connection errors

### Cache Not Invalidating

1. Ensure Redis is configured and connected
2. Verify the `redis.channel` matches between service and client
3. Check Redis pub/sub is working

### Performance Issues

1. Increase `cache-ttl-seconds` for less frequent checks
2. Ensure Redis is properly configured for L2 caching
3. Monitor feature toggle service response times
