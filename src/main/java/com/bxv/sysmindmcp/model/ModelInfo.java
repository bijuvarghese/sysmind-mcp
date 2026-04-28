package com.bxv.sysmindmcp.model;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
public class ModelInfo {
    private String id;
    private String object;

    @JsonProperty("owned_by")
    private String ownedBy;
}