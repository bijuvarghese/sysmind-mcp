# sysmind-mcp

Spring Boot 4 backend for SysMind. It receives chat prompts, asks an OpenAI-compatible LLM to choose an applicable system tool, executes that tool when appropriate, and sends the result back through the LLM for a readable response.

## Endpoints

- `POST /agent`
  - Body: `{"prompt":"...","model":"optional-model-id"}`
  - `prompt` is required and validated.
  - Returns the LLM chat completion response JSON.
- `GET /v1/models`
  - Proxies the configured LLM model list.

## Tools

Registered system tools:

- `disk_usage`: returns disk free, used, and total values.
- `latest_news`: fetches current web news headlines from an RSS feed.
- `ram_usage`: returns memory free, used, and total values.

If no tool applies, or the LLM returns an invalid/unparseable tool decision, the backend asks the LLM directly without injecting tool data.

## Configuration

`application.yaml` intentionally does not include default values for LLM connection settings. Provide them through environment variables or an imported `.env` file:

```env
LLM_URL=http://localhost:1234
LLM_TIMEOUT=3m
CHROMA_URL=http://localhost:8000
CHROMA_TIMEOUT=5s
CHROMA_TENANT=default_tenant
CHROMA_DATABASE=default_database
CHROMA_COLLECTION=sysmind
NEWS_LANGUAGE=en-US
NEWS_COUNTRY=US
NEWS_CEID=
```

For Docker from the repository root, use:

```env
LLM_URL=http://host.docker.internal:1234
LLM_TIMEOUT=3m
```

Spring imports optional env files from:

- `sysmind-mcp/.env`
- repository root `.env`

The `llm.*` settings are bound through `AppConfig` with `@ConfigurationProperties`, so IDE tooling can recognize `llm.url` and `llm.timeout`.

If `NEWS_CEID` is empty, the backend derives it from `NEWS_COUNTRY` and `NEWS_LANGUAGE`, for example `US:en`.

The Chroma settings are bound through `chroma.*`. `chroma_status` checks `/api/v2/healthcheck` and `/api/v2/version` so the agent can confirm the vector database is reachable before retrieval tools run.

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

## Error Handling

LLM calls use the configured timeout.

- Timeout: `504 Gateway Timeout`
- Upstream request/status failure: `502 Bad Gateway`
- Invalid `/agent` request body: `400 Bad Request`

Logs use SLF4J and avoid printing full prompts by default.

## Docker

The root Compose stack builds this service and injects:

```env
LLM_URL
LLM_TIMEOUT
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
