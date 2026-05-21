# Kafka Lens

**Language:** [English](README.md) | [한국어](README.ko.md)

> 운영자가 실제로 던지는 질문에 맞춰 만든 self-hosted Kafka UI — 토픽 횡단 **자유 텍스트 검색**, **실시간 컨슈머 lag 시계열 차트**, **DLQ 흐름 시각화**, 재처리 전용 DLQ write. 단일 Docker 이미지, 외부 의존성 0.

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![JDK 21+](https://img.shields.io/badge/JDK-21%2B-orange.svg)](https://adoptium.net/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1-7F52FF.svg)](https://kotlinlang.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F.svg)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18-61DAFB.svg)](https://react.dev/)
[![Docker Hub](https://img.shields.io/docker/pulls/nugaba/kafka-lens?label=Docker%20Hub&logo=docker)](https://hub.docker.com/r/nugaba/kafka-lens)
[![GHCR](https://img.shields.io/badge/GHCR-1989v%2Fkafka--lens-2088FF?logo=github)](https://github.com/1989v/kafka-lens/pkgs/container/kafka-lens)

## 🚀 30초 만에 띄우기

```bash
docker pull nugaba/kafka-lens:latest
docker run -p 9192:9192 \
  -e CLUSTERS_0_ID=local \
  -e CLUSTERS_0_NAME='Local Kafka' \
  -e CLUSTERS_0_BOOTSTRAPSERVERS=host.docker.internal:9092 \
  nugaba/kafka-lens:latest
```

브라우저에서 <http://localhost:9192>. Multi-arch (linux/amd64 + linux/arm64) — Apple Silicon, Raspberry Pi 4+, OCI Ampere A1 등에서 네이티브로 동작.

GitHub Container Registry 도 사용 가능: `docker pull ghcr.io/1989v/kafka-lens:latest`.

---

## ⭐ 이 도구가 잘 하는 단 한 가지 — Kafka 메시지를 원하는 값으로 간편 검색

**찾고 싶은 값을 입력하세요. 그 값이 들어있는 메시지를 다 찾아줍니다.** 그게 전부입니다.

Kafka Lens 는 Kafka 메시지를 `grep` 처럼 검색합니다 — 키/값 부분 일치, JSON 본문 내 dotted-path 조회, 그리고 같은 쿼리를 여러 토픽에 동시에 던지는 것까지:

| 찾고 싶은 것 | Kafka Lens 에서 |
|---|---|
| value 에 `ORD-2026-12345` 포함된 메시지 다 | Topic → Messages → `value contains: ORD-2026-12345` |
| `payload.orderId` 가 `ORD-2026-` 으로 시작하는 메시지 | Topic → Messages → JSON field path `payload.orderId`, contains `ORD-2026-` |
| 같은 쿼리를 `order.events` + `payment.events` + `analytics.events` 동시에 | 사이드바 → **Cross-topic Search** → topics 콤마 구분 |
| `correlation-id=abc-123` 을 파이프라인 전체에서 추적 | Cross-topic Search → header equals 또는 value contains |

정규식 필요 없음. Elasticsearch 사전 인덱싱 필요 없음. `docker run` 한 줄 + 검색창에 타이핑.

### 지원하는 검색 기능

- 키 / 값 완전 일치
- 키 / 값 부분 일치 (substring)
- JSON dotted-path 검색 (예: `payload.orderId`)
- 여러 토픽 동시 검색
- Correlation-id 토픽 횡단 trace
- 시간 / 파티션 / offset 필터

---

## 그 외에 가능한 것 (부가 기능)

아래는 모두 부가 — 메인은 검색입니다. 필요할 때만 쓰세요:

- **컨슈머 lag 모니터링** — 토픽별 Stats 탭에 lag 시계열 라인 차트 (10분 롤링, 10초 샘플링), production rate, drain ETA, partition 분포
- **DLQ 안전성** — 토픽↔DLQ 자동 매핑, 재처리 전용 write (DLQ 직접 발행은 API 거부), dedupe window
- **Brokers 페이지** — 브로커별 leader/replica 부하 + controller 표시
- **토픽 Configurations** 카드 — override vs default, sensitive 마킹
- **Kafka Connect** — connector 목록 + restart/pause/resume/delete (`CLUSTERS_0_CONNECTURL` 설정 시)
- **Confluent Schema Registry / Avro 디코딩** — wire-format 자동 JSON 변환 (`CLUSTERS_0_SCHEMAREGISTRYURL` 설정 시)
- **토픽 관리** (생성 / 삭제 / 파티션 추가) — `TOPICOPS_ALLOWDESTRUCTIVE` 게이트 뒤, 기본 OFF

## 설계 원칙

1. **선인덱싱 없음.** Kafka 자체가 source of truth. 검색은 on-demand consume + 서버측 필터.
2. **외부 의존성 0.** 운영 메타데이터는 SQLite. ElasticSearch X, 공유 DB X, Redis X.
3. **단일 Docker 이미지.** 백엔드 + 번들된 SPA. `docker run` 한 줄.
4. **DLQ 는 신성한 영역.** DLQ 로의 write 는 재처리 → 원본 외엔 불가.
5. **브로커 호환성 우선.** Apache Kafka 2.8+ (KRaft / ZooKeeper 모두). 신버전 의존 기능은 `ApiVersions` 협상 + UI 에 라벨링.

## 사용 가이드

`docker run` 후 <http://localhost:9192> 진입. 좌측 사이드바에 두 그룹:
- **Cluster** — Dashboard / Brokers / Topics / Consumer Groups
- **Operations** — Cross-topic Search / DLQ Ops / Publish / Connectors

클러스터 설정이 없으면 setup 카드가 떠서 어떤 env var 또는 YAML 을 넣어야 하는지 알려줍니다.

### 단일 토픽 메시지 검색

1. 사이드바 → **Topics** → 토픽 행 클릭.
2. **Messages** 탭이 열림. mode 선택:
   - **Latest** — 가장 최근 *N* 건 (pageSize). "지금 흘러가는 메시지" 보기.
   - **Earliest** — 토픽 시작부터. 깊은 과거의 특정 키 찾기.
   - **From timestamp** / **Within range** — 시간 기반 seek.
3. 옵션 필터: `key contains`, `value contains` (substring, JSON 본문에도 적용).
4. 행 클릭 → 모달에 JSON pretty + 헤더 + Avro 스키마 배지 (디코딩된 경우).

> Latest + 필터는 최근 윈도우만 검사합니다. 오래된 키일 수 있으면 빈 결과 화면에서 **Earliest** 로 1-click 전환 가능.

### 토픽 횡단 / JSON 필드 검색

*"`order.events` / `payment.events` / `analytics.events` 모두에서 `payload.orderId` 가 `ORD-2026-` 으로 시작하는 메시지 찾기"* 같은 시나리오:

1. 사이드바 → **Cross-topic Search**.
2. Topics: comma-separated.
3. JSON field path: 예 `payload.orderId`. JSON field equals/contains: 찾을 값.
4. 시간 범위 / 최대 결과 등 설정 후 Run.

### 컨슈머 lag 모니터링

1. 사이드바 → **Dashboard**. **Topic** 탭이 기본 — 상단에 검색 가능한 picker.
2. 토픽 선택 시 stats 패널이 즉시 렌더:
   - 5 카드: Total lag · Production rate · Top group drain ETA · Partitions · Available messages
   - **그룹별 lag 시계열 라인 차트** (10분 롤링 윈도우, 10초 샘플링 — 2 sample 모이면 line 나타남, ~20초)
   - 그룹 테이블: current lag · consume rate · drain ETA
   - 파티션 분포 바 차트
3. 클러스터 전체 lag 순위: **Total Overview** 탭 (클릭 시에만 fetch).

### DLQ 운영

1. 사이드바 → **DLQ Ops** → **Auto-detect mappings**. 클러스터의 `dlqNamingPatterns` (기본 `{topic}.DLT`, `{topic}-dlq`, `dead-letter-{topic}`) 에 맞는 토픽들을 스캔.
2. 매핑 행 클릭 → 메시지 목록 (원본 offset, exception class, 재시도 횟수 포함).
3. 재처리할 메시지 체크 → **Reprocess → `<원본 토픽>`** 버튼.

안전 가드:
- Publish 콘솔에서 DLQ 직접 발행은 HTTP 403 으로 거부.
- dedupe window (`dlq.reprocess.duplicate-detection-window`, 기본 24h) 가 같은 메시지의 중복 재처리 차단.

### 토픽 관리 (게이트)

토픽 생성 / 삭제 / 파티션 추가는 `TOPICOPS_ALLOWDESTRUCTIVE` 뒤에 가려져 있습니다 (기본 `false`). 명시적으로 켜기:

```bash
docker run -p 9192:9192 \
  -e TOPICOPS_ALLOWDESTRUCTIVE=true \
  -e CLUSTERS_0_ID=local \
  -e CLUSTERS_0_BOOTSTRAPSERVERS=host.docker.internal:9092 \
  nugaba/kafka-lens:latest
```

켜진 상태에서:
- Topics 페이지 → **+ New topic** 버튼.
- 토픽 detail 헤더 → **Add partitions** / **Delete topic** 버튼.
- Delete 는 토픽명 정확히 타이핑 후에만 활성화.
- Add partitions 는 key-ordering 깨짐 + 컨슈머 그룹 rebalance 경고 동반.

### Kafka Connect

`CLUSTERS_0_CONNECTURL=http://connect.host:8083` 설정 후 사이드바 → **Connectors**:
- connector 테이블: name · type (source/sink) · state · tasks (running / failed) · class.
- 행 클릭 → 상세 모달: JSON config, 태스크별 상태, Restart / Pause / Resume / Delete (typed confirmation).

`connectUrl` 미설정 시 페이지가 setup 안내 카드를 표시.

### Confluent Schema Registry / Avro

`CLUSTERS_0_SCHEMAREGISTRYURL=http://schema-registry.host:8081` 설정. Confluent magic byte `0x00` + 4 byte schema id 형식 메시지는 자동으로 JSON 디코딩. 메시지 모달에 **Avro · schema #N** 배지 + encoding 라벨 표시. 스키마는 한 번 fetch 후 프로세스 수명 동안 캐시.

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

Docker Hub 에서도 받을 수 있습니다 (발행 이후):

```bash
docker pull nugaba/kafka-lens:latest
```

이미지는 multi-arch — `linux/amd64`, `linux/arm64` 모두 빌드됨 (Apple Silicon, Raspberry Pi 4+, OCI Ampere A1 등).

브라우저에서 <http://localhost:9192>.

Kafka 까지 함께 띄우는 예시: [`docker-compose.example.yml`](./docker-compose.example.yml).

## 설정

두 가지 방식, 자유롭게 조합. env 가 YAML 을 키 단위로 덮어씁니다.

### Environment variables

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

## 기여

이슈/PR 환영. 코드베이스는 의도적으로 minimal — 순수 Spring Boot + Kotlin 백엔드, React + Vite 프론트엔드, 마이크로서비스 없음. Claude Code 같은 AI 도구로 기여하실 거면 [`CLAUDE.md`](./CLAUDE.md) 참고.

## 라이선스

Apache-2.0. [`LICENSE`](./LICENSE) 참조.

---

**키워드**: kafka, kafka-ui, kafka-tools, kafka-monitoring, consumer-lag, dlq, dead-letter-queue, kafka-connect, schema-registry, avro, observability, devtools, self-hosted, spring-boot, kotlin, react, msk.
