import { jsonRequest, proxiedUrl } from "./http";
import { guideSports, isOnAir, safeSportsImage, sportsEventIdentity, sportsQualifierKey, type SportsGuideEvent } from "./sportsGuide";
import type { AddonCatalog, InstalledAddon } from "./types";
import { attachSportsArtwork, type SportsEventArtwork } from "./sportsArtwork";
import { isInformationalAddonStream } from "./addonStreamInfo";

export interface SportsAddonEvent {
  key: string; installation: string; addonId: string; addonName: string; type: string; eventId: string;
  title: string; genres: string[]; startsAt?: number; live: boolean; observedAt: number; artwork?: string;
}
export interface SportsAddonStream { name: string; url: string; headers: Record<string, string>; external: boolean; description?: string }
const sports = /sport|football|soccer|basketball|tennis|motorsport|formula|racing|rugby|hockey|baseball|boxing|ufc|mma|cricket|golf/i;
export function sportsEventCatalogs(addon: InstalledAddon): AddonCatalog[] {
  if (addon.enabled === false || !addon.resources.some(r => /^(stream|streams)$/i.test(typeof r === "string" ? r : r.name))) return [];
  const sportsAddon = sports.test(`${addon.name} ${addon.description ?? ""} ${addon.types?.join(" ")}`)
    || addon.catalogs.some(c => sports.test(`${c.type} ${c.id} ${c.name}`));
  return addon.catalogs.filter(c => (sports.test(`${c.type} ${c.id} ${c.name}`) || sportsAddon && /live|event|today|upcoming/i.test(`${c.id} ${c.name}`))
    && !/replay|network|24\/7|24_7|highlights/i.test(`${c.id} ${c.name}`)
    && !c.extra?.some(e => e.isRequired && e.name !== "skip"))
    .sort((a, b) => rank(a) - rank(b));
}
const rank = (c: AddonCatalog) => /live/i.test(c.id) ? 0 : /today|upcoming/i.test(c.id) ? 1 : 2;
export function sportsAddonUrl(manifest: string, resource: string, type: string, id: string, skip?: number) {
  const url = new URL(manifest.replace(/^stremio:\/\//, "https://"));
  if (!/^https?:$/.test(url.protocol)) throw new Error("Invalid add-on URL");
  url.pathname = `${url.pathname.replace(/\/manifest\.json$/, "").replace(/\/+$/, "")}/${resource}/${encodeURIComponent(type)}/${encodeURIComponent(id)}${skip === undefined ? "" : `/skip=${skip}`}.json`;
  return url.toString();
}
export function toSportsAddonEvent(meta: Record<string, unknown>, addon: InstalledAddon, catalog: AddonCatalog, now: number): SportsAddonEvent | null {
  if (typeof meta.id !== "string" || !meta.id || typeof meta.name !== "string") return null;
  const cleanStatus = (value: string) => value.replace(/^[^\p{L}\p{N}]+/u, "").trim();
  const cleanName = cleanStatus(meta.name);
  const title = cleanName.replace(/^LIVE\s*:?\s+/i, "").trim();
  const release = typeof meta.releaseInfo === "string" ? meta.releaseInfo : "";
  const description = typeof meta.description === "string" ? meta.description : "";
  if (description.split("\n").some(line => /^(finished|ended|cancelled|canceled|postponed|replay)\b/i.test(cleanStatus(line)))) return null;
  if (!title || /^(leaf:|recap:)/.test(meta.id) || release === "24/7" || /\b(replay|finished|ended|cancelled|canceled|postponed|highlights)\b/i.test(`${title} ${release}`)) return null;
  let startsAt = typeof meta.released === "string" ? Date.parse(meta.released) : NaN;
  if (!Number.isFinite(startsAt)) {
    const utc = `${release}\n${description}`.match(/\b(\d{1,2} [A-Za-z]{3} \d{4})\s*[·,]?\s*(\d{2}:\d{2})\s*UTC\b/);
    if (utc) startsAt = Date.parse(`${utc[1]} ${utc[2]} UTC`);
  }
  const live = /^(LIVE|LIVE NOW)$/i.test(cleanStatus(release)) || /^LIVE\s*:\s*/i.test(cleanName)
    || description.split("\n").some(line => /^(LIVE|LIVE NOW)$/i.test(cleanStatus(line)));
  if (!Number.isFinite(startsAt) && !live) return null;
  if (Number.isFinite(startsAt) && (startsAt < now - 86_400_000 || startsAt > now + 172_800_000)) return null;
  const type = typeof meta.type === "string" && meta.type ? meta.type : catalog.type;
  return { key: JSON.stringify([addon.manifestUrl, type, meta.id]), installation: addon.manifestUrl,
    addonId: addon.id, addonName: addon.name, type, eventId: meta.id, title,
    genres: Array.isArray(meta.genres) ? meta.genres.filter((g): g is string => typeof g === "string") : [],
    startsAt: Number.isFinite(startsAt) ? startsAt : undefined, live, observedAt: now,
    artwork: typeof meta.background === "string" ? safeSportsImage(meta.background) : undefined };
}
// Allow for the guide's periodic clock tick preceding a freshly fetched live status.
export const addonEventIsLive = (event: SportsAddonEvent, now: number) => event.live && now - event.observedAt >= -60_000 && now - event.observedAt < 300_000 && (event.startsAt === undefined || event.startsAt <= now);

export function mergeSportsAddonEvent(previous: SportsAddonEvent, incoming: SportsAddonEvent): SportsAddonEvent {
  if (previous.key !== incoming.key || sportsEventIdentity(previous.title) !== sportsEventIdentity(incoming.title)
    || previous.startsAt !== undefined && incoming.startsAt !== undefined && Math.abs(previous.startsAt - incoming.startsAt) > 7_200_000) return incoming;
  const live = [previous, incoming].filter(e => e.live).sort((a, b) => b.observedAt - a.observedAt)[0];
  return { ...incoming, startsAt: incoming.startsAt ?? previous.startsAt, genres: incoming.genres.length ? incoming.genres : previous.genres,
    artwork: incoming.artwork || previous.artwork, live: Boolean(live), observedAt: live?.observedAt ?? incoming.observedAt };
}

export function attachSportsAddonSources(events: SportsGuideEvent[], sources: SportsAddonEvent[], now: number, artwork: SportsEventArtwork[] = []): SportsGuideEvent[] {
  const output = events.map(e => ({ ...e, addonSources: [] as SportsAddonEvent[] }));
  const byIdentity = new Map<string, number[]>();
  output.forEach((e, i) => { const key = sportsEventIdentity(e.title); byIdentity.set(key, [...(byIdentity.get(key) ?? []), i]); });
  const unique = new Map<string, SportsAddonEvent>();
  sources.forEach(s => unique.set(s.key, unique.has(s.key) ? mergeSportsAddonEvent(unique.get(s.key)!, s) : s));
  for (const source of unique.values()) {
    const sportId = guideSports.find(s => s.pattern.test(source.genres.join(" ").replace(/_/g, " ")))?.id
      ?? guideSports.find(s => s.pattern.test(source.title))?.id ?? "other";
    const identity = sportsEventIdentity(source.title);
    const candidates = (byIdentity.get(identity) ?? []).filter(i => {
      const e = output[i];
      return !e.channelOnly && !["finished", "postponed"].includes(e.fixture?.status ?? "")
        && (e.sportId === sportId || sportId === "other" || e.sportId === "other") && sportsQualifierKey(e.title) === sportsQualifierKey(source.title)
        && (source.startsAt !== undefined ? Math.abs(e.programme.startUtcMillis - source.startsAt) <= 7_200_000 : addonEventIsLive(source, now) && isOnAir(e, now));
    });
    const distance = (i: number) => source.startsAt === undefined ? Infinity : Math.abs(output[i].programme.startUtcMillis - source.startsAt);
    const ranked = candidates.sort((a, b) => distance(a) - distance(b));
    const match = ranked.length === 1 ? ranked[0] : ranked.length > 1 && new Set(ranked.map(i => output[i].sportId)).size === 1
      && distance(ranked[0]) < distance(ranked[1]) ? ranked[0] : undefined;
    if (match !== undefined) {
      const e = output[match]; output[match] = { ...e, addonSources: [...e.addonSources, source], artwork: e.artwork || (e.teamArtwork ? undefined : source.artwork) };
    } else if (addonEventIsLive(source, now) || source.startsAt !== undefined && source.startsAt > now) {
      const start = source.startsAt ?? source.observedAt;
      byIdentity.set(identity, [...(byIdentity.get(identity) ?? []), output.length]);
      output.push({ id: `addon:${source.key}`, title: source.title, sportId,
        programme: { title: source.title, startUtcMillis: start, endUtcMillis: start }, channels: [], artwork: source.artwork, addonSources: [source] });
    }
  }
  // Apply subscription artwork after adding standalone add-on events as well.
  return attachSportsArtwork(output, artwork.filter(item => ["TheSportsDB", "ESPN", "MLB"].includes(item.source ?? "")), true);
}

const cache = new Map<string, { at: number; events: SportsAddonEvent[] }>();
export async function loadSportsAddonEvents(addons: InstalledAddon[], signal: AbortSignal, publish: (events: SportsAddonEvent[]) => void) {
  const jobs = addons.flatMap(addon => sportsEventCatalogs(addon).map(catalog => ({ addon, catalog })));
  const results = new Map<string, SportsAddonEvent>();
  const emit = (events: SportsAddonEvent[]) => { if (!signal.aborted) { events.forEach(e => results.set(e.key, results.has(e.key) ? mergeSportsAddonEvent(results.get(e.key)!, e) : e)); publish([...results.values()]); } };
  let next = 0;
  await Promise.all(Array.from({ length: Math.min(3, jobs.length) }, async () => {
    while (next < jobs.length && !signal.aborted) {
      const { addon, catalog } = jobs[next++];
      const key = JSON.stringify([addon.manifestUrl, addon.version, catalog.type, catalog.id]);
      const now = Date.now(), cached = cache.get(key);
      if (cached && now - cached.at >= 0 && now - cached.at < 120_000) { emit(cached.events); continue; }
      const items = new Map<string, SportsAddonEvent>(), seen = new Set<string>();
      const paginated = catalog.extra?.some(e => e.name === "skip");
      let skip = 0;
      let failed = false;
      for (let page = 0; page < 50 && !signal.aborted; page++) {
        try {
          const payload = await jsonRequest<{ metas?: Record<string, unknown>[]; items?: Record<string, unknown>[] }>(
            proxiedUrl(sportsAddonUrl(addon.manifestUrl, "catalog", catalog.type, catalog.id, paginated ? skip : undefined)),
            { signal: AbortSignal.any([signal, AbortSignal.timeout(8_000)]) });
          const metas = payload.metas ?? payload.items;
          if (!Array.isArray(metas) || !metas.length) break;
          const ids = metas.filter(m => typeof m?.id === "string").map(m => m.id as string);
          if (ids.every(id => seen.has(id))) break;
          ids.forEach(id => seen.add(id));
          metas.forEach(meta => { const e = meta && toSportsAddonEvent(meta, addon, catalog, now); if (e) items.set(e.key, items.has(e.key) ? mergeSportsAddonEvent(items.get(e.key)!, e) : e); });
          emit([...items.values()]);
          if (!paginated) break;
          skip += metas.length;
        } catch { failed = true; break; }
      }
      if (!signal.aborted && !failed) { cache.set(key, { at: now, events: [...items.values()] }); if (cache.size > 100) cache.delete(cache.keys().next().value!); }
    }
  }));
  return [...results.values()];
}

export async function resolveSportsAddon(event: SportsAddonEvent, addons: InstalledAddon[], signal: AbortSignal): Promise<SportsAddonStream[]> {
  const addon = addons.find(a => a.enabled !== false && a.id === event.addonId && a.manifestUrl === event.installation);
  if (!addon) return [];
  const payload = await jsonRequest<{ streams?: { name?: string; title?: string; url?: string; externalUrl?: string; ytId?: string; headers?: Record<string, string>;
    behaviorHints?: { headers?: Record<string, string>; proxyHeaders?: { request?: Record<string, string> } } }[] }>(
    proxiedUrl(sportsAddonUrl(addon.manifestUrl, "stream", event.type, event.eventId)), { signal: AbortSignal.any([signal, AbortSignal.timeout(15_000)]) });
  const results = (payload.streams ?? []).flatMap(s => {
    if (isInformationalAddonStream(s)) return [];
    const url = s.url || s.externalUrl || (s.ytId ? `https://www.youtube.com/watch?v=${encodeURIComponent(s.ytId)}` : "");
    if (!/^https?:\/\//i.test(url)) return [];
    return [{ name: s.name || s.title || addon.name, description: s.title && s.title !== s.name ? s.title : undefined,
      url, headers: { ...s.headers, ...s.behaviorHints?.headers, ...s.behaviorHints?.proxyHeaders?.request }, external: !s.url }];
  });
  return [...new Map(results.filter(s => !(s.external && /\b(support the project|donate|donation)\b/i.test(`${s.name} ${s.description ?? ""}`)))
    .map(s => [JSON.stringify([s.url, s.headers, s.external]), s])).values()].sort((a, b) => Number(a.external) - Number(b.external));
}
