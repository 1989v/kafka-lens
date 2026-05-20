# Kafka Lens

**Language:** [English](README.md) | [한국어](README.ko.md)

> A self-hosted Kafka UI built around the questions operators actually ask — free-text search across topics, real-time consumer-lag charts, DLQ flow visualization, and reprocess-only DLQ writes. Single Docker image, no external dependencies.

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![JDK 21+](https://img.shields.io/badge/JDK-21%2B-orange.svg)](https://adoptium.net/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1-7F52FF.svg)](https://kotlinlang.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F.svg)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18-61DAFB.svg)](https://react.dev/)

---

## Why another Kafka UI?

provectus/kafka-ui, AKHQ, Redpanda Console all answer **"what's in this topic right now?"** well. They struggle with the questions operators actually face:

- **"Find every message in `order.events` where `payload.orderId` contains `ORD-2026-`."**
  Existing tools only do **exact** key/value match — substring or JSON-field search is missing. **Kafka Lens does free-text contains + JSON dotted-path search.**

- **"Show me lag history for this topic over the last 10 minutes, per consumer group, with drain ETA."**
  Existing tools show only the current lag snapshot. **Kafka Lens samples lag every 10 s into an in-memory ring buffer and renders Datadog/Grafana-style charts per topic.**

- **"Reprocess the 80 messages stuck in `payment.events.DLT` back to the original topic — but don't ever let anyone publish directly to a DLQ."**
  Most tools treat DLQs as ordinary topics. **Kafka Lens auto-detects topic ↔ DLQ mappings, hides the DLQ from the Producer console, and offers a reprocess-only write path with dedupe windows.**

- **"Decode Confluent Avro messages without leaving the page."**
  **Kafka Lens does Avro wire-format decode** (magic byte 0x00 + schema id + Avro payload → JSON) when a Schema Registry URL is configured.

If those questions sound familiar, this tool was built for you.

## Headline features

### 🔎 Free-text Kafka search — the kafka-ui exact-match limitation, solved

| Feature | provectus/kafka-ui | AKHQ | Kafka Lens |
|---|---|---|---|
| Exact key/value equals | ✅ | ✅ | ✅ |
| **Key/value substring** | ❌ | ⚠️ regex only | ✅ |
| **JSON field dotted-path contains** | ❌ | ❌ | ✅ |
| **Cross-topic search** with a single query | ❌ | ❌ | ✅ |
| Correlation-id trace across multiple topics | ❌ | ❌ | ✅ |
| Time range, partition range, offset range | ⚠️ partial | ✅ | ✅ |

### 📈 Per-topic monitoring panel — Datadog/Grafana style without the agent

- Total lag · production rate · drain ETA · partition distribution
- **Lag-over-time line chart** per consumer group (10 min rolling window, 10 s sampling)
- **Drill from cluster overview → topic stats** without leaving the page

### 🛡️ DLQ safety as a first-class concept

- Auto-detected `{topic}.DLT` / `dead-letter-{topic}` mappings (configurable patterns)
- Direct DLQ publishing is **refused at the API level** — HTTP 403
- Reprocess flow: single / group-by-failure-reason / all → back to origin topic, with dedupe window so the same message can't be reprocessed twice

### ⚙️ Operator scaffolding without bloat

- Brokers page with per-broker leader/replica load
- Topic Configurations card (override-vs-default, sensitive marking)
- Cluster Dashboard (Topic / Total Overview tabs — heavy tables stay opt-in)
- Kafka Connect connector list + restart/pause/resume/delete (per-cluster `connectUrl`)
- Confluent Schema Registry Avro decode (per-cluster `schemaRegistryUrl`)
- **Destructive ops (delete topic, add partitions) gated behind `TOPICOPS_ALLOWDESTRUCTIVE` — OFF by default.**

## Design tenets

1. **No pre-indexing.** Kafka is the source of truth. Search is on-demand consume + server-side filter.
2. **Zero external dependencies.** SQLite for operational metadata. No ElasticSearch, no shared DB, no Redis.
3. **Single Docker image.** Backend + bundled SPA. `docker run`, you're done.
4. **DLQ is sacred.** The only write path to a DLQ is reprocess → origin.
5. **Broker compatibility first.** Targets Apache Kafka 2.8+ (KRaft and ZooKeeper). Features that need newer brokers are negotiated via `ApiVersions` and labeled in the UI.

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
