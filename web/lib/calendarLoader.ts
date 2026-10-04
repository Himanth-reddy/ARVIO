import { config } from "./config";
import { tmdb, mapTmdbItem, resolveTmdbId } from "./tmdb";
import { authoritativeEpisodeTime, mergeCalendarTitles, parseCalendarDate, sortCalendarReleases, type CalendarRelease, type CalendarSource, type CalendarTitle, type ReleaseKind } from "./calendar";
import type { MediaItem } from "./types";

interface Episode { season_number: number; episode_number: number; air_date?: string; name?: string; still_path?: string }
interface Season { id: number; season_number: number; air_date?: string; episode_count?: number }
interface ReleaseDate { type: number; release_date?: string }
interface Details {
  id: number; title?: string; name?: string; poster_path?: string; backdrop_path?: string; adult?: boolean; release_date?: string; status?: string;
  seasons?: Season[]; last_episode_to_air?: Episode; next_episode_to_air?: Episode;
  release_dates?: { results?: Array<{ iso_3166_1: string; release_dates: ReleaseDate[] }> };
}
export interface CalendarResult { releases: CalendarRelease[]; failedSources: CalendarSource[]; failedTitles: number; titleCount: number }
export interface CalendarRead { source: CalendarSource; read: () => Promise<MediaItem[]> }
export interface CalendarLoadOptions {
  sources: CalendarRead[]; start: string; end: string; language: string; region: string; customApiKey?: string; signal?: AbortSignal;
  episodeTime?: (item: MediaItem, season: number, episode: number) => Promise<string | null | undefined>;
  onProgress?: (result: CalendarResult) => void;
}
const cache = new Map<string, { at: number; value: unknown }>();
async function metadata<T>(path: string, options: CalendarLoadOptions, extra: Record<string, string> = {}): Promise<T> {
  if (options.signal?.aborted) throw new Error("Calendar request cancelled");
  const key = `${options.language}:${path}:${JSON.stringify(extra)}`;
  const cached = cache.get(key);
  if (cached && Date.now() - cached.at < 10 * 60_000) return cached.value as T;
  const value = await tmdb<T>(path, { language: options.language, ...extra }, options.customApiKey);
  cache.set(key, { at: Date.now(), value });
  if (cache.size > 600) cache.delete(cache.keys().next().value!);
  return value;
}

/** Date-only release metadata keeps its published civil date in every timezone. */
export function movieCalendarReleases(title: CalendarTitle, details: Details, region: string): CalendarRelease[] {
  const regions = details.release_dates?.results ?? [];
  // Use the requested country, then US; a global primary date is the fallback.
  const selected = regions.find(row => row.iso_3166_1 === region) ?? regions.find(row => row.iso_3166_1 === "US");
  const kinds: Record<number, ReleaseKind> = { 2: "cinema", 3: "cinema", 4: "digital", 5: "physical", 6: "tv" };
  const releases = new Map<string, CalendarRelease>();
  for (const row of selected?.release_dates ?? []) {
    const date = row.release_date?.slice(0, 10);
    const kind = kinds[row.type];
    if (!date || !parseCalendarDate(date) || !kind) continue;
    const id = `movie:${title.item.id}:${kind}:${date}`;
    releases.set(id, { ...title, id, date, kind, region: selected?.iso_3166_1, artwork: title.item.backdrop || title.item.image });
  }
  if (!releases.size && parseCalendarDate(details.release_date)) {
    const date = details.release_date!;
    releases.set(`movie:${title.item.id}:release:${date}`, { ...title, id: `movie:${title.item.id}:release:${date}`, date, kind: "release", artwork: title.item.backdrop || title.item.image });
  }
  return [...releases.values()];
}

export function calendarSeasonCandidates(seasons: Season[], start: string, end: string): Season[] {
  // A later season starting does not prove the previous season has finished.
  // Specials and unknown season dates must be queried too.
  return [...seasons].filter(row => row.season_number >= 0 && (!row.air_date || row.air_date <= end)).sort((a, b) => a.season_number - b.season_number);
}

async function titleReleases(title: CalendarTitle, options: CalendarLoadOptions): Promise<{ releases: CalendarRelease[]; failed: boolean }> {
  const { item } = title;
  const details = await metadata<Details>(`${item.mediaType}/${item.id}`, options, item.mediaType === "movie" ? { append_to_response: "release_dates" } : {});
  if (details.adult) return { releases: [], failed: false };
  const hydrated: CalendarTitle = { ...title, item: { ...item, ...mapTmdbItem(details, item.mediaType), traktId: item.traktId } };
  if (item.mediaType === "movie") return { releases: movieCalendarReleases(hydrated, details, options.region), failed: false };
  if (["Ended", "Canceled"].includes(details.status ?? "") && parseCalendarDate(details.last_episode_to_air?.air_date) && details.last_episode_to_air!.air_date! < options.start && !details.next_episode_to_air) return { releases: [], failed: false };
  const episodes = new Map<string, Episode>();
  for (const episode of [details.last_episode_to_air, details.next_episode_to_air]) if (episode) episodes.set(`${episode.season_number}:${episode.episode_number}`, episode);
  let failed = false;
  for (const season of calendarSeasonCandidates(details.seasons ?? [], options.start, options.end)) {
    try {
      const data = await metadata<{ episodes?: Episode[] }>(`tv/${item.id}/season/${season.season_number}`, options);
      for (const episode of data.episodes ?? []) episodes.set(`${episode.season_number}:${episode.episode_number}`, episode);
    } catch (error) { if (options.signal?.aborted) throw error; failed = true; }
  }
  const releases: CalendarRelease[] = [];
  for (const episode of episodes.values()) {
    if (options.signal?.aborted) throw new Error("Calendar request cancelled");
    if (!parseCalendarDate(episode.air_date) || episode.season_number < 0 || episode.episode_number <= 0) continue;
    // One day of padding lets an authoritative UTC time cross the local month boundary.
    const date = episode.air_date!;
    if (date < options.start || date > options.end) continue;
    let time: ReturnType<typeof authoritativeEpisodeTime> = null;
    if (options.episodeTime) {
      time = authoritativeEpisodeTime(await options.episodeTime(item, episode.season_number, episode.episode_number).catch(() => null));
    }
    releases.push({ ...hydrated, id: `tv:${item.id}:${episode.season_number}:${episode.episode_number}`, kind: "episode", date: time?.date ?? date, timestamp: time?.timestamp, season: episode.season_number, episode: episode.episode_number, episodeTitle: episode.name, artwork: episode.still_path ? `${config.backdropBase}${episode.still_path}` : hydrated.item.backdrop || hydrated.item.image });
  }
  return { releases, failed };
}

export async function loadCalendar(options: CalendarLoadOptions): Promise<CalendarResult> {
  const settled = await Promise.allSettled(options.sources.map(async source => ({ source: source.source, items: await source.read() })));
  const failedSources = settled.flatMap((result, index) => result.status === "rejected" ? [options.sources[index].source] : []);
  const groups = settled.flatMap(result => result.status === "fulfilled" ? [{ ...result.value, items: result.value.items.map(item => ({ ...item })) }] : []);
  // Resolve tracker-only identities before merging; never mistake a tracker ID for TMDB.
  const unresolved = groups.flatMap(group => group.items.filter(item => item.id <= 0));
  let cursor = 0;
  await Promise.all(Array.from({ length: Math.min(4, unresolved.length) }, async () => {
    while (cursor < unresolved.length && !options.signal?.aborted) {
      const item = unresolved[cursor++];
      const id = await resolveTmdbId(item).catch(() => null);
      if (id) item.id = id;
    }
  }));
  const titles = mergeCalendarTitles(groups);
  const releases: CalendarRelease[] = [];
  let failedTitles = unresolved.filter(item => item.id <= 0).length;
  let completed = 0;
  let lastPublished = 0;
  const snapshot = (): CalendarResult => ({ releases: sortCalendarReleases(releases.filter(row => row.date >= options.start && row.date <= options.end)), failedSources, failedTitles, titleCount: titles.length });
  options.onProgress?.(snapshot());
  cursor = 0;
  await Promise.all(Array.from({ length: Math.min(4, titles.length) }, async () => {
    while (cursor < titles.length && !options.signal?.aborted) {
      const title = titles[cursor++];
      try { const result = await titleReleases(title, options); releases.push(...result.releases); if (result.failed) failedTitles++; }
      catch { if (!options.signal?.aborted) failedTitles++; }
      completed++;
      if (!options.signal?.aborted && (completed === 1 || completed === titles.length || Date.now() - lastPublished > 150)) {
        options.onProgress?.(snapshot()); lastPublished = Date.now();
      }
    }
  }));
  return snapshot();
}
