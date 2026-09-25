export type AddonChange = { updatedAt: number; removed: boolean };
export type AddonChanges = Record<string, AddonChange>;

export function mergeAddonChanges(...sources: unknown[]): AddonChanges {
  const result: AddonChanges = Object.create(null);
  for (const source of sources) {
    if (!source || typeof source !== 'object' || Array.isArray(source)) continue;
    for (const [id, value] of Object.entries(source)) {
      const change = value as AddonChange | null;
      if (!id.trim() || !change || !Number.isSafeInteger(change.updatedAt) || change.updatedAt <= 0 ||
          typeof change.removed !== 'boolean' || (id === 'opensubtitles' && change.removed)) continue;
      const previous = result[id];
      if (!previous || change.updatedAt > previous.updatedAt ||
          (change.updatedAt === previous.updatedAt && change.removed)) result[id] = { ...change };
    }
  }
  return result;
}

export function recordAddonChanges(previous: AddonChanges, added: string[], removed: string[], timestamp: number): AddonChanges {
  return mergeAddonChanges(previous, Object.fromEntries([
    ...added.map(id => [id, { updatedAt: timestamp, removed: false }]),
    ...removed.map(id => [id, { updatedAt: timestamp, removed: true }])
  ]));
}
