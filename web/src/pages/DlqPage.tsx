import { useEffect, useState } from "react";
import { DlqMapping, DlqMessage, ReprocessJob, api } from "../api";

export default function DlqPage({ clusterId }: { clusterId: string }) {
  const [mappings, setMappings] = useState<DlqMapping[]>([]);
  const [selected, setSelected] = useState<DlqMapping | null>(null);
  const [messages, setMessages] = useState<DlqMessage[]>([]);
  const [history, setHistory] = useState<ReprocessJob[]>([]);
  const [checked, setChecked] = useState<Set<string>>(new Set());
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState<string | null>(null);

  const refreshMappings = async (force = false) => {
    const list = await api.listMappings(clusterId, force);
    setMappings(list);
    if (!selected && list.length > 0) setSelected(list[0]);
  };

  useEffect(() => { if (clusterId) refreshMappings(); }, [clusterId]);

  useEffect(() => {
    if (!selected) return;
    api.readDlq(clusterId, selected.dlqTopic, 100).then((p) => setMessages(p.messages));
    api.reprocessHistory(clusterId).then(setHistory);
  }, [clusterId, selected]);

  const fp = (m: DlqMessage) => `${m.record.partition}:${m.record.offset}`;
  const toggle = (m: DlqMessage) => {
    const next = new Set(checked);
    if (next.has(fp(m))) next.delete(fp(m)); else next.add(fp(m));
    setChecked(next);
  };

  const reprocess = async () => {
    if (!selected || checked.size === 0) return;
    const targets = Array.from(checked).map((s) => {
      const [p, o] = s.split(":").map(Number);
      return { partition: p, offset: o };
    });
    setBusy(true);
    try {
      const job = await api.reprocess(clusterId, selected.dlqTopic, targets, "GROUP");
      setStatus(`Reprocessed: ${job.succeeded} ok, ${job.failed} failed`);
      setChecked(new Set());
      const p = await api.readDlq(clusterId, selected.dlqTopic, 100);
      setMessages(p.messages);
      const h = await api.reprocessHistory(clusterId);
      setHistory(h);
    } catch (e: any) {
      setStatus(`Failed: ${e.message ?? e}`);
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <div className="breadcrumbs">
        <span className="crumb-current">DLQ Ops</span>
      </div>
      <div className="page">
        <div className="page-header">
          <h1>DLQ Operations</h1>
          <button className="secondary" onClick={() => refreshMappings(true)}>Auto-detect mappings</button>
        </div>

        <div className="card" style={{ marginBottom: 16 }}>
          <div className="card-header">Topic ↔ DLQ mappings</div>
          <table className="kl-table">
            <thead>
              <tr>
                <th>Origin topic</th>
                <th>DLQ topic</th>
                <th style={{ width: 100 }}>Source</th>
                <th style={{ width: 100 }}>Confidence</th>
                <th style={{ width: 80 }}></th>
              </tr>
            </thead>
            <tbody>
              {mappings.map((m) => (
                <tr
                  key={`${m.originTopic}-${m.dlqTopic}`}
                  onClick={() => setSelected(m)}
                  className="clickable"
                  style={selected?.dlqTopic === m.dlqTopic ? { background: "rgba(88,166,255,0.06)" } : undefined}
                >
                  <td className="mono">{m.originTopic}</td>
                  <td className="mono">{m.dlqTopic}</td>
                  <td><span className={`tag ${m.source === "MANUAL" ? "accent" : ""}`}>{m.source}</span></td>
                  <td>{m.confidence}</td>
                  <td>{selected?.dlqTopic === m.dlqTopic && <span className="tag accent">selected</span>}</td>
                </tr>
              ))}
              {mappings.length === 0 && (
                <tr><td colSpan={5} className="empty">No mappings yet. Click "Auto-detect mappings".</td></tr>
              )}
            </tbody>
          </table>
        </div>

        {selected && (
          <>
            <div className="card" style={{ marginBottom: 16 }}>
              <div className="card-header">
                <span>Messages in <code>{selected.dlqTopic}</code></span>
                <div className="row">
                  <span className="muted" style={{ fontSize: 12 }}>{checked.size} selected</span>
                  <button onClick={reprocess} disabled={busy || checked.size === 0}>
                    Reprocess → <code>{selected.originTopic}</code>
                  </button>
                </div>
              </div>
              {status && <div className="muted" style={{ padding: "8px 14px", fontSize: 12 }}>{status}</div>}
              <table className="kl-table">
                <thead>
                  <tr>
                    <th style={{ width: 30 }}></th>
                    <th style={{ width: 160 }}>Time</th>
                    <th style={{ width: 120 }}>Part / Offset</th>
                    <th style={{ width: 220 }}>Origin</th>
                    <th>Failure</th>
                    <th style={{ width: 60 }} className="numeric">Retry</th>
                  </tr>
                </thead>
                <tbody>
                  {messages.map((m) => (
                    <tr key={fp(m)}>
                      <td>
                        <input
                          type="checkbox"
                          checked={checked.has(fp(m))}
                          disabled={!m.isReprocessable}
                          onChange={() => toggle(m)}
                        />
                      </td>
                      <td className="muted mono" style={{ fontSize: 11 }}>{new Date(m.record.timestamp).toISOString().replace("T", " ").slice(0, 19)}</td>
                      <td className="mono">{m.record.partition} / {m.record.offset}</td>
                      <td className="mono">
                        {m.originTopic ? (
                          <>{m.originTopic} / {m.originPartition ?? "?"} / {m.originOffset ?? "?"}</>
                        ) : <span className="tag warn">orphan</span>}
                      </td>
                      <td>
                        <details>
                          <summary><code>{(m.failureReason ?? m.exceptionClass ?? "—").slice(0, 100)}</code></summary>
                          <pre>{m.failureReason ?? ""}</pre>
                        </details>
                      </td>
                      <td className="numeric">{m.retryCount ?? "—"}</td>
                    </tr>
                  ))}
                  {messages.length === 0 && (
                    <tr><td colSpan={6} className="empty">DLQ is empty.</td></tr>
                  )}
                </tbody>
              </table>
            </div>

            <div className="card">
              <div className="card-header">Reprocess history</div>
              <table className="kl-table">
                <thead>
                  <tr>
                    <th style={{ width: 160 }}>When</th>
                    <th>DLQ → Origin</th>
                    <th style={{ width: 80 }}>Mode</th>
                    <th style={{ width: 100 }}>Status</th>
                    <th style={{ width: 100 }} className="numeric">OK / Fail</th>
                    <th>Notes</th>
                  </tr>
                </thead>
                <tbody>
                  {history.filter((h) => h.dlqTopic === selected.dlqTopic).map((h) => (
                    <tr key={h.id}>
                      <td className="muted mono" style={{ fontSize: 11 }}>{new Date(h.createdAt).toISOString().replace("T", " ").slice(0, 19)}</td>
                      <td className="mono">{h.dlqTopic} → {h.originTopic}</td>
                      <td>{h.mode}</td>
                      <td><span className={`tag ${h.status === "COMPLETED" ? "success" : h.status === "FAILED" ? "danger" : ""}`}>{h.status}</span></td>
                      <td className="numeric">{h.succeeded} / {h.failed}</td>
                      <td className="muted">{h.notes ?? ""}</td>
                    </tr>
                  ))}
                  {history.length === 0 && (
                    <tr><td colSpan={6} className="empty">No reprocess history yet.</td></tr>
                  )}
                </tbody>
              </table>
            </div>
          </>
        )}
      </div>
    </>
  );
}
