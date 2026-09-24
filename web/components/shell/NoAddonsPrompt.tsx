"use client";

import { Puzzle, Server, Tv, X } from "lucide-react";
import { useEffect, useState } from "react";
import { useTranslation } from "@/lib/i18n";
import { useApp } from "@/lib/store";
import { requestSourceSettings, sourceSetupState } from "@/lib/sourceSetup";

const DISMISS_KEY_PREFIX = "arvio.web.noAddonsPrompt.v1:";

export function NoAddonsPrompt() {
  const translateUi = useTranslation();
  const { view, section, setSection, activeProfile, addons, addonsReady, settings } = useApp();
  const [dismissal, setDismissal] = useState<{ profileId: string; dismissed: boolean } | null>(null);
  const profileId = activeProfile?.id ?? "";

  useEffect(() => {
    let dismissed = false;
    try { dismissed = window.sessionStorage.getItem(`${DISMISS_KEY_PREFIX}${profileId}`) === "1"; } catch { /* storage is optional */ }
    setDismissal({ profileId, dismissed });
  }, [profileId]);

  const sources = sourceSetupState(addons, settings);
  if (view !== "app" || section !== "home" || !profileId || !addonsReady || sources.hasAny ||
    dismissal?.profileId !== profileId || dismissal.dismissed) return null;

  const dismiss = () => {
    try { window.sessionStorage.setItem(`${DISMISS_KEY_PREFIX}${profileId}`, "1"); } catch { /* storage is optional */ }
    setDismissal({ profileId, dismissed: true });
  };
  const openSettings = (target: "homeserver" | "tv") => {
    requestSourceSettings(target);
    setSection("settings");
  };

  return <aside className="source-setup-card" aria-labelledby="source-setup-title">
    <button type="button" className="no-addons-close" onClick={dismiss} aria-label={translateUi("Close")}><X size={20} /></button>
    <h2 id="source-setup-title"><Puzzle size={22} />{translateUi("Connect your media sources")}</h2>
    <p>{translateUi("ARVIO does not include a film or TV subscription. Connect your Plex, Emby or Jellyfin server in Settings, or add a compatible addon supplied by a service you are authorized to use.")}</p>
    {sources.catalogOnly && <p className="source-setup-warning">{translateUi("None of your enabled addons provide playback sources.")}</p>}
    <div className="source-setup-actions">
      <button type="button" className="primary" onClick={() => setSection("addons")}><Puzzle size={17} />{translateUi("Addons")}</button>
      <button type="button" className="secondary" onClick={() => openSettings("homeserver")}><Server size={17} />{translateUi("Home Server")}</button>
      <button type="button" className="secondary" onClick={() => openSettings("tv")}><Tv size={17} />{translateUi("Live TV")}</button>
    </div>
  </aside>;
}
