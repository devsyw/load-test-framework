package com.elandinnople.loadpilot.domain.loadtest.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LambdaResponse {
    private int statusCode;
    private String taskId;
    private String message;
    private String error;
}