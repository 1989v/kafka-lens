import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { ClusterDashboard, fetchDashboard } from "../api";
import TopicPicker from "../components/TopicPicker";
import TopicStatsTab from "./TopicStatsTab";

type Tab = "topic" | "overview";

const REFRESH_OPTIONS = [
  { label: "off", value: 0 },
  { label: "5s", value: 5_000 },
  { label: "15s", value: 15_000 },
  { label: "60s", value: 60_000 },
];

export default function DashboardPage({ clusterId }: { clusterId: string }) {
  const [tab, setTab] = useState<Tab>("topic");
  const [topic, setTopic] = useState<string | null>(null);

  return (
    <>
      <div className="breadcrumbs">
        <span className="crumb-current">Dashboard</span>
      </div>
      <div className="page">
        <div className="page-header">
          <h1>Dashboard</h1>
        </div>

        <div className="tabs">
          <a className={tab === "topic" ? "active" : ""} onClick={() => setTab("topic")} style={{ cursor: "pointer" }}>Topic</a>
          <a className={tab === "overview" ? "active" : ""} onClick={() => setTab("overview")} style={{ cursor: "pointer" }}>Total Overview</a>
        </div>

        {tab === "topic" && <TopicTabContent clusterId={clusterId} topic={topic} onTopicChange={setTopic} />}
        {tab === "overview" && <TotalOverviewTab clusterId={clusterId} />}
      </div>
    </>
  );
}

function TopicTabContent({
  clusterId,
  topic,
  onTopicChange,
}: {
  clusterId: string;
  topic: string | null;
  onTopicChange: (t: string) => void;
}) {
  return (
    <>
      <div className="card" style={{ marginBottom: 16 }}>
        <div className="card-body">
          <div className="row" style={{ gap: 12, alignItems: "center" }}>
            <span className="muted" style={{ fontSize: 12, textTransform: "uppercase", letterSpacing: "0.05em" }}>Topic</span>
            <TopicPicker clusterId={clusterId} value={topic} onChange={onTopicChange} />
          </div>
        </div>
      </div>

      {topic ? (
        <TopicStatsTab clusterId={clusterId} topicName={topic} />
      ) : (
        <div className="empty">
          Select a topic above to see its lag, production rate, drain ETA, and per-partition distribution charts.
        </div>
      )}
    </>
  );
}

function TotalOverviewTab({ clusterId }: { clusterId: string }) {
  const [data, setData] = useState<ClusterDashboard | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [includeInternal, setIncludeInternal] = useState(false);
  const [refreshMs, setRefreshMs] = useState(0);
  const [lastFetched, setLastFetched] = useState<Date | null>(null);
  const [topicFilter, setTopicFilter] = useState("");
  const [groupFilter, setGroupFilter] = useState("");

  const load = async () => {
    setLoading(true); setError(null);
    try { setData(await fetchDashboard(clusterId, includeInternal)); setLastFetched(new Date()); }
    catch (e: any) { setError(e.message ?? String(e)); }
    finally { setLoading(false); }
  };

  useEffect(() => { void load(); /* eslint-disable-line react-hooks/exhaustive-deps */ }, [clusterId, includeInternal]);

  useEffect(() => {
    if (refreshMs === 0) return;
    const id = window.setInterval(load, refreshMs);
    return () => window.clearInterval(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [refreshMs, clusterId, includeInternal]);

  const visibleTopics = useMemo(
    () => (data?.topicStats ?? []).filter((t) => t.name.toLowerCase().includes(topicFilter.toLowerCase())),
    [data, topicFilter],
  );
  const visibleGroups = useMemo(
    () => (data?.groupStats ?? []).filter((g) => g.groupId.toLowerCase().includes(groupFilter.toLowerCase())),
    [data, groupFilter],
  );

  return (
    <>
      <div className="row spread" style={{ marginBottom: 12, flexWrap: "wrap", gap: 8 }}>
        <div className="row" style={{ fontSize: 12 }}>
          {data && (
            <>
              <span className="muted">
                {data.brokerCount} brokers · {data.topicCount.toLocaleString()} topics · {data.consumerGroupCount.toLocaleString()} groups
              </span>
              {lastFetched && <span className="muted">· last fetched {lastFetched.toLocaleTimeString()}</span>}
            </>
          )}
        </div>
        <div className="row" style={{ fontSize: 12 }}>
          <label className="row" style={{ gap: 6 }}>
            <input type="checkbox" checked={includeInternal} onChange={(e) => setIncludeInternal(e.target.checked)} />
            <span className="muted">include internal</span>
          </label>
          <span className="muted">refresh</span>
          <select value={refreshMs} onChange={(e) => setRefreshMs(Number(e.target.value))} style={{ fontSize: 12 }}>
            {REFRESH_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
          </select>
          <button className="secondary" onClick={load} disabled={loading} type="button">
            {loading ? "Loading…" : "Refresh"}
          </button>
        </div>
      </div>

      {error && <div className="tag danger" style={{ padding: "6px 10px", display: "inline-block", marginBottom: 12 }}>{error}</div>}
      {!data && loading && <div className="empty">Loading cluster overview…</div>}

      {data && (
        <>
          <div className="stat-grid" style={{ marginBottom: 16 }}>
            <Stat label="Brokers" value={data.brokerCount.toLocaleString()} hint={data.brokerVersion ?? undefined} />
            <Stat
              label="Topics"
              value={data.topicCount.toLocaleString()}
              hint={data.internalTopicCount > 0 ? `${data.internalTopicCount} internal hidden` : undefined}
            />
            <Stat label="Total messages" value={data.totalMessages.toLocaleString()} />
            <Stat label="Consumer groups" value={data.consumerGroupCount.toLocaleString()} />
            <Stat label="Total lag" value={data.totalLag.toLocaleString()} tone={data.totalLag > 0 ? "warn" : undefined} />
          </div>

          <div className="card" style={{ marginBottom: 16 }}>
            <div className="card-header">
              <span>Topics by lag</span>
              <input
                type="search"
                placeholder="Filter topic…"
                value={topicFilter}
                onChange={(e) => setTopicFilter(e.target.value)}
                style={{ maxWidth: 220 }}
              />
            </div>
            <table className="kl-table">
              <thead>
                <tr>
                  <th>Topic</th>
                  <th style={{ width: 90 }} className="numeric">Partitions</th>
                  <th style={{ width: 130 }} className="numeric">Messages</th>
                  <th style={{ width: 110 }} className="numeric">Groups</th>
                  <th style={{ width: 140 }} className="numeric">Total lag</th>
                </tr>
              </thead>
              <tbody>
                {visibleTopics.map((t) => (
                  <tr key={t.name} className="clickable">
                    <td className="mono">
                      <Link to={`/c/${clusterId}/topics/${encodeURIComponent(t.name)}/stats`}>{t.name}</Link>
                      {t.internal && <> <span className="tag warn" style={{ marginLeft: 6 }}>internal</span></>}
                    </td>
                    <td className="numeric">{t.partitions}</td>
                    <td className="numeric">{t.totalMessages.toLocaleString()}</td>
                    <td className="numeric">{t.consumingGroups}</td>
                    <td className="numeric">
                      {t.totalLag > 0 ? <span className="tag warn">{t.totalLag.toLocaleString()}</span> : <span className="muted">0</span>}
                    </td>
                  </tr>
                ))}
                {visibleTopics.length === 0 && (
                  <tr><td colSpan={5} className="empty">No topics match the filter.</td></tr>
                )}
              </tbody>
            </table>
          </div>

          <div className="card">
            <div className="card-header">
              <span>Consumer groups by lag</span>
              <input
                type="search"
                placeholder="Filter group…"
                value={groupFilter}
                onChange={(e) => setGroupFilter(e.target.value)}
                style={{ maxWidth: 220 }}
              />
            </div>
            <table className="kl-table">
              <thead>
                <tr>
                  <th>Group ID</th>
                  <th style={{ width: 100 }}>State</th>
                  <th style={{ width: 90 }} className="numeric">Members</th>
                  <th style={{ width: 90 }} className="numeric">Topics</th>
                  <th style={{ width: 140 }} className="numeric">Total lag</th>
                </tr>
              </thead>
              <tbody>
                {visibleGroups.map((g) => (
                  <tr key={g.groupId}>
                    <td className="mono">
                      <Link to={`/c/${clusterId}/consumer-groups?focus=${encodeURIComponent(g.groupId)}`}>{g.groupId}</Link>
                    </td>
                    <td><span className={`tag ${g.state === "Stable" ? "success" : g.state === "Empty" ? "warn" : ""}`}>{g.state}</span></td>
                    <td className="numeric">{g.members}</td>
                    <td className="numeric">{g.topicCount}</td>
                    <td className="numeric">
                      {g.totalLag > 0 ? <span className="tag warn">{g.totalLag.toLocaleString()}</span> : <span className="muted">0</span>}
                    </td>
                  </tr>
                ))}
                {visibleGroups.length === 0 && (
                  <tr><td colSpan={5} className="empty">No groups match the filter.</td></tr>
                )}
              </tbody>
            </table>
          </div>
        </>
      )}
    </>
  );
}

function Stat({ label, value, hint, tone }: { label: string; value: string; hint?: string; tone?: "warn" }) {
  return (
    <div className={`stat-card ${tone === "warn" ? "stat-warn" : ""}`}>
      <div className="stat-label">{label}</div>
      <div className="stat-value">{value}</div>
      {hint && <div className="stat-hint">{hint}</div>}
    </div>
  );
}
