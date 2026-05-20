# Kafka Lens

**Language:** [English](README.md) | [한국어](README.ko.md)

> A self-hosted Kafka UI built around the questions operators actually ask — free-text search across topics, real-time consumer-lag charts, DLQ flow visualization, and reprocess-only DLQ writes. Single Docker image, no external dependencies.

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![JDK 21+](https://img.shields.io/badge/JDK-21%2B-orange.svg)](https://adoptium.net/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1-7F52FF.svg)](https://kotlinlang.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F.svg)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18-61DAFB.svg)](https://react.dev/)
[![Docker Hub](https://img.shields.io/docker/pulls/nugaba/kafka-lens?label=Docker%20Hub&logo=docker)](https://hub.docker.com/r/nugaba/kafka-lens)
[![GHCR](https://img.shields.io/badge/GHCR-1989v%2Fkafka--lens-2088FF?logo=github)](https://github.com/1989v/kafka-lens/pkgs/container/kafka-lens)

## 🚀 Try in 30 seconds

```bash
docker pull nugaba/kafka-lens:latest
docker run -p 9192:9192 \
  -e CLUSTERS_0_ID=local \
  -e CLUSTERS_0_NAME='Local Kafka' \
  -e CLUSTERS_0_BOOTSTRAPSERVERS=host.docker.internal:9092 \
  nugaba/kafka-lens:latest
```

Open <http://localhost:9192>. Multi-arch image (linux/amd64 + linux/arm64) — runs natively on Apple Silicon, Raspberry Pi 4+, OCI Ampere A1, etc.

Or pull from GitHub Container Registry: `docker pull ghcr.io/1989v/kafka-lens:latest`.

---

## ⭐ The one thing this tool does well — search Kafka messages by any value

**Type the value you want. Find every message that contains it.** That is the point.

provectus/kafka-ui, AKHQ, Redpanda Console all require the **exact** key or value. If you don't already know the precise string, you're out of luck — or stuck writing a one-off consumer script. Kafka Lens lets you search like `grep`:

| What you want to find | How in Kafka Lens |
|---|---|
| Any message whose value contains `ORD-2026-12345` | Topic → Messages → `value contains: ORD-2026-12345` |
| Messages where `payload.orderId` starts with `ORD-2026-` | Topic → Messages → JSON field path `payload.orderId`, contains `ORD-2026-` |
| Same query across `order.events` + `payment.events` + `analytics.events` | Sidebar → **Cross-topic Search** → topics comma-separated, same filters |
| Trace `correlation-id=abc-123` across the whole pipeline | Cross-topic Search → header equals or value-contains the id |

No regex required. No pre-indexed Elasticsearch. Just `docker run` and type.

### Side-by-side vs. other Kafka UIs

| Search capability | provectus/kafka-ui | AKHQ | **Kafka Lens** |
|---|---|---|---|
| Exact key / value equals | ✅ | ✅ | ✅ |
| **Key / value substring** | ❌ | ⚠️ regex only | ✅ |
| **JSON dotted-path contains** (e.g. `payload.orderId`) | ❌ | ❌ | ✅ |
| **Cross-topic search** with one query | ❌ | ❌ | ✅ |
| Correlation-id trace across topics | ❌ | ❌ | ✅ |
| Time range / partition / offset filters | ⚠️ partial | ✅ | ✅ |

If you've ever opened kafka-ui and given up because you don't know the *exact* key — this tool was built for you.

---

## Also included (auxiliary)

Everything below is a side benefit, not the headline. Use them if you need them:

- **Consumer-lag monitoring** — per-topic Stats tab with lag-over-time line chart (10 min rolling, 10 s sampling), production rate, drain ETA, partition distribution
- **DLQ safety** — auto-detected topic↔DLQ mappings, reprocess-only writes (direct DLQ publish is refused at the API level), dedupe window
- **Brokers page** — per-broker leader/replica load + controller flag
- **Topic Configurations** card — override-vs-default with sensitive marking
- **Kafka Connect** — connector list + restart/pause/resume/delete (set `CLUSTERS_0_CONNECTURL`)
- **Confluent Schema Registry / Avro decode** — wire-format decode to JSON in place (set `CLUSTERS_0_SCHEMAREGISTRYURL`)
- **Topic management** (create / delete / add partitions) — gated behind `TOPICOPS_ALLOWDESTRUCTIVE`, OFF by default

## Design tenets

1. **No pre-indexing.** Kafka is the source of truth. Search is on-demand consume + server-side filter.
2. **Zero external dependencies.** SQLite for operational metadata. No ElasticSearch, no shared DB, no Redis.
3. **Single Docker image.** Backend + bundled SPA. `docker run`, you're done.
4. **DLQ is sacred.** The only write path to a DLQ is reprocess → origin.
5. **Broker compatibility first.** Targets Apache Kafka 2.8+ (KRaft and ZooKeeper). Features that need newer brokers are negotiated via `ApiVersions` and labeled in the UI.

## Usage guide

After `docker run`, open <http://localhost:9192>. The left sidebar has two groups:
- **Cluster** — Dashboard / Brokers / Topics / Consumer Groups
- **Operations** — Cross-topic Search / DLQ Ops / Publish / Connectors

If no cluster is configured, you land on a setup card with the exact env vars / YAML to add.

### Searching messages in a single topic

1. Sidebar → **Topics** → click any topic row.
2. The **Messages** tab opens. Pick a mode:
   - **Latest** — last *N* messages (the page size). Best for "what's flowing right now."
   - **Earliest** — from the start of the topic. Best for finding specific keys anywhere in history.
   - **From timestamp** / **Within range** — seek by time.
3. Optional filters: `key contains`, `value contains` (substring, works on JSON bodies too).
4. Click any row → modal with prettified JSON + headers + Avro schema badge when decoded.

> Latest + filter only checks the recent window. If a key could be older, the empty-result UI gives you a one-click switch to Earliest.

### Cross-topic / JSON-field search

For things like *"find every message in `order.events`, `payment.events`, and `analytics.events` where `payload.orderId` contains `ORD-2026-`"*:

1. Sidebar → **Cross-topic Search**.
2. Topics: comma-separated.
3. JSON field path: e.g. `payload.orderId`. JSON field equals/contains: target value.
4. Time range, max results, etc. Run.

### Monitoring consumer lag

1. Sidebar → **Dashboard**. The **Topic** tab is the default — searchable topic picker on top.
2. Pick a topic. Its full stats panel renders inline:
   - 5 cards: Total lag · Production rate · Top group drain ETA · Partitions · Available messages
   - **Lag-over-time line chart** per consumer group (10-min rolling window, 10s sampling — the line appears once two samples are collected, ~20s)
   - Consumer groups table with current lag · consume rate · drain ETA
   - Partition message distribution bar chart
3. For cluster-wide lag rankings: **Total Overview** tab (loaded only when clicked).

### DLQ operations

1. Sidebar → **DLQ Ops** → **Auto-detect mappings**. Kafka Lens scans for topics matching the cluster's `dlqNamingPatterns` (default: `{topic}.DLT`, `{topic}-dlq`, `dead-letter-{topic}`).
2. Pick a mapping row → message list with parsed origin offsets, exception class, retry count.
3. Check messages to reprocess → **Reprocess → `<origin-topic>`** button.

Safety guards:
- The Publish console refuses direct DLQ publishing (HTTP 403).
- A dedupe window (`dlq.reprocess.duplicate-detection-window`, default 24h) blocks reprocessing the same message twice.

### Topic management (gated)

Topic create / delete / add partitions are gated behind `TOPICOPS_ALLOWDESTRUCTIVE` (default `false`). Enable it deliberately:

```bash
docker run -p 9192:9192 \
  -e TOPICOPS_ALLOWDESTRUCTIVE=true \
  -e CLUSTERS_0_ID=local \
  -e CLUSTERS_0_BOOTSTRAPSERVERS=host.docker.internal:9092 \
  nugaba/kafka-lens:latest
```

When ON:
- Topics page → **+ New topic** button.
- Topic detail header → **Add partitions** / **Delete topic** buttons.
- Delete requires typing the exact topic name to confirm.
- Add partitions warns about per-key ordering breakage + consumer-group rebalance.

### Kafka Connect

Set `CLUSTERS_0_CONNECTURL=http://connect.host:8083`. Sidebar → **Connectors**:
- Connector table: name · type (source/sink) · state · tasks (running / failed) · class.
- Row click → detail modal with JSON config, per-task status, Restart / Pause / Resume / Delete (typed confirmation).

If `connectUrl` isn't set, the page renders a setup card pointing at the env var.

### Confluent Schema Registry / Avro

Set `CLUSTERS_0_SCHEMAREGISTRYURL=http://schema-registry.host:8081`. Messages with the Confluent magic byte `0x00` + 4-byte schema id are auto-decoded to JSON. The message modal shows an **Avro · schema #N** badge plus the encoding label. Schemas are fetched once and cached for the lifetime of the process.

## Quick start

```bash
docker run -p 9192:9192 \
  -e CLUSTERS_0_ID=local \
  -e CLUSTERS_0_NAME='Local Kafka' \
  -e CLUSTERS_0_BOOTSTRAPSERVERS=host.docker.internal:9092 \
  -e AUTH_MODE=none \
  -v $(pwd)/data:/app/data \
  ghcr.io/1989v/kafka-lens:latest
```

Or from Docker Hub (once published):

```bash
docker pull nugaba/kafka-lens:latest
```

Multi-arch images are built for `linux/amd64` and `linux/arm64` (works on Apple Silicon, Raspberry Pi 4+, OCI Ampere A1, etc.).

Open <http://localhost:9192>.

See [`docker-compose.example.yml`](./docker-compose.example.yml) for a working Kafka-alongside setup.

## Configuration

Two ways, compose freely. Environment variables override YAML per key.

### Environment variables (provectus/kafka-ui style)

```bash
CLUSTERS_0_ID=prod
CLUSTERS_0_NAME='Prod MSK'
CLUSTERS_0_BOOTSTRAPSERVERS=b-1.prod.kafka:9094,b-2.prod.kafka:9094
CLUSTERS_0_SECURITY_PROTOCOL=SASL_SSL
CLUSTERS_0_SECURITY_SASLMECHANISM=AWS_MSK_IAM
CLUSTERS_0_SECURITY_SASLJAASCONFIG='software.amazon.msk.auth.iam.IAMLoginModule required;'
CLUSTERS_0_CONNECTURL=http://connect.prod:8083
CLUSTERS_0_SCHEMAREGISTRYURL=http://schema-registry.prod:8081

CLUSTERS_1_ID=staging
CLUSTERS_1_BOOTSTRAPSERVERS=b-1.staging.kafka:9092

AUTH_MODE=none
TOPICOPS_ALLOWDESTRUCTIVE=false   # keep OFF unless you really need delete/add-partitions
STORAGE_SQLITEPATH=/app/data/kafka-lens.db
```

### YAML config file

```bash
docker run -p 9192:9192 \
  -v $(pwd)/config.yml:/app/config.yml:ro \
  -e SPRING_CONFIG_ADDITIONAL_LOCATION=file:/app/config.yml \
  ghcr.io/1989v/kafka-lens:latest
```

See [`config.example.yml`](./config.example.yml) for the full reference (SASL/SSL, DLQ naming patterns, scan limits, auth modes, multi-cluster, Connect, Schema Registry).

## Build from source

```bash
./gradlew bootJar
java -jar build/libs/kafka-lens.jar
```

JDK 21+ required (Java 25 recommended). Frontend assets are built (`npm run build` under `web/`) and bundled into the JAR automatically.

## Security posture

Kafka Lens is meant for **trusted networks** — homelabs, VPNs, internal Kubernetes clusters. It ships with:

- ✅ DLQ direct-publish refused at the API level
- ✅ Destructive topic ops gated behind an explicit env flag
- ✅ Non-root container runtime (UID 10001)
- ✅ Parameterized SQL (no string concat anywhere)
- ✅ Per-cluster TLS / SASL for the Kafka client
- ⚠️ **No HTTP auth out of the box** (basic / OIDC adapters on the roadmap)
- ⚠️ **No HTTPS for the web UI** by default — terminate TLS at your reverse proxy

Do **not** expose Kafka Lens to the public internet without a reverse proxy that adds TLS + authentication.

## Roadmap

- [x] Multi-cluster via env + YAML
- [x] Free-text + JSON-path search, cross-topic search, correlation-id trace
- [x] DLQ auto-detect + reprocess-only flow
- [x] Per-topic lag charts (10-min rolling window)
- [x] Kafka Connect integration
- [x] Confluent Schema Registry Avro decode
- [x] Destructive-ops feature gate
- [ ] OIDC / Basic auth adapters (M5)
- [ ] PII masking rules per topic field (M6)
- [ ] Multi-arch GitHub Actions build → `ghcr.io/1989v/kafka-lens` (M6)
- [ ] MCP server endpoint so AI assistants can query the cluster (M6)

## Contributing

Issues and PRs welcome. The codebase is intentionally lean — pure Spring Boot + Kotlin backend, React + Vite frontend, no microservices. See [`CLAUDE.md`](./CLAUDE.md) for AI-assisted contribution notes if you use Claude Code or similar tools.

## License

Apache-2.0. See [`LICENSE`](./LICENSE).

---

**Keywords**: kafka, kafka-ui, kafka-tools, kafka-monitoring, consumer-lag, dlq, dead-letter-queue, kafka-connect, schema-registry, avro, observability, devtools, self-hosted, spring-boot, kotlin, react, msk.
