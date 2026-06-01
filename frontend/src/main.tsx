import React from "react";
import ReactDOM from "react-dom/client";

import App from "@/App";
import { useAuthStore } from "@/stores/authStore";
import { useFeatureStore } from "@/stores/featureStore";
import { useThemeStore } from "@/stores/themeStore";
import "@/styles/tokens.css";
import "@/styles/globals.css";

useThemeStore.getState().initialize();
useAuthStore.getState().checkAuth();
useFeatureStore.getState().loadCapabilities().catch(() => null);

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
