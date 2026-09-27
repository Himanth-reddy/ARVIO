import type { AuthClient } from "./auth";
import { saveCloudSettings } from "./cloud";
import { loadStored, saveStored } from "./storage";
import type { AppSettings } from "./types";

type Pending = { id: string; profileId: string; settings: AppSettings; baseline: AppSettings | null; changedAt: number };
const keyFor = (userId: string) => `arvio.web.settingsOutbox.v1:${userId}`;
const running = new Map<string, Promise<void>>();

export function hasPendingSettings(auth: AuthClient, profileId?: string | null) {
  if (!auth.session) return false;
  return loadStored<Pending[]>(keyFor(auth.session.userId), []).some((entry) => !profileId || entry.profileId === profileId);
}

/** Overlay only unsaved edits onto a pull; stale browser defaults are not edits. */
export function settingsWithPendingEdits(auth: AuthClient, profileId: string | null, remote: AppSettings, beforePull: AppSettings, current: AppSettings): AppSettings {
  const pending = auth.session
    ? loadStored<Pending[]>(keyFor(auth.session.userId), []).find(entry => entry.profileId === profileId)
    : undefined;
  const baseline = pending?.baseline ?? beforePull;
  const result = { ...remote };
  for (const field of Object.keys(current) as Array<keyof AppSettings>) {
    if (JSON.stringify(current[field]) !== JSON.stringify(baseline[field])) {
      Object.assign(result, { [field]: current[field] });
    }
  }
  return result;
}

export function queueSettings(auth: AuthClient, profileId: string, settings: AppSettings, baseline: AppSettings | null) {
  if (!auth.session || !baseline) return;
  const key = keyFor(auth.session.userId);
  const entries = loadStored<Pending[]>(key, []);
  const previous = entries.find((entry) => entry.profileId === profileId);
  // Keep a reversion explicit even if the earlier edit is already being sent.
  // Comparing only with the original baseline would silently turn it into a no-op.
  const pendingBaseline = { ...(previous?.baseline ?? baseline) };
  if (previous) {
    for (const field of Object.keys(settings) as Array<keyof AppSettings>) {
      const previouslyEdited = JSON.stringify(previous.settings[field]) !== JSON.stringify(pendingBaseline[field]);
      if (!previouslyEdited) {
        // A fresh pull may adopt another device's value for an untouched field.
        // Rebase that field so autosave does not mistake hydration for an edit.
        Object.assign(pendingBaseline, { [field]: baseline[field] });
      } else if (JSON.stringify(settings[field]) === JSON.stringify(pendingBaseline[field]) &&
          JSON.stringify(settings[field]) !== JSON.stringify(previous.settings[field])) {
        Object.assign(pendingBaseline, { [field]: previous.settings[field] });
      }
    }
  }
  const next = { id: crypto.randomUUID(), profileId, settings, baseline: pendingBaseline, changedAt: Date.now() };
  saveStored(key, [...entries.filter((entry) => entry.profileId !== profileId), next]);
  if (!loadStored<Pending[]>(key, []).some((entry) => entry.id === next.id)) throw new Error("Device storage is full. Keep this page open and retry saving.");
}

export async function flushSettingsOutbox(auth: AuthClient): Promise<void> {
  const userId = auth.session?.userId;
  if (!userId) return;
  const current = running.get(userId);
  if (current) return current;
  const promise = (async () => {
    const key = keyFor(userId);
    while (auth.session?.userId === userId) {
      const entry = loadStored<Pending[]>(key, [])[0];
      if (!entry) return;
      // Older clients queued full default snapshots before profile hydration.
      // They have no acknowledged baseline, so cannot safely describe user edits.
      if (!entry.baseline) {
        saveStored(key, loadStored<Pending[]>(key, []).filter(pending => pending.id !== entry.id));
        continue;
      }
      await saveCloudSettings(auth, entry.settings, [], entry.profileId, [], entry.baseline, entry.changedAt);
      // Do not acknowledge a newer edit queued while the request was in flight.
      saveStored(key, loadStored<Pending[]>(key, []).filter((pending) => pending.id !== entry.id));
    }
  })();
  running.set(userId, promise);
  try { await promise; } finally { if (running.get(userId) === promise) running.delete(userId); }
}
