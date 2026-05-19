import { useEffect, useMemo, useState } from "react";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { TopicStatsPayload, fetchTopicStats } from "../api";

const REFRESH_OPTIONS = [
  { label: "off", value: 0 },
  { label: "5s", value: 5_000 },
  { label: "15s", value: 15_000 },
  { label: "30s", value: 30_000 },
];

const COLORS = ["#58a6ff", "#3fb950", "#d29922", "#f85149", "#a371f7", "#79c0ff", "#56d364", "#e3b341"];

export default function TopicStatsTab({ clusterId, topicName }: { clusterId: string; topicName: string }) {
  const [data, setData] = useState<TopicStatsPayload | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [refreshMs, setRefreshMs] = useState(15_000);

  const load = async () => {
    setLoading(true); setError(null);
    try { setData(await fetchTopicStats(clusterId, topicName)); }
    catch (e: any) { setError(e.message ?? String(e)); }
    finally { setLoading(false); }
  };

  useEffect(() => { void load(); /* eslint-disable-line react-hooks/exhaustive-deps */ }, [clusterId, topicName]);

  useEffect(() => {
    if (refreshMs === 0) return;
    const id = window.setInterval(load, refreshMs);
    return () => window.clearInterval(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [refreshMs, clusterId, topicName]);

  const lagSeriesData = useMemo(() => {
    if (!data) return [];
    return data.series.map((s) => ({
      t: new Date(s.timestamp).toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit", second: "2-digit" }),
      endOffset: s.endOffset,
      ...s.lagByGroup,
    }));
  }, [data]);

  const groupKeys = useMemo(() => {
    if (!data) return [];
    return data.groups.slice(0, 8).map((g) => g.groupId);
  }, [data]);

  return (
    <div>
      <div className="row spread" style={{ marginBottom: 12 }}>
        <div className="row" style={{ fontSize: 12 }}>
          {data && (
            <span className="muted">
              {data.samplesAvailable} sample{data.samplesAvailable === 1 ? "" : "s"}
              {data.windowSeconds ? ` over ${data.windowSeconds}s` : ""}
              {" · "}sampled at {new Date(data.sampledAt).toLocaleTimeString()}
            </span>
          )}
        </div>
        <div className="row" style={{ fontSize: 12 }}>
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

      {data && (
        <>
          <div className="stat-grid">
            <Stat label="Total lag" value={data.totalLag.toLocaleString()} tone={data.totalLag > 0 ? "warn" : undefined} />
            <Stat
              label="Production rate"
              value={data.productionRatePerSec != null ? `${formatRate(data.productionRatePerSec)} msg/s` : "—"}
              hint={data.windowSeconds ? `over ${data.windowSeconds}s` : "needs ≥2 samples"}
            />
            <Stat
              label="Top group drain ETA"
              value={topDrainEta(data) ?? "—"}
              hint={data.groups.length > 0 ? data.groups[0].groupId : undefined}
            />
            <Stat label="Partitions" value={String(data.partitions)} />
            <Stat
              label="Available messages"
              value={data.availableMessages.toLocaleString()}
              hint={`end offset ${data.currentEndOffset.toLocaleString()}`}
            />
          </div>

          <div className="card" style={{ marginTop: 16 }}>
            <div className="card-header">Lag over time (per consumer group)</div>
            <div style={{ height: 280, padding: 12 }}>
              {lagSeriesData.length >= 2 ? (
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart data={lagSeriesData} margin={{ top: 6, right: 18, bottom: 0, left: 0 }}>
                    <CartesianGrid stroke="#21262d" strokeDasharray="3 3" />
                    <XAxis dataKey="t" stroke="#8b949e" tick={{ fontSize: 11 }} />
                    <YAxis stroke="#8b949e" tick={{ fontSize: 11 }} width={70} />
                    <Tooltip contentStyle={{ background: "#161b22", border: "1px solid #30363d", fontSize: 12 }} />
                    <Legend wrapperStyle={{ fontSize: 11 }} />
                    {groupKeys.map((g, i) => (
                      <Line
                        key={g}
                        type="monotone"
                        dataKey={g}
                        stroke={COLORS[i % COLORS.length]}
                        strokeWidth={1.5}
                        dot={false}
                        isAnimationActive={false}
                      />
                    ))}
                  </LineChart>
                </ResponsiveContainer>
              ) : (
                <div className="empty" style={{ padding: 30 }}>
                  Collecting samples (need at least 2). The collector polls every ~10s; the chart will appear shortly.
                </div>
              )}
            </div>
          </div>

          <div className="card" style={{ marginTop: 16 }}>
            <div className="card-header">Consumer groups (current lag · consume rate · drain ETA)</div>
            <table className="kl-table">
              <thead>
                <tr>
                  <th>Group ID</th>
                  <th style={{ width: 140 }} className="numeric">Current lag</th>
                  <th style={{ width: 140 }} className="numeric">Consume rate</th>
                  <th style={{ width: 160 }} className="numeric">Drain ETA</th>
                </tr>
              </thead>
              <tbody>
                {data.groups.map((g) => (
                  <tr key={g.groupId}>
                    <td className="mono">{g.groupId}</td>
                    <td className="numeric">
                      {g.currentLag > 0
                        ? <span className="tag warn">{g.currentLag.toLocaleString()}</span>
                        : <span className="muted">0</span>}
                    </td>
                    <td className="numeric">{g.consumeRatePerSec != null ? `${formatRate(g.consumeRatePerSec)} /s` : <span className="muted">—</span>}</td>
                    <td className="numeric">{g.drainEtaSeconds != null ? formatEta(g.drainEtaSeconds) : <span className="muted">—</span>}</td>
                  </tr>
                ))}
                {data.groups.length === 0 && (
                  <tr><td colSpan={4} className="empty">No consumer groups consuming this topic.</td></tr>
                )}
              </tbody>
            </table>
          </div>

          <div className="card" style={{ marginTop: 16 }}>
            <div className="card-header">Partition message distribution</div>
            <div style={{ height: Math.min(28 * data.partitionDistribution.length + 60, 360), padding: 12 }}>
              <ResponsiveContainer width="100%" height="100%">
                <BarChart
                  data={data.partitionDistribution}
                  layout="vertical"
                  margin={{ top: 6, right: 30, bottom: 0, left: 30 }}
                >
                  <CartesianGrid stroke="#21262d" strokeDasharray="3 3" horizontal={false} />
                  <XAxis type="number" stroke="#8b949e" tick={{ fontSize: 11 }} />
                  <YAxis type="category" dataKey="partition" stroke="#8b949e" tick={{ fontSize: 11 }} width={40} />
                  <Tooltip contentStyle={{ background: "#161b22", border: "1px solid #30363d", fontSize: 12 }} />
                  <Bar dataKey="messages" fill="#58a6ff" />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </div>
        </>
      )}
      {!data && loading && <div className="empty">Loading stats…</div>}
    </div>
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

function formatRate(n: number): string {
  if (n >= 10_000) return `${(n / 1000).toFixed(1)}k`;
  if (n >= 100) return n.toFixed(0);
  if (n >= 10) return n.toFixed(1);
  return n.toFixed(2);
}

function formatEta(seconds: number): string {
  if (seconds < 60) return `${seconds}s`;
  if (seconds < 3600) return `${Math.round(seconds / 60)}m`;
  if (seconds < 86_400) return `${(seconds / 3600).toFixed(1)}h`;
  return `${(seconds / 86_400).toFixed(1)}d`;
}

function topDrainEta(data: TopicStatsPayload): string | null {
  const top = data.groups[0];
  if (!top || top.drainEtaSeconds == null) return null;
  return formatEta(top.drainEtaSeconds);
}
