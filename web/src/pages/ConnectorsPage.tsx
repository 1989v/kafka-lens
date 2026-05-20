import { useEffect, useMemo, useState } from "react";
import {
  ConnectorDetail,
  ConnectorSummary,
  api,
  ClusterDetail,
  connectorAction,
  fetchConnector,
  fetchConnectors,
} from "../api";

export default function ConnectorsPage({ clusterId }: { clusterId: string }) {
  const [cluster, setCluster] = useState<ClusterDetail | null>(null);
  const [connectors, setConnectors] = useState<ConnectorSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState("");
  const [selected, setSelected] = useState<string | null>(null);

  useEffect(() => { api.getCluster(clusterId).then(setCluster); }, [clusterId]);

  const load = async () => {
    setLoading(true); setError(null);
    try { setConnectors(await fetchConnectors(clusterId)); }
    catch (e: any) { setError(e.message ?? String(e)); }
    finally { setLoading(false); }
  };
  useEffect(() => { if (cluster?.connectConfigured) void load(); }, [clusterId, cluster?.connectConfigured]);

  const visible = useMemo(
    () => connectors.filter((c) => c.name.toLowerCase().includes(filter.toLowerCase())),
    [connectors, filter],
  );

  if (!cluster) return <div className="empty">Loading…</div>;

  if (!cluster.connectConfigured) {
    return (
      <>
        <div className="breadcrumbs"><span className="crumb-current">Connectors</span></div>
        <div className="page">
          <div className="page-header"><h1>Connectors</h1></div>
          <div className="card"><div className="card-body">
            <p>Kafka Connect endpoint is not configured for this cluster.</p>
            <p className="muted" style={{ fontSize: 12 }}>
              Set <code>CLUSTERS_0_CONNECTURL</code> (env) or <code>clusters[0].connectUrl</code> in YAML
              to the Connect REST endpoint (typically <code>http://&lt;host&gt;:8083</code>) and restart.
            </p>
          </div></div>
        </div>
      </>
    );
  }

  return (
    <>
      <div className="breadcrumbs"><span className="crumb-current">Connectors</span></div>
      <div className="page">
        <div className="page-header">
          <h1>Connectors</h1>
          <div className="row">
            <span className="meta">{visible.length} of {connectors.length}</span>
            <button className="secondary" onClick={load} disabled={loading} type="button">
              {loading ? "Loading…" : "Refresh"}
            </button>
          </div>
        </div>

        {error && <div className="tag danger" style={{ padding: "6px 10px", display: "inline-block", marginBottom: 12 }}>{error}</div>}

        <div className="kl-table-wrap">
          <div className="kl-table-toolbar">
            <input type="search" placeholder="Filter connector name…" value={filter} onChange={(e) => setFilter(e.target.value)} />
          </div>
          <table className="kl-table">
            <thead>
              <tr>
                <th>Name</th>
                <th style={{ width: 80 }}>Type</th>
                <th style={{ width: 110 }}>State</th>
                <th style={{ width: 120 }} className="numeric">Tasks (run / fail)</th>
                <th>Class</th>
              </tr>
            </thead>
            <tbody>
              {visible.map((c) => (
                <tr key={c.name} className="clickable" onClick={() => setSelected(c.name)}>
                  <td className="mono">{c.name}</td>
                  <td><span className={`tag ${c.type === "SOURCE" ? "accent" : c.type === "SINK" ? "success" : ""}`}>{c.type}</span></td>
                  <td>
                    <span className={`tag ${c.state === "RUNNING" ? "success" : c.state === "PAUSED" ? "warn" : c.state === "FAILED" ? "danger" : ""}`}>{c.state}</span>
                  </td>
                  <td className="numeric">
                    {c.runningTasks} / {c.failedTasks > 0 ? <span className="tag danger">{c.failedTasks}</span> : 0}
                  </td>
                  <td className="mono muted" style={{ fontSize: 12 }}>{c.connectorClass?.split(".").pop() ?? "—"}</td>
                </tr>
              ))}
              {visible.length === 0 && !loading && (
                <tr><td colSpan={5} className="empty">{filter ? "No matches" : "No connectors"}</td></tr>
              )}
            </tbody>
          </table>
        </div>

        {selected && (
          <ConnectorDetailModal
            clusterId={clusterId}
            name={selected}
            onClose={() => setSelected(null)}
            onChanged={() => { load(); }}
          />
        )}
      </div>
    </>
  );
}

function ConnectorDetailModal({
  clusterId,
  name,
  onClose,
  onChanged,
}: {
  clusterId: string;
  name: string;
  onClose: () => void;
  onChanged: () => void;
}) {
  const [detail, setDetail] = useState<ConnectorDetail | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [confirmDelete, setConfirmDelete] = useState("");

  const load = async () => {
    setError(null);
    try { setDetail(await fetchConnector(clusterId, name)); }
    catch (e: any) { setError(e.message ?? String(e)); }
  };
  useEffect(() => { void load(); /* eslint-disable-line */ }, [clusterId, name]);

  const act = async (fn: () => Promise<void>) => {
    setBusy(true); setError(null);
    try { await fn(); await load(); onChanged(); }
    catch (e: any) { setError(e.message ?? String(e)); }
    finally { setBusy(false); }
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" style={{ width: "min(960px, 100%)" }} onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <div className="mono">{name}</div>
          <button className="secondary icon" onClick={onClose} type="button">✕</button>
        </div>
        <div className="modal-body">
          {!detail && !error && <div className="empty">Loading…</div>}
          {error && <div className="tag danger" style={{ padding: "6px 10px", display: "inline-block", marginBottom: 12 }}>{error}</div>}
          {detail && (
            <>
              <dl className="kv" style={{ marginBottom: 16 }}>
                <dt>State</dt>
                <dd><span className={`tag ${detail.summary.state === "RUNNING" ? "success" : detail.summary.state === "PAUSED" ? "warn" : "danger"}`}>{detail.summary.state}</span></dd>
                <dt>Type</dt><dd>{detail.summary.type}</dd>
                <dt>Class</dt><dd className="mono">{detail.summary.connectorClass ?? "—"}</dd>
                <dt>Worker</dt><dd className="mono">{detail.summary.workerId ?? "—"}</dd>
                <dt>Topics</dt>
                <dd>{detail.summary.topics.length > 0 ? detail.summary.topics.map((t) => <code key={t} style={{ marginRight: 8 }}>{t}</code>) : <span className="muted">—</span>}</dd>
              </dl>

              <h3 style={{ fontSize: 12, color: "var(--fg-muted)", textTransform: "uppercase", letterSpacing: ".06em", margin: "4px 0" }}>Tasks ({detail.summary.tasks.length})</h3>
              <table className="kl-table" style={{ marginBottom: 16 }}>
                <thead>
                  <tr>
                    <th style={{ width: 40 }} className="numeric">#</th>
                    <th style={{ width: 100 }}>State</th>
                    <th>Worker</th>
                    <th style={{ width: 100 }}></th>
                  </tr>
                </thead>
                <tbody>
                  {detail.summary.tasks.map((t) => (
                    <tr key={t.id}>
                      <td className="numeric">{t.id}</td>
                      <td><span className={`tag ${t.state === "RUNNING" ? "success" : t.state === "FAILED" ? "danger" : "warn"}`}>{t.state}</span></td>
                      <td className="mono muted">{t.workerId ?? "—"}</td>
                      <td>
                        <button className="secondary" disabled={busy} onClick={() => act(() => connectorAction.restartTask(clusterId, name, t.id))} type="button">Restart</button>
                      </td>
                    </tr>
                  ))}
                  {detail.summary.tasks.length === 0 && <tr><td colSpan={4} className="empty">No tasks</td></tr>}
                </tbody>
              </table>

              <h3 style={{ fontSize: 12, color: "var(--fg-muted)", textTransform: "uppercase", letterSpacing: ".06em", margin: "12px 0 4px" }}>Config</h3>
              <pre>{JSON.stringify(detail.config, null, 2)}</pre>

              <h3 style={{ fontSize: 12, color: "var(--fg-muted)", textTransform: "uppercase", letterSpacing: ".06em", margin: "12px 0 4px" }}>Danger zone</h3>
              <div className="row" style={{ gap: 8 }}>
                <input
                  type="text"
                  placeholder={`type "${name}" to enable delete`}
                  value={confirmDelete}
                  onChange={(e) => setConfirmDelete(e.target.value)}
                  style={{ flex: 1, fontFamily: "var(--code-font)" }}
                />
                <button
                  className="danger"
                  disabled={busy || confirmDelete !== name}
                  onClick={() => act(() => connectorAction.delete(clusterId, name).then(onClose))}
                  type="button"
                >Delete connector</button>
              </div>
            </>
          )}
        </div>
        <div className="modal-footer">
          {detail && (
            <>
              <button className="secondary" onClick={() => act(() => connectorAction.restart(clusterId, name, false))} disabled={busy} type="button">Restart</button>
              <button className="secondary" onClick={() => act(() => connectorAction.restart(clusterId, name, true))} disabled={busy} type="button">Restart failed</button>
              {detail.summary.state === "PAUSED" ? (
                <button onClick={() => act(() => connectorAction.resume(clusterId, name))} disabled={busy} type="button">Resume</button>
              ) : (
                <button className="secondary" onClick={() => act(() => connectorAction.pause(clusterId, name))} disabled={busy} type="button">Pause</button>
              )}
            </>
          )}
          <button className="secondary" onClick={onClose} type="button">Close</button>
        </div>
      </div>
    </div>
  );
}
