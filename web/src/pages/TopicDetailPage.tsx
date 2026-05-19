import { useEffect, useMemo, useState } from "react";
import { Link, NavLink, Navigate, Route, Routes, useParams } from "react-router-dom";
import { Topic, api } from "../api";
import TopicMessagesTab from "./TopicMessagesTab";
import TopicStatsTab from "./TopicStatsTab";

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
            <Route path="overview" element={<OverviewTab topic={topic} />} />
            <Route path="*" element={<Navigate to="messages" replace />} />
          </Routes>
        )}
      </div>
    </>
  );
}

function OverviewTab({ topic }: { topic: Topic | null }) {
  const partitionDistribution = useMemo(() => {
    if (!topic) return null;
    return topic.partitions.map((p) => ({ ...p, count: Math.max(p.endOffset - p.beginningOffset, 0) }));
  }, [topic]);

  if (!topic) return <div className="empty">Topic not found.</div>;
  return (
    <div className="card">
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
  );
}
