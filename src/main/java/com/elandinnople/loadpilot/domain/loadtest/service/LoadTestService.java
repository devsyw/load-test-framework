package com.elandinnople.loadpilot.domain.loadtest.service;

import com.amazonaws.services.ecs.model.ResourceNotFoundException;
import com.elandinnople.loadpilot.common.service.EcsService;
import com.elandinnople.loadpilot.common.service.LambdaService;
import com.elandinnople.loadpilot.common.service.S3Service;
import com.elandinnople.loadpilot.domain.loadtest.dto.request.CreateLoadTestRequest;
import com.elandinnople.loadpilot.domain.loadtest.dto.request.LoadTestRequest;
import com.elandinnople.loadpilot.domain.loadtest.dto.request.TestResultProcessRequest;
import com.elandinnople.loadpilot.domain.loadtest.dto.response.LoadTestStatusResponse;
import com.elandinnople.loadpilot.domain.loadtest.dto.response.TestResultResponse;
import com.elandinnople.loadpilot.domain.loadtest.entity.AggregatedTestResult;
import com.elandinnople.loadpilot.domain.loadtest.entity.LoadTest;
import com.elandinnople.loadpilot.domain.loadtest.entity.TestResult;
import com.elandinnople.loadpilot.domain.loadtest.entity.type.TaskStatus;
import com.elandinnople.loadpilot.domain.loadtest.entity.type.TestStatus;
import com.elandinnople.loadpilot.domain.loadtest.repository.AggregatedTestResultRepository;
import com.elandinnople.loadpilot.domain.loadtest.repository.LoadTestRepository;
import com.elandinnople.loadpilot.domain.loadtest.repository.TestResultRepository;
import com.elandinnople.loadpilot.domain.user.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoadTestService {

    private final LoadTestRepository loadTestRepository;
    private final TestResultRepository testResultRepository;
    private final AggregatedTestResultRepository aggregatedTestResultRepository;
    private final LambdaService lambdaService;
    private final EcsService ecsService;
    private final S3Service s3Service;

    @Transactional
    public LoadTest createLoadTest(CreateLoadTestRequest request, User user) {
        LoadTest loadTest = new LoadTest();
        loadTest.setName(request.getName());
        loadTest.setDescription(request.getDescription());
        loadTest.setTargetUrl(request.getTargetUrl());
        loadTest.setTestType(request.getTestType());
        loadTest.setVirtualUsers(request.getVirtualUsers());
        loadTest.setDurationSeconds(request.getDurationSeconds());
        loadTest.setRampUpSeconds(request.getRampUpSeconds());
        loadTest.setScriptContent(request.getScriptContent());
        loadTest.setStatus(TestStatus.PENDING);
        loadTest.setUser(user);

        // 컨테이너 수 설정 (기본값 1, 최대 5)
        Integer containerCount = request.getContainerCount();
        if (containerCount == null || containerCount < 1) {
            containerCount = 1;
        } else if (containerCount > 5) {
            containerCount = 5;
        }
        loadTest.setContainerCount(containerCount);
        loadTest.setCompletedContainerCount(0);

        return loadTestRepository.save(loadTest);
    }

    @Transactional
    public LoadTest startLoadTest(Long id, Long userId) {
        LoadTest loadTest = getLoadTest(id, userId);

        if (loadTest.getStatus() != TestStatus.PENDING) {
            throw new IllegalStateException("Load test is already started or completed");
        }

        try {
            // 여러 컨테이너 시작
            int containerCount = loadTest.getContainerCount();
            List<String> taskIds = new ArrayList<>();

            for (int i = 0; i < containerCount; i++) {
                // 각 컨테이너별로 Lambda 요청 생성
                LoadTestRequest lambdaRequest = new LoadTestRequest();
                lambdaRequest.setTestId(loadTest.getId());
                lambdaRequest.setTargetUrl(loadTest.getTargetUrl());

                // 가상 사용자 수를 컨테이너 수로 분배
                int vusPerContainer = loadTest.getVirtualUsers() / containerCount;
                // 마지막 컨테이너가 나머지 사용자를 처리
                if (i == containerCount - 1) {
                    vusPerContainer += loadTest.getVirtualUsers() % containerCount;
                }
                lambdaRequest.setVirtualUsers(vusPerContainer);

                lambdaRequest.setDurationSeconds(loadTest.getDurationSeconds());
                lambdaRequest.setRampUpSeconds(loadTest.getRampUpSeconds());
                lambdaRequest.setScriptContent(loadTest.getScriptContent());
                lambdaRequest.setContainerIndex(i);
                lambdaRequest.setTotalContainers(containerCount);

                // Lambda 호출하여 ECS 태스크 시작
                String taskId = lambdaService.invokeEcsTask(lambdaRequest);
                taskIds.add(taskId);
            }

            // 태스크 ID 저장 (첫 번째 태스크 ID만 저장하거나, 모든 ID를 JSON으로 저장)
            loadTest.setTaskId(taskIds.get(0));
            // 선택적: 모든 태스크 ID를 JSON으로 저장
            // loadTest.setAllTaskIds(new ObjectMapper().writeValueAsString(taskIds));

            // 상태 업데이트
            loadTest.setStatus(TestStatus.RUNNING);

            return loadTestRepository.save(loadTest);
        } catch (Exception e) {
            log.error("부하 테스트 시작 중 오류: {}", e.getMessage());
            loadTest.setStatus(TestStatus.FAILED);
            return loadTestRepository.save(loadTest);
        }
    }

    @Transactional
    public void processTestResult(Long loadTestId, TestResultProcessRequest resultRequest) {
        LoadTest loadTest = loadTestRepository.findById(loadTestId)
                .orElseThrow(() -> new ResourceNotFoundException("Load test not found"));

        // 컨테이너 인덱스 확인
        Integer containerIndex = resultRequest.getContainerIndex();
        if (containerIndex == null) {
            containerIndex = 0; // 기본값으로 0 설정
        }

        // 이미 해당 컨테이너의 결과가 처리된 경우 중복 처리 방지
        if (testResultRepository.existsByParentTestIdAndContainerIndex(loadTestId, containerIndex)) {
            log.warn("Test result already exists for load test ID {} and container index {}",
                    loadTestId, containerIndex);
            return;
        }

        // S3에 결과 업로드 (컨테이너 인덱스 포함)
        String resultJson = resultRequest.getSummaryJson();
        String resultFileName = String.format("result-%d-container-%d.json", loadTestId, containerIndex);
        String resultUrl = s3Service.uploadTestResult(loadTestId, resultJson, resultFileName);

        // 테스트 결과 저장
        TestResult testResult = new TestResult();
        testResult.setParentTest(loadTest);
        testResult.setContainerIndex(containerIndex);
        testResult.setStartTime(resultRequest.getStartTime());
        testResult.setEndTime(resultRequest.getEndTime());
        testResult.setSummaryJson(resultJson);
        testResult.setTotalRequests(resultRequest.getTotalRequests());
        testResult.setSuccessfulRequests(resultRequest.getSuccessfulRequests());
        testResult.setFailedRequests(resultRequest.getFailedRequests());
        testResult.setAvgResponseTimeMs(resultRequest.getAvgResponseTimeMs());
        testResult.setP95ResponseTimeMs(resultRequest.getP95ResponseTimeMs());
        testResult.setP99ResponseTimeMs(resultRequest.getP99ResponseTimeMs());
        testResult.setMaxResponseTimeMs(resultRequest.getMaxResponseTimeMs());
        testResult.setMinResponseTimeMs(resultRequest.getMinResponseTimeMs());
        testResult.setRequestsPerSecond(resultRequest.getRequestsPerSecond());
        testResult.setResultFilePath(resultUrl);

        testResultRepository.save(testResult);

        // 완료된 컨테이너 수 증가
        loadTest.incrementCompletedContainerCount();

        // 모든 컨테이너가 완료되었는지 확인
        if (loadTest.isAllContainersCompleted()) {
            // 모든 컨테이너 결과 집계
            aggregateTestResults(loadTest);

            // 테스트 상태 업데이트
            loadTest.setStatus(TestStatus.COMPLETED);
        }

        loadTestRepository.save(loadTest);
    }

    // 테스트 결과 집계 메서드
    private void aggregateTestResults(LoadTest loadTest) {
        try {
            List<TestResult> results = testResultRepository.findByParentTestId(loadTest.getId());
            if (results.isEmpty()) {
                log.warn("No test results found for load test ID {}", loadTest.getId());
                return;
            }

            // 집계 시작
            AggregatedTestResult aggregated = new AggregatedTestResult();
            aggregated.setLoadTest(loadTest);

            long totalRequests = 0;
            long successfulRequests = 0;
            long failedRequests = 0;
            double totalResponseTime = 0;
            double maxP95 = 0;
            double maxP99 = 0;
            double maxResponseTime = 0;
            double minResponseTime = Double.MAX_VALUE;
            double totalRps = 0;

            LocalDateTime earliestStart = null;
            LocalDateTime latestEnd = null;

            // 모든 결과 순회하며 집계
            for (TestResult result : results) {
                totalRequests += result.getTotalRequests();
                successfulRequests += result.getSuccessfulRequests();
                failedRequests += result.getFailedRequests();

                // 가중 평균을 위한 계산
                totalResponseTime += result.getAvgResponseTimeMs() * result.getTotalRequests();

                // 최대/최소값 계산
                maxP95 = Math.max(maxP95, result.getP95ResponseTimeMs());
                maxP99 = Math.max(maxP99, result.getP99ResponseTimeMs());
                maxResponseTime = Math.max(maxResponseTime, result.getMaxResponseTimeMs());
                minResponseTime = Math.min(minResponseTime, result.getMinResponseTimeMs());

                // RPS 합산
                totalRps += result.getRequestsPerSecond();

                // 시작 및 종료 시간 계산
                if (earliestStart == null || result.getStartTime().isBefore(earliestStart)) {
                    earliestStart = result.getStartTime();
                }
                if (latestEnd == null || result.getEndTime().isAfter(latestEnd)) {
                    latestEnd = result.getEndTime();
                }
            }

            // 평균 응답 시간 계산 (가중 평균)
            double avgResponseTime = totalRequests > 0 ? totalResponseTime / totalRequests : 0;

            // 집계 결과 설정
            aggregated.setTotalRequests(totalRequests);
            aggregated.setSuccessfulRequests(successfulRequests);
            aggregated.setFailedRequests(failedRequests);
            aggregated.setAvgResponseTimeMs(avgResponseTime);
            aggregated.setP95ResponseTimeMs(maxP95);
            aggregated.setP99ResponseTimeMs(maxP99);
            aggregated.setMaxResponseTimeMs(maxResponseTime);
            aggregated.setMinResponseTimeMs(minResponseTime == Double.MAX_VALUE ? 0 : minResponseTime);
            aggregated.setRequestsPerSecond(totalRps);
            aggregated.setStartTime(earliestStart);
            aggregated.setEndTime(latestEnd);

            // JSON 결과 생성
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> aggregatedJson = new HashMap<>();
            aggregatedJson.put("totalRequests", totalRequests);
            aggregatedJson.put("successfulRequests", successfulRequests);
            aggregatedJson.put("failedRequests", failedRequests);
            aggregatedJson.put("avgResponseTimeMs", avgResponseTime);
            aggregatedJson.put("p95ResponseTimeMs", maxP95);
            aggregatedJson.put("p99ResponseTimeMs", maxP99);
            aggregatedJson.put("maxResponseTimeMs", maxResponseTime);
            aggregatedJson.put("minResponseTimeMs", minResponseTime == Double.MAX_VALUE ? 0 : minResponseTime);
            aggregatedJson.put("requestsPerSecond", totalRps);
            aggregatedJson.put("successRate", totalRequests > 0
                    ? (double) successfulRequests / totalRequests * 100 : 0);
            aggregatedJson.put("startTime", earliestStart);
            aggregatedJson.put("endTime", latestEnd);
            aggregatedJson.put("containerCount", loadTest.getContainerCount());

            aggregated.setAggregatedJson(objectMapper.writeValueAsString(aggregatedJson));

            // DB에 저장
            aggregatedTestResultRepository.save(aggregated);

            log.info("Successfully aggregated test results for load test ID {}", loadTest.getId());
        } catch (Exception e) {
            log.error("Error aggregating test results for load test ID {}: {}",
                    loadTest.getId(), e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public TestResultResponse getAggregatedTestResult(Long loadTestId, Long userId) {
        LoadTest loadTest = getLoadTest(loadTestId, userId);

        // 테스트가 완료되지 않았으면 오류
        if (loadTest.getStatus() != TestStatus.COMPLETED || !loadTest.isAllContainersCompleted()) {
            throw new IllegalStateException("Test results are not ready yet");
        }

        // 집계 결과 조회
        AggregatedTestResult aggregated = aggregatedTestResultRepository.findByLoadTestId(loadTestId)
                .orElseThrow(() -> new ResourceNotFoundException("Aggregated test result not found"));

        // 응답 객체 생성
        TestResultResponse response = new TestResultResponse();
        response.setId(aggregated.getId());
        response.setLoadTestId(loadTestId);
        response.setStartTime(aggregated.getStartTime());
        response.setEndTime(aggregated.getEndTime());
        response.setTotalRequests(aggregated.getTotalRequests());
        response.setSuccessfulRequests(aggregated.getSuccessfulRequests());
        response.setFailedRequests(aggregated.getFailedRequests());
        response.setAvgResponseTimeMs(aggregated.getAvgResponseTimeMs());
        response.setP95ResponseTimeMs(aggregated.getP95ResponseTimeMs());
        response.setP99ResponseTimeMs(aggregated.getP99ResponseTimeMs());
        response.setMaxResponseTimeMs(aggregated.getMaxResponseTimeMs());
        response.setMinResponseTimeMs(aggregated.getMinResponseTimeMs());
        response.setRequestsPerSecond(aggregated.getRequestsPerSecond());

        // 개별 컨테이너 결과 URL 리스트 추가 (선택적)
        List<TestResult> individualResults = testResultRepository.findByParentTestId(loadTestId);
        List<String> resultUrls = individualResults.stream()
                .map(TestResult::getResultFilePath)
                .collect(Collectors.toList());
        response.setContainerResultUrls(resultUrls);

        return response;
    }

    // LoadTestService.java 클래스 내부에 있어야 하는 메서드
    @Transactional(readOnly = true)
    public LoadTest getLoadTest(Long id, Long userId) {
        return loadTestRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Load test not found"));
    }

    // 또는 인증된 사용자가 아닌 경우에도 조회 가능한 버전(관리자용)이 필요하다면:
    @Transactional(readOnly = true)
    public LoadTest getLoadTestById(Long id) {
        return loadTestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Load test not found"));
    }

    @Transactional
    public LoadTestStatusResponse getLoadTestStatus(Long id, Long userId) {
        LoadTest loadTest = getLoadTest(id, userId);

        // 이미 완료 또는 실패 상태라면 추가 조회 필요 없음
        if (loadTest.getStatus() == TestStatus.COMPLETED || loadTest.getStatus() == TestStatus.FAILED) {
            return new LoadTestStatusResponse(
                    loadTest.getId(),
                    loadTest.getStatus(),
                    null,
                    loadTest.getContainerCount(),
                    loadTest.getCompletedContainerCount(),
                    loadTest.isAllContainersCompleted()
            );
        }

        // 실행 중인 경우 ECS 태스크 상태 조회
        if (loadTest.getStatus() == TestStatus.RUNNING && loadTest.getTaskId() != null) {
            TaskStatus taskStatus = ecsService.getTaskStatus(loadTest.getTaskId());

            // 다중 컨테이너의 경우, 첫 번째 컨테이너의 상태만 조회
            // 실제 완료된 컨테이너 수는 DB에 기록된 값 사용

            // 상태 업데이트가 필요한 경우
            if ((taskStatus == TaskStatus.COMPLETED && loadTest.getStatus() != TestStatus.COMPLETED && loadTest.isAllContainersCompleted()) ||
                    (taskStatus == TaskStatus.FAILED && loadTest.getStatus() != TestStatus.FAILED)) {

                loadTest.setStatus(taskStatus == TaskStatus.COMPLETED ?
                        TestStatus.COMPLETED : TestStatus.FAILED);
                loadTestRepository.save(loadTest);
            }

            return new LoadTestStatusResponse(
                    loadTest.getId(),
                    loadTest.getStatus(),
                    taskStatus == TaskStatus.UNKNOWN ? "UNKNOWN" : taskStatus.name(),
                    loadTest.getContainerCount(),
                    loadTest.getCompletedContainerCount(),
                    loadTest.isAllContainersCompleted()
            );
        }

        return new LoadTestStatusResponse(
                loadTest.getId(),
                loadTest.getStatus(),
                null,
                loadTest.getContainerCount(),
                loadTest.getCompletedContainerCount(),
                loadTest.isAllContainersCompleted()
        );
    }


    /**
     * 로드 테스트의 결과를 조회합니다.
     * 다중 컨테이너 환경에서는 집계된 결과를 반환합니다.
     */
    @Transactional(readOnly = true)
    public TestResultResponse getTestResult(Long loadTestId, Long userId) {
        LoadTest loadTest = getLoadTest(loadTestId, userId);

        // 테스트가 완료되지 않은 경우
        if (loadTest.getStatus() != TestStatus.COMPLETED) {
            throw new IllegalStateException("Test is not completed yet");
        }

        // 모든 컨테이너가 완료되지 않은 경우
        if (!loadTest.isAllContainersCompleted()) {
            throw new IllegalStateException("Not all containers have completed yet");
        }

        // 집계된 결과가 있는지 확인
        Optional<AggregatedTestResult> aggregatedResult =
                aggregatedTestResultRepository.findByLoadTestId(loadTestId);

        if (aggregatedResult.isPresent()) {
            // 집계된 결과가 있으면 그것을 반환
            AggregatedTestResult result = aggregatedResult.get();

            TestResultResponse response = new TestResultResponse();
            response.setId(result.getId());
            response.setLoadTestId(loadTestId);
            response.setStartTime(result.getStartTime());
            response.setEndTime(result.getEndTime());
            response.setTotalRequests(result.getTotalRequests());
            response.setSuccessfulRequests(result.getSuccessfulRequests());
            response.setFailedRequests(result.getFailedRequests());
            response.setAvgResponseTimeMs(result.getAvgResponseTimeMs());
            response.setP95ResponseTimeMs(result.getP95ResponseTimeMs());
            response.setP99ResponseTimeMs(result.getP99ResponseTimeMs());
            response.setMaxResponseTimeMs(result.getMaxResponseTimeMs());
            response.setMinResponseTimeMs(result.getMinResponseTimeMs());
            response.setRequestsPerSecond(result.getRequestsPerSecond());

            // 개별 컨테이너 결과 URL 추가
            List<TestResult> individualResults = testResultRepository.findByParentTestId(loadTestId);
            List<String> resultUrls = individualResults.stream()
                    .map(TestResult::getResultFilePath)
                    .collect(Collectors.toList());
            response.setContainerResultUrls(resultUrls);

            return response;
        } else {
            // 집계된 결과가 없는 경우 개별 결과들을 조회
            List<TestResult> results = testResultRepository.findByParentTestId(loadTestId);

            if (results.isEmpty()) {
                throw new ResourceNotFoundException("Test results not found");
            }

            // 첫 번째 결과를 기본으로 사용
            TestResult firstResult = results.get(0);

            TestResultResponse response = new TestResultResponse();
            response.setId(firstResult.getId());
            response.setLoadTestId(loadTestId);
            response.setStartTime(firstResult.getStartTime());
            response.setEndTime(firstResult.getEndTime());

            // 전체 결과 집계 (간단한 합산)
            long totalRequests = 0;
            long successfulRequests = 0;
            long failedRequests = 0;
            double totalResponseTime = 0;
            double maxP95 = 0;
            double maxP99 = 0;
            double maxResponse = 0;
            double minResponse = Double.MAX_VALUE;
            double totalRps = 0;

            for (TestResult result : results) {
                totalRequests += result.getTotalRequests();
                successfulRequests += result.getSuccessfulRequests();
                failedRequests += result.getFailedRequests();
                totalResponseTime += result.getAvgResponseTimeMs() * result.getTotalRequests();
                maxP95 = Math.max(maxP95, result.getP95ResponseTimeMs());
                maxP99 = Math.max(maxP99, result.getP99ResponseTimeMs());
                maxResponse = Math.max(maxResponse, result.getMaxResponseTimeMs());
                minResponse = Math.min(minResponse, result.getMinResponseTimeMs());
                totalRps += result.getRequestsPerSecond();
            }

            // 평균 응답 시간 계산
            double avgResponseTime = totalRequests > 0 ? totalResponseTime / totalRequests : 0;

            response.setTotalRequests(totalRequests);
            response.setSuccessfulRequests(successfulRequests);
            response.setFailedRequests(failedRequests);
            response.setAvgResponseTimeMs(avgResponseTime);
            response.setP95ResponseTimeMs(maxP95);
            response.setP99ResponseTimeMs(maxP99);
            response.setMaxResponseTimeMs(maxResponse);
            response.setMinResponseTimeMs(minResponse == Double.MAX_VALUE ? 0 : minResponse);
            response.setRequestsPerSecond(totalRps);

            // 개별 컨테이너 결과 URL 추가
            List<String> resultUrls = results.stream()
                    .map(TestResult::getResultFilePath)
                    .collect(Collectors.toList());
            response.setContainerResultUrls(resultUrls);

            return response;
        }
    }

    /**
     * 로드 테스트를 삭제합니다.
     * 다중 컨테이너 환경에서는 관련된 모든 테스트 결과도 함께 삭제합니다.
     */
    @Transactional
    public void deleteLoadTest(Long id, Long userId) {
        LoadTest loadTest = getLoadTest(id, userId);

        // 실행 중인 테스트는 삭제 불가
        if (loadTest.getStatus() == TestStatus.RUNNING) {
            throw new IllegalStateException("Cannot delete running load test");
        }

        // 집계된 결과가 있으면 삭제
        Optional<AggregatedTestResult> aggregatedResult =
                aggregatedTestResultRepository.findByLoadTestId(id);
        aggregatedResult.ifPresent(aggregatedTestResultRepository::delete);

        // 개별 테스트 결과들 삭제 (cascading으로 처리될 수도 있음)
        List<TestResult> results = testResultRepository.findByParentTestId(id);
        if (!results.isEmpty()) {
            testResultRepository.deleteAll(results);
        }

        // S3에서 결과 파일 삭제 (선택적)
        try {
            s3Service.deleteTestResults(id);
        } catch (Exception e) {
            log.warn("Failed to delete test results from S3: {}", e.getMessage());
        }

        // 로드 테스트 삭제
        loadTestRepository.delete(loadTest);
    }

    /**
     * 사용자의 로드 테스트 목록을 페이지 단위로 조회합니다.
     *
     * @param userId 사용자 ID
     * @param status 필터링할 테스트 상태 (null인 경우 모든 상태)
     * @param page 페이지 번호 (0부터 시작)
     * @param size 페이지 크기
     * @return 로드 테스트 목록 페이지
     */
    @Transactional(readOnly = true)
    public Page<LoadTest> getLoadTests(Long userId, TestStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        if (status != null) {
            return loadTestRepository.findByUserIdAndStatus(userId, status, pageable);
        } else {
            return loadTestRepository.findByUserId(userId, pageable);
        }
    }

    /**
     * 상태별 테스트 수를 집계합니다.
     *
     * @param userId 사용자 ID
     * @return 상태별 테스트 수 맵
     */
    @Transactional(readOnly = true)
    public Map<TestStatus, Long> getTestCountsByStatus(Long userId) {
        List<Object[]> counts = loadTestRepository.countByUserIdGroupByStatus(userId);

        Map<TestStatus, Long> result = new EnumMap<>(TestStatus.class);
        // 모든 상태에 대해 기본값 0 설정
        Arrays.stream(TestStatus.values()).forEach(status -> result.put(status, 0L));

        // 조회 결과로 업데이트
        for (Object[] count : counts) {
            TestStatus status = (TestStatus) count[0];
            Long countValue = (Long) count[1];
            result.put(status, countValue);
        }

        return result;
    }
}