import { useEffect, useMemo, useState } from "react";
import { BrowseMode, BrowsePage, Message, Topic, browseTopic } from "../api";

type Mode = BrowseMode;
const MODES: { value: Mode; label: string }[] = [
  { value: "LATEST", label: "Latest" },
  { value: "EARLIEST", label: "Earliest" },
  { value: "FROM_TIMESTAMP", label: "From timestamp" },
  { value: "RANGE", label: "Within range" },
];

export default function TopicMessagesTab({
  clusterId,
  topicName,
  topic,
}: {
  clusterId: string;
  topicName: string;
  topic: Topic | null;
}) {
  const [mode, setMode] = useState<Mode>("LATEST");
  const [keyContains, setKeyContains] = useState("");
  const [valueContains, setValueContains] = useState("");
  const [fromTs, setFromTs] = useState("");
  const [toTs, setToTs] = useState("");
  const [pageSize, setPageSize] = useState(50);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState<BrowsePage | null>(null);
  const [history, setHistory] = useState<(Record<number, number> | null)[]>([]);
  const [selected, setSelected] = useState<Message | null>(null);

  // Fetch first page on mode change (or initial load).
  useEffect(() => {
    if (!topic) return;
    void load(null);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [topic?.name, mode]);

  const load = async (cursor: Record<number, number> | null) => {
    setLoading(true); setError(null);
    try {
      const result = await browseTopic(clusterId, topicName, {
        mode: cursor != null ? "FROM_OFFSET" : mode,
        pageSize,
        fromOffset: cursor ?? undefined,
        fromTimestamp: (mode === "FROM_TIMESTAMP" || mode === "RANGE") && fromTs ? new Date(fromTs).toISOString() : undefined,
        toTimestamp: mode === "RANGE" && toTs ? new Date(toTs).toISOString() : undefined,
        keyContains: keyContains || undefined,
        valueContains: valueContains || undefined,
      });
      setPage(result);
    } catch (e: any) {
      setError(e.message ?? String(e));
    } finally {
      setLoading(false);
    }
  };

  const onNext = async () => {
    if (!page?.nextCursor) return;
    setHistory((h) => [...h, page?.nextCursor ?? null]);
    await load(page.nextCursor);
  };

  const onPrev = async () => {
    if (history.length === 0) {
      // back to first page
      await load(null);
      return;
    }
    const prev = [...history];
    prev.pop();
    const target = prev[prev.length - 1] ?? null;
    setHistory(prev);
    await load(target);
  };

  const onApply = async () => {
    setHistory([]);
    await load(null);
  };

  const messages = page?.messages ?? [];

  return (
    <div className="kl-table-wrap">
      <div className="mode-bar">
        {MODES.map((m) => (
          <button
            key={m.value}
            className={`mode-btn ${mode === m.value ? "active" : ""}`}
            onClick={() => { setMode(m.value); setHistory([]); }}
            type="button"
          >
            {m.label}
          </button>
        ))}
        <span className="spacer" />
        <input
          type="text"
          placeholder="key contains…"
          value={keyContains}
          onChange={(e) => setKeyContains(e.target.value)}
          style={{ width: 140 }}
        />
        <input
          type="text"
          placeholder="value contains…"
          value={valueContains}
          onChange={(e) => setValueContains(e.target.value)}
          style={{ width: 160 }}
        />
        {(mode === "FROM_TIMESTAMP" || mode === "RANGE") && (
          <input
            type="datetime-local"
            value={fromTs}
            onChange={(e) => setFromTs(e.target.value)}
          />
        )}
        {mode === "RANGE" && (
          <input
            type="datetime-local"
            value={toTs}
            onChange={(e) => setToTs(e.target.value)}
          />
        )}
        <select value={pageSize} onChange={(e) => setPageSize(Number(e.target.value))} style={{ fontSize: 12 }}>
          {[20, 50, 100, 200, 500].map((n) => (
            <option key={n} value={n}>{n} / page</option>
          ))}
        </select>
        <button onClick={onApply} disabled={loading} type="button">{loading ? "Loading…" : "Apply"}</button>
      </div>

      {error && (
        <div style={{ padding: 12, color: "var(--danger)", fontSize: 12, borderBottom: "1px solid var(--border-muted)" }}>
          {error}
        </div>
      )}

      <table className="kl-table">
        <thead>
          <tr>
            <th style={{ width: 80 }} className="numeric">Offset</th>
            <th style={{ width: 70 }} className="numeric">Part</th>
            <th style={{ width: 160 }}>Timestamp</th>
            <th style={{ width: 180 }}>Key</th>
            <th>Value</th>
          </tr>
        </thead>
        <tbody>
          {messages.map((m, i) => (
            <tr
              key={`${m.partition}-${m.offset}-${i}`}
              className="clickable"
              onClick={() => setSelected(m)}
            >
              <td className="numeric mono">{m.offset.toLocaleString()}</td>
              <td className="numeric">{m.partition}</td>
              <td className="muted mono" style={{ fontSize: 11 }}>{new Date(m.timestamp).toISOString().replace("T", " ").slice(0, 19)}</td>
              <td className="mono" style={{ whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis", maxWidth: 200 }}>{m.key ?? "—"}</td>
              <td className="mono" style={{ whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis", maxWidth: 0 }}>
                {(m.value ?? "").slice(0, 200)}{(m.value?.length ?? 0) > 200 ? "…" : ""}
              </td>
            </tr>
          ))}
          {messages.length === 0 && !loading && (
            <tr>
              <td colSpan={5} className="empty">
                <div>No messages.</div>
                {(mode === "LATEST") && (keyContains || valueContains) && (
                  <div className="muted" style={{ marginTop: 8, fontSize: 12, lineHeight: 1.5 }}>
                    Latest mode only checks the most recent slice of the topic.
                    If the message could be older, switch to{" "}
                    <button
                      type="button"
                      className="secondary"
                      style={{ padding: "2px 8px", fontSize: 12 }}
                      onClick={() => { setMode("EARLIEST"); setHistory([]); }}
                    >Earliest</button>
                    {" "}or use the Cross-topic Search page.
                  </div>
                )}
              </td>
            </tr>
          )}
        </tbody>
      </table>

      <div className="pagination">
        <span>
          {messages.length} messages · scanned partitions [{page?.partitionsScanned?.join(", ") ?? "—"}]
          {page && <> · {page.durationMs} ms</>}
        </span>
        <div className="pager">
          <button className="secondary" onClick={onPrev} disabled={loading || (history.length === 0 && !page)}>‹ Prev</button>
          <button className="secondary" onClick={onNext} disabled={loading || !page?.hasMore}>Next ›</button>
        </div>
      </div>

      {selected && <MessageModal message={selected} onClose={() => setSelected(null)} />}
    </div>
  );
}

function MessageModal({ message, onClose }: { message: Message; onClose: () => void }) {
  const prettyValue = useMemo(() => {
    if (!message.value) return "";
    try {
      return JSON.stringify(JSON.parse(message.value), null, 2);
    } catch {
      return message.value;
    }
  }, [message]);
  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <div className="mono">
            {message.topic} <span className="muted">/ p{message.partition} · o{message.offset}</span>
            {message.encoding === "AVRO" && message.schemaId != null && (
              <span className="tag accent" style={{ marginLeft: 10 }}>Avro · schema #{message.schemaId}</span>
            )}
          </div>
          <button className="secondary icon" onClick={onClose} type="button">✕</button>
        </div>
        <div className="modal-body">
          <dl className="kv" style={{ marginBottom: 16 }}>
            <dt>Timestamp</dt><dd>{new Date(message.timestamp).toISOString()}</dd>
            <dt>Key</dt><dd>{message.key ?? "—"}</dd>
            {message.encoding && <><dt>Encoding</dt><dd>{message.encoding}</dd></>}
          </dl>
          <h3 style={{ fontSize: 12, color: "var(--fg-muted)", textTransform: "uppercase", letterSpacing: ".06em", margin: "4px 0" }}>Value</h3>
          <pre>{prettyValue || <span className="muted">(empty)</span>}</pre>
          {Object.keys(message.headers).length > 0 && (
            <>
              <h3 style={{ fontSize: 12, color: "var(--fg-muted)", textTransform: "uppercase", letterSpacing: ".06em", margin: "12px 0 4px" }}>Headers</h3>
              <pre>{Object.entries(message.headers).map(([k, v]) => `${k}: ${v}`).join("\n")}</pre>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
