import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Topic, api } from "../api";

export default function TopicsPage({ clusterId }: { clusterId: string }) {
  const [topics, setTopics] = useState<Topic[]>([]);
  const [includeInternal, setIncludeInternal] = useState(false);
  const [filter, setFilter] = useState("");
  const [loading, setLoading] = useState(false);
  const [sort, setSort] = useState<{ key: keyof Topic | "totalMessages" | "partitionCount"; dir: 1 | -1 }>({
    key: "name",
    dir: 1,
  });
  const navigate = useNavigate();

  useEffect(() => {
    if (!clusterId) return;
    setLoading(true);
    api.listTopics(clusterId, includeInternal)
      .then(setTopics)
      .finally(() => setLoading(false));
  }, [clusterId, includeInternal]);

  const visible = useMemo(() => {
    const filtered = topics.filter((t) => t.name.toLowerCase().includes(filter.toLowerCase()));
    return filtered.sort((a, b) => {
      const av = readField(a, sort.key);
      const bv = readField(b, sort.key);
      if (av < bv) return -1 * sort.dir;
      if (av > bv) return 1 * sort.dir;
      return 0;
    });
  }, [topics, filter, sort]);

  const onSort = (key: typeof sort.key) =>
    setSort((s) => ({ key, dir: s.key === key ? (s.dir === 1 ? -1 : 1) : 1 }));

  return (
    <>
      <div className="breadcrumbs">
        <span className="crumb-current">Topics</span>
      </div>
      <div className="page">
        <div className="page-header">
          <h1>Topics</h1>
          <span className="meta">{visible.length} of {topics.length}</span>
        </div>

        <div className="kl-table-wrap">
          <div className="kl-table-toolbar">
            <input
              type="search"
              placeholder="Filter topic name…"
              value={filter}
              onChange={(e) => setFilter(e.target.value)}
            />
            <label className="row" style={{ gap: 6, fontSize: 12 }}>
              <input type="checkbox" checked={includeInternal} onChange={(e) => setIncludeInternal(e.target.checked)} />
              <span className="muted">include internal</span>
            </label>
          </div>

          {loading ? (
            <div className="empty">Loading…</div>
          ) : (
            <table className="kl-table">
              <thead>
                <tr>
                  <Th label="Name" sortKey="name" current={sort} onSort={onSort} />
                  <Th label="Partitions" sortKey="partitionCount" current={sort} onSort={onSort} align="right" width={100} />
                  <Th label="Messages" sortKey="totalMessages" current={sort} onSort={onSort} align="right" width={140} />
                  <th style={{ width: 80 }}>Type</th>
                </tr>
              </thead>
              <tbody>
                {visible.map((t) => (
                  <tr
                    key={t.name}
                    className="clickable"
                    onClick={() => navigate(`/c/${clusterId}/topics/${encodeURIComponent(t.name)}/messages`)}
                  >
                    <td className="mono">{t.name}</td>
                    <td className="numeric">{t.partitions.length}</td>
                    <td className="numeric">{t.totalMessages.toLocaleString()}</td>
                    <td>
                      {t.internal ? <span className="tag warn">internal</span> : <span className="tag">topic</span>}
                    </td>
                  </tr>
                ))}
                {visible.length === 0 && (
                  <tr><td colSpan={4} className="empty">No topics match the filter.</td></tr>
                )}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </>
  );
}

function Th({
  label,
  sortKey,
  current,
  onSort,
  width,
  align,
}: {
  label: string;
  sortKey: any;
  current: { key: any; dir: 1 | -1 };
  onSort: (k: any) => void;
  width?: number;
  align?: "right";
}) {
  const isActive = current.key === sortKey;
  return (
    <th
      style={{ width, cursor: "pointer", textAlign: align ?? "left" }}
      className={align === "right" ? "numeric" : ""}
      onClick={() => onSort(sortKey)}
    >
      {label} {isActive ? (current.dir === 1 ? "▲" : "▼") : ""}
    </th>
  );
}

function readField(t: Topic, key: string): any {
  if (key === "partitionCount") return t.partitions.length;
  return (t as any)[key];
}
