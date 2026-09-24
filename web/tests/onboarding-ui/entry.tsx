import React from "react";
import { createRoot } from "react-dom/client";
import { LanguageProvider } from "../../lib/i18n";
import { NoAddonsPrompt } from "../../components/shell/NoAddonsPrompt";
import { AddonsScreen } from "../../components/addons/AddonsScreen";
import { requestedSourceSettings } from "../../lib/sourceSetup";
import { useApp } from "./store";
import "../../app/globals.css";
import "../../app/source-setup.css";
function Fixture() {
  const { section, toast } = useApp();
  return <main className="app-shell"><section className="content">
    {section === "home" && <><NoAddonsPrompt /><p style={{ padding: 24 }}>Fixture home content remains available.</p></>}
    {section === "addons" && <AddonsScreen />}
    {section === "settings" && <output data-testid="settings-target">{requestedSourceSettings()}</output>}
    {toast && <p role="status">{toast}</p>}
  </section></main>;
}
createRoot(document.getElementById("root")!).render(<LanguageProvider language={new URLSearchParams(location.search).get("lang") || "en"}><Fixture /></LanguageProvider>);
