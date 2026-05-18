import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Topic, api } from "../api";

export default function TopicsPage({ clusterId }: { clusterId: string }) {
  const [topics, setTopics] = useState<Topic[]>([]);
  const [includeInternal, setIncludeInternal] = useState(false);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!clusterId) return;
    setLoading(true);
    api.listTopics(clusterId, includeInternal)
      .then(setTopics)
      .finally(() => setLoading(false));
  }, [clusterId, includeInternal]);

  return (
    <>
      <div className="row spread" style={{ marginBottom: 12 }}>
        <h1>Topics</h1>
        <label className="row" style={{ gap: 6 }}>
          <input type="checkbox" checked={includeInternal} onChange={(e) => setIncludeInternal(e.target.checked)} />
          <span className="muted">include internal</span>
        </label>
      </div>
      {loading ? <div className="empty">Loading…</div> : (
        <table>
          <thead>
            <tr>
              <th>Name</th>
              <th style={{ width: 80 }}>Partitions</th>
              <th style={{ width: 120 }}>Messages</th>
              <th style={{ width: 60 }}>Internal</th>
              <th style={{ width: 80 }}></th>
            </tr>
          </thead>
          <tbody>
            {topics.map((t) => (
              <tr key={t.name}>
                <td><code>{t.name}</code></td>
                <td>{t.partitions.length}</td>
                <td>{t.totalMessages.toLocaleString()}</td>
                <td>{t.internal ? <span className="tag warn">internal</span> : ""}</td>
                <td>
                  <Link to={`/c/${clusterId}/search?topic=${encodeURIComponent(t.name)}`}>search →</Link>
                </td>
              </tr>
            ))}
            {topics.length === 0 && (
              <tr><td colSpan={5} className="empty">No topics</td></tr>
            )}
          </tbody>
        </table>
      )}
    </>
  );
}
