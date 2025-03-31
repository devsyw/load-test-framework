package com.elandinnople.loadpilot.domain.loadtest.controller;

import com.elandinnople.loadpilot.domain.loadtest.dto.request.TestResultProcessRequest;
import com.elandinnople.loadpilot.domain.loadtest.service.LoadTestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/callback")
@RequiredArgsConstructor
@Slf4j
public class ResultCallbackController {

    private final LoadTestService loadTestService;

    // ECS 컨테이너에서 테스트 완료 후 결과를 전송하는 엔드포인트
    @PostMapping("/test-result")
    public ResponseEntity<Void> processTestResult(
            @RequestHeader("X-API-Key") String apiKey,
            @RequestBody TestResultProcessRequest request) {

        // API 키 검증 (실제 구현에서는 보안 설정 필요)
        if (!validateApiKey(apiKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            loadTestService.processTestResult(request.getLoadTestId(), request);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("테스트 결과 처리 중 오류: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private boolean validateApiKey(String apiKey) {
        // 실제 구현에서는 안전한, 환경 변수 기반 또는 DB 기반 검증 로직 필요
        String expectedApiKey = System.getenv("API_KEY");
        return expectedApiKey != null && expectedApiKey.equals(apiKey);
    }
}
