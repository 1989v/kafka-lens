import { useEffect, useState } from "react";
import { BrokerInfo, fetchBrokers } from "../api";

export default function BrokersPage({ clusterId }: { clusterId: string }) {
  const [brokers, setBrokers] = useState<BrokerInfo[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = async () => {
    setLoading(true); setError(null);
    try { setBrokers(await fetchBrokers(clusterId)); }
    catch (e: any) { setError(e.message ?? String(e)); }
    finally { setLoading(false); }
  };
  useEffect(() => { void load(); /* eslint-disable-line */ }, [clusterId]);

  return (
    <>
      <div className="breadcrumbs"><span className="crumb-current">Brokers</span></div>
      <div className="page">
        <div className="page-header">
          <h1>Brokers</h1>
          <div className="row">
            <span className="muted" style={{ fontSize: 12 }}>{brokers.length} node{brokers.length === 1 ? "" : "s"}</span>
            <button className="secondary" onClick={load} disabled={loading} type="button">
              {loading ? "Loading…" : "Refresh"}
            </button>
          </div>
        </div>

        {error && <div className="tag danger" style={{ padding: "6px 10px", display: "inline-block", marginBottom: 12 }}>{error}</div>}

        <div className="kl-table-wrap">
          {loading && brokers.length === 0 ? <div className="empty">Loading…</div> : (
            <table className="kl-table">
              <thead>
                <tr>
                  <th style={{ width: 80 }} className="numeric">ID</th>
                  <th>Host : Port</th>
                  <th style={{ width: 100 }}>Rack</th>
                  <th style={{ width: 100 }}>Role</th>
                  <th style={{ width: 140 }} className="numeric">Leader partitions</th>
                  <th style={{ width: 140 }} className="numeric">Total replicas</th>
                </tr>
              </thead>
              <tbody>
                {brokers.map((b) => (
                  <tr key={b.id}>
                    <td className="numeric mono">{b.id}</td>
                    <td className="mono">{b.host}:{b.port}</td>
                    <td className="muted">{b.rack ?? "—"}</td>
                    <td>
                      {b.isController ? <span className="tag accent">controller</span> : <span className="tag">broker</span>}
                    </td>
                    <td className="numeric">{b.leaderPartitions.toLocaleString()}</td>
                    <td className="numeric">{b.totalReplicas.toLocaleString()}</td>
                  </tr>
                ))}
                {brokers.length === 0 && !loading && (
                  <tr><td colSpan={6} className="empty">No brokers</td></tr>
                )}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </>
  );
}
