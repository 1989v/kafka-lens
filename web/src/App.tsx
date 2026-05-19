import { useEffect, useState } from "react";
import { NavLink, Navigate, Route, Routes, useNavigate, useParams } from "react-router-dom";
import { Cluster, api } from "./api";
import TopicsPage from "./pages/TopicsPage";
import TopicDetailPage from "./pages/TopicDetailPage";
import ConsumerGroupsPage from "./pages/ConsumerGroupsPage";
import SearchPage from "./pages/SearchPage";
import DlqPage from "./pages/DlqPage";
import PublishPage from "./pages/PublishPage";
import SetupGuide from "./pages/SetupGuide";
import DashboardPage from "./pages/DashboardPage";
import BrokersPage from "./pages/BrokersPage";

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<RootRedirect />} />
      <Route path="/setup" element={<SetupGuide />} />
      <Route path="/c/:clusterId/*" element={<ClusterShell />} />
      <Route path="*" element={<Navigate to="/" />} />
    </Routes>
  );
}

function RootRedirect() {
  const navigate = useNavigate();
  const [state, setState] = useState<"loading" | "error">("loading");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.listClusters()
      .then((list) => {
        if (list.length > 0) navigate(`/c/${list[0].id}/topics`, { replace: true });
        else navigate("/setup", { replace: true });
      })
      .catch((e) => { setError(e.message ?? String(e)); setState("error"); });
  }, [navigate]);

  if (state === "loading") return <div className="empty">Loading clusters…</div>;
  return (
    <div className="empty">
      <h2>Backend unreachable</h2>
      <p className="muted">{error}</p>
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

  const current = clusters.find((c) => c.id === clusterId);

  return (
    <div className="layout">
      <aside className="sidebar">
        <div className="sidebar-header">
          <div className="brand">
            <span className="dot" />
            <span>Kafka Lens</span>
          </div>
          <select
            className="cluster-select"
            value={clusterId}
            onChange={(e) => navigate(`/c/${e.target.value}/topics`)}
          >
            {clusters.map((c) => (
              <option key={c.id} value={c.id}>{c.name}</option>
            ))}
          </select>
          {current && <div className="cluster-meta">{current.bootstrapServers}</div>}
        </div>

        <nav className="sidebar-nav">
          <div className="nav-group">
            <h3>Cluster</h3>
            <NavLink to={`/c/${clusterId}/dashboard`}>Dashboard</NavLink>
            <NavLink to={`/c/${clusterId}/brokers`}>Brokers</NavLink>
            <NavLink to={`/c/${clusterId}/topics`}>Topics</NavLink>
            <NavLink to={`/c/${clusterId}/consumer-groups`}>Consumer Groups</NavLink>
          </div>
          <div className="nav-group">
            <h3>Operations</h3>
            <NavLink to={`/c/${clusterId}/search`}>Cross-topic Search</NavLink>
            <NavLink to={`/c/${clusterId}/dlq`}>DLQ Ops</NavLink>
            <NavLink to={`/c/${clusterId}/publish`}>Publish</NavLink>
          </div>
        </nav>
      </aside>

      <main className="content">
        <Routes>
          <Route path="dashboard" element={<DashboardPage clusterId={clusterId} />} />
          <Route path="brokers" element={<BrokersPage clusterId={clusterId} />} />
          <Route path="topics" element={<TopicsPage clusterId={clusterId} />} />
          <Route path="topics/:topicName/*" element={<TopicDetailPage clusterId={clusterId} />} />
          <Route path="consumer-groups" element={<ConsumerGroupsPage clusterId={clusterId} />} />
          <Route path="search" element={<SearchPage clusterId={clusterId} />} />
          <Route path="dlq" element={<DlqPage clusterId={clusterId} />} />
          <Route path="publish" element={<PublishPage clusterId={clusterId} />} />
          <Route path="*" element={<Navigate to="topics" />} />
        </Routes>
      </main>
    </div>
  );
}

