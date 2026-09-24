"use client";

import { useEffect } from "react";
import { shouldApplyUpdate } from "@/lib/updatePolicy";

// iOS home-screen webapps cache the start page HTML for days and there is no
// service worker to invalidate it — users end up running old bundles long
// after a deploy. This watcher compares the bundle's baked-in build stamp with
// the server's /version.json (no-store) on boot, on return-to-foreground and
// every 10 minutes, and reloads the app when a newer deploy exists.
const RELOAD_GUARD_KEY = "arvio.web.lastUpdateReload";
const ATTEMPTED_VERSION_KEY = "arvio.web.attemptedUpdateVersion";

export function UpdateWatcher() {
  useEffect(() => {
    const baked = process.env.NEXT_PUBLIC_BUILD_STAMP;
    if (!baked) return undefined;
    let disposed = false;
    let checking = false;
    let reloading = false;
    let attempted = new URLSearchParams(window.location.search).get("_v") || "";
    try { attempted ||= window.localStorage.getItem(ATTEMPTED_VERSION_KEY) || ""; } catch { /* storage is optional */ }

    // Strip the cache-bust param left by a prior update reload so the URL stays
    // clean and doesn't keep growing across updates.
    if (window.location.search.includes("_v=")) {
      const clean = new URL(window.location.href);
      clean.searchParams.delete("_v");
      window.history.replaceState(window.history.state, "", clean.toString());
    }

    const check = async () => {
      if (checking || reloading) return;
      checking = true;
      try {
        const response = await fetch("/version.json", { cache: "no-store" });
        if (!response.ok) return;
        const payload = (await response.json()) as { v?: string | number };
        if (disposed) return;
        // A paused/buffering player or an embedded trailer must not be closed by
        // an update. Wait for the player to close and a later scheduled check.
        const playerPresent = Boolean(document.querySelector('video, iframe[src*="youtube.com"], iframe[src*="youtube-nocookie.com"]'));
        let last = 0;
        try { last = Number(window.localStorage.getItem(RELOAD_GUARD_KEY) ?? 0); } catch { /* storage is optional */ }
        if (!shouldApplyUpdate({ current: baked, remote: payload?.v, attempted, playerPresent, now: Date.now(), lastReloadAt: last })) return;
        // Remember this exact version, not merely a four-minute cooldown. A
        // stale response can never repeatedly reset the user's current screen.
        attempted = String(payload.v);
        reloading = true;
        try {
          window.localStorage.setItem(RELOAD_GUARD_KEY, String(Date.now()));
          window.localStorage.setItem(ATTEMPTED_VERSION_KEY, attempted);
        } catch { /* the _v URL also guards the next load when storage is blocked */ }
        // location.reload() can re-serve cached HTML on iOS; navigating to a
        // fresh URL forces the document to be re-fetched so it references the
        // newest hashed CSS/JS. The cache-bust param is stripped on load.
        const url = new URL(window.location.href);
        url.searchParams.set("_v", String(payload.v));
        window.location.replace(url.toString());
      } catch {
        // Offline or blocked — retry on the next trigger.
      } finally {
        checking = false;
      }
    };

    const onVisible = () => {
      if (document.visibilityState === "visible") void check();
    };
    void check();
    document.addEventListener("visibilitychange", onVisible);
    const timer = window.setInterval(() => void check(), 10 * 60 * 1000);
    return () => {
      disposed = true;
      document.removeEventListener("visibilitychange", onVisible);
      window.clearInterval(timer);
    };
  }, []);

  return null;
}
