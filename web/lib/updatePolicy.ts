const BUILD_STAMP = /^\d{10,16}$/;

export function isNewerBuild(remote: unknown, current: string): remote is string | number {
  // Older manifests may be stale CDN responses or an intentional rollback.
  // A rollback requires a manual refresh; never repeatedly navigate backwards.
  const candidate = String(remote ?? "");
  if (!BUILD_STAMP.test(candidate) || !BUILD_STAMP.test(current)) return false;
  const next = Number(candidate), previous = Number(current);
  return Number.isSafeInteger(next) && Number.isSafeInteger(previous) && next > previous;
}

export function shouldApplyUpdate({ current, remote, attempted, playerPresent, now, lastReloadAt }: {
  current: string; remote: unknown; attempted: string; playerPresent: boolean; now: number; lastReloadAt: number;
}) {
  return isNewerBuild(remote, current) && String(remote) !== attempted && !playerPresent &&
    (!lastReloadAt || now - lastReloadAt >= 4 * 60 * 1000);
}
