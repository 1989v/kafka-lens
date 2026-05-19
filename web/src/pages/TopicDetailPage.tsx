import { useEffect, useMemo, useState } from "react";
import { Link, NavLink, Navigate, Route, Routes, useParams } from "react-router-dom";
import { Topic, TopicConfigEntry, api, fetchTopicConfigs } from "../api";
import TopicMessagesTab from "./TopicMessagesTab";
import TopicStatsTab from "./TopicStatsTab";
import TopicManageActions from "./TopicManageActions";

export default function TopicDetailPage({ clusterId }: { clusterId: string }) {
  const { topicName: rawTopicName = "" } = useParams();
  const topicName = decodeURIComponent(rawTopicName);
  const [topic, setTopic] = useState<Topic | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!clusterId || !topicName) return;
    setLoading(true);
    fetch(`/api/clusters/${clusterId}/topics/${encodeURIComponent(topicName)}`)
      .then((r) => (r.ok ? r.json() : null))
      .then(setTopic)
      .finally(() => setLoading(false));
  }, [clusterId, topicName]);

  return (
    <>
      <div className="breadcrumbs">
        <Link to={`/c/${clusterId}/topics`}>Topics</Link>
        <span className="sep">/</span>
        <span className="crumb-current mono">{topicName}</span>
      </div>
      <div className="page">
        <div className="page-header">
          <div>
            <h1 className="mono">{topicName}</h1>
            {topic && (
              <span className="meta">
                {topic.partitions.length} partition{topic.partitions.length === 1 ? "" : "s"}
                {" · "}
                {topic.totalMessages.toLocaleString()} messages
                {topic.internal && <> · <span className="tag warn">internal</span></>}
              </span>
            )}
          </div>
          <TopicManageActions clusterId={clusterId} topic={topic} topicName={topicName} />
        </div>

        <div className="tabs">
          <NavLink end to={`/c/${clusterId}/topics/${encodeURIComponent(topicName)}/messages`}>Messages</NavLink>
          <NavLink end to={`/c/${clusterId}/topics/${encodeURIComponent(topicName)}/stats`}>Stats</NavLink>
          <NavLink end to={`/c/${clusterId}/topics/${encodeURIComponent(topicName)}/overview`}>Overview</NavLink>
        </div>

        {loading && !topic ? (
          <div className="empty">Loading…</div>
        ) : (
          <Routes>
            <Route path="messages" element={<TopicMessagesTab clusterId={clusterId} topicName={topicName} topic={topic} />} />
            <Route path="stats" element={<TopicStatsTab clusterId={clusterId} topicName={topicName} />} />
            <Route path="overview" element={<OverviewTab clusterId={clusterId} topicName={topicName} topic={topic} />} />
            <Route path="*" element={<Navigate to="messages" replace />} />
          </Routes>
        )}
      </div>
    </>
  );
}

function OverviewTab({
  clusterId,
  topicName,
  topic,
}: {
  clusterId: string;
  topicName: string;
  topic: Topic | null;
}) {
  const partitionDistribution = useMemo(() => {
    if (!topic) return null;
    return topic.partitions.map((p) => ({ ...p, count: Math.max(p.endOffset - p.beginningOffset, 0) }));
  }, [topic]);

  const [configs, setConfigs] = useState<TopicConfigEntry[] | null>(null);
  const [configError, setConfigError] = useState<string | null>(null);
  const [showAllConfigs, setShowAllConfigs] = useState(false);

  useEffect(() => {
    if (!topicName) return;
    fetchTopicConfigs(clusterId, topicName)
      .then(setConfigs)
      .catch((e) => setConfigError(e.message ?? String(e)));
  }, [clusterId, topicName]);

  const visibleConfigs = useMemo(
    () => (configs ?? []).filter((c) => showAllConfigs || !c.isDefault),
    [configs, showAllConfigs],
  );

  if (!topic) return <div className="empty">Topic not found.</div>;
  return (
    <>
      <div className="card" style={{ marginBottom: 16 }}>
        <div className="card-header">Partitions</div>
        <table className="kl-table">
          <thead>
            <tr>
              <th>Partition</th>
              <th>Leader</th>
              <th>Replicas</th>
              <th>ISR</th>
              <th className="numeric">Beginning offset</th>
              <th className="numeric">End offset</th>
              <th className="numeric">Messages</th>
            </tr>
          </thead>
          <tbody>
            {partitionDistribution?.map((p) => (
              <tr key={p.partition}>
                <td className="numeric">{p.partition}</td>
                <td>{p.leader ?? "—"}</td>
                <td className="mono">[{p.replicas.join(", ")}]</td>
                <td className="mono">[{p.inSyncReplicas.join(", ")}]</td>
                <td className="numeric">{p.beginningOffset.toLocaleString()}</td>
                <td className="numeric">{p.endOffset.toLocaleString()}</td>
                <td className="numeric">{p.count.toLocaleString()}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="card">
        <div className="card-header">
          <span>Configurations {configs && <span className="muted" style={{ fontSize: 11 }}>({visibleConfigs.length} {showAllConfigs ? "all" : "overridden"} / {configs.length} total)</span>}</span>
          <label className="row" style={{ gap: 6, fontSize: 12 }}>
            <input type="checkbox" checked={showAllConfigs} onChange={(e) => setShowAllConfigs(e.target.checked)} />
            <span className="muted">show defaults</span>
          </label>
        </div>
        {configError && (
          <div style={{ padding: 12 }} className="tag danger">{configError}</div>
        )}
        {!configs && !configError && <div className="empty">Loading…</div>}
        {configs && (
          <table className="kl-table">
            <thead>
              <tr>
                <th>Name</th>
                <th>Value</th>
                <th style={{ width: 140 }}>Source</th>
                <th style={{ width: 100 }}>Flags</th>
              </tr>
            </thead>
            <tbody>
              {visibleConfigs.map((c) => (
                <tr key={c.name}>
                  <td className="mono" style={{ wordBreak: "break-all" }}>{c.name}</td>
                  <td className="mono" style={{ wordBreak: "break-all" }}>
                    {c.sensitive ? <span className="muted">(sensitive)</span> : (c.value ?? <span className="muted">—</span>)}
                  </td>
                  <td><span className={`tag ${c.isDefault ? "" : "accent"}`}>{c.source}</span></td>
                  <td>
                    {c.readOnly && <span className="tag warn" style={{ marginRight: 4 }}>read-only</span>}
                    {c.sensitive && <span className="tag danger">sensitive</span>}
                  </td>
                </tr>
              ))}
              {visibleConfigs.length === 0 && (
                <tr><td colSpan={4} className="empty">No overridden configs. Tick "show defaults" to see broker defaults.</td></tr>
              )}
            </tbody>
          </table>
        )}
      </div>
    </>
  );
}
