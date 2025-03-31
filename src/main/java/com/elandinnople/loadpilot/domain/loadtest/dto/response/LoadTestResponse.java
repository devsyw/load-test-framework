package com.elandinnople.loadpilot.domain.loadtest.dto.response;


import com.elandinnople.loadpilot.domain.loadtest.entity.LoadTest;
import com.elandinnople.loadpilot.domain.loadtest.entity.type.TestStatus;
import com.elandinnople.loadpilot.domain.loadtest.entity.type.TestType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoadTestResponse {
    private Long id;
    private String name;
    private String description;
    private String targetUrl;
    private TestType testType;
    private Integer virtualUsers;
    private Integer durationSeconds;
    private Integer rampUpSeconds;
    private TestStatus status;
    private String taskId;
    private Integer containerCount;
    private Integer completedContainerCount;
    private Boolean allContainersCompleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static LoadTestResponse fromEntity(LoadTest loadTest) {
        LoadTestResponse response = new LoadTestResponse();
        response.setId(loadTest.getId());
        response.setName(loadTest.getName());
        response.setDescription(loadTest.getDescription());
        response.setTargetUrl(loadTest.getTargetUrl());
        response.setTestType(loadTest.getTestType());
        response.setVirtualUsers(loadTest.getVirtualUsers());
        response.setDurationSeconds(loadTest.getDurationSeconds());
        response.setRampUpSeconds(loadTest.getRampUpSeconds());
        response.setStatus(loadTest.getStatus());
        response.setTaskId(loadTest.getTaskId());
        response.setContainerCount(loadTest.getContainerCount());
        response.setCompletedContainerCount(loadTest.getCompletedContainerCount());
        response.setAllContainersCompleted(loadTest.isAllContainersCompleted());
        response.setCreatedAt(loadTest.getCreatedAt());
        response.setUpdatedAt(loadTest.getUpdatedAt());
        return response;
    }
}