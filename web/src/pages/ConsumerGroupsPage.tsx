import { useEffect, useMemo, useState } from "react";

type ConsumerGroup = {
  groupId: string;
  state: string;
  members: { memberId: string; clientId: string; host: string }[];
  offsets: { topic: string; partition: number; currentOffset: number; endOffset: number; lag: number }[];
  totalLag: number;
};

export default function ConsumerGroupsPage({ clusterId }: { clusterId: string }) {
  const [groups, setGroups] = useState<ConsumerGroup[]>([]);
  const [filter, setFilter] = useState("");
  const [loading, setLoading] = useState(false);
  const [expanded, setExpanded] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    fetch(`/api/clusters/${clusterId}/consumer-groups`)
      .then((r) => r.json())
      .then(setGroups)
      .finally(() => setLoading(false));
  }, [clusterId]);

  const filtered = useMemo(
    () => groups.filter((g) => g.groupId.toLowerCase().includes(filter.toLowerCase())),
    [groups, filter],
  );

  return (
    <>
      <div className="breadcrumbs">
        <span className="crumb-current">Consumer Groups</span>
      </div>
      <div className="page">
        <div className="page-header">
          <h1>Consumer Groups</h1>
          <span className="meta">{filtered.length} of {groups.length}</span>
        </div>

        <div className="kl-table-wrap">
          <div className="kl-table-toolbar">
            <input
              type="search"
              placeholder="Filter group id…"
              value={filter}
              onChange={(e) => setFilter(e.target.value)}
            />
          </div>
          {loading ? (
            <div className="empty">Loading…</div>
          ) : (
            <table className="kl-table">
              <thead>
                <tr>
                  <th>Group ID</th>
                  <th style={{ width: 100 }}>State</th>
                  <th style={{ width: 100 }} className="numeric">Members</th>
                  <th style={{ width: 120 }} className="numeric">Total lag</th>
                  <th style={{ width: 60 }}></th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((g) => (
                  <>
                    <tr key={g.groupId} className="clickable" onClick={() => setExpanded(expanded === g.groupId ? null : g.groupId)}>
                      <td className="mono">{g.groupId}</td>
                      <td><span className={`tag ${g.state === "Stable" ? "success" : "warn"}`}>{g.state}</span></td>
                      <td className="numeric">{g.members.length}</td>
                      <td className="numeric">{g.totalLag.toLocaleString()}</td>
                      <td>{expanded === g.groupId ? "▾" : "▸"}</td>
                    </tr>
                    {expanded === g.groupId && (
                      <tr className="expanded">
                        <td colSpan={5}>
                          <div className="row spread" style={{ marginBottom: 8 }}>
                            <strong>Offsets per partition</strong>
                            <span className="muted">{g.offsets.length} assignments</span>
                          </div>
                          <table className="kl-table" style={{ background: "transparent" }}>
                            <thead>
                              <tr>
                                <th>Topic</th>
                                <th style={{ width: 80 }} className="numeric">Partition</th>
                                <th style={{ width: 120 }} className="numeric">Current</th>
                                <th style={{ width: 120 }} className="numeric">End</th>
                                <th style={{ width: 100 }} className="numeric">Lag</th>
                              </tr>
                            </thead>
                            <tbody>
                              {g.offsets.map((o) => (
                                <tr key={`${o.topic}-${o.partition}`}>
                                  <td className="mono">{o.topic}</td>
                                  <td className="numeric">{o.partition}</td>
                                  <td className="numeric">{o.currentOffset.toLocaleString()}</td>
                                  <td className="numeric">{o.endOffset.toLocaleString()}</td>
                                  <td className="numeric">{o.lag > 0 ? <span className="tag warn">{o.lag.toLocaleString()}</span> : <span className="muted">0</span>}</td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        </td>
                      </tr>
                    )}
                  </>
                ))}
                {filtered.length === 0 && (
                  <tr><td colSpan={5} className="empty">No groups</td></tr>
                )}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </>
  );
}
