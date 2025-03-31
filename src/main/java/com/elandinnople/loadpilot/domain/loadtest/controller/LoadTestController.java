package com.elandinnople.loadpilot.domain.loadtest.controller;

import com.elandinnople.loadpilot.domain.loadtest.dto.request.CreateLoadTestRequest;
import com.elandinnople.loadpilot.domain.loadtest.dto.response.LoadTestResponse;
import com.elandinnople.loadpilot.domain.loadtest.dto.response.LoadTestStatusResponse;
import com.elandinnople.loadpilot.domain.loadtest.dto.response.TestResultResponse;
import com.elandinnople.loadpilot.domain.loadtest.entity.LoadTest;
import com.elandinnople.loadpilot.domain.loadtest.entity.type.TestStatus;
import com.elandinnople.loadpilot.domain.loadtest.service.LoadTestService;
import com.elandinnople.loadpilot.domain.user.entity.User;
import com.elandinnople.loadpilot.domain.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/load-tests")
@RequiredArgsConstructor
@Slf4j
public class LoadTestController {

    private final LoadTestService loadTestService;
    private final UserService userService;

    @PostMapping
    public ResponseEntity<LoadTestResponse> createLoadTest(
            @RequestBody @Valid CreateLoadTestRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        String keycloakId = jwt.getSubject();
        User user = userService.findByKeycloakId(keycloakId);

        LoadTest loadTest = loadTestService.createLoadTest(request, user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(LoadTestResponse.fromEntity(loadTest));
    }

    @GetMapping
    public ResponseEntity<Page<LoadTestResponse>> getLoadTests(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) TestStatus status) {

        String keycloakId = jwt.getSubject();
        User user = userService.findByKeycloakId(keycloakId);

        Page<LoadTest> loadTests = loadTestService.getLoadTests(user.getId(), status, page, size);
        Page<LoadTestResponse> response = loadTests.map(LoadTestResponse::fromEntity);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<LoadTestResponse> getLoadTest(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        String keycloakId = jwt.getSubject();
        User user = userService.findByKeycloakId(keycloakId);

        LoadTest loadTest = loadTestService.getLoadTest(id, user.getId());
        return ResponseEntity.ok(LoadTestResponse.fromEntity(loadTest));
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<LoadTestResponse> startLoadTest(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        String keycloakId = jwt.getSubject();
        User user = userService.findByKeycloakId(keycloakId);

        LoadTest loadTest = loadTestService.startLoadTest(id, user.getId());
        return ResponseEntity.ok(LoadTestResponse.fromEntity(loadTest));
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<LoadTestStatusResponse> getLoadTestStatus(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        String keycloakId = jwt.getSubject();
        User user = userService.findByKeycloakId(keycloakId);

        LoadTestStatusResponse status = loadTestService.getLoadTestStatus(id, user.getId());
        return ResponseEntity.ok(status);
    }

    @GetMapping("/{id}/result")
    public ResponseEntity<TestResultResponse> getLoadTestResult(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        String keycloakId = jwt.getSubject();
        User user = userService.findByKeycloakId(keycloakId);

        TestResultResponse result = loadTestService.getTestResult(id, user.getId());
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLoadTest(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        String keycloakId = jwt.getSubject();
        User user = userService.findByKeycloakId(keycloakId);

        loadTestService.deleteLoadTest(id, user.getId());
        return ResponseEntity.noContent().build();
    }
}