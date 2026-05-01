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
NEWS_LANGUAGE=en-US
NEWS_COUNTRY=US
NEWS_CEID=
NEWS_FEED_URL=https://news.google.com/rss?hl={language}&gl={country}&ceid={ceid}
NEWS_LOCATION_FEED_URL_TEMPLATE=https://news.google.com/rss/search?q={query}&hl={language}&gl={country}&ceid={ceid}
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

`NEWS_FEED_URL` is bound through `news.feed-url`.
`NEWS_LOCATION_FEED_URL_TEMPLATE` is used when a prompt names a place. Include `{query}` where the encoded location search should go.
News URL templates support `{query}`, `{language}`/`{hl}`, `{country}`/`{gl}`, `{languageCode}`, and `{ceid}`. If `NEWS_CEID` is empty, the backend derives it from `NEWS_COUNTRY` and `NEWS_LANGUAGE`, for example `US:en`.

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
NEWS_FEED_URL
NEWS_LOCATION_FEED_URL_TEMPLATE
NEWS_LANGUAGE
NEWS_COUNTRY
NEWS_CEID
SPRING_PROFILES_ACTIVE
```

Use root scripts for day-to-day Docker workflow:

```bash
../deploy.sh
../shutdown.sh
```
