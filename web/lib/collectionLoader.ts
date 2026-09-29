import { collectionSourceSupports } from './collectionPresentation';
import { loadCatalog, loadCollectionSource } from './tmdb';
import type { CatalogConfig, HomeServerConfig, InstalledAddon, MediaItem, MediaType } from './types';

/** A cursor per source prevents a failed source from losing its place on retry. */
export function createCollectionLoader(catalog: CatalogConfig, type: MediaType, language: string, addons: InstalledAddon[], servers: HomeServerConfig[]) {
  const sources = (catalog.collectionSources || []).filter(s => collectionSourceSupports(s, type));
  const states = sources.map(source => ({ source: { ...source, mediaType: type }, page: 1, done: false }));
  const seen = new Set<string>();
  let legacyDone = false;
  return async () => {
    const items: MediaItem[] = [];
    const errors: string[] = [];
    if (!states.length && !legacyDone) {
      try {
        const row = await loadCatalog(catalog, language, addons, servers);
        items.push(...(row?.items || []).filter(i => i.mediaType === type));
        legacyDone = true;
      } catch (e) { errors.push(e instanceof Error ? e.message : 'Collection could not be loaded'); }
    }
    const pending = states.filter(s => !s.done);
    // Bound fan-out for collections combining several services or lists.
    for (let offset = 0; offset < pending.length; offset += 3) {
      const batches = await Promise.all(pending.slice(offset, offset + 3).map(async state => {
        try {
          const paged = ['TMDB_DISCOVER', 'TMDB_GENRE', 'TMDB_KEYWORD', 'TMDB_WATCH_PROVIDER'].includes(state.source.kind.toUpperCase());
          const result = await loadCollectionSource(state.source, language, addons, servers, paged ? { page: state.page, pageLimit: 1 } : undefined);
          state.page++;
          state.done = !paged || result.length < 20 || state.page > 500;
          return result.filter(i => i.mediaType === type);
        } catch (e) {
          errors.push(e instanceof Error ? e.message : 'Collection could not be loaded');
          return [];
        }
      }));
      items.push(...batches.flat());
    }
    return {
      items: items.filter(item => {
        const key = `${item.mediaType}:${item.id}`;
        if (seen.has(key)) return false;
        seen.add(key);
        return true;
      }),
      hasMore: states.length ? states.some(s => !s.done) : !legacyDone,
      errors
    };
  };
}
