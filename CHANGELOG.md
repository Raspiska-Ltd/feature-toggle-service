# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.1.0] - 2026-01-30

### Added

- **Toggle Groups**: Organize feature toggles by group for visual management
  - New `groupName` field in FeatureToggle entity (default: "default")
  - Filter toggles by group: `GET /api/v1/toggles?group=payment`
- **Scheduled Toggles**: Schedule automatic status changes at specific times
  - `POST /api/v1/toggles/{name}/schedule` - Schedule a status change
  - `DELETE /api/v1/toggles/{name}/schedule` - Cancel scheduled change
  - Background job processes scheduled changes every 60 seconds
- **Audit Logging**: Track all changes with optional actor identification
  - `X-Actor` header for identifying who made changes
  - `GET /api/v1/audit` - Query audit logs with filters
  - Async logging for minimal performance impact
- **Prometheus Metrics**: Monitor service performance
  - `feature_toggle_checks` - Counter by feature and result
  - `feature_toggle_cache_hits` / `feature_toggle_cache_misses`
  - `feature_toggle_check_duration` - Latency histogram
  - Endpoint: `/actuator/prometheus`
- **Health Checks**: Redis and database health monitoring
  - `/actuator/health` with detailed component status
  - Kubernetes readiness/liveness probes enabled
- **Performance Tests**: Load testing with report generation
  - Run with `./gradlew performanceTest`
  - Report generated at `build/reports/performance/load-test-report.md`

### Changed

- **API Request/Response**: New fields added to DTOs
  - `CreateFeatureToggleRequest` now accepts optional `groupName`
  - `UpdateFeatureToggleRequest` now accepts optional `groupName`
  - `FeatureToggleDto` now includes `groupName`, `scheduledStatus`, `scheduledAt`
- All mutating endpoints now accept optional `X-Actor` header for audit tracking

### Breaking Changes

- **Database Schema**: New columns added to `feature_toggles` table
  - `group_name` (VARCHAR, nullable, default: "default")
  - `scheduled_status` (VARCHAR, nullable)
  - `scheduled_at` (TIMESTAMP, nullable)
  - New table: `audit_logs`
  - **Migration**: JPA auto-updates schema, but manual migration may be needed for production
- **API Response Changes**: `FeatureToggleDto` now includes additional fields
  - Clients parsing JSON strictly may need updates to handle new fields
  - Fields are nullable, so existing integrations should work if using lenient parsing

### Indexes Added

- `idx_feature_name` on `feature_toggles.feature_name`
- `idx_group_name` on `feature_toggles.group_name`
- `idx_status` on `feature_toggles.status`
- `idx_scheduled_at` on `feature_toggles.scheduled_at`
- `idx_audit_feature_name`, `idx_audit_actor`, `idx_audit_action`, `idx_audit_timestamp` on `audit_logs`

## [1.0.0] - 2026-01-29

### Added

- Initial release
- Feature toggle management with ENABLED, DISABLED, LIST_MODE statuses
- SQLite persistence with Redis caching
- Whitelist/Blacklist user management
- Redis pub/sub for cache invalidation
- Spring Boot client library with auto-configuration
- Direct Redis mode for sub-millisecond reads
- HTML management UI
- Redis Sentinel and Cluster support
- GitHub Actions CI/CD workflows
- Docker support

[Unreleased]: https://github.com/Raspiska-Ltd/feature-toggle-service/compare/v1.1.0...HEAD
[1.1.0]: https://github.com/Raspiska-Ltd/feature-toggle-service/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/Raspiska-Ltd/feature-toggle-service/releases/tag/v1.0.0
