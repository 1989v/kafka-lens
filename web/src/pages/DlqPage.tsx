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
      <div className="row spread" style={{ marginBottom: 12 }}>
        <h1>DLQ Operations</h1>
        <div className="row">
          <button className="secondary" onClick={() => refreshMappings(true)}>Auto-detect mappings</button>
        </div>
      </div>

      <h2>Topic ↔ DLQ Mappings</h2>
      <table style={{ marginBottom: 16 }}>
        <thead>
          <tr>
            <th>Origin topic</th>
            <th>DLQ topic</th>
            <th style={{ width: 80 }}>Source</th>
            <th style={{ width: 80 }}>Confidence</th>
            <th style={{ width: 80 }}></th>
          </tr>
        </thead>
        <tbody>
          {mappings.map((m) => (
            <tr key={`${m.originTopic}-${m.dlqTopic}`} onClick={() => setSelected(m)} style={{ cursor: "pointer", background: selected?.dlqTopic === m.dlqTopic ? "rgba(255,255,255,.04)" : undefined }}>
              <td><code>{m.originTopic}</code></td>
              <td><code>{m.dlqTopic}</code></td>
              <td><span className={`tag ${m.source === "MANUAL" ? "ok" : ""}`}>{m.source}</span></td>
              <td>{m.confidence}</td>
              <td>{selected?.dlqTopic === m.dlqTopic ? <span className="tag ok">selected</span> : ""}</td>
            </tr>
          ))}
          {mappings.length === 0 && <tr><td colSpan={5} className="empty">No mappings yet. Click "Auto-detect mappings".</td></tr>}
        </tbody>
      </table>

      {selected && (
        <>
          <div className="row spread">
            <h2 style={{ margin: 0 }}>Messages in {selected.dlqTopic}</h2>
            <div className="row">
              <span className="muted">{checked.size} selected</span>
              <button onClick={reprocess} disabled={busy || checked.size === 0}>Reprocess → {selected.originTopic}</button>
            </div>
          </div>
          {status && <div className="muted" style={{ marginTop: 4 }}>{status}</div>}
          <table>
            <thead>
              <tr>
                <th style={{ width: 24 }}></th>
                <th style={{ width: 160 }}>Time</th>
                <th style={{ width: 120 }}>Part / Offset</th>
                <th style={{ width: 200 }}>Origin</th>
                <th>Failure</th>
                <th style={{ width: 60 }}>Retry</th>
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
                  <td className="muted">{new Date(m.record.timestamp).toISOString().replace("T", " ").slice(0, 19)}</td>
                  <td><code>{m.record.partition} / {m.record.offset}</code></td>
                  <td>
                    {m.originTopic ? (
                      <code>{m.originTopic} / {m.originPartition ?? "?"} / {m.originOffset ?? "?"}</code>
                    ) : <span className="tag warn">orphan</span>}
                  </td>
                  <td>
                    <details>
                      <summary><code>{(m.failureReason ?? m.exceptionClass ?? "—").slice(0, 100)}</code></summary>
                      <pre>{m.failureReason ?? ""}</pre>
                    </details>
                  </td>
                  <td>{m.retryCount ?? "—"}</td>
                </tr>
              ))}
              {messages.length === 0 && <tr><td colSpan={6} className="empty">DLQ is empty.</td></tr>}
            </tbody>
          </table>

          <h2 style={{ marginTop: 24 }}>Reprocess history</h2>
          <table>
            <thead>
              <tr>
                <th style={{ width: 160 }}>When</th>
                <th>DLQ → Origin</th>
                <th style={{ width: 80 }}>Mode</th>
                <th style={{ width: 80 }}>Status</th>
                <th style={{ width: 80 }}>OK / Fail</th>
                <th>Notes</th>
              </tr>
            </thead>
            <tbody>
              {history.filter((h) => h.dlqTopic === selected.dlqTopic).map((h) => (
                <tr key={h.id}>
                  <td className="muted">{new Date(h.createdAt).toISOString().slice(0, 19).replace("T", " ")}</td>
                  <td><code>{h.dlqTopic} → {h.originTopic}</code></td>
                  <td>{h.mode}</td>
                  <td>{h.status}</td>
                  <td>{h.succeeded} / {h.failed}</td>
                  <td className="muted">{h.notes ?? ""}</td>
                </tr>
              ))}
              {history.length === 0 && <tr><td colSpan={6} className="empty">No reprocess history yet.</td></tr>}
            </tbody>
          </table>
        </>
      )}
    </>
  );
}
