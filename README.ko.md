# Kafka Lens

**Language:** [English](README.md) | [한국어](README.ko.md)

> 운영자가 실제로 던지는 질문에 맞춰 만든 self-hosted Kafka UI — 토픽 횡단 **자유 텍스트 검색**, **실시간 컨슈머 lag 시계열 차트**, **DLQ 흐름 시각화**, 재처리 전용 DLQ write. 단일 Docker 이미지, 외부 의존성 0.

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![JDK 21+](https://img.shields.io/badge/JDK-21%2B-orange.svg)](https://adoptium.net/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1-7F52FF.svg)](https://kotlinlang.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F.svg)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18-61DAFB.svg)](https://react.dev/)

---

## 왜 또 다른 Kafka UI 인가

provectus/kafka-ui, AKHQ, Redpanda Console 는 **"지금 이 토픽에 뭐가 들어있나"** 까지는 잘 답해줍니다. 하지만 운영자가 실제로 부딪히는 질문에는 약합니다:

- **"`order.events` 에서 `payload.orderId` 가 `ORD-2026-` 으로 시작하는 메시지 다 찾아줘"**
  기존 도구는 키/값 **완전 일치**만 지원. 부분 일치(substring)나 **JSON 필드 검색은 없음**. → **Kafka Lens 는 free-text contains + JSON dotted-path 검색** 제공.

- **"이 토픽의 lag 을 지난 10분간 시계열로, 그룹별로, drain ETA 까지 보여줘"**
  기존 도구는 현재 시점 lag 스냅샷만. → **Kafka Lens 는 10초마다 lag 을 in-memory ring buffer 에 샘플링** 해서 토픽별 Datadog/Grafana 스타일 차트 렌더.

- **"`payment.events.DLT` 에 쌓인 80건을 원본 토픽으로 재처리해줘. 단, DLQ 에 누군가 직접 발행하는 건 절대 금지"**
  대부분의 도구는 DLQ 를 보통 토픽 취급. → **Kafka Lens 는 토픽 ↔ DLQ 매핑 자동 탐지**, Producer 콘솔에서 DLQ 숨김, **재처리만 허용** (dedupe window 포함).

- **"Confluent Avro 메시지를 화면 떠나지 않고 디코딩해줘"**
  → Schema Registry URL 설정 시 **Avro wire-format 자동 디코딩** (magic byte 0x00 + schema id + Avro payload → JSON).

이런 질문이 익숙하다면 이 도구는 당신을 위해 만들어졌습니다.

## 핵심 기능

### 🔎 Free-text Kafka 검색 — kafka-ui 의 정확 일치 제약을 해소

| 기능 | provectus/kafka-ui | AKHQ | Kafka Lens |
|---|---|---|---|
| 키/값 완전 일치 | ✅ | ✅ | ✅ |
| **키/값 부분 일치 (substring)** | ❌ | ⚠️ regex 만 | ✅ |
| **JSON dotted-path 검색** (예: `payload.orderId`) | ❌ | ❌ | ✅ |
| **여러 토픽 동시 검색** (한 번에) | ❌ | ❌ | ✅ |
| Correlation-id 토픽 횡단 trace | ❌ | ❌ | ✅ |
| 시간 / 파티션 / offset 범위 | ⚠️ 부분 | ✅ | ✅ |

### 📈 토픽별 모니터링 패널 — 에이전트 없이도 Datadog/Grafana 수준

- Total lag · production rate · drain ETA · partition 분포
- **컨슈머 그룹별 lag 시계열 차트** (10분 롤링 윈도우, 10초 샘플링)
- **클러스터 overview → 토픽 stats 드릴다운** 한 화면에서

### 🛡️ DLQ 안전성을 1급 컨셉으로

- `{topic}.DLT` / `dead-letter-{topic}` 매핑 자동 탐지 (패턴 설정 가능)
- DLQ 에 직접 발행은 **API 레벨에서 거부** — HTTP 403
- 재처리 흐름: 단건 / 실패 사유별 그룹 / 전체 → 원본 토픽으로, dedupe window 로 중복 재처리 차단

### ⚙️ 운영용 부가 기능 (군더더기 없이)

- Brokers 페이지 — 브로커별 leader / replica 부하
- 토픽 Configurations 카드 (override vs default, sensitive 마킹)
- 클러스터 Dashboard (Topic / Total Overview 탭 — 무거운 테이블은 명시적으로 클릭해야 로드)
- Kafka Connect 통합 — connector 목록 + restart/pause/resume/delete (per-cluster `connectUrl`)
- Confluent Schema Registry Avro 디코딩 (per-cluster `schemaRegistryUrl`)
- **Destructive ops (토픽 삭제, 파티션 추가) 는 `TOPICOPS_ALLOWDESTRUCTIVE` 가드 뒤 — 기본 OFF.**

## 설계 원칙

1. **선인덱싱 없음.** Kafka 자체가 source of truth. 검색은 on-demand consume + 서버측 필터.
2. **외부 의존성 0.** 운영 메타데이터는 SQLite. ElasticSearch X, 공유 DB X, Redis X.
3. **단일 Docker 이미지.** 백엔드 + 번들된 SPA. `docker run` 한 줄.
4. **DLQ 는 신성한 영역.** DLQ 로의 write 는 재처리 → 원본 외엔 불가.
5. **브로커 호환성 우선.** Apache Kafka 2.8+ (KRaft / ZooKeeper 모두). 신버전 의존 기능은 `ApiVersions` 협상 + UI 에 라벨링.

## 빠른 시작

```bash
docker run -p 9192:9192 \
  -e CLUSTERS_0_ID=local \
  -e CLUSTERS_0_NAME='Local Kafka' \
  -e CLUSTERS_0_BOOTSTRAPSERVERS=host.docker.internal:9092 \
  -e AUTH_MODE=none \
  -v $(pwd)/data:/app/data \
  ghcr.io/1989v/kafka-lens:latest
```

브라우저에서 <http://localhost:9192>.

Kafka 까지 함께 띄우는 예시: [`docker-compose.example.yml`](./docker-compose.example.yml).

## 설정

두 가지 방식, 자유롭게 조합. env 가 YAML 을 키 단위로 덮어씁니다.

### Environment variables (provectus/kafka-ui 스타일)

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
TOPICOPS_ALLOWDESTRUCTIVE=false   # 토픽 삭제/파티션 추가 게이트, 기본 OFF
STORAGE_SQLITEPATH=/app/data/kafka-lens.db
```

### YAML 파일

```bash
docker run -p 9192:9192 \
  -v $(pwd)/config.yml:/app/config.yml:ro \
  -e SPRING_CONFIG_ADDITIONAL_LOCATION=file:/app/config.yml \
  ghcr.io/1989v/kafka-lens:latest
```

전체 레퍼런스: [`config.example.yml`](./config.example.yml) (SASL/SSL · DLQ 네이밍 · scan 한계 · auth 모드 · 멀티 클러스터 · Connect · Schema Registry).

## 소스에서 빌드

```bash
./gradlew bootJar
java -jar build/libs/kafka-lens.jar
```

JDK 21+ 필요 (Java 25 권장). Frontend 자산(`web/` 의 `npm run build`)도 JAR 에 자동 번들.

## 보안 가이드

Kafka Lens 는 **신뢰된 네트워크** (홈랩, VPN, 사내 K8s) 안에서 쓰는 도구입니다. 기본 제공:

- ✅ DLQ 직접 발행 API 레벨 거부
- ✅ Destructive 토픽 ops 명시적 env 플래그 뒤로 가드
- ✅ Non-root 컨테이너 (UID 10001)
- ✅ Parameterized SQL (string concat 없음)
- ✅ Per-cluster TLS / SASL Kafka 클라이언트
- ⚠️ **HTTP 인증 미구현** (basic / OIDC 어댑터는 로드맵)
- ⚠️ **웹 UI 자체 HTTPS 없음** — reverse proxy 에서 TLS 종단 권장

**공용 인터넷에 직접 노출 금지**. reverse proxy + TLS + 인증 필수.

## 로드맵

- [x] env + YAML 멀티 클러스터
- [x] free-text + JSON-path 검색, 토픽 횡단 검색, correlation-id trace
- [x] DLQ 자동 탐지 + 재처리 전용 흐름
- [x] 토픽별 lag 시계열 차트 (10분 윈도우)
- [x] Kafka Connect 통합
- [x] Confluent Schema Registry Avro 디코딩
- [x] Destructive ops 기능 게이트
- [ ] OIDC / Basic auth 어댑터 (M5)
- [ ] 토픽 필드별 PII 마스킹 룰 (M6)
- [ ] Multi-arch GitHub Actions 빌드 → `ghcr.io/1989v/kafka-lens` (M6)
- [ ] AI 어시스턴트가 클러스터 조회 가능한 MCP 서버 엔드포인트 (M6)

## 기여

이슈/PR 환영. 코드베이스는 의도적으로 minimal — 순수 Spring Boot + Kotlin 백엔드, React + Vite 프론트엔드, 마이크로서비스 없음. Claude Code 같은 AI 도구로 기여하실 거면 [`CLAUDE.md`](./CLAUDE.md) 참고.

## 라이선스

Apache-2.0. [`LICENSE`](./LICENSE) 참조.

---

**키워드**: kafka, kafka-ui, kafka-tools, kafka-monitoring, consumer-lag, dlq, dead-letter-queue, kafka-connect, schema-registry, avro, observability, devtools, self-hosted, spring-boot, kotlin, react, msk.
