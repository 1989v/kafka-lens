import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { Message, SearchRequest, SearchResult, api } from "../api";

export default function SearchPage({ clusterId }: { clusterId: string }) {
  const [params] = useSearchParams();
  const initialTopic = params.get("topic") ?? "";

  const [topicInput, setTopicInput] = useState(initialTopic);
  const [keyContains, setKeyContains] = useState("");
  const [valueContains, setValueContains] = useState("");
  const [jsonFieldPath, setJsonFieldPath] = useState("");
  const [jsonFieldValue, setJsonFieldValue] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [maxResults, setMaxResults] = useState(100);
  const [running, setRunning] = useState(false);
  const [result, setResult] = useState<SearchResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<Message | null>(null);

  useEffect(() => { setTopicInput(initialTopic); }, [initialTopic]);

  const canRun = topicInput.trim().length > 0 && !running;

  const submit = async () => {
    setRunning(true); setError(null); setResult(null);
    try {
      const topics = topicInput.split(",").map((s) => s.trim()).filter(Boolean);
      const req: SearchRequest = {
        topics,
        keyContains: keyContains || undefined,
        valueContains: valueContains || undefined,
        jsonFieldEquals: jsonFieldPath && jsonFieldValue ? { [jsonFieldPath]: jsonFieldValue } : undefined,
        from: from ? new Date(from).toISOString() : undefined,
        to: to ? new Date(to).toISOString() : undefined,
        maxResults,
      };
      const r = await api.search(clusterId, req);
      setResult(r);
    } catch (e: any) {
      setError(e.message ?? String(e));
    } finally {
      setRunning(false);
    }
  };

  return (
    <>
      <div className="breadcrumbs">
        <span className="crumb-current">Cross-topic Search</span>
      </div>
      <div className="page">
        <div className="page-header">
          <h1>Cross-topic Search</h1>
          <span className="meta">on-demand scan + filter — JSON / time / correlation</span>
        </div>

        <div className="card" style={{ marginBottom: 16 }}>
          <div className="card-header">Filters</div>
          <div className="card-body">
            <div className="form-grid">
              <label>
                Topics (comma-separated)
                <input value={topicInput} onChange={(e) => setTopicInput(e.target.value)} placeholder="order.events,payment.events" />
              </label>
              <label>
                Key contains
                <input value={keyContains} onChange={(e) => setKeyContains(e.target.value)} />
              </label>
              <label>
                Value contains
                <input value={valueContains} onChange={(e) => setValueContains(e.target.value)} />
              </label>
              <label>
                JSON field path
                <input value={jsonFieldPath} onChange={(e) => setJsonFieldPath(e.target.value)} placeholder="payload.orderId" />
              </label>
              <label>
                JSON field equals
                <input value={jsonFieldValue} onChange={(e) => setJsonFieldValue(e.target.value)} placeholder="ORD-2026-12345" />
              </label>
              <label>
                From
                <input type="datetime-local" value={from} onChange={(e) => setFrom(e.target.value)} />
              </label>
              <label>
                To
                <input type="datetime-local" value={to} onChange={(e) => setTo(e.target.value)} />
              </label>
              <label>
                Max results
                <input type="number" value={maxResults} onChange={(e) => setMaxResults(Number(e.target.value))} min={1} max={5000} />
              </label>
            </div>
            <div className="button-row">
              <button onClick={submit} disabled={!canRun}>{running ? "Scanning…" : "Run search"}</button>
              {result && (
                <span className="muted" style={{ fontSize: 12 }}>
                  scanned {result.scannedCount.toLocaleString()} · matched {result.matched.length} · {result.durationMs} ms
                  {result.limitsHit.length > 0 && <> · <span className="tag warn">{result.limitsHit.join(", ")}</span></>}
                </span>
              )}
              {error && <span className="tag danger" style={{ padding: "4px 8px" }}>{error}</span>}
            </div>
          </div>
        </div>

        <Results messages={result?.matched ?? []} onSelect={setSelected} />
        {selected && <MessageModal message={selected} onClose={() => setSelected(null)} />}
      </div>
    </>
  );
}

function Results({ messages, onSelect }: { messages: Message[]; onSelect: (m: Message) => void }) {
  if (messages.length === 0) return <div className="empty">No matches yet.</div>;
  return (
    <div className="kl-table-wrap">
      <table className="kl-table">
        <thead>
          <tr>
            <th style={{ width: 160 }}>Time</th>
            <th style={{ width: 200 }}>Topic / Part / Offset</th>
            <th style={{ width: 180 }}>Key</th>
            <th>Value</th>
          </tr>
        </thead>
        <tbody>
          {messages.map((m, i) => (
            <tr key={`${m.partition}-${m.offset}-${i}`} className="clickable" onClick={() => onSelect(m)}>
              <td className="muted mono" style={{ fontSize: 11 }}>{new Date(m.timestamp).toISOString().replace("T", " ").slice(0, 19)}</td>
              <td className="mono">{m.topic} / {m.partition} / {m.offset.toLocaleString()}</td>
              <td className="mono" style={{ whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis", maxWidth: 200 }}>{m.key ?? "—"}</td>
              <td className="mono" style={{ whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis", maxWidth: 0 }}>
                {(m.value ?? "").slice(0, 200)}{(m.value?.length ?? 0) > 200 ? "…" : ""}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function MessageModal({ message, onClose }: { message: Message; onClose: () => void }) {
  const pretty = useMemo(() => {
    try { return JSON.stringify(JSON.parse(message.value ?? ""), null, 2); } catch { return message.value ?? ""; }
  }, [message]);
  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <div className="mono">{message.topic} <span className="muted">/ p{message.partition} · o{message.offset}</span></div>
          <button className="secondary icon" onClick={onClose}>✕</button>
        </div>
        <div className="modal-body">
          <dl className="kv" style={{ marginBottom: 16 }}>
            <dt>Timestamp</dt><dd>{new Date(message.timestamp).toISOString()}</dd>
            <dt>Key</dt><dd>{message.key ?? "—"}</dd>
          </dl>
          <pre>{pretty || <span className="muted">(empty)</span>}</pre>
          {Object.keys(message.headers).length > 0 && (
            <pre style={{ marginTop: 12 }}>{Object.entries(message.headers).map(([k, v]) => `${k}: ${v}`).join("\n")}</pre>
          )}
        </div>
      </div>
    </div>
  );
}
