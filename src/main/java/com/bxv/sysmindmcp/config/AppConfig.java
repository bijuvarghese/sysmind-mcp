package com.bxv.sysmindmcp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;


@Component
public class AppConfig {
    @Value("${llm.url}")
    private String llmUrl;

    public String getLLMUrl() {
        return llmUrl;
    }
}
