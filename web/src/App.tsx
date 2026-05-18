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
  useEffect(() => {
    api.listClusters().then((list) => {
      if (list.length > 0) navigate(`/c/${list[0].id}/topics`, { replace: true });
    });
  }, [navigate]);
  return <div className="empty">Loading clusters…</div>;
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
