package com.elandinnople.loadpilot.domain.loadtest.dto.response;


import com.elandinnople.loadpilot.domain.loadtest.entity.TestResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestResultResponse {
    private Long id;
    private Long loadTestId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long totalRequests;
    private Long successfulRequests;
    private Long failedRequests;
    private Double avgResponseTimeMs;
    private Double p95ResponseTimeMs;
    private Double p99ResponseTimeMs;
    private Double maxResponseTimeMs;
    private Double minResponseTimeMs;
    private Double requestsPerSecond;
    private String resultUrl;
    private List<String> containerResultUrls; // 컨테이너별 결과 URL 리스트

    public static TestResultResponse fromEntity(TestResult testResult) {
        TestResultResponse response = new TestResultResponse();
        response.setId(testResult.getId());
        response.setLoadTestId(testResult.getLoadTest().getId());
        response.setStartTime(testResult.getStartTime());
        response.setEndTime(testResult.getEndTime());
        response.setTotalRequests(testResult.getTotalRequests());
        response.setSuccessfulRequests(testResult.getSuccessfulRequests());
        response.setFailedRequests(testResult.getFailedRequests());
        response.setAvgResponseTimeMs(testResult.getAvgResponseTimeMs());
        response.setP95ResponseTimeMs(testResult.getP95ResponseTimeMs());
        response.setP99ResponseTimeMs(testResult.getP99ResponseTimeMs());
        response.setMaxResponseTimeMs(testResult.getMaxResponseTimeMs());
        response.setMinResponseTimeMs(testResult.getMinResponseTimeMs());
        response.setRequestsPerSecond(testResult.getRequestsPerSecond());
        response.setResultUrl(testResult.getResultFilePath());
//        response.setContainerResultUrls(testResult.get());
        return response;
    }
}