FROM grafana/k6:latest

# 필요한 도구 설치
RUN apk add --no-cache curl jq bash

# 엔트리포인트 스크립트 복사
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

# 결과 저장 디렉토리 생성
RUN mkdir -p /results

# 환경 변수 설정
ENV TEST_ID=""
ENV TARGET_URL=""
ENV VIRTUAL_USERS="1"
ENV DURATION_SECONDS="60"
ENV RAMP_UP_SECONDS="0"
ENV SCRIPT_CONTENT=""
ENV BACKEND_API_URL="http://your-api-host/api/callback/test-result"
ENV API_KEY="your-api-key"

# 엔트리포인트 설정
ENTRYPOINT ["/entrypoint.sh"]