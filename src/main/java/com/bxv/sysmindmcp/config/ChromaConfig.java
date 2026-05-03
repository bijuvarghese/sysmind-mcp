package com.bxv.sysmindmcp.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Component
@Validated
@ConfigurationProperties(prefix = "chroma")
@Getter
@Setter
public class ChromaConfig {
    @NotBlank
    private String url;

    @NotNull
    private Duration timeout;

    @NotBlank
    private String tenant;

    @NotBlank
    private String database;

    @NotBlank
    private String collection;
}
