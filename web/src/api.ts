export type Cluster = { id: string; name: string; bootstrapServers: string };
export type ClusterDetail = Cluster & {
  brokerVersion: string | null;
  brokerCount: number;
  supportedFeatures: Record<string, boolean>;
  dlqNamingPatterns: string[];
  connectConfigured: boolean;
  schemaRegistryConfigured: boolean;
};
export type Partition = {
  partition: number;
  leader: number | null;
  replicas: number[];
  inSyncReplicas: number[];
  beginningOffset: number;
  endOffset: number;
};
export type Topic = {
  clusterId: string;
  name: string;
  partitions: Partition[];
  internal: boolean;
  totalMessages: number;
};
export type Message = {
  topic: string;
  partition: number;
  offset: number;
  timestamp: string;
  key: string | null;
  value: string | null;
  headers: Record<string, string>;
};
export type SearchResult = {
  jobId: string;
  matched: Message[];
  scannedCount: number;
  durationMs: number;
  completed: boolean;
  cancelled: boolean;
  limitsHit: string[];
};
export type DlqMapping = {
  clusterId: string;
  originTopic: string;
  dlqTopic: string;
  source: "AUTO" | "MANUAL";
  confidence: "HIGH" | "MEDIUM" | "LOW";
  detectedAt: string;
};
export type DlqMessage = {
  record: Message;
  originTopic: string | null;
  originPartition: number | null;
  originOffset: number | null;
  failureReason: string | null;
  exceptionClass: string | null;
  retryCount: number | null;
  lastAttemptAt: string | null;
  correlationId: string | null;
  isReprocessable: boolean;
};
export type DlqPage = { originTopic: string | null; messages: DlqMessage[] };
export type ReprocessJob = {
  id: string;
  clusterId: string;
  dlqTopic: string;
  originTopic: string;
  mode: "SINGLE" | "GROUP" | "ALL";
  status: "PENDING" | "IN_PROGRESS" | "COMPLETED" | "FAILED" | "CANCELLED";
  requestedBy: string;
  createdAt: string;
  completedAt: string | null;
  totalRequested: number;
  succeeded: number;
  failed: number;
  notes: string | null;
};

async function http<T>(input: string, init?: RequestInit): Promise<T> {
  const res = await fetch(input, {
    ...init,
    headers: { "Content-Type": "application/json", ...(init?.headers ?? {}) },
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`${res.status} ${res.statusText}: ${text}`);
  }
  if (res.status === 204) return undefined as unknown as T;
  return (await res.json()) as T;
}

export const api = {
  listClusters: () => http<Cluster[]>("/api/clusters"),
  getCluster: (id: string) => http<ClusterDetail>(`/api/clusters/${id}`),
  listTopics: (clusterId: string, includeInternal = false) =>
    http<Topic[]>(`/api/clusters/${clusterId}/topics?includeInternal=${includeInternal}`),
  listConsumerGroups: (clusterId: string) =>
    http<unknown[]>(`/api/clusters/${clusterId}/consumer-groups`),
  search: (clusterId: string, body: SearchRequest) =>
    http<SearchResult>(`/api/clusters/${clusterId}/search`, {
      method: "POST",
      body: JSON.stringify(body),
    }),
  cancelJob: (clusterId: string, jobId: string) =>
    http<void>(`/api/clusters/${clusterId}/jobs/${jobId}/cancel`, { method: "POST" }),
  listMappings: (clusterId: string, refresh = false) =>
    http<DlqMapping[]>(`/api/clusters/${clusterId}/dlq/mappings?refresh=${refresh}`),
  autoDetectMappings: (clusterId: string) =>
    http<DlqMapping[]>(`/api/clusters/${clusterId}/dlq/mappings/auto-detect`, { method: "POST" }),
  upsertMapping: (clusterId: string, body: { originTopic: string; dlqTopic: string }) =>
    http<void>(`/api/clusters/${clusterId}/dlq/mappings`, {
      method: "PUT",
      body: JSON.stringify(body),
    }),
  deleteMapping: (clusterId: string, originTopic: string, dlqTopic: string) =>
    http<void>(
      `/api/clusters/${clusterId}/dlq/mappings?originTopic=${encodeURIComponent(originTopic)}&dlqTopic=${encodeURIComponent(dlqTopic)}`,
      { method: "DELETE" },
    ),
  readDlq: (clusterId: string, dlqTopic: string, limit = 50, fromOffset?: number) => {
    const params = new URLSearchParams({ limit: String(limit) });
    if (fromOffset != null) params.set("fromOffset", String(fromOffset));
    return http<DlqPage>(
      `/api/clusters/${clusterId}/dlq/topics/${encodeURIComponent(dlqTopic)}/messages?${params.toString()}`,
    );
  },
  reprocess: (
    clusterId: string,
    dlqTopic: string,
    targets: { partition: number; offset: number }[],
    mode: ReprocessJob["mode"] = "GROUP",
    notes?: string,
  ) =>
    http<ReprocessJob>(`/api/clusters/${clusterId}/dlq/topics/${encodeURIComponent(dlqTopic)}/reprocess`, {
      method: "POST",
      body: JSON.stringify({ targets, mode, notes }),
    }),
  reprocessHistory: (clusterId: string) =>
    http<ReprocessJob[]>(`/api/clusters/${clusterId}/dlq/reprocess-history`),
  publish: (
    clusterId: string,
    body: { topic: string; key: string | null; value: string; headers?: Record<string, string> },
  ) =>
    http<{ partition: number; offset: number; timestampMs: number }>(
      `/api/clusters/${clusterId}/publish`,
      { method: "POST", body: JSON.stringify(body) },
    ),
};

export type BrokerInfo = {
  id: number;
  host: string;
  port: number;
  rack: string | null;
  leaderPartitions: number;
  totalReplicas: number;
  isController: boolean;
};

export const fetchBrokers = (clusterId: string) =>
  fetch(`/api/clusters/${clusterId}/brokers`).then(async (r) => {
    if (!r.ok) throw new Error(`${r.status} ${r.statusText}: ${await r.text()}`);
    return (await r.json()) as BrokerInfo[];
  });

export type TopicConfigEntry = {
  name: string;
  value: string | null;
  source: string;
  isDefault: boolean;
  readOnly: boolean;
  sensitive: boolean;
  documentation: string | null;
};

export const fetchTopicConfigs = (clusterId: string, topic: string) =>
  fetch(`/api/clusters/${clusterId}/topics/${encodeURIComponent(topic)}/configs`)
    .then(async (r) => {
      if (!r.ok) throw new Error(`${r.status} ${r.statusText}: ${await r.text()}`);
      return (await r.json()) as TopicConfigEntry[];
    });

async function httpVoid(input: string, init?: RequestInit): Promise<void> {
  const res = await fetch(input, { ...init, headers: { "Content-Type": "application/json", ...(init?.headers ?? {}) } });
  if (!res.ok) throw new Error(`${res.status} ${res.statusText}: ${await res.text()}`);
}

export const createTopic = (
  clusterId: string,
  body: { name: string; numPartitions: number; replicationFactor: number; configs?: Record<string, string> },
) => httpVoid(`/api/clusters/${clusterId}/topics`, { method: "POST", body: JSON.stringify(body) });

export const deleteTopic = (clusterId: string, name: string) =>
  httpVoid(`/api/clusters/${clusterId}/topics/${encodeURIComponent(name)}`, { method: "DELETE" });

export const addPartitions = (clusterId: string, name: string, totalPartitions: number) =>
  httpVoid(`/api/clusters/${clusterId}/topics/${encodeURIComponent(name)}/partitions`, {
    method: "POST",
    body: JSON.stringify({ totalPartitions }),
  });

export const alterTopicConfigs = (clusterId: string, name: string, entries: Record<string, string | null>) =>
  httpVoid(`/api/clusters/${clusterId}/topics/${encodeURIComponent(name)}/configs`, {
    method: "PUT",
    body: JSON.stringify({ entries }),
  });

// ---- Kafka Connect ----
export type ConnectorType = "SOURCE" | "SINK" | "UNKNOWN";
export type TaskSummary = {
  id: number;
  state: string;
  workerId: string | null;
  trace: string | null;
};
export type ConnectorSummary = {
  name: string;
  type: ConnectorType;
  connectorClass: string | null;
  state: string;
  workerId: string | null;
  tasks: TaskSummary[];
  topics: string[];
  failedTasks: number;
  runningTasks: number;
};
export type ConnectorDetail = {
  summary: ConnectorSummary;
  config: Record<string, string>;
};

export const fetchConnectors = (clusterId: string) =>
  fetch(`/api/clusters/${clusterId}/connect/connectors`).then(async (r) => {
    if (!r.ok) throw new Error(`${r.status} ${r.statusText}: ${await r.text()}`);
    return (await r.json()) as ConnectorSummary[];
  });

export const fetchConnector = (clusterId: string, name: string) =>
  fetch(`/api/clusters/${clusterId}/connect/connectors/${encodeURIComponent(name)}`).then(async (r) => {
    if (!r.ok) throw new Error(`${r.status} ${r.statusText}: ${await r.text()}`);
    return (await r.json()) as ConnectorDetail;
  });

export const connectorAction = {
  restart: (clusterId: string, name: string, onlyFailed = false) =>
    httpVoid(`/api/clusters/${clusterId}/connect/connectors/${encodeURIComponent(name)}/restart?onlyFailed=${onlyFailed}`, { method: "POST" }),
  restartTask: (clusterId: string, name: string, taskId: number) =>
    httpVoid(`/api/clusters/${clusterId}/connect/connectors/${encodeURIComponent(name)}/tasks/${taskId}/restart`, { method: "POST" }),
  pause: (clusterId: string, name: string) =>
    httpVoid(`/api/clusters/${clusterId}/connect/connectors/${encodeURIComponent(name)}/pause`, { method: "PUT" }),
  resume: (clusterId: string, name: string) =>
    httpVoid(`/api/clusters/${clusterId}/connect/connectors/${encodeURIComponent(name)}/resume`, { method: "PUT" }),
  delete: (clusterId: string, name: string) =>
    httpVoid(`/api/clusters/${clusterId}/connect/connectors/${encodeURIComponent(name)}`, { method: "DELETE" }),
};

export type TopicStats = {
  name: string;
  partitions: number;
  totalMessages: number;
  consumingGroups: number;
  totalLag: number;
  internal: boolean;
};
export type GroupStats = {
  groupId: string;
  state: string;
  members: number;
  topicCount: number;
  totalLag: number;
};
export type ClusterDashboard = {
  clusterId: string;
  brokerCount: number;
  brokerVersion: string | null;
  supports: Record<string, boolean>;
  topicCount: number;
  internalTopicCount: number;
  totalMessages: number;
  consumerGroupCount: number;
  totalLag: number;
  topicStats: TopicStats[];
  groupStats: GroupStats[];
};

export type SamplePoint = { timestamp: string; endOffset: number; lagByGroup: Record<string, number> };
export type GroupMetric = {
  groupId: string;
  currentLag: number;
  consumeRatePerSec: number | null;
  drainEtaSeconds: number | null;
};
export type PartitionPoint = {
  partition: number;
  beginningOffset: number;
  endOffset: number;
  messages: number;
};
export type TopicStatsPayload = {
  clusterId: string;
  topic: string;
  sampledAt: string;
  partitions: number;
  currentEndOffset: number;
  currentBeginningOffset: number;
  availableMessages: number;
  totalLag: number;
  productionRatePerSec: number | null;
  windowSeconds: number | null;
  samplesAvailable: number;
  groups: GroupMetric[];
  partitionDistribution: PartitionPoint[];
  series: SamplePoint[];
};

export const fetchTopicStats = (clusterId: string, topic: string) =>
  fetch(`/api/clusters/${clusterId}/topics/${encodeURIComponent(topic)}/stats`)
    .then(async (r) => {
      if (!r.ok) throw new Error(`${r.status} ${r.statusText}: ${await r.text()}`);
      return (await r.json()) as TopicStatsPayload;
    });

export const fetchDashboard = (clusterId: string, includeInternal = false) =>
  fetch(`/api/clusters/${clusterId}/dashboard?includeInternal=${includeInternal}`)
    .then(async (r) => {
      if (!r.ok) throw new Error(`${r.status} ${r.statusText}: ${await r.text()}`);
      return (await r.json()) as ClusterDashboard;
    });

export type BrowseMode = "LATEST" | "EARLIEST" | "FROM_OFFSET" | "FROM_TIMESTAMP" | "RANGE";

export type BrowseRequest = {
  mode: BrowseMode;
  partitions?: number[];
  pageSize?: number;
  fromOffset?: Record<number, number>;
  fromTimestamp?: string;
  toTimestamp?: string;
  keyContains?: string;
  valueContains?: string;
  timeoutSeconds?: number;
};

export type BrowsePage = {
  messages: Message[];
  nextCursor: Record<number, number> | null;
  hasMore: boolean;
  partitionsScanned: number[];
  durationMs: number;
};

export const browseTopic = (clusterId: string, topic: string, body: BrowseRequest) =>
  fetch(`/api/clusters/${clusterId}/topics/${encodeURIComponent(topic)}/browse`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  }).then(async (res) => {
    if (!res.ok) throw new Error(`${res.status} ${res.statusText}: ${await res.text()}`);
    return (await res.json()) as BrowsePage;
  });

export type SearchRequest = {
  topics: string[];
  partitions?: number[];
  from?: string;
  to?: string;
  fromOffset?: number;
  toOffset?: number;
  keyContains?: string;
  valueContains?: string;
  headerEquals?: Record<string, string>;
  jsonFieldEquals?: Record<string, string>;
  jsonFieldContains?: Record<string, string>;
  maxResults?: number;
  maxScanMessages?: number;
  timeoutSeconds?: number;
};
