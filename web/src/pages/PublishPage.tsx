import { useState } from "react";
import { api } from "../api";

export default function PublishPage({ clusterId }: { clusterId: string }) {
  const [topic, setTopic] = useState("");
  const [key, setKey] = useState("");
  const [value, setValue] = useState("{}");
  const [headers, setHeaders] = useState("");
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState<string | null>(null);

  const submit = async () => {
    setBusy(true); setStatus(null);
    try {
      const parsedHeaders: Record<string, string> = {};
      headers.split("\n").forEach((line) => {
        const idx = line.indexOf(":");
        if (idx > 0) parsedHeaders[line.slice(0, idx).trim()] = line.slice(idx + 1).trim();
      });
      const r = await api.publish(clusterId, { topic, key: key || null, value, headers: parsedHeaders });
      setStatus(`Published: partition=${r.partition} offset=${r.offset}`);
    } catch (e: any) {
      setStatus(`Failed: ${e.message ?? e}`);
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <h1>Publish message</h1>
      <p className="muted">
        Direct publishing to a topic registered as a DLQ is refused by the backend (HTTP 403).
        Use the DLQ tab to reprocess DLQ messages instead.
      </p>
      <div className="form-grid">
        <label>
          Topic
          <input value={topic} onChange={(e) => setTopic(e.target.value)} placeholder="order.events" />
        </label>
        <label>
          Key
          <input value={key} onChange={(e) => setKey(e.target.value)} placeholder="(optional)" />
        </label>
      </div>
      <label style={{ display: "block", marginBottom: 10 }}>
        <div className="muted" style={{ fontSize: 12, marginBottom: 4 }}>Value</div>
        <textarea
          value={value}
          onChange={(e) => setValue(e.target.value)}
          rows={8}
          style={{ width: "100%", fontFamily: "var(--code-font)" }}
        />
      </label>
      <label style={{ display: "block", marginBottom: 10 }}>
        <div className="muted" style={{ fontSize: 12, marginBottom: 4 }}>Headers (one per line, key: value)</div>
        <textarea
          value={headers}
          onChange={(e) => setHeaders(e.target.value)}
          rows={4}
          style={{ width: "100%", fontFamily: "var(--code-font)" }}
          placeholder="correlation-id: abc-123&#10;source: kafka-lens"
        />
      </label>
      <div className="row">
        <button onClick={submit} disabled={busy || !topic}>Publish</button>
        {status && <span className="muted">{status}</span>}
      </div>
    </>
  );
}
