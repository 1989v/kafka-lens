export default function SetupGuide() {
  return (
    <div className="layout" style={{ gridTemplateColumns: "1fr" }}>
      <main className="content">
        <div className="page" style={{ maxWidth: 760, margin: "60px auto" }}>
          <h1 style={{ textAlign: "center", marginBottom: 8 }}>No Kafka clusters configured</h1>
          <p className="muted" style={{ textAlign: "center", marginBottom: 28 }}>
            Pick whichever configuration mode fits your runtime. Environment variables and YAML compose;
            env overrides YAML on a per-key basis.
          </p>

          <div className="card" style={{ marginBottom: 16 }}>
            <div className="card-header">Option 1 — environment variables (recommended for docker / k8s)</div>
            <div className="card-body">
              <pre>{`docker run -p 9192:9192 \\
  -e CLUSTERS_0_ID=local \\
  -e CLUSTERS_0_NAME='Local Kafka' \\
  -e CLUSTERS_0_BOOTSTRAPSERVERS=host.docker.internal:9092 \\
  -e AUTH_MODE=none \\
  kafka-lens:dev`}</pre>
              <p className="muted" style={{ marginTop: 10, fontSize: 12 }}>
                Index clusters with <code>CLUSTERS_0_*</code>, <code>CLUSTERS_1_*</code>, … For SASL/SSL,
                use <code>CLUSTERS_0_SECURITY_PROTOCOL</code>, <code>CLUSTERS_0_SECURITY_SASLMECHANISM</code>, etc.
              </p>
            </div>
          </div>

          <div className="card">
            <div className="card-header">Option 2 — YAML config file</div>
            <div className="card-body">
              <pre>{`# config.yml
clusters:
  - id: local
    name: Local Kafka
    bootstrapServers: localhost:9092

auth:
  mode: none`}</pre>
              <pre style={{ marginTop: 10 }}>{`java -jar build/libs/kafka-lens.jar \\
  --spring.config.additional-location=file:./config.yml`}</pre>
              <p className="muted" style={{ marginTop: 10, fontSize: 12 }}>
                See <code>config.example.yml</code> in the repo for the full reference (SASL/SSL,
                DLQ naming patterns, scan limits, auth modes, multi-cluster).
              </p>
            </div>
          </div>
        </div>
      </main>
    </div>
  );
}
