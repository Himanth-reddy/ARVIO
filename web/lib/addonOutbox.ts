import type { AuthClient } from './auth';
import type { InstalledAddon } from './types';
import { saveCloudAddons } from './cloud';
import { loadStored, saveStored } from './storage';
type Edit = { id: string; profileId: string | null; addons: InstalledAddon[]; removedIds: string[] };
const key = (id: string) => `arvio.web.addonOutbox.v1:${id}`;
const running = new Map<string, Promise<void>>();
export function pendingAddonSnapshot(auth: AuthClient) {
  return auth.session ? loadStored<Edit[]>(key(auth.session.userId), []).at(-1)?.addons : undefined;
}
export function hasPendingAddons(auth: AuthClient) {
  return !!auth.session && loadStored<Edit[]>(key(auth.session.userId), []).length > 0;
}
export function queueAddons(auth: AuthClient, addons: InstalledAddon[], profileId: string | null, removedIds: string[] = []) {
  if (!auth.session) return;
  const storageKey = key(auth.session.userId);
  const pending = loadStored<Edit[]>(storageKey, []);
  const previous = pending.find(e => e.profileId === profileId);
  const installed = new Set(addons.map(a => a.id));
  const edit = { id: crypto.randomUUID(), profileId, addons,
    removedIds: [...new Set([...(previous?.removedIds ?? []), ...removedIds])].filter(id => !installed.has(id)) };
  saveStored(storageKey, [...pending.filter(e => e.profileId !== profileId), edit]);
  if (!loadStored<Edit[]>(storageKey, []).some(e => e.id === edit.id)) throw new Error('Device storage is full. Keep this page open and retry saving.');
}
export async function flushAddonOutbox(auth: AuthClient) {
  const id = auth.session?.userId;
  if (!id) return;
  if (running.has(id)) return running.get(id);
  const task = (async () => {
    while (auth.session?.userId === id) {
      const edit = loadStored<Edit[]>(key(id), [])[0];
      if (!edit) return;
      await saveCloudAddons(auth, edit.addons, edit.profileId, { removedIds: edit.removedIds });
      saveStored(key(id), loadStored<Edit[]>(key(id), []).filter(e => e.id !== edit.id));
    }
  })();
  running.set(id, task);
  try { await task; } finally { if (running.get(id) === task) running.delete(id); }
}
