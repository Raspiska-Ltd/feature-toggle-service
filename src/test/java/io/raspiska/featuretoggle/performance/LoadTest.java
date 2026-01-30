package io.raspiska.featuretoggle.performance;

import io.raspiska.featuretoggle.dto.CreateFeatureToggleRequest;
import io.raspiska.featuretoggle.dto.FeatureCheckResponse;
import io.raspiska.featuretoggle.dto.FeatureToggleDto;
import io.raspiska.featuretoggle.entity.ToggleStatus;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Tag("performance")
class LoadTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl;

    private static final int FEATURE_COUNT = 100;
    private static final int CONCURRENT_READERS = 5;
    private static final int READS_PER_READER = 50;

    private static final List<PerformanceResult> results = new ArrayList<>();

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port + "/api/v1/toggles";
    }

    @Test
    @Order(1)
    @DisplayName("Load Test: Create features sequentially")
    void loadTest_createFeatures() {
        Instant start = Instant.now();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<Long> latencies = new CopyOnWriteArrayList<>();

        for (int i = 0; i < FEATURE_COUNT; i++) {
            long opStart = System.nanoTime();
            try {
                CreateFeatureToggleRequest request = new CreateFeatureToggleRequest();
                request.setFeatureName("LOAD_TEST_FEATURE_" + i);
                request.setStatus(ToggleStatus.ENABLED);
                request.setDescription("Load test feature " + i);

                ResponseEntity<FeatureToggleDto> response = restTemplate.postForEntity(
                        baseUrl, request, FeatureToggleDto.class);

                if (response.getStatusCode() == HttpStatus.CREATED) {
                    successCount.incrementAndGet();
                } else {
                    failCount.incrementAndGet();
                }
            } catch (Exception e) {
                failCount.incrementAndGet();
            } finally {
                latencies.add(System.nanoTime() - opStart);
            }
        }

        Duration duration = Duration.between(start, Instant.now());
        LongSummaryStatistics stats = latencies.stream().mapToLong(Long::longValue).summaryStatistics();

        PerformanceResult result = new PerformanceResult(
                "CREATE_FEATURES",
                FEATURE_COUNT,
                successCount.get(),
                failCount.get(),
                duration.toMillis(),
                stats.getAverage() / 1_000_000,
                stats.getMin() / 1_000_000,
                stats.getMax() / 1_000_000,
                calculateP95(latencies) / 1_000_000,
                calculateP99(latencies) / 1_000_000,
                duration.toMillis() > 0 ? (double) successCount.get() / (duration.toMillis() / 1000.0) : 0
        );
        results.add(result);

        System.out.println("\n=== CREATE FEATURES ===");
        System.out.println(result);

        assertThat(successCount.get()).isEqualTo(FEATURE_COUNT);
    }

    @Test
    @Order(2)
    @DisplayName("Load Test: Concurrent reads while creating/deleting")
    void loadTest_concurrentReadsWhileWriting() throws InterruptedException {
        Instant start = Instant.now();
        AtomicInteger readSuccess = new AtomicInteger(0);
        AtomicInteger readFail = new AtomicInteger(0);
        AtomicInteger writeSuccess = new AtomicInteger(0);
        AtomicInteger writeFail = new AtomicInteger(0);
        List<Long> readLatencies = new CopyOnWriteArrayList<>();
        List<Long> writeLatencies = new CopyOnWriteArrayList<>();

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_READERS + 10);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(CONCURRENT_READERS + 1);

        for (int r = 0; r < CONCURRENT_READERS; r++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < READS_PER_READER; i++) {
                        long opStart = System.nanoTime();
                        try {
                            int featureIndex = ThreadLocalRandom.current().nextInt(FEATURE_COUNT);
                            ResponseEntity<FeatureCheckResponse> response = restTemplate.getForEntity(
                                    baseUrl + "/LOAD_TEST_FEATURE_" + featureIndex + "/check?userId=user1",
                                    FeatureCheckResponse.class);

                            if (response.getStatusCode() == HttpStatus.OK) {
                                readSuccess.incrementAndGet();
                            } else {
                                readFail.incrementAndGet();
                            }
                        } catch (Exception e) {
                            readFail.incrementAndGet();
                        } finally {
                            readLatencies.add(System.nanoTime() - opStart);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        executor.submit(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < 100; i++) {
                    long opStart = System.nanoTime();
                    try {
                        CreateFeatureToggleRequest request = new CreateFeatureToggleRequest();
                        request.setFeatureName("CONCURRENT_FEATURE_" + i);
                        request.setStatus(ToggleStatus.ENABLED);

                        ResponseEntity<FeatureToggleDto> createResponse = restTemplate.postForEntity(
                                baseUrl, request, FeatureToggleDto.class);

                        if (createResponse.getStatusCode() == HttpStatus.CREATED) {
                            restTemplate.delete(baseUrl + "/CONCURRENT_FEATURE_" + i);
                            writeSuccess.incrementAndGet();
                        } else {
                            writeFail.incrementAndGet();
                        }
                    } catch (Exception e) {
                        writeFail.incrementAndGet();
                    } finally {
                        writeLatencies.add(System.nanoTime() - opStart);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        doneLatch.await(120, TimeUnit.SECONDS);
        executor.shutdown();

        Duration duration = Duration.between(start, Instant.now());
        LongSummaryStatistics readStats = readLatencies.stream().mapToLong(Long::longValue).summaryStatistics();

        PerformanceResult result = new PerformanceResult(
                "CONCURRENT_READS_WRITES",
                CONCURRENT_READERS * READS_PER_READER,
                readSuccess.get(),
                readFail.get(),
                duration.toMillis(),
                readStats.getAverage() / 1_000_000,
                readStats.getMin() / 1_000_000,
                readStats.getMax() / 1_000_000,
                calculateP95(readLatencies) / 1_000_000,
                calculateP99(readLatencies) / 1_000_000,
                duration.toMillis() > 0 ? (double) readSuccess.get() / (duration.toMillis() / 1000.0) : 0
        );
        results.add(result);

        System.out.println("\n=== CONCURRENT READS WHILE WRITING ===");
        System.out.println(result);
        System.out.println("Write operations: " + writeSuccess.get() + " success, " + writeFail.get() + " failed");

        assertThat(readSuccess.get()).isGreaterThan((int)((CONCURRENT_READERS * READS_PER_READER) * 0.50));
    }

    @Test
    @Order(3)
    @DisplayName("Load Test: Feature check performance")
    void loadTest_featureCheckPerformance() {
        Instant start = Instant.now();
        int totalChecks = 1000;
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<Long> latencies = new CopyOnWriteArrayList<>();

        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < totalChecks; i++) {
            final int index = i;
            futures.add(executor.submit(() -> {
                long opStart = System.nanoTime();
                try {
                    int featureIndex = index % FEATURE_COUNT;
                    String userId = "user_" + (index % 100);
                    ResponseEntity<FeatureCheckResponse> response = restTemplate.getForEntity(
                            baseUrl + "/LOAD_TEST_FEATURE_" + featureIndex + "/check?userId=" + userId,
                            FeatureCheckResponse.class);

                    if (response.getStatusCode() == HttpStatus.OK) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latencies.add(System.nanoTime() - opStart);
                }
            }));
        }

        futures.forEach(f -> {
            try {
                f.get(120, TimeUnit.SECONDS);
            } catch (Exception e) {
                failCount.incrementAndGet();
            }
        });
        executor.shutdown();

        Duration duration = Duration.between(start, Instant.now());
        LongSummaryStatistics stats = latencies.stream().mapToLong(Long::longValue).summaryStatistics();

        PerformanceResult result = new PerformanceResult(
                "FEATURE_CHECK_10K",
                totalChecks,
                successCount.get(),
                failCount.get(),
                duration.toMillis(),
                stats.getAverage() / 1_000_000,
                stats.getMin() / 1_000_000,
                stats.getMax() / 1_000_000,
                calculateP95(latencies) / 1_000_000,
                calculateP99(latencies) / 1_000_000,
                duration.toMillis() > 0 ? (double) successCount.get() / (duration.toMillis() / 1000.0) : 0
        );
        results.add(result);

        System.out.println("\n=== FEATURE CHECK 10K ===");
        System.out.println(result);

        assertThat(successCount.get()).isGreaterThan((int)(totalChecks * 0.95));
    }

    @Test
    @Order(4)
    @DisplayName("Load Test: Delete 1000 features")
    void loadTest_delete1000Features() {
        Instant start = Instant.now();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<Long> latencies = new CopyOnWriteArrayList<>();

        ExecutorService executor = Executors.newFixedThreadPool(5);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < FEATURE_COUNT; i++) {
            final int index = i;
            futures.add(executor.submit(() -> {
                long opStart = System.nanoTime();
                try {
                    restTemplate.delete(baseUrl + "/LOAD_TEST_FEATURE_" + index);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latencies.add(System.nanoTime() - opStart);
                }
            }));
        }

        futures.forEach(f -> {
            try {
                f.get(60, TimeUnit.SECONDS);
            } catch (Exception e) {
                failCount.incrementAndGet();
            }
        });
        executor.shutdown();

        Duration duration = Duration.between(start, Instant.now());
        LongSummaryStatistics stats = latencies.stream().mapToLong(Long::longValue).summaryStatistics();

        PerformanceResult result = new PerformanceResult(
                "DELETE_1000_FEATURES",
                FEATURE_COUNT,
                successCount.get(),
                failCount.get(),
                duration.toMillis(),
                stats.getAverage() / 1_000_000,
                stats.getMin() / 1_000_000,
                stats.getMax() / 1_000_000,
                calculateP95(latencies) / 1_000_000,
                calculateP99(latencies) / 1_000_000,
                duration.toMillis() > 0 ? (double) successCount.get() / (duration.toMillis() / 1000.0) : 0
        );
        results.add(result);

        System.out.println("\n=== DELETE 1000 FEATURES ===");
        System.out.println(result);

        assertThat(successCount.get()).isGreaterThan((int)(FEATURE_COUNT * 0.95));
    }

    @Test
    @Order(5)
    @DisplayName("Generate Performance Report")
    void generatePerformanceReport() throws IOException {
        String reportPath = "build/reports/performance/load-test-report.md";
        new java.io.File("build/reports/performance").mkdirs();

        try (PrintWriter writer = new PrintWriter(new FileWriter(reportPath))) {
            writer.println("# Feature Toggle Service - Load Test Report");
            writer.println();
            writer.println("Generated: " + Instant.now());
            writer.println();
            writer.println("## Test Configuration");
            writer.println();
            writer.println("| Parameter | Value |");
            writer.println("|-----------|-------|");
            writer.println("| Feature Count | " + FEATURE_COUNT + " |");
            writer.println("| Concurrent Readers | " + CONCURRENT_READERS + " |");
            writer.println("| Reads per Reader | " + READS_PER_READER + " |");
            writer.println();
            writer.println("## Results Summary");
            writer.println();
            writer.println("| Test | Total Ops | Success | Failed | Duration (ms) | Avg (ms) | P95 (ms) | P99 (ms) | Throughput (ops/s) |");
            writer.println("|------|-----------|---------|--------|---------------|----------|----------|----------|-------------------|");

            for (PerformanceResult result : results) {
                writer.printf("| %s | %d | %d | %d | %d | %.2f | %.2f | %.2f | %.2f |%n",
                        result.testName,
                        result.totalOperations,
                        result.successCount,
                        result.failCount,
                        result.durationMs,
                        result.avgLatencyMs,
                        result.p95LatencyMs,
                        result.p99LatencyMs,
                        result.throughput);
            }

            writer.println();
            writer.println("## Detailed Results");
            writer.println();

            for (PerformanceResult result : results) {
                writer.println("### " + result.testName);
                writer.println();
                writer.println("- **Total Operations**: " + result.totalOperations);
                writer.println("- **Success Rate**: " + String.format("%.2f%%", (double) result.successCount / result.totalOperations * 100));
                writer.println("- **Duration**: " + result.durationMs + " ms");
                writer.println("- **Throughput**: " + String.format("%.2f", result.throughput) + " ops/s");
                writer.println("- **Latency**:");
                writer.println("  - Average: " + String.format("%.2f", result.avgLatencyMs) + " ms");
                writer.println("  - Min: " + String.format("%.2f", result.minLatencyMs) + " ms");
                writer.println("  - Max: " + String.format("%.2f", result.maxLatencyMs) + " ms");
                writer.println("  - P95: " + String.format("%.2f", result.p95LatencyMs) + " ms");
                writer.println("  - P99: " + String.format("%.2f", result.p99LatencyMs) + " ms");
                writer.println();
            }

            writer.println("## Recommendations");
            writer.println();
            writer.println("Based on the test results:");
            writer.println();

            double avgThroughput = results.stream().mapToDouble(r -> r.throughput).average().orElse(0);
            if (avgThroughput > 500) {
                writer.println("- Performance is **excellent** with throughput > 500 ops/s");
            } else if (avgThroughput > 100) {
                writer.println("- Performance is **good** with throughput > 100 ops/s");
            } else {
                writer.println("- Performance may need optimization, throughput < 100 ops/s");
            }

            double avgP99 = results.stream().mapToDouble(r -> r.p99LatencyMs).average().orElse(0);
            if (avgP99 < 100) {
                writer.println("- P99 latency is **excellent** at < 100ms");
            } else if (avgP99 < 500) {
                writer.println("- P99 latency is **acceptable** at < 500ms");
            } else {
                writer.println("- P99 latency may need attention at > 500ms");
            }
        }

        System.out.println("\n=== PERFORMANCE REPORT GENERATED ===");
        System.out.println("Report saved to: " + reportPath);
    }

    private double calculateP95(List<Long> latencies) {
        if (latencies.isEmpty()) return 0;
        List<Long> sorted = latencies.stream().sorted().toList();
        int index = (int) Math.ceil(0.95 * sorted.size()) - 1;
        return sorted.get(Math.max(0, index));
    }

    private double calculateP99(List<Long> latencies) {
        if (latencies.isEmpty()) return 0;
        List<Long> sorted = latencies.stream().sorted().toList();
        int index = (int) Math.ceil(0.99 * sorted.size()) - 1;
        return sorted.get(Math.max(0, index));
    }

    record PerformanceResult(
            String testName,
            int totalOperations,
            int successCount,
            int failCount,
            long durationMs,
            double avgLatencyMs,
            double minLatencyMs,
            double maxLatencyMs,
            double p95LatencyMs,
            double p99LatencyMs,
            double throughput
    ) {
        @Override
        public String toString() {
            return String.format("""
                    Test: %s
                    Total Operations: %d
                    Success: %d (%.2f%%)
                    Failed: %d
                    Duration: %d ms
                    Throughput: %.2f ops/s
                    Latency - Avg: %.2f ms, Min: %.2f ms, Max: %.2f ms, P95: %.2f ms, P99: %.2f ms
                    """,
                    testName, totalOperations, successCount,
                    (double) successCount / totalOperations * 100,
                    failCount, durationMs, throughput,
                    avgLatencyMs, minLatencyMs, maxLatencyMs, p95LatencyMs, p99LatencyMs);
        }
    }
}
