# Kafka Lens

> A self-hosted Kafka message explorer with first-class search UX, DLQ flow visualization, and correlation-id tracing.

Kafka Lens is an operator tool — not a SaaS, not a hosted service. You run it where your Kafka lives, with the access *you* control.

## Why another Kafka UI?

The existing OSS Kafka UIs (provectus/kafka-ui, AKHQ, redpanda console) all answer the question "what's in this topic right now?" well, but stumble on the questions operators actually ask:

- "Find every message in `order.events` where `payload.orderId` starts with `ORD-2026-`."
- "Show me the DLQ messages that came from the partition my failing pod was reading."
- "Trace correlation-id `abc-123` across `order.events`, `analytics.events`, and `search.indexer`."
- "Reprocess the 80 timeouts in `payment.events.DLT` back to the original topic — but block direct DLQ publishing entirely."

Kafka Lens is built around those questions.

## Design tenets

- **No pre-indexing.** Kafka is the source of truth. Search is on-demand consume + server-side filter (same pattern as provectus/AKHQ, but with a better search UX).
- **Zero external dependencies.** SQLite for operational metadata (history, mappings, templates) — no shared DB, no ES, no Redis.
- **Single Docker image.** Backend + bundled SPA. `docker run`, you're done.
- **DLQ is sacred.** You cannot publish directly to a DLQ from this tool. The only DLQ write path is *reprocess to original topic*.
- **Broker compatibility first.** Targets Apache Kafka 2.8+ (KRaft & ZooKeeper). Features that need newer brokers are negotiated via `ApiVersions` and labeled in the UI.

## Quick start

```bash
docker run -p 9192:9192 \
  -e CLUSTERS_0_ID=local \
  -e CLUSTERS_0_NAME='Local Kafka' \
  -e CLUSTERS_0_BOOTSTRAPSERVERS=host.docker.internal:9092 \
  -e AUTH_MODE=none \
  -v $(pwd)/data:/app/data \
  ghcr.io/{org}/kafka-lens:latest
```

Then open <http://localhost:9192>.

See [`docker-compose.example.yml`](./docker-compose.example.yml) for a working Kafka-alongside setup.

## Configuration

Kafka Lens accepts configuration two ways. Use whichever fits your runtime — they
compose, with environment variables overriding YAML on a per-key basis.

### Environment variables (provectus/kafka-ui style)

Cluster definitions are indexed: `CLUSTERS_0_*`, `CLUSTERS_1_*`, and so on.

```bash
CLUSTERS_0_ID=prod
CLUSTERS_0_NAME='Prod MSK'
CLUSTERS_0_BOOTSTRAPSERVERS=b-1.prod.kafka:9094,b-2.prod.kafka:9094
CLUSTERS_0_SECURITY_PROTOCOL=SASL_SSL
CLUSTERS_0_SECURITY_SASLMECHANISM=AWS_MSK_IAM
CLUSTERS_0_SECURITY_SASLJAASCONFIG='software.amazon.msk.auth.iam.IAMLoginModule required;'

CLUSTERS_1_ID=staging
CLUSTERS_1_BOOTSTRAPSERVERS=b-1.staging.kafka:9092

AUTH_MODE=none
STORAGE_SQLITEPATH=/app/data/kafka-lens.db
```

This is the recommended way for `docker run` / `docker compose` / Kubernetes
Deployments — no file mount needed.

### YAML config file

Mount or place a `config.yml` next to the JAR and point Kafka Lens at it:

```bash
java -jar build/libs/kafka-lens.jar \
  --spring.config.additional-location=file:./config.yml
```

Or with Docker:

```bash
docker run -p 9192:9192 \
  -v $(pwd)/config.yml:/app/config.yml:ro \
  -e SPRING_CONFIG_ADDITIONAL_LOCATION=file:/app/config.yml \
  ghcr.io/{org}/kafka-lens:latest
```

See [`config.example.yml`](./config.example.yml) for the full YAML reference
(SASL/SSL, DLQ naming patterns, scan limits, auth modes, multi-cluster).

## Build from source

```bash
./gradlew bootJar
java -jar build/libs/kafka-lens.jar
```

Requires JDK 21+ at build time (Java 25 recommended); the Gradle toolchain will pick the JDK up automatically when one is installed. Frontend assets are built (`npm run build` under `web/`) and bundled into the JAR automatically.

## License

Apache-2.0. See [`LICENSE`](./LICENSE).
