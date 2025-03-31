package com.elandinnople.loadpilot.domain.loadtest.dto.response;

import com.elandinnople.loadpilot.domain.loadtest.entity.type.TestStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoadTestStatusResponse {
    private Long id;
    private TestStatus status;
    private String taskStatus;
    private Integer containerCount;
    private Integer completedContainerCount;
    private Boolean allContainersCompleted;

    // 기존 생성자 오버로드 (하위 호환성 유지)
    public LoadTestStatusResponse(Long id, TestStatus status, String taskStatus) {
        this.id = id;
        this.status = status;
        this.taskStatus = taskStatus;
        this.containerCount = 1;
        this.completedContainerCount = 0;
        this.allContainersCompleted = false;
    }
}