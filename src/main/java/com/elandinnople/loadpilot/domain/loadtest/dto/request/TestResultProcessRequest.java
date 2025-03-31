package com.elandinnople.loadpilot.domain.loadtest.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestResultProcessRequest {
    @NotNull
    private Long loadTestId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String summaryJson;
    private Long totalRequests;
    private Long successfulRequests;
    private Long failedRequests;
    private Double avgResponseTimeMs;
    private Double p95ResponseTimeMs;
    private Double p99ResponseTimeMs;
    private Double maxResponseTimeMs;
    private Double minResponseTimeMs;
    private Double requestsPerSecond;
    private Integer containerIndex; // 컨테이너 인덱스
}

