package com.bxv.sysmindmcp.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
@NoArgsConstructor
public class ModelInfo {
    private String id;
    private String object;

    @JsonProperty("owned_by")
    private String ownedBy;
}
