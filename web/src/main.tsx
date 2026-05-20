import React from "react";
import ReactDOM from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import App from "./App";
import { AppInfoProvider } from "./AppInfoContext";
import "./styles.css";

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <BrowserRouter>
      <AppInfoProvider>
        <App />
      </AppInfoProvider>
    </BrowserRouter>
  </React.StrictMode>,
);
