import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Topic, addPartitions, deleteTopic } from "../api";
import { useFeature } from "../AppInfoContext";

/**
 * Action bar shown on the Topic Detail header. Hosts the dangerous-or-
 * structural operations (delete topic, increase partitions) behind their
 * own confirmation modals so a misclick never wipes data.
 *
 * Whole bar stays hidden unless the operator enabled the
 * TOPICOPS_ALLOWDESTRUCTIVE feature flag — the corresponding API
 * endpoints reject the calls with HTTP 403 either way.
 */
export default function TopicManageActions({
  clusterId,
  topic,
  topicName,
}: {
  clusterId: string;
  topic: Topic | null;
  topicName: string;
}) {
  const allowed = useFeature("allowDestructiveTopicOps");
  const [mode, setMode] = useState<"none" | "delete" | "grow">("none");

  if (!allowed) return null;

  return (
    <>
      <div className="row" style={{ gap: 8 }}>
        <button
          className="secondary"
          onClick={() => setMode("grow")}
          disabled={!topic}
          type="button"
        >Add partitions</button>
        <button
          className="danger"
          onClick={() => setMode("delete")}
          type="button"
        >Delete topic</button>
      </div>

      {mode === "delete" && (
        <DeleteTopicModal clusterId={clusterId} topicName={topicName} onClose={() => setMode("none")} />
      )}
      {mode === "grow" && topic && (
        <AddPartitionsModal
          clusterId={clusterId}
          topicName={topicName}
          current={topic.partitions.length}
          onClose={() => setMode("none")}
        />
      )}
    </>
  );
}

function DeleteTopicModal({
  clusterId,
  topicName,
  onClose,
}: {
  clusterId: string;
  topicName: string;
  onClose: () => void;
}) {
  const [confirm, setConfirm] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const navigate = useNavigate();

  const ok = confirm === topicName;
  const submit = async () => {
    setBusy(true); setError(null);
    try {
      await deleteTopic(clusterId, topicName);
      navigate(`/c/${clusterId}/topics`, { replace: true });
    } catch (e: any) {
      setError(e.message ?? String(e));
      setBusy(false);
    }
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <div>Delete topic</div>
          <button className="secondary icon" onClick={onClose} type="button">✕</button>
        </div>
        <div className="modal-body">
          <p>
            This permanently deletes <code>{topicName}</code> from the cluster.
            All messages and consumer offsets for the topic are lost. There is no undo.
          </p>
          <p className="muted" style={{ fontSize: 12 }}>
            Type the topic name to confirm:
          </p>
          <input
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
            placeholder={topicName}
            style={{ width: "100%", fontFamily: "var(--code-font)" }}
            autoFocus
          />
          {error && <div className="tag danger" style={{ padding: "6px 10px", display: "inline-block", marginTop: 8 }}>{error}</div>}
        </div>
        <div className="modal-footer">
          <button className="secondary" onClick={onClose} type="button">Cancel</button>
          <button className="danger" onClick={submit} disabled={!ok || busy} type="button">
            {busy ? "Deleting…" : "Delete topic"}
          </button>
        </div>
      </div>
    </div>
  );
}

function AddPartitionsModal({
  clusterId,
  topicName,
  current,
  onClose,
}: {
  clusterId: string;
  topicName: string;
  current: number;
  onClose: () => void;
}) {
  const [target, setTarget] = useState(current + 1);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  const submit = async () => {
    setBusy(true); setError(null);
    try {
      await addPartitions(clusterId, topicName, target);
      setDone(true);
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
          <div>Add partitions to <code>{topicName}</code></div>
          <button className="secondary icon" onClick={onClose} type="button">✕</button>
        </div>
        <div className="modal-body">
          <p className="muted" style={{ fontSize: 12 }}>
            Kafka can only <strong>increase</strong> partition counts, never decrease.
            Adding partitions changes the partition assignment for keyed messages,
            which breaks ordering guarantees for that key going forward. Make sure
            consumers can tolerate the rehash before proceeding.
          </p>
          <div className="form-grid">
            <label>
              Current
              <input type="number" value={current} disabled />
            </label>
            <label>
              New total
              <input
                type="number"
                value={target}
                onChange={(e) => setTarget(Number(e.target.value))}
                min={current + 1}
                max={10_000}
                autoFocus
              />
            </label>
          </div>
          {done && <div className="tag success" style={{ padding: "6px 10px", display: "inline-block" }}>Partitions updated.</div>}
          {error && <div className="tag danger" style={{ padding: "6px 10px", display: "inline-block" }}>{error}</div>}
        </div>
        <div className="modal-footer">
          <button className="secondary" onClick={onClose} type="button">Close</button>
          <button onClick={submit} disabled={busy || done || target <= current} type="button">
            {busy ? "Applying…" : "Add partitions"}
          </button>
        </div>
      </div>
    </div>
  );
}
