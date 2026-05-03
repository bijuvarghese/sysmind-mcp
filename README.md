# sysmind-mcp

Spring Boot 4 stateless Model Context Protocol server for SysMind.

## Endpoint

- `POST /mcp`
  - Stateless MCP JSON-RPC endpoint.
  - Supports `initialize`, `tools/list`, and `tools/call`.
  - Does not require `Mcp-Session-Id`.

## Tools

Registered MCP tools:

- `disk_usage`: returns disk free, used, and total values.
- `latest_news`: fetches current web news headlines from an RSS feed.
- `ram_usage`: returns memory free, used, and total values.
- `chroma_status`: checks whether the Chroma vector database is reachable.

## Configuration

Spring imports optional env files from:

- `sysmind-mcp/.env`
- repository root `.env`

Useful values:

```env
CHROMA_URL=http://localhost:8000
CHROMA_TIMEOUT=5s
CHROMA_TENANT=default_tenant
CHROMA_DATABASE=default_database
CHROMA_COLLECTION=sysmind
NEWS_LANGUAGE=en-US
NEWS_COUNTRY=US
NEWS_CEID=
```

If `NEWS_CEID` is empty, the backend derives it from `NEWS_COUNTRY` and `NEWS_LANGUAGE`, for example `US:en`.

The Chroma settings are bound through `chroma.*`. `chroma_status` checks `/api/v2/healthcheck` and `/api/v2/version`.

## Development

Run locally:

```bash
./mvnw spring-boot:run
```

Run tests:

```bash
./mvnw test
```

Build the jar:

```bash
./mvnw clean package
```

## Docker

The root Compose stack builds this service and injects:

```env
NEWS_LANGUAGE
NEWS_COUNTRY
NEWS_CEID
CHROMA_URL
CHROMA_TIMEOUT
CHROMA_TENANT
CHROMA_DATABASE
CHROMA_COLLECTION
SPRING_PROFILES_ACTIVE
```

Use root scripts for day-to-day Docker workflow:

```bash
../deploy.sh
../shutdown.sh
```
