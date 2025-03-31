package com.elandinnople.loadpilot.common.service;

import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;
import com.elandinnople.loadpilot.domain.loadtest.dto.response.LambdaResponse;
import com.elandinnople.loadpilot.domain.loadtest.dto.request.LoadTestRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
@Slf4j
public class LambdaService {

    private final AWSLambda lambdaClient;
    private final String functionName;

    public LambdaService(
            AWSLambda lambdaClient,
            @Value("${aws.lambda.function-name}") String functionName) {
        this.lambdaClient = lambdaClient;
        this.functionName = functionName;
    }

    public String invokeEcsTask(LoadTestRequest request) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            String payload = objectMapper.writeValueAsString(request);

            InvokeRequest invokeRequest = new InvokeRequest()
                    .withFunctionName(functionName)
                    .withPayload(payload);

            InvokeResult invokeResult = lambdaClient.invoke(invokeRequest);

            if (invokeResult.getFunctionError() != null) {
                log.error("Lambda 함수 실행 오류: {}", invokeResult.getFunctionError());
                throw new RuntimeException("Lambda 함수 실행 중 오류가 발생했습니다.");
            }

            String response = new String(invokeResult.getPayload().array(), StandardCharsets.UTF_8);
            log.info("Lambda 응답: {}", response);

            // 응답에서 ECS 태스크 ID 추출
            LambdaResponse lambdaResponse = objectMapper.readValue(response, LambdaResponse.class);
            return lambdaResponse.getTaskId();
        } catch (Exception e) {
            log.error("Lambda 함수 호출 중 오류: {}", e.getMessage());
            throw new RuntimeException("Lambda 함수 호출 중 오류가 발생했습니다.", e);
        }
    }
}
