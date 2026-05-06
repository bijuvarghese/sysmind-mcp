# sysmind-mcp

Stateless Model Context Protocol (MCP) server for SysMind, built with Spring Boot 4.

It exposes read-only tools for local machine status, disk and RAM usage, Google News RSS headlines, and Chroma database health checks.

In the full SysMind workspace, this backend is called by `sysmind-ui` through Next.js API routes and by `sysmind-agent` through its MCP backend configuration.

## Requirements

- Java 25
- Docker, optional for the Compose workflow

## Endpoint

- `POST /mcp`
  - Stateless MCP JSON-RPC endpoint.
  - Supports `initialize`, `tools/list`, and `tools/call`.
  - Does not require `Mcp-Session-Id`.

Local callers should use `http://localhost:8080/mcp`. In the root Docker stack, nginx proxies the same route at `http://localhost:${NGINX_PORT:-80}/mcp`.

## Tools

Registered MCP tools:

- `disk_usage`: returns disk free, used, and total values.
- `ram_usage`: returns memory free, used, and total values.
- `latest_news`: fetches current web news headlines from an RSS feed.
- `chroma_status`: checks whether the Chroma vector database is reachable.
- `machine_status`: returns computer name, OS, CPU, RAM, storage, and uptime details.

All tools are read-only. `latest_news` accepts optional `query`, `language`, `country`, and `ceid` string arguments. The other tools accept empty arguments.

Example `machine_status` call:

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/call",
  "params": {
    "name": "machine_status",
    "arguments": {}
  }
}
```

The `machine_status` response includes:

- `computerName`, `operatingSystem`, `machineType`, and `processor`.
- `processorDetails`: total cores, performance/efficiency core split when available, physical/logical cores, clock speeds, load averages, per-core usage when available, and CPU usage.
- `memoryDetails`: total, available, used, swap/page-file values, cached/wired/compressed values when available, pressure, usage percent, and health status.
- `storageDetails`: total, free, used, per-volume usage, read/write throughput when available, SMART health when available, usage percent, and health status.
- `systemTemperature`: system temperature in Celsius/Fahrenheit when a platform sensor is available, source, and health status.
- `powerDetails`: battery percent, charging state, power source, cycle count, health, condition, and status when available.
- `processDetails`: process count plus top CPU and memory consumers.
- `networkDetails`: host addresses, interface details, active interface, Wi-Fi SSID, public IP when reachable, network throughput when available, DNS servers, and default gateway.
- `thermalDetails`: fan speed, thermal pressure, sensor list, CPU/GPU temperatures when available, and status.
- `gpuDetails`: GPU model, utilization, memory, driver, and Metal details when available.
- `systemDetails`: logged-in user, timezone, locale, kernel version, boot time, and recent sleep/wake history when available.
- `jvmDetails`: MCP JVM process id, uptime, heap/non-heap usage, process CPU usage, open file descriptors, and thread count.
- `systemStatus`: last started time, uptime text, and uptime seconds.

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

`machine_status` reads host metrics from the JVM and platform utilities when available. Missing platform-specific values are returned as zero, empty lists, null, or `Unknown` rather than failing the whole tool call. On Apple Silicon, `processorDetails.coreSummary` can report values such as `14 (10 Performance and 4 Efficiency)` when `hw.perflevel*` data is available. Some advanced values depend on optional or privileged platform tools such as `smartctl`, `powermetrics`, `istats`, `osx-cpu-temp`, `nvidia-smi`, `lspci`, `iwgetid`, or `curl`.

## Development

Run locally:

```bash
./mvnw spring-boot:run
```

The MCP endpoint is available at:

```text
http://localhost:8080/mcp
```

List tools:

```bash
curl -s http://localhost:8080/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}'
```

Run tests:

```bash
./mvnw test
```

Build the jar:

```bash
./mvnw clean package
```

Run the packaged jar:

```bash
java -jar target/sysmind-mcp.jar
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

The root Compose stack runs this MCP backend with `sysmind-ui`, Chroma, and nginx. `sysmind-agent` is tracked as a sibling submodule and currently runs locally against this MCP endpoint.

For this standalone repository, you can also run:

```bash
docker compose up --build
```

## License

This project is released under the MIT License. See [LICENSE.md](LICENSE.md).
