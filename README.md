# Feature Toggle Service

[![CI](https://github.com/Raspiska-Ltd/feature-toggle-service/actions/workflows/ci.yml/badge.svg)](https://github.com/Raspiska-Ltd/feature-toggle-service/actions/workflows/ci.yml) [![Java](https://img.shields.io/badge/Java-24%2B-blue.svg)](https://openjdk.org/) [![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-green.svg)](https://spring.io/projects/spring-boot) [![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE) [![Coverage](https://img.shields.io/badge/Coverage-82%25-brightgreen.svg)](#testing)

A centralized feature toggle management system with SQLite persistence and Redis caching for microservices architecture.

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│              Feature Toggle Service                      │
│  ┌─────────────┐  ┌──────────────┐  ┌───────────────┐  │
│  │   SQLite    │  │  REST API    │  │ Cache Pub/Sub │  │
│  │  (persist)  │  │   + HTML UI  │  │  (invalidate) │  │
│  └─────────────┘  └──────────────┘  └───────────────┘  │
└─────────────────────────────────────────────────────────┘
                           │
         ┌─────────────────┼─────────────────┐
         ▼                 ▼                 ▼
   ┌───────────┐    ┌───────────┐    ┌───────────┐
   │ Service A │    │ Service B │    │ Service C │
   │  (client) │    │  (client) │    │  (client) │
   └───────────┘    └───────────┘    └───────────┘
```

## Features

- **SQLite Database**: Lightweight, zero-config persistence
- **Redis Caching**: Sub-millisecond reads with pub/sub cache invalidation
- **Redis HA**: Supports Sentinel and Cluster modes for high availability
- **Direct Redis Mode**: Clients can read directly from Redis (zero HTTP overhead)
- **Local Cache**: In-memory cache with configurable TTL
- **Whitelist/Blacklist**: User-level feature control for millions of users
- **Safe Defaults**: Configurable fallback behavior per feature
- **Spring Boot Client**: Auto-configured client library with annotations
- **HTML Management UI**: Built-in web interface for toggle management
- **Toggle Groups**: Organize toggles by group for visual management
- **Scheduled Toggles**: Schedule automatic status changes at specific times
- **Audit Logging**: Track all changes with optional actor identification
- **Prometheus Metrics**: Monitor toggle checks, cache hits/misses
- **Health Checks**: Redis and database health via Spring Actuator

## Toggle Statuses

| Status | Behavior |
|--------|----------|
| `ENABLED` | Feature enabled for all users |
| `DISABLED` | Feature disabled for all users |
| `LIST_MODE` | Check whitelist/blacklist for user |

## Quick Start

### Prerequisites
- Java 24+
- Redis (optional for local dev, required for production)

### Run Locally

```bash
./gradlew bootRun
```

Access the management UI at: **http://localhost:8090**

### Docker

```bash
./gradlew bootJar
docker build -t feature-toggle-service .
docker run -p 8090:8090 \
  -e REDIS_HOST=redis \
  -e REDIS_ENABLED=true \
  -v ./data:/app/data \
  feature-toggle-service
```

### Docker Compose

```yaml
version: '3.8'
services:
  feature-toggle:
    image: feature-toggle-service:latest
    ports:
      - "8090:8090"
    environment:
      - REDIS_HOST=redis
      - REDIS_ENABLED=true
    volumes:
      - ./data:/app/data
    depends_on:
      - redis

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
```

## Management UI

Access the built-in HTML management interface at the root URL:

```
http://localhost:8090
```

Features:
- View all feature toggles
- Create/Edit/Delete toggles
- Manage whitelist and blacklist users
- Real-time status updates

## API Endpoints

### Toggle Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/toggles` | List all toggles |
| GET | `/api/v1/toggles/{name}` | Get toggle details |
| POST | `/api/v1/toggles` | Create toggle |
| PUT | `/api/v1/toggles/{name}` | Update toggle |
| DELETE | `/api/v1/toggles/{name}` | Delete toggle |

### Feature Check

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/toggles/{name}/check?userId={id}` | Check if feature is enabled |

### Whitelist/Blacklist

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/toggles/{name}/whitelist` | List whitelisted users |
| POST | `/api/v1/toggles/{name}/whitelist` | Add users to whitelist |
| DELETE | `/api/v1/toggles/{name}/whitelist` | Remove users from whitelist |
| GET | `/api/v1/toggles/{name}/blacklist` | List blacklisted users |
| POST | `/api/v1/toggles/{name}/blacklist` | Add users to blacklist |
| DELETE | `/api/v1/toggles/{name}/blacklist` | Remove users from blacklist |

### Scheduled Toggles

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/toggles/{name}/schedule` | Schedule status change |
| DELETE | `/api/v1/toggles/{name}/schedule` | Cancel scheduled change |

### Audit Logs

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/audit?featureName={name}&actor={actor}` | Query audit logs |

### Monitoring

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/actuator/health` | Health check (Redis, DB) |
| GET | `/actuator/prometheus` | Prometheus metrics |
| GET | `/actuator/metrics` | Spring metrics |

## API Examples

### Create a Toggle

```bash
curl -X POST http://localhost:8090/api/v1/toggles \
  -H "Content-Type: application/json" \
  -H "X-Actor: admin@example.com" \
  -d '{
    "featureName": "WITHDRAW",
    "status": "ENABLED",
    "description": "Global withdraw feature",
    "groupName": "payment"
  }'
```

### Disable Withdrawals

```bash
curl -X PUT http://localhost:8090/api/v1/toggles/WITHDRAW \
  -H "Content-Type: application/json" \
  -d '{
    "status": "DISABLED"
  }'
```

### Enable for Specific Users Only

```bash
# Set to list mode
curl -X PUT http://localhost:8090/api/v1/toggles/WITHDRAW \
  -H "Content-Type: application/json" \
  -d '{
    "status": "LIST_MODE"
  }'

# Add users to whitelist
curl -X POST http://localhost:8090/api/v1/toggles/WITHDRAW/whitelist \
  -H "Content-Type: application/json" \
  -d '{
    "userIds": ["user-123", "user-456"]
  }'
```

### Check Feature

```bash
curl "http://localhost:8090/api/v1/toggles/WITHDRAW/check?userId=user-123"
```

### Schedule a Toggle Change

```bash
curl -X POST http://localhost:8090/api/v1/toggles/WITHDRAW/schedule \
  -H "Content-Type: application/json" \
  -H "X-Actor: admin@example.com" \
  -d '{
    "scheduledStatus": "DISABLED",
    "scheduledAt": "2026-02-01T00:00:00Z"
  }'
```

### Filter by Group

```bash
curl "http://localhost:8090/api/v1/toggles?group=payment"
```

### View Audit Logs

```bash
curl "http://localhost:8090/api/v1/audit?featureName=WITHDRAW"
```

---

# Feature Toggle Client

A Spring Boot starter library for consuming the Feature Toggle Service.

## Installation

Add to your `build.gradle`:

```gradle
dependencies {
    implementation 'io.raspiska:feature-toggle-client:1.0.0'
}
```

Or build locally:

```bash
cd feature-toggle-client
./gradlew publishToMavenLocal
```

## Configuration

Add to your `application.yml`:

```yaml
feature-toggle:
  client:
    service-url: http://feature-toggle-service:8090
    cache:
      ttl-seconds: 30
      max-size: 1000
    redis:
      enabled: true
      channel: feature-toggle-updates
    # Safe defaults when service is unavailable
    global-default: DISABLED
    defaults:
      WITHDRAW: DISABLED          # Block withdrawals if unknown
      NEW_UI_FEATURE: ENABLED     # Allow new UI if unknown
```

## Usage

### Programmatic Check

```java
@Service
@RequiredArgsConstructor
public class WithdrawService {

    private final FeatureToggleClient featureToggle;

    public void processWithdraw(String userId, String bankCode) {
        // Simple check
        if (!featureToggle.isEnabled("WITHDRAW", userId)) {
            throw new FeatureDisabledException("WITHDRAW", "Withdrawals are disabled");
        }

        // Or use requireEnabled (throws FeatureDisabledException)
        featureToggle.requireEnabled("WITHDRAW_BANK_" + bankCode, userId);

        // Process withdraw...
    }
}
```

### Annotation-Based Check

```java
@Service
public class WithdrawService {

    @FeatureEnabled(value = "WITHDRAW", userIdParam = "userId")
    public void processWithdraw(String userId, BigDecimal amount) {
        // Only executes if feature is enabled for user
    }

    @FeatureEnabled(value = "NEW_FEATURE", throwOnDisabled = false)
    public String getNewFeatureData() {
        // Returns null if feature is disabled (doesn't throw)
        return "data";
    }
}
```

### Get Detailed Result

```java
FeatureCheckResult result = featureToggle.check("WITHDRAW", userId);

if (result.isEnabled()) {
    // Feature is enabled
} else {
    log.info("Feature disabled: {} - {}", result.getFeatureName(), result.getReason());
}

// Check if result came from cache or default
if (result.isFromDefault()) {
    log.warn("Using default behavior for feature: {}", result.getFeatureName());
}
```

## Cache Behavior

1. **Local Cache** (fastest): In-memory with configurable TTL
2. **Redis**: Shared cache with pub/sub invalidation
3. **Service Call**: HTTP call to feature-toggle-service
4. **Safe Default**: Fallback when service unavailable

When a toggle is updated, the service publishes to Redis pub/sub, and all clients automatically invalidate their local cache.

## Safe Defaults

Configure per-feature defaults for when the service is unavailable:

```yaml
feature-toggle:
  client:
    global-default: DISABLED  # Default for unknown features
    defaults:
      WITHDRAW: DISABLED      # Critical: fail closed
      DEPOSIT: ENABLED        # Non-critical: fail open
      NEW_UI: ENABLED         # Low risk: fail open
```

**Recommendation**: Use `DISABLED` for sensitive operations like withdrawals, payments, etc.

## Direct Redis Mode (Zero HTTP Overhead)

When your microservices share the same Redis instance as the feature toggle service, clients can read directly from Redis - eliminating HTTP calls entirely:

```yaml
feature-toggle:
  client:
    redis:
      enabled: true
      direct-mode: true  # Read directly from Redis!
```

| Mode | Latency | Use Case |
|------|---------|----------|
| HTTP Mode | ~5-10ms | Separate networks |
| Direct Redis | ~0.5ms | Shared Redis instance |

## Redis High Availability

Spring Boot's Redis auto-configuration supports Sentinel and Cluster modes:

### Redis Sentinel

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
```

### Redis Cluster

```yaml
spring:
  data:
    redis:
      cluster:
        nodes:
          - redis1:6379
          - redis2:6379
          - redis3:6379
        max-redirects: 3
      password: ${REDIS_PASSWORD}
```

See [CLIENT_USAGE.md](CLIENT_USAGE.md) for complete Redis HA configuration options.

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `REDIS_HOST` | `localhost` | Redis server host |
| `REDIS_PORT` | `6379` | Redis server port |
| `REDIS_PASSWORD` | `` | Redis password |
| `REDIS_DATABASE` | `0` | Redis database number (0-15) |
| `REDIS_ENABLED` | `true` | Enable/disable Redis |

## Project Structure

```
feature-toggle-service/
├── src/main/java/io/raspiska/featuretoggle/
│   ├── controller/      # REST API endpoints
│   ├── service/         # Business logic & caching
│   ├── repository/      # Data access
│   ├── entity/          # JPA entities
│   ├── dto/             # Data transfer objects
│   └── config/          # Redis & app configuration
├── src/main/resources/
│   ├── static/          # HTML management UI
│   └── application.yml  # Configuration
├── feature-toggle-client/   # Client library
│   └── src/main/java/io/raspiska/featuretoggle/client/
└── data/                # SQLite database (auto-created)
```

## Testing

```bash
# Run all tests with coverage
./gradlew test jacocoTestReport

# View coverage report
open build/reports/jacoco/test/html/index.html
```

## Performance Testing

Run load tests to benchmark the service:

```bash
# Run performance tests
./gradlew performanceTest

# View performance report
cat build/reports/performance/load-test-report.md
```

Performance tests include:
- Feature creation throughput
- Concurrent reads while writing
- Feature check latency (P95, P99)
- Delete operations

## License

MIT
