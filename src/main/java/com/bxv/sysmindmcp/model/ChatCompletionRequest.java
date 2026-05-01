package com.bxv.sysmindmcp.model;

import java.util.List;

public record ChatCompletionRequest(
        String model,
        List<Message> messages
) {
    public static ChatCompletionRequest userMessage(String model, String content) {
        return new ChatCompletionRequest(
                model,
                List.of(new Message("user", content))
        );
    }

    public record Message(
            String role,
            String content
    ) {
    }
}
