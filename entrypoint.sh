#!/bin/bash
# 파일명: entrypoint.sh
# 설명: ECS 컨테이너에서 k6 부하 테스트를 실행하고 결과를 백엔드로 전송하는 스크립트

set -e

# 환경 변수 확인
if [ -z "$TEST_ID" ]; then
  echo "ERROR: TEST_ID 환경 변수가 설정되지 않았습니다."
  exit 1
fi

if [ -z "$TARGET_URL" ]; then
  echo "ERROR: TARGET_URL 환경 변수가 설정되지 않았습니다."
  exit 1
fi

if [ -z "$VIRTUAL_USERS" ]; then
  echo "ERROR: VIRTUAL_USERS 환경 변수가 설정되지 않았습니다."
  exit 1
fi

if [ -z "$DURATION_SECONDS" ]; then
  echo "ERROR: DURATION_SECONDS 환경 변수가 설정되지 않았습니다."
  exit 1
fi

# 선택적 환경 변수에 대한 기본값 설정
if [ -z "$RAMP_UP_SECONDS" ]; then
  RAMP_UP_SECONDS=0
fi

if [ -z "$BACKEND_API_URL" ]; then
  BACKEND_API_URL="http://api.loadtest-service.com/api/callback/test-result"
fi

if [ -z "$API_KEY" ]; then
  echo "WARNING: API_KEY 환경 변수가 설정되지 않았습니다. 결과 전송이 실패할 수 있습니다."
  API_KEY="default-key"
fi

# 컨테이너 인덱스 관련 환경 변수 기본값 설정
if [ -z "$CONTAINER_INDEX" ]; then
  CONTAINER_INDEX=0
fi

if [ -z "$TOTAL_CONTAINERS" ]; then
  TOTAL_CONTAINERS=1
fi

echo "======= 테스트 정보 ======="
echo "테스트 ID: $TEST_ID"
echo "대상 URL: $TARGET_URL"
echo "가상 사용자: $VIRTUAL_USERS"
echo "테스트 시간: $DURATION_SECONDS초"
echo "점진적 증가 시간: $RAMP_UP_SECONDS초"
echo "컨테이너 인덱스: $CONTAINER_INDEX (총 $TOTAL_CONTAINERS 개 중)"
echo "=========================="

# 테스트 시작 시간 기록
START_TIME=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
echo "테스트 시작 시간: $START_TIME"

# 스크립트 파일 생성
if [ -n "$SCRIPT_CONTENT" ]; then
  echo "사용자 정의 스크립트를 사용합니다."
  echo "$SCRIPT_CONTENT" > /tmp/load-test.js
else
  echo "기본 스크립트를 생성합니다."
  cat > /tmp/load-test.js <<EOL
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { Rate } from 'k6/metrics';

// 사용자 정의 메트릭
const errors = new Counter('errors');
const successRate = new Rate('success_rate');

export const options = {
  vus: ${VIRTUAL_USERS},
  duration: '${DURATION_SECONDS}s',
  thresholds: {
    http_req_duration: ['p(95)<500'], // 95% 요청은 500ms 이내 응답
    'success_rate': ['rate>0.95'],    // 95% 이상 성공률
  },
};

// 테스트 대상 URL
const BASE_URL = '${TARGET_URL}';

// 기본 시나리오 함수
export default function() {
  const response = http.get(BASE_URL);

  // 응답 확인
  const success = check(response, {
    'status is 200': (r) => r.status === 200,
  });

  // 메트릭 기록
  if (!success) {
    errors.add(1);
  }
  successRate.add(success);

  // 요청 간 짧은 대기 시간 추가
  sleep(1);
}
EOL
fi

# k6 테스트 실행
echo "k6 테스트를 시작합니다... (컨테이너 #$CONTAINER_INDEX)"
k6 run --out json=/tmp/results.json /tmp/load-test.js

# 테스트 종료 시간 기록
END_TIME=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
echo "테스트 종료 시간: $END_TIME"

# 결과 파일에서 요약 정보 추출
echo "테스트 결과를 분석합니다..."
TOTAL_REQUESTS=$(jq '.metrics.http_reqs.values.count' /tmp/results.json)
FAILED_REQUESTS=$(jq '.metrics.http_req_failed.values.fails' /tmp/results.json)
SUCCESSFUL_REQUESTS=$((TOTAL_REQUESTS - FAILED_REQUESTS))
AVG_RESPONSE_TIME=$(jq '.metrics.http_req_duration.values.avg' /tmp/results.json)
P95_RESPONSE_TIME=$(jq '.metrics.http_req_duration.values["p(95)"]' /tmp/results.json)
P99_RESPONSE_TIME=$(jq '.metrics.http_req_duration.values["p(99)"]' /tmp/results.json)
MAX_RESPONSE_TIME=$(jq '.metrics.http_req_duration.values.max' /tmp/results.json)
MIN_RESPONSE_TIME=$(jq '.metrics.http_req_duration.values.min' /tmp/results.json)
REQUESTS_PER_SECOND=$(jq '.metrics.http_reqs.values.rate' /tmp/results.json)

# 요약 결과를 JSON 파일로 생성
cat > /tmp/summary.json <<EOL
{
  "loadTestId": ${TEST_ID},
  "startTime": "${START_TIME}",
  "endTime": "${END_TIME}",
  "totalRequests": ${TOTAL_REQUESTS},
  "successfulRequests": ${SUCCESSFUL_REQUESTS},
  "failedRequests": ${FAILED_REQUESTS},
  "avgResponseTimeMs": ${AVG_RESPONSE_TIME},
  "p95ResponseTimeMs": ${P95_RESPONSE_TIME},
  "p99ResponseTimeMs": ${P99_RESPONSE_TIME},
  "maxResponseTimeMs": ${MAX_RESPONSE_TIME},
  "minResponseTimeMs": ${MIN_RESPONSE_TIME},
  "requestsPerSecond": ${REQUESTS_PER_SECOND},
  "containerIndex": ${CONTAINER_INDEX},
  "summaryJson": $(jq '.' /tmp/results.json)
}
EOL

# 결과를 백엔드로 전송
echo "결과를 백엔드 서버로 전송합니다... (컨테이너 #$CONTAINER_INDEX)"
curl -X POST \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $API_KEY" \
  -d @/tmp/summary.json \
  $BACKEND_API_URL

# 종료 상태 확인
if [ $? -eq 0 ]; then
  echo "결과 전송 성공!"
else
  echo "결과 전송 실패. 로컬에 결과를 저장합니다."
  cp /tmp/summary.json /results/summary_${TEST_ID}_container_${CONTAINER_INDEX}.json
  cp /tmp/results.json /results/full_${TEST_ID}_container_${CONTAINER_INDEX}.json
fi

echo "테스트가 완료되었습니다. (컨테이너 #$CONTAINER_INDEX)"