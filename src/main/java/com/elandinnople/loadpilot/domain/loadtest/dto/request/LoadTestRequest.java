package com.elandinnople.loadpilot.domain.loadtest.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoadTestRequest {
    private Long testId;
    private String targetUrl;
    private Integer virtualUsers;
    private Integer durationSeconds;
    private Integer rampUpSeconds;
    private String scriptContent;
    private Integer containerIndex; //  컨테이너 인덱스
    private Integer totalContainers; // 전체 컨테이너 수
}