package com.bxv.sysmindmcp.model;

import jakarta.validation.constraints.NotBlank;

public record AgentRequest(
        @NotBlank(message = "prompt is required")
        String prompt,
        String model
) {
}
