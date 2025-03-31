package com.elandinnople.loadpilot.domain.loadtest.entity;

import com.elandinnople.loadpilot.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "aggregated_test_results")
@Getter
@Setter
@NoArgsConstructor
public class AggregatedTestResult extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "load_test_id")
    private LoadTest loadTest;

    // 집계된 테스트 결과 필드
    // (기존 TestResult와 유사한 필드들)

    @Column(name = "total_requests")
    private Long totalRequests;

    @Column(name = "successful_requests")
    private Long successfulRequests;

    @Column(name = "failed_requests")
    private Long failedRequests;

    @Column(name = "avg_response_time_ms")
    private Double avgResponseTimeMs;

    @Column(name = "p95_response_time_ms")
    private Double p95ResponseTimeMs;

    @Column(name = "p99_response_time_ms")
    private Double p99ResponseTimeMs;

    @Column(name = "max_response_time_ms")
    private Double maxResponseTimeMs;

    @Column(name = "min_response_time_ms")
    private Double minResponseTimeMs;

    @Column(name = "requests_per_second")
    private Double requestsPerSecond;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "aggregated_json", columnDefinition = "TEXT")
    private String aggregatedJson;
}
