import { useEffect, useMemo, useRef, useState } from "react";
import { Topic, api } from "../api";

/**
 * Searchable topic select. Inline filter input + dropdown list — closes on
 * outside click and Escape, and keyboard arrows traverse the filtered options.
 */
export default function TopicPicker({
  clusterId,
  value,
  onChange,
  placeholder = "Search and select a topic…",
  autoSelectFirst = false,
}: {
  clusterId: string;
  value: string | null;
  onChange: (topic: string) => void;
  placeholder?: string;
  autoSelectFirst?: boolean;
}) {
  const [topics, setTopics] = useState<Topic[]>([]);
  const [loading, setLoading] = useState(false);
  const [query, setQuery] = useState("");
  const [open, setOpen] = useState(false);
  const [highlight, setHighlight] = useState(0);
  const rootRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!clusterId) return;
    setLoading(true);
    api.listTopics(clusterId)
      .then((list) => {
        setTopics(list);
        if (autoSelectFirst && !value && list.length > 0) onChange(list[0].name);
      })
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [clusterId]);

  useEffect(() => {
    if (!open) return;
    const onDocClick = (e: MouseEvent) => {
      if (!rootRef.current?.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") setOpen(false);
    };
    document.addEventListener("mousedown", onDocClick);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onDocClick);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  const filtered = useMemo(() => {
    const q = query.toLowerCase();
    return topics.filter((t) => t.name.toLowerCase().includes(q)).slice(0, 200);
  }, [topics, query]);

  useEffect(() => { if (highlight >= filtered.length) setHighlight(Math.max(0, filtered.length - 1)); }, [filtered, highlight]);

  const select = (name: string) => {
    onChange(name);
    setOpen(false);
    setQuery("");
  };

  return (
    <div ref={rootRef} className="topic-picker">
      <div className="topic-picker-control" onClick={() => { setOpen(true); inputRef.current?.focus(); }}>
        {open ? (
          <input
            ref={inputRef}
            type="text"
            placeholder={placeholder}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "ArrowDown") { setHighlight((h) => Math.min(h + 1, filtered.length - 1)); e.preventDefault(); }
              else if (e.key === "ArrowUp") { setHighlight((h) => Math.max(h - 1, 0)); e.preventDefault(); }
              else if (e.key === "Enter" && filtered[highlight]) { select(filtered[highlight].name); }
            }}
            autoFocus
          />
        ) : (
          <div className="topic-picker-value mono">
            {value ? value : <span className="muted">{loading ? "Loading topics…" : placeholder}</span>}
            <span className="topic-picker-chevron">▾</span>
          </div>
        )}
      </div>
      {open && (
        <ul className="topic-picker-list">
          {filtered.map((t, i) => (
            <li
              key={t.name}
              className={`topic-picker-option ${i === highlight ? "active" : ""}`}
              onMouseDown={(e) => { e.preventDefault(); select(t.name); }}
              onMouseEnter={() => setHighlight(i)}
            >
              <code>{t.name}</code>
              <span className="muted topic-picker-meta">
                p{t.partitions.length} · {t.totalMessages.toLocaleString()}
              </span>
            </li>
          ))}
          {filtered.length === 0 && (
            <li className="topic-picker-option muted">No matches</li>
          )}
        </ul>
      )}
    </div>
  );
}
