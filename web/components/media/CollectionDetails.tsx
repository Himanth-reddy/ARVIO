"use client";

import { useCallback, useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { useVirtualizer } from '@tanstack/react-virtual';
import { ArrowLeft, LoaderCircle, RotateCw } from 'lucide-react';
import { useApp } from '@/lib/store';
import { useTranslation } from '@/lib/i18n';
import { collectionMediaTypes } from '@/lib/collectionPresentation';
import { createCollectionLoader } from '@/lib/collectionLoader';
import { getCollectionPreview, getCardProviders, getLogoUrl } from '@/lib/tmdb';
import { getImdbRating } from '@/lib/imdbRatings';
import { IMDB_LOGO, serviceClearLogo } from '@/lib/serviceLogos';
import type { CatalogConfig, MediaItem, MediaType } from '@/lib/types';
import { MediaCard } from './MediaCard';

export function CollectionDetails({ catalog, onBack, onOpen }: { catalog: CatalogConfig; onBack: () => void; onOpen: (item: MediaItem) => void }) {
  const { settings, addons, selected } = useApp();
  const types = collectionMediaTypes(catalog);
  const [type, setType] = useState<MediaType>(types[0] || 'movie');
  const dialog = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    if (selected) dialog.current?.close();
    else dialog.current?.showModal();
  }, [selected]);
  useEffect(() => {
    if (selected) return;
    const previous = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => { document.body.style.overflow = previous; };
  }, [selected]);
  return createPortal(<dialog ref={dialog} className="collection-page" aria-label={catalog.name}
    onCancel={e => { e.preventDefault(); onBack(); }}>
    <CollectionContent key={`${catalog.id}:${settings.language}:${JSON.stringify(catalog.collectionSources)}`} catalog={catalog}
      type={type} types={types} setType={setType} onBack={onBack} onOpen={onOpen}
      language={settings.language} addons={addons} servers={settings.homeServers} posterMode={settings.cardLayoutMode === 'poster'} />
  </dialog>, document.body);
}

type TabState = { items: MediaItem[]; loading: boolean; hasMore: boolean; errors: string[]; scroll: number; focus?: MediaItem };
const emptyTab = (): TabState => ({ items: [], loading: false, hasMore: true, errors: [], scroll: 0 });

function CollectionContent({ catalog, type, types, setType, onBack, onOpen, language, addons, servers, posterMode }: {
  catalog: CatalogConfig; type: MediaType; types: MediaType[]; setType: (type: MediaType) => void;
  onBack: () => void; onOpen: (item: MediaItem) => void; language: string;
  addons: Parameters<typeof createCollectionLoader>[3]; servers: Parameters<typeof createCollectionLoader>[4]; posterMode: boolean;
}) {
  const t = useTranslation();
  const tabs = useRef<Record<MediaType, TabState>>({ movie: emptyTab(), tv: emptyTab() });
  const loaders = useRef<Partial<Record<MediaType, ReturnType<typeof createCollectionLoader>>>>({});
  const alive = useRef(true);
  const [, update] = useState(0);
  const viewport = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(1000);
  const tab = tabs.current[type];
  const posters = catalog.collectionGroup !== 'GENRE' && posterMode;
  const columns = Math.max(1, Math.floor((width + 16) / (posters ? width < 600 ? 126 : 226 : width < 600 ? 160 : width < 1100 ? 300 : 420)));
  const cardWidth = (width - (columns - 1) * 16) / columns;
  const rowHeight = Math.ceil(cardWidth * (posters ? 1.5 : 9 / 16)) + 88;
  const rows = useVirtualizer({ count: Math.ceil(tab.items.length / columns), getScrollElement: () => viewport.current,
    estimateSize: () => rowHeight, overscan: 2, getItemKey: index => `${type}:${index}:${columns}` });
  useEffect(() => { rows.measure(); }, [rowHeight, type, rows]);
  useEffect(() => {
    const node = viewport.current;
    if (!node) return;
    const observer = new ResizeObserver(entries => setWidth(entries[0].contentRect.width));
    observer.observe(node);
    return () => observer.disconnect();
  }, []);
  useEffect(() => { alive.current = true; return () => { alive.current = false; }; }, []);
  const load = useCallback(async (target: MediaType) => {
    const state = tabs.current[target];
    if (state.loading || !state.hasMore) return;
    state.loading = true;
    update(n => n + 1);
    try {
      loaders.current[target] ??= createCollectionLoader(catalog, target, language, addons, servers);
      const result = await loaders.current[target]!();
      if (!alive.current) return;
      state.items = [...state.items, ...result.items];
      state.hasMore = result.hasMore;
      state.errors = result.errors;
    } catch (error) {
      state.errors = [error instanceof Error ? error.message : 'Collection could not be loaded'];
    } finally {
      state.loading = false;
      if (alive.current) update(n => n + 1);
    }
  }, [catalog, language, addons, servers]);
  useEffect(() => {
    viewport.current?.scrollTo({ top: tabs.current[type].scroll, behavior: 'instant' });
    if (!tabs.current[type].items.length && !tabs.current[type].errors.length) void load(type);
  }, [type, load]);
  const focus = useCallback((item: MediaItem) => { tabs.current[item.mediaType].focus = item; update(n => n + 1); }, []);
  const preview = tab.focus || tab.items[0];
  const [hero, setHero] = useState<{ key: string; item: MediaItem; logo: string | null; rating: string | null; services: string[] } | null>(null);
  const heroCache = useRef(new Map<string, NonNullable<typeof hero>>());
  const previewKey = preview ? `${preview.mediaType}:${preview.id}` : '';
  useEffect(() => {
    if (!preview) return;
    const cached = heroCache.current.get(`${language}:${previewKey}`);
    if (cached) { setHero(cached); return; }
    let cancelled = false;
    const timer = setTimeout(async () => {
      const [details, logo, services] = await Promise.all([
        getCollectionPreview(preview, language).catch(() => null),
        getLogoUrl(preview).catch(() => null), getCardProviders(preview).catch(() => [])
      ]);
      const item = details || preview;
      const serviceLogos = services.map(serviceClearLogo).filter((url): url is string => Boolean(url));
      if (!cancelled) setHero({ key: previewKey, item, logo, rating: null, services: serviceLogos });
      const rating = await getImdbRating(item.mediaType, item.imdbId).catch(() => null);
      if (!cancelled) {
        const result = { key: previewKey, item, logo, rating, services: serviceLogos };
        if (heroCache.current.size >= 100) heroCache.current.delete(heroCache.current.keys().next().value!);
        heroCache.current.set(`${language}:${previewKey}`, result);
        setHero(result);
      }
    }, 120);
    return () => { cancelled = true; clearTimeout(timer); };
  }, [previewKey, language]);
  const metadata = hero?.key === previewKey ? hero : null;
  const item = metadata?.item || preview;
  const artwork = item?.backdrop || catalog.collectionHeroImageUrl;
  const budget = item?.mediaType === 'movie' && item.budget && item.budget > 0 ? new Intl.NumberFormat(language, { style: 'currency', currency: 'USD', notation: 'compact', maximumFractionDigits: 1 }).format(item.budget) : null;
  return <div className="collection-page-body">
    {artwork && <div className="collection-page-backdrop" style={{ backgroundImage: `url(${JSON.stringify(artwork)})` }} />}
    <header className="collection-page-header"><button className="icon-button" type="button" title={t('Back')} aria-label={t('Back')} onClick={onBack}><ArrowLeft size={24} /></button><h1>{catalog.name}</h1></header>
    <section className="collection-spotlight" aria-label={item?.title || catalog.name}>
      <div className="collection-spotlight-title">{metadata?.logo ? <img src={metadata.logo} alt={item?.title} onError={() => setHero(h => h ? { ...h, logo: null } : null)} /> : <h2>{item?.title || catalog.name}</h2>}</div>
      <div className="collection-spotlight-facts">
        {metadata?.services.slice(0, 2).map(url => <img key={url} src={url} alt="" className="collection-service" />)}
        {metadata?.rating ? <span className="collection-imdb"><img src={IMDB_LOGO} alt="IMDb" />{metadata.rating}</span> : item?.rating && <span>TMDB {item.rating}</span>}
        {item?.year && <span>{item.year}</span>}{item?.duration && <span>{item.duration}</span>}{budget && <span>{t('Budget')} {budget}</span>}
      </div>
      <p>{item?.overview || catalog.collectionDescription}</p>
    </section>
    <div className="collection-tabs" role="tablist" aria-label={catalog.name}>{types.map(value => <button key={value} id={`collection-tab-${value}`} type="button" role="tab" aria-selected={type === value} aria-controls="collection-items"
      onClick={() => setType(value)} onKeyDown={e => { if (e.key === 'ArrowLeft' || e.key === 'ArrowRight') { e.preventDefault(); const next = types[(types.indexOf(value) + 1) % types.length]; setType(next); document.getElementById(`collection-tab-${next}`)?.focus(); } }}>{t(value === 'movie' ? 'Movies' : 'Series')}</button>)}</div>
    <div ref={viewport} id="collection-items" className="collection-grid-viewport" role="tabpanel" aria-labelledby={`collection-tab-${type}`} aria-busy={tab.loading}
      onScroll={e => { tab.scroll = e.currentTarget.scrollTop; if (!tab.errors.length && e.currentTarget.scrollHeight - e.currentTarget.scrollTop - e.currentTarget.clientHeight < 350) void load(type); }}>
      <div className="collection-virtual-grid" style={{ height: rows.getTotalSize() }}>
        {rows.getVirtualItems().map(row => <div key={row.key} className="collection-grid-row" style={{ transform: `translateY(${row.start}px)`, gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))` }}>
          {tab.items.slice(row.index * columns, (row.index + 1) * columns).map(media => <div key={`${media.mediaType}:${media.id}`} onMouseEnter={() => focus(media)}><MediaCard item={media} onOpen={onOpen} onFocus={focus} posterMode={posters} /></div>)}
        </div>)}
      </div>
      <footer className="collection-load-state">
        {tab.loading ? <span role="status"><LoaderCircle className="spin" size={20} />{t('Loading...')}</span> : <>
          {tab.errors.length > 0 && <p role="alert">{t('Some sources could not be loaded.')}</p>}
          {!tab.items.length && !tab.errors.length && <p>{t('No titles found')}</p>}
          {tab.hasMore && <button className="secondary" type="button" onClick={() => void load(type)}>{tab.errors.length ? <><RotateCw size={18} />{t('Retry')}</> : t('Load more')}</button>}
        </>}
      </footer>
    </div>
  </div>;
}
