import type { AuthClient } from "./auth";
import { config } from "./config";
import { jsonRequest } from "./http";

export type PremiumFunnelEvent =
  | "paywall_view"
  | "account_connected"
  | "trial_requested"
  | "trial_start_failed"
  | "checkout_opened"
  | "membership_link_started"
  | "membership_linked"
  | "membership_link_failed"
  | "first_playback"
  | "external_playback_requested"
  | "download_requested"
  | "download_handoff"
  | "download_failed";

const ATTRIBUTION_KEY = "arvio.premium.attribution.v1";
export const TRIAL_INTENT_KEY = "arvio.premium.trial-intent.v1";
const inFlight = new Set<string>();

function browserStorage(kind: "sessionStorage" | "localStorage") {
  try { return typeof window === "undefined" ? undefined : window[kind]; } catch { return undefined; }
}

function storageGet(storage: Storage | undefined, key: string) {
  try { return storage?.getItem(key) ?? null; } catch { return null; }
}

function storageSet(storage: Storage | undefined, key: string, value: string) {
  try { storage?.setItem(key, value); } catch { /* storage is optional */ }
}

function clean(value: string | null, max = 80) {
  return String(value || "").replace(/[^a-z0-9._-]/gi, "").slice(0, max);
}

export function capturePremiumAttribution() {
  if (typeof window === "undefined") return {};
  const params = new URLSearchParams(window.location.search);
  const existing = (() => {
    try { return JSON.parse(storageGet(browserStorage("localStorage"), ATTRIBUTION_KEY) || "{}"); } catch { return {}; }
  })() as Record<string, string>;
  let referrer = existing.referrer || "";
  try { referrer = document.referrer ? new URL(document.referrer).hostname : referrer; } catch { /* ignore invalid referrers */ }
  const next = {
    source: clean(params.get("utm_source") || existing.source || "direct"),
    medium: clean(params.get("utm_medium") || existing.medium || "web"),
    campaign: clean(params.get("utm_campaign") || existing.campaign || "premium"),
    content: clean(params.get("utm_content") || existing.content || "unspecified"),
    referrer: clean(referrer)
  };
  storageSet(browserStorage("localStorage"), ATTRIBUTION_KEY, JSON.stringify(next));
  return next;
}

export async function trackPremiumEvent(
  auth: AuthClient,
  eventName: PremiumFunnelEvent,
  metadata: Record<string, string | number | boolean> = {},
  oncePerSession = false
) {
  if (!auth.session) return false;
  const sessionKey = `arvio.premium.session.${auth.session.userId}.${eventName}`;
  const sessionStore = browserStorage("sessionStorage");
  if (oncePerSession && storageGet(sessionStore, sessionKey)) return true;
  if (oncePerSession && inFlight.has(sessionKey)) return false;
  if (oncePerSession) inFlight.add(sessionKey);
  try {
    const token = await auth.accessToken();
    await jsonRequest(`${config.netlifyBackendUrl.replace(/\/+$/, "")}/premium-funnel-event`, {
      method: "POST",
      headers: { Authorization: `Bearer ${token}` },
      body: JSON.stringify({
        event_name: eventName,
        metadata: { ...capturePremiumAttribution(), ...metadata }
      })
    });
    if (oncePerSession) storageSet(sessionStore, sessionKey, "1");
    return true;
  } catch {
    return false;
  } finally {
    if (oncePerSession) inFlight.delete(sessionKey);
  }
}

export async function trackPremiumMilestone(
  auth: AuthClient,
  eventName: Extract<PremiumFunnelEvent, "account_connected" | "first_playback">,
  metadata: Record<string, string | number | boolean> = {}
) {
  const accountId = auth.session?.userId;
  if (!accountId || typeof window === "undefined") return false;
  const key = `arvio.premium.milestone.${eventName}.${accountId}`;
  if (storageGet(browserStorage("localStorage"), key)) return true;
  const recorded = await trackPremiumEvent(auth, eventName, metadata);
  if (recorded) storageSet(browserStorage("localStorage"), key, "1");
  return recorded;
}
