"use client";
import { useTranslation } from "@/lib/i18n";


import { Plus, Server, Sparkles, Tv } from "lucide-react";
import { useState } from "react";
import { useApp } from "@/lib/store";
import { addonProvidesStreams, requestSourceSettings, sourceSetupState } from "@/lib/sourceSetup";

function resourceLabel(resources: unknown) {
  if (!Array.isArray(resources)) return "manifest";
  const names = resources.map((resource) => {
    if (typeof resource === "string") return resource;
    if (resource && typeof resource === "object") {
      const name = (resource as { name?: unknown }).name;
      return typeof name === "string" ? name : "";
    }
    return "";
  }).filter(Boolean);
  return names.join(", ") || "manifest";
}

export function AddonsScreen() {
  const translateUi = useTranslation();
  const { addons, addonsReady, settings, setSection, installAddon, removeAddon, setToast } = useApp();
  const [url, setUrl] = useState("");
  const [installing, setInstalling] = useState(false);
  const sources = sourceSetupState(addons, settings);
  const openSettings = (target: "homeserver" | "tv") => {
    requestSourceSettings(target);
    setSection("settings");
  };
  const install = async () => {
    if (!url.trim()) {
      setToast("Enter an addon manifest URL first.");
      return;
    }
    setInstalling(true);
    try {
      await installAddon(url.trim());
      setUrl("");
      setToast("Addon installed.");
    } catch (error) {
      setToast(error instanceof Error ? error.message : "Could not install addon.");
    } finally {
      setInstalling(false);
    }
  };
  return (
    <div className="screen has-section-heading">
      <section className="section-heading">
        <p className="eyebrow">{translateUi("Sources")}</p>
        <h2>{translateUi("Addons")}</h2>
      </section>
      {addonsReady && !sources.hasOnDemand && <section className="source-setup-panel" aria-labelledby="addon-setup-title">
        <h3 id="addon-setup-title">{translateUi("Connect your media sources")}</h3>
        {sources.catalogOnly && <p className="source-setup-warning">{translateUi("None of your enabled addons provide playback sources.")}</p>}
        <p>{translateUi("ARVIO does not include a film or TV subscription. Connect your Plex, Emby or Jellyfin server in Settings, or add a compatible addon supplied by a service you are authorized to use.")}</p>
        <div className="source-setup-actions">
          <button type="button" className="secondary" onClick={() => openSettings("homeserver")}><Server size={17} />{translateUi("Home Server")}</button>
          <button type="button" className="secondary" onClick={() => openSettings("tv")}><Tv size={17} />{translateUi("Live TV")}</button>
        </div>
      </section>}
      <label htmlFor="addon-manifest-url" className="addon-install-label">{translateUi("Addon settings")}</label>
      <form className="inline-form wide" onSubmit={(event) => { event.preventDefault(); if (!installing) void install(); }}>
        <input id="addon-manifest-url" value={url} onChange={(event) => setUrl(event.target.value)} autoCapitalize="none" autoCorrect="off" spellCheck={false} inputMode="url" placeholder={translateUi("https://addon.example.com/manifest.json")} />
        <button type="submit" className="primary" disabled={installing || !url.trim()}><Plus size={18} /> {installing ? translateUi("Installing...") : translateUi("Install")}</button>
      </form>
      <p className="addon-install-help">{translateUi("Install Stremio-compatible addons by URL above.")}</p>
      <div className="addon-grid">
        {addons.map((addon) => (
          <article className="addon-tile" key={addon.id}>
            <Sparkles size={24} />
            <h3>{addon.name}</h3>
            <p>{addon.description || addon.manifestUrl}</p>
            <div className="chips">
              <span>{addon.version}</span>
              <span>{resourceLabel(addon.resources)}</span>
              {addonProvidesStreams(addon) && <span>{translateUi("Streams")}</span>}
            </div>
            <button type="button" className="secondary" onClick={() => removeAddon(addon)}>{translateUi("Remove")}</button>
          </article>
        ))}
      </div>
    </div>
  );
}
