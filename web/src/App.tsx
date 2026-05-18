import { useEffect, useState } from "react";
import { NavLink, Navigate, Route, Routes, useNavigate, useParams } from "react-router-dom";
import { Cluster, api } from "./api";
import TopicsPage from "./pages/TopicsPage";
import SearchPage from "./pages/SearchPage";
import DlqPage from "./pages/DlqPage";
import PublishPage from "./pages/PublishPage";

export default function App() {
  return (
    <div className="app">
      <Routes>
        <Route path="/" element={<RootRedirect />} />
        <Route path="/c/:clusterId/*" element={<ClusterShell />} />
        <Route path="*" element={<Navigate to="/" />} />
      </Routes>
    </div>
  );
}

function RootRedirect() {
  const navigate = useNavigate();
  const [state, setState] = useState<"loading" | "empty" | "error">("loading");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.listClusters()
      .then((list) => {
        if (list.length > 0) navigate(`/c/${list[0].id}/topics`, { replace: true });
        else setState("empty");
      })
      .catch((e) => { setError(e.message ?? String(e)); setState("error"); });
  }, [navigate]);

  if (state === "loading") return <div className="empty">Loading clusters…</div>;
  if (state === "error") return (
    <div className="empty">
      <h2>Backend unreachable</h2>
      <p className="muted">{error}</p>
    </div>
  );
  return (
    <div className="empty" style={{ maxWidth: 640, margin: "60px auto", textAlign: "left" }}>
      <h1 style={{ textAlign: "center" }}>No Kafka clusters configured</h1>
      <p>
        Kafka Lens reads cluster definitions from a YAML file. You're seeing this screen
        because the running instance has an empty <code>clusters</code> list.
      </p>
      <p>Create a <code>config.yml</code> next to the container or jar:</p>
      <pre>{`clusters:
  - id: local
    name: Local Kafka
    bootstrapServers: localhost:9092

auth:
  mode: none`}</pre>
      <p>Then start Kafka Lens pointing at it:</p>
      <pre>{`# JAR
java -jar build/libs/kafka-lens.jar \\
  --spring.config.additional-location=file:./config.yml

# Docker
docker run -p 9192:9192 \\
  -v "$(pwd)/config.yml":/app/config.yml \\
  -e SPRING_CONFIG_ADDITIONAL_LOCATION=file:/app/config.yml \\
  kafka-lens:dev`}</pre>
      <p className="muted">
        See <code>config.example.yml</code> in the repo for the full reference (SASL/SSL,
        DLQ naming patterns, scan limits, auth modes).
      </p>
    </div>
  );
}

function ClusterShell() {
  const { clusterId = "" } = useParams();
  const [clusters, setClusters] = useState<Cluster[]>([]);
  const navigate = useNavigate();

  useEffect(() => {
    api.listClusters().then(setClusters);
  }, []);

  return (
    <>
      <header className="topbar">
        <div className="brand">Kafka Lens</div>
        <nav>
          <NavLink to={`/c/${clusterId}/topics`}>Topics</NavLink>
          <NavLink to={`/c/${clusterId}/search`}>Search</NavLink>
          <NavLink to={`/c/${clusterId}/dlq`}>DLQ</NavLink>
          <NavLink to={`/c/${clusterId}/publish`}>Publish</NavLink>
        </nav>
        <span className="spacer" />
        <select
          value={clusterId}
          onChange={(e) => navigate(`/c/${e.target.value}/topics`)}
        >
          {clusters.map((c) => (
            <option key={c.id} value={c.id}>{c.name}</option>
          ))}
        </select>
      </header>
      <main>
        <Routes>
          <Route path="topics" element={<TopicsPage clusterId={clusterId} />} />
          <Route path="search" element={<SearchPage clusterId={clusterId} />} />
          <Route path="dlq" element={<DlqPage clusterId={clusterId} />} />
          <Route path="publish" element={<PublishPage clusterId={clusterId} />} />
          <Route path="*" element={<Navigate to="topics" />} />
        </Routes>
      </main>
    </>
  );
}
