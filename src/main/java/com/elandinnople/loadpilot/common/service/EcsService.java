package com.elandinnople.loadpilot.common.service;

import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.*;
import com.elandinnople.loadpilot.domain.loadtest.entity.LoadTest;
import com.elandinnople.loadpilot.domain.loadtest.entity.type.TaskStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;

@Service
@Slf4j
public class EcsService {

    private final AmazonECS ecsClient;
    private final String clusterName;
    private final String taskDefinition;
    private final String subnetId;
    private final String securityGroupId;

    public EcsService(
            AmazonECS ecsClient,
            @Value("${aws.ecs.cluster}") String clusterName,
            @Value("${aws.ecs.task-definition}") String taskDefinition,
            @Value("${aws.ecs.subnet-id}") String subnetId,
            @Value("${aws.ecs.security-group-id}") String securityGroupId) {
        this.ecsClient = ecsClient;
        this.clusterName = clusterName;
        this.taskDefinition = taskDefinition;
        this.subnetId = subnetId;
        this.securityGroupId = securityGroupId;
    }

    public String runEcsTask(LoadTest loadTest) {
        try {
            // k6 스크립트 내용 준비
            String scriptContent = loadTest.getScriptContent();

            // 환경 변수 설정
            Collection<KeyValuePair> environmentVariables = new ArrayList<>();
            environmentVariables.add(new KeyValuePair().withName("TARGET_URL").withValue(loadTest.getTargetUrl()));
            environmentVariables.add(new KeyValuePair().withName("VIRTUAL_USERS").withValue(loadTest.getVirtualUsers().toString()));
            environmentVariables.add(new KeyValuePair().withName("DURATION_SECONDS").withValue(loadTest.getDurationSeconds().toString()));

            if (loadTest.getRampUpSeconds() != null) {
                environmentVariables.add(new KeyValuePair().withName("RAMP_UP_SECONDS").withValue(loadTest.getRampUpSeconds().toString()));
            }

            environmentVariables.add(new KeyValuePair().withName("TEST_ID").withValue(loadTest.getId().toString()));
            environmentVariables.add(new KeyValuePair().withName("SCRIPT_CONTENT").withValue(scriptContent));

            // 태스크 실행 설정
            RunTaskRequest runTaskRequest = new RunTaskRequest()
                    .withCluster(clusterName)
                    .withTaskDefinition(taskDefinition)
                    .withLaunchType(LaunchType.FARGATE)
                    .withNetworkConfiguration(new NetworkConfiguration()
                            .withAwsvpcConfiguration(new AwsVpcConfiguration()
                                    .withSubnets(subnetId)
                                    .withSecurityGroups(securityGroupId)
                                    .withAssignPublicIp(AssignPublicIp.ENABLED)))
                    .withOverrides(new TaskOverride()
                            .withContainerOverrides(new ContainerOverride()
                                    .withName("k6-runner")
                                    .withEnvironment(environmentVariables)));

            RunTaskResult runTaskResult = ecsClient.runTask(runTaskRequest);

            if (runTaskResult.getTasks().isEmpty()) {
                throw new RuntimeException("ECS 태스크 실행 실패");
            }

            String taskId = runTaskResult.getTasks().get(0).getTaskArn();
            log.info("ECS 태스크 실행됨: {}", taskId);

            return taskId;
        } catch (Exception e) {
            log.error("ECS 태스크 실행 중 오류: {}", e.getMessage());
            throw new RuntimeException("ECS 태스크 실행 중 오류가 발생했습니다.", e);
        }
    }

    public TaskStatus getTaskStatus(String taskId) {
        try {
            DescribeTasksRequest describeTasksRequest = new DescribeTasksRequest()
                    .withCluster(clusterName)
                    .withTasks(taskId);

            DescribeTasksResult describeTasksResult = ecsClient.describeTasks(describeTasksRequest);

            if (describeTasksResult.getTasks().isEmpty()) {
                return TaskStatus.UNKNOWN;
            }

            Task task = describeTasksResult.getTasks().get(0);
            String lastStatus = task.getLastStatus();

            switch (lastStatus) {
                case "PROVISIONING":
                case "PENDING":
                    return TaskStatus.PENDING;
                case "RUNNING":
                    return TaskStatus.RUNNING;
                case "STOPPED":
                    // Stopped 상태에서 exitCode 확인
                    if (task.getContainers().get(0).getExitCode() != null &&
                            task.getContainers().get(0).getExitCode() == 0) {
                        return TaskStatus.COMPLETED;
                    } else {
                        return TaskStatus.FAILED;
                    }
                default:
                    return TaskStatus.UNKNOWN;
            }
        } catch (Exception e) {
            log.error("태스크 상태 조회 중 오류: {}", e.getMessage());
            return TaskStatus.UNKNOWN;
        }
    }
}
