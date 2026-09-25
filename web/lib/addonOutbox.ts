import type { AuthClient } from './auth';
import type { InstalledAddon } from './types';
import { saveCloudAddons } from './cloud';
import { recordAddonChanges, type AddonChanges } from './addonChanges';
import { loadStored, saveStored } from './storage';
type Edit = { id: string; profileId: string | null; addons: InstalledAddon[]; removedIds: string[]; changes?: AddonChanges };
const key = (id: string) => `arvio.web.addonOutbox.v1:${id}`;
const running = new Map<string, Promise<void>>();
export function pendingAddonSnapshot(auth: AuthClient) {
  return auth.session ? loadStored<Edit[]>(key(auth.session.userId), []).at(-1)?.addons : undefined;
}
export function hasPendingAddons(auth: AuthClient) {
  return !!auth.session && loadStored<Edit[]>(key(auth.session.userId), []).length > 0;
}
export function queueAddons(auth: AuthClient, addons: InstalledAddon[], profileId: string | null, removedIds: string[] = [], addedIds: string[] = []) {
  if (!auth.session) return;
  const storageKey = key(auth.session.userId);
  const pending = loadStored<Edit[]>(storageKey, []);
  const previous = pending.find(e => e.profileId === profileId);
  const installed = new Set(addons.map(a => a.id));
  const timestamp = Math.max(Date.now(), ...pending.flatMap(e => Object.values(e.changes ?? {}).map(c => c.updatedAt + 1)));
  const priorChanges = previous?.changes ?? recordAddonChanges({}, [], previous?.removedIds ?? [], timestamp - 1);
  const changes = recordAddonChanges(priorChanges, addedIds, removedIds, timestamp);
  const edit = { id: crypto.randomUUID(), profileId, addons, changes,
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
      const pending = loadStored<Edit[]>(key(id), []);
      let edit = pending[0];
      if (!edit) return;
      if (!edit.changes) {
        edit = { ...edit, changes: recordAddonChanges({}, [], edit.removedIds, Date.now()) };
        saveStored(key(id), [edit, ...pending.slice(1)]);
        if (!loadStored<Edit[]>(key(id), [])[0]?.changes) throw new Error('Could not persist addon edits before sync');
      }
      await saveCloudAddons(auth, edit.addons, edit.profileId, { removedIds: edit.removedIds, changes: edit.changes });
      saveStored(key(id), loadStored<Edit[]>(key(id), []).filter(e => e.id !== edit.id));
    }
  })();
  running.set(id, task);
  try { await task; } finally { if (running.get(id) === task) running.delete(id); }
}
