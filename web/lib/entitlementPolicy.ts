import type { EntitlementState } from "./entitlement";

export function currentEntitlement(state: EntitlementState | null, now = Date.now()): EntitlementState | null {
  if (!state?.entitled || !state.expiresAt) return state;
  const expiry = Date.parse(state.expiresAt);
  if (Number.isFinite(expiry) && expiry > now) return state;
  return { ...state, entitled: false, reason: "expired", trialAvailable: false };
}

// Recheck expiry even if the tab stays focused for the whole trial. Cap long
// deadlines to avoid overflowing browser timers and to notice paid renewals.
export function entitlementCheckDelay(state: EntitlementState | null, now = Date.now()): number | null {
  if (!state?.entitled) return null;
  const expiry = state.expiresAt ? Date.parse(state.expiresAt) : Infinity;
  return Math.max(1000, Math.min(15 * 60_000, Number.isFinite(expiry) ? expiry - now + 100 : 15 * 60_000));
}
