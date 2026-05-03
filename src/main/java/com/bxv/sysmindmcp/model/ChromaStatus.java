package com.bxv.sysmindmcp.model;

public record ChromaStatus(
        boolean healthy,
        String url,
        String tenant,
        String database,
        String collection,
        String version,
        String healthcheck,
        String error
) {
    public static ChromaStatus healthy(
            String url,
            String tenant,
            String database,
            String collection,
            String version,
            String healthcheck) {
        return new ChromaStatus(true, url, tenant, database, collection, version, healthcheck, null);
    }

    public static ChromaStatus unhealthy(
            String url,
            String tenant,
            String database,
            String collection,
            String error) {
        return new ChromaStatus(false, url, tenant, database, collection, null, null, error);
    }
}
