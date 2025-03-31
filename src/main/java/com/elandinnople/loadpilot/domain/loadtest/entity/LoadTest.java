package com.elandinnople.loadpilot.domain.loadtest.entity;

import com.elandinnople.loadpilot.common.entity.BaseEntity;
import com.elandinnople.loadpilot.domain.loadtest.entity.type.TestStatus;
import com.elandinnople.loadpilot.domain.loadtest.entity.type.TestType;
import com.elandinnople.loadpilot.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "load_tests")
@Getter @Setter
@NoArgsConstructor
public class LoadTest extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(name = "target_url", nullable = false)
    private String targetUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "test_type", nullable = false)
    private TestType testType; // SMOKE, LOAD, STRESS, SOAK

    @Column(name = "virtual_users", nullable = false)
    private Integer virtualUsers;

    @Column(name = "duration_seconds", nullable = false)
    private Integer durationSeconds;

    @Column(name = "ramp_up_seconds")
    private Integer rampUpSeconds;

    @Column(name = "script_content", length = 10000)
    private String scriptContent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TestStatus status; // PENDING, RUNNING, COMPLETED, FAILED

    @Column(name = "task_id")
    private String taskId; // ECS 태스크 ID

    @Column(name = "log_url")
    private String logUrl; // CloudWatch 로그 접근 URL

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToOne(mappedBy = "loadTest", cascade = CascadeType.ALL)
    private TestResult testResult;
    @Column(name = "container_count", nullable = false)
    private Integer containerCount = 1; // 기본값 1, 최대 5

    @Column(name = "completed_container_count", nullable = false)
    private Integer completedContainerCount = 0; // 완료된 컨테이너 수

    @OneToMany(mappedBy = "parentTest", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TestResult> testResults = new ArrayList<>();

    // 결과 확인 메서드
    public boolean isAllContainersCompleted() {
        return completedContainerCount >= containerCount;
    }

    // 완료된 컨테이너 수 증가 메서드
    public void incrementCompletedContainerCount() {
        this.completedContainerCount++;
    }

}