package com.elandinnople.loadpilot.domain.loadtest.dto.request;

import com.elandinnople.loadpilot.domain.loadtest.entity.type.TestType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.URL;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateLoadTestRequest {
    @NotBlank(message = "Name is required")
    private String name;

    private String description;

    @NotBlank(message = "Target URL is required")
    @URL(message = "Invalid URL format")
    private String targetUrl;

    @NotNull(message = "Test type is required")
    private TestType testType;

    @NotNull(message = "Virtual users count is required")
    @Min(value = 1, message = "Virtual users must be at least 1")
    private Integer virtualUsers;

    @NotNull(message = "Duration is required")
    @Min(value = 1, message = "Duration must be at least 1 second")
    private Integer durationSeconds;

    @Min(value = 0, message = "Ramp-up duration must be non-negative")
    private Integer rampUpSeconds;

    private String scriptContent;

    @Min(value = 1, message = "Container count must be at least 1")
    @Max(value = 5, message = "Container count must be at most 5")
    private Integer containerCount = 1; // 기본값 1, 최대 5
}
