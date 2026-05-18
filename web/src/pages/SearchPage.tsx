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
      <h1>Search</h1>
      <div className="form-grid">
        <label>
          Topics (comma-separated)
          <input value={topicInput} onChange={(e) => setTopicInput(e.target.value)} placeholder="order.events,payment.events" />
        </label>
        <label>
          Key contains
          <input value={keyContains} onChange={(e) => setKeyContains(e.target.value)} placeholder="" />
        </label>
        <label>
          Value contains
          <input value={valueContains} onChange={(e) => setValueContains(e.target.value)} placeholder="" />
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

      <div className="row" style={{ marginBottom: 14 }}>
        <button onClick={submit} disabled={!canRun}>{running ? "Scanning…" : "Run search"}</button>
        {result && (
          <span className="muted">
            scanned {result.scannedCount.toLocaleString()} / matched {result.matched.length} in {result.durationMs} ms
            {result.limitsHit.length > 0 && <> · <span className="tag warn">{result.limitsHit.join(", ")}</span></>}
          </span>
        )}
        {error && <span className="tag danger" style={{ padding: "4px 8px" }}>{error}</span>}
      </div>

      <Results messages={result?.matched ?? []} />
    </>
  );
}

function Results({ messages }: { messages: Message[] }) {
  if (messages.length === 0) return <div className="empty">No matches yet.</div>;
  return (
    <table>
      <thead>
        <tr>
          <th style={{ width: 160 }}>Time</th>
          <th style={{ width: 180 }}>Topic / Part / Offset</th>
          <th style={{ width: 140 }}>Key</th>
          <th>Value</th>
        </tr>
      </thead>
      <tbody>
        {messages.map((m, i) => (
          <tr key={`${m.partition}-${m.offset}-${i}`} className="message-row">
            <td className="muted">{new Date(m.timestamp).toISOString().replace("T", " ").slice(0, 19)}</td>
            <td><code>{m.topic} / {m.partition} / {m.offset}</code></td>
            <td className="value"><code>{m.key ?? "—"}</code></td>
            <td className="value">
              <details>
                <summary><code>{(m.value ?? "").slice(0, 120)}{(m.value?.length ?? 0) > 120 ? "…" : ""}</code></summary>
                <pre>{m.value ?? ""}</pre>
                {Object.keys(m.headers).length > 0 && (
                  <pre style={{ marginTop: 6 }}>{Object.entries(m.headers).map(([k, v]) => `${k}: ${v}`).join("\n")}</pre>
                )}
              </details>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
