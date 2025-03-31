package com.elandinnople.loadpilot.domain.loadtest.entity;


import com.elandinnople.loadpilot.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "test_results")
@Getter @Setter
@NoArgsConstructor
public class TestResult extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "load_test_id")
    private LoadTest loadTest;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    // JSON 형태로 저장되는 요약 결과
    @Column(name = "summary_json", columnDefinition = "TEXT")
    private String summaryJson;

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

    @Column(name = "result_file_path")
    private String resultFilePath; // S3에 저장된 전체 결과 파일 경로

    @ManyToOne
    @JoinColumn(name = "load_test_id")
    private LoadTest parentTest;

    @Column(name = "container_index")
    private Integer containerIndex; // 0부터 시작하는 컨테이너 인덱스

    @Column(name = "is_aggregated_result")
    private Boolean isAggregatedResult = false; // 집계 결과 여부
}
