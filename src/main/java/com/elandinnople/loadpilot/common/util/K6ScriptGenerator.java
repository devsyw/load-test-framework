package com.elandinnople.loadpilot.common.util;

import com.elandinnople.loadpilot.domain.loadtest.entity.LoadTest;
import com.elandinnople.loadpilot.domain.loadtest.entity.type.TestType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class K6ScriptGenerator {

    /**
     * 테스트 유형에 따라 기본 k6 스크립트를 생성합니다.
     *
     * @param targetUrl 대상 URL
     * @param testType 테스트 유형 (SMOKE, LOAD, STRESS, SOAK)
     * @param virtualUsers 가상 사용자 수
     * @param durationSeconds 테스트 기간(초)
     * @param rampUpSeconds 점진적 증가 시간(초)
     * @return 생성된 k6 스크립트
     */
    public String generateScript(String targetUrl, TestType testType,
                                 int virtualUsers, int durationSeconds, Integer rampUpSeconds) {

        StringBuilder script = new StringBuilder();

        script.append("import http from 'k6/http';\n");
        script.append("import { check, sleep } from 'k6';\n");
        script.append("import { Counter } from 'k6/metrics';\n");
        script.append("import { Rate } from 'k6/metrics';\n\n");

        // 사용자 정의 메트릭 설정
        script.append("// 사용자 정의 메트릭\n");
        script.append("const errors = new Counter('errors');\n");
        script.append("const successRate = new Rate('success_rate');\n\n");

        // 테스트 옵션 생성
        script.append("export const options = {\n");

        // 테스트 유형별 다른 설정 적용
        switch (testType) {
            case SMOKE:
                // 작은 규모의 테스트를 통해 기본 기능성 확인
                script.append("  // 기본 기능성 확인을 위한 스모크 테스트\n");
                script.append("  vus: ").append(Math.min(virtualUsers, 5)).append(",\n");
                script.append("  duration: '").append(Math.min(durationSeconds, 60)).append("s',\n");
                break;

            case LOAD:
                // 일반적인 부하 테스트
                script.append("  // 정상 부하 상태에서의 성능 테스트\n");
                script.append("  stages: [\n");

                if (rampUpSeconds != null && rampUpSeconds > 0) {
                    script.append("    // 점진적으로 사용자 증가\n");
                    script.append("    { duration: '").append(rampUpSeconds).append("s', target: ").append(virtualUsers).append(" },\n");
                }

                script.append("    // 목표 부하 지속\n");
                script.append("    { duration: '").append(durationSeconds).append("s', target: ").append(virtualUsers).append(" },\n");
                script.append("    // 점진적 종료\n");
                script.append("    { duration: '10s', target: 0 },\n");
                script.append("  ],\n");
                break;

            case STRESS:
                // 임계점 찾기 위한 스트레스 테스트
                script.append("  // 시스템 임계점을 찾기 위한 스트레스 테스트\n");
                script.append("  stages: [\n");

                if (rampUpSeconds != null && rampUpSeconds > 0) {
                    int rampUpStep = rampUpSeconds / 3;
                    script.append("    // 점진적으로 사용자 증가\n");
                    script.append("    { duration: '").append(rampUpStep).append("s', target: ").append(virtualUsers / 2).append(" },\n");
                    script.append("    { duration: '").append(rampUpStep).append("s', target: ").append(virtualUsers).append(" },\n");
                    script.append("    { duration: '").append(rampUpStep).append("s', target: ").append(virtualUsers * 2).append(" },\n");
                } else {
                    script.append("    // 빠르게 최대 부하로 증가\n");
                    script.append("    { duration: '30s', target: ").append(virtualUsers * 2).append(" },\n");
                }

                script.append("    // 최대 부하 지속\n");
                script.append("    { duration: '").append(durationSeconds).append("s', target: ").append(virtualUsers * 2).append(" },\n");
                script.append("    // 점진적 종료\n");
                script.append("    { duration: '30s', target: 0 },\n");
                script.append("  ],\n");
                break;

            case SOAK:
                // 장시간 안정성 테스트
                script.append("  // 장시간 안정성 테스트\n");
                script.append("  stages: [\n");

                if (rampUpSeconds != null && rampUpSeconds > 0) {
                    script.append("    // 점진적으로 사용자 증가\n");
                    script.append("    { duration: '").append(rampUpSeconds).append("s', target: ").append(virtualUsers).append(" },\n");
                }

                script.append("    // 중간 부하 장시간 지속\n");
                script.append("    { duration: '").append(durationSeconds).append("s', target: ").append(virtualUsers).append(" },\n");
                script.append("    // 점진적 종료\n");
                script.append("    { duration: '30s', target: 0 },\n");
                script.append("  ],\n");
                break;
        }

        // 공통 옵션 설정
        script.append("  thresholds: {\n");
        script.append("    http_req_duration: ['p(95)<500'], // 95% 요청은 500ms 이내 응답\n");
        script.append("    'success_rate': ['rate>0.95'],    // 95% 이상 성공률\n");
        script.append("  },\n");

        script.append("};\n\n");

        // 설정 및 변수 추가
        script.append("// 테스트 대상 URL\n");
        script.append("const BASE_URL = '").append(targetUrl).append("';\n\n");

        // 기본 시나리오 함수
        script.append("// 기본 시나리오 함수\n");
        script.append("export default function() {\n");
        script.append("  const response = http.get(BASE_URL);\n\n");

        script.append("  // 응답 확인\n");
        script.append("  const success = check(response, {\n");
        script.append("    'status is 200': (r) => r.status === 200,\n");
        script.append("  });\n\n");

        script.append("  // 메트릭 기록\n");
        script.append("  if (!success) {\n");
        script.append("    errors.add(1);\n");
        script.append("  }\n");
        script.append("  successRate.add(success);\n\n");

        script.append("  // 요청 간 짧은 대기 시간 추가\n");
        script.append("  sleep(1);\n");
        script.append("}\n");

        return script.toString();
    }

    /**
     * 커스텀 스크립트가 제공되지 않은 경우 테스트 유형에 맞는 기본 스크립트를 생성합니다.
     *
     * @param loadTest 로드 테스트 객체
     * @return 생성된 스크립트
     */
    public String generateScriptForLoadTest(LoadTest loadTest) {
        // 사용자가 커스텀 스크립트를 제공했다면 그것을 사용
        if (loadTest.getScriptContent() != null && !loadTest.getScriptContent().trim().isEmpty()) {
            return loadTest.getScriptContent();
        }

        // 기본 스크립트 생성
        return generateScript(
                loadTest.getTargetUrl(),
                loadTest.getTestType(),
                loadTest.getVirtualUsers(),
                loadTest.getDurationSeconds(),
                loadTest.getRampUpSeconds()
        );
    }
}
