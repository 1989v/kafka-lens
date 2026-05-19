import { useState } from "react";
import { createTopic } from "../api";

export default function NewTopicModal({
  clusterId,
  onClose,
  onCreated,
}: {
  clusterId: string;
  onClose: () => void;
  onCreated: (name: string) => void;
}) {
  const [name, setName] = useState("");
  const [partitions, setPartitions] = useState(3);
  const [replication, setReplication] = useState(1);
  const [configsText, setConfigsText] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async () => {
    setBusy(true); setError(null);
    try {
      const configs: Record<string, string> = {};
      configsText.split("\n").forEach((line) => {
        const idx = line.indexOf("=");
        if (idx > 0) configs[line.slice(0, idx).trim()] = line.slice(idx + 1).trim();
      });
      await createTopic(clusterId, {
        name: name.trim(),
        numPartitions: partitions,
        replicationFactor: replication,
        configs,
      });
      onCreated(name.trim());
    } catch (e: any) {
      setError(e.message ?? String(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <div>Create new topic</div>
          <button className="secondary icon" onClick={onClose} type="button">✕</button>
        </div>
        <div className="modal-body">
          <div className="form-grid">
            <label>
              Topic name
              <input value={name} onChange={(e) => setName(e.target.value)} placeholder="order.events" autoFocus />
            </label>
            <label>
              Partitions
              <input type="number" value={partitions} onChange={(e) => setPartitions(Number(e.target.value))} min={1} max={10000} />
            </label>
            <label>
              Replication factor
              <input type="number" value={replication} onChange={(e) => setReplication(Number(e.target.value))} min={1} max={10} />
            </label>
          </div>
          <label style={{ display: "block", marginBottom: 6 }}>
            <div className="muted" style={{ fontSize: 11, textTransform: "uppercase", letterSpacing: ".04em", marginBottom: 4 }}>
              Topic configs (one per line, key=value, optional)
            </div>
            <textarea
              value={configsText}
              onChange={(e) => setConfigsText(e.target.value)}
              rows={6}
              style={{ width: "100%", fontFamily: "var(--code-font)" }}
              placeholder="retention.ms=604800000&#10;cleanup.policy=delete&#10;compression.type=snappy"
            />
          </label>
          {error && <div className="tag danger" style={{ padding: "6px 10px", display: "inline-block" }}>{error}</div>}
        </div>
        <div className="modal-footer">
          <button className="secondary" onClick={onClose} type="button">Cancel</button>
          <button onClick={submit} disabled={busy || !name.trim()} type="button">{busy ? "Creating…" : "Create topic"}</button>
        </div>
      </div>
    </div>
  );
}
