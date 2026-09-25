"use client";
import { useEffect, useRef, useState } from 'react';
import { LoaderCircle, Search } from 'lucide-react';
import { useTranslation } from '@/lib/i18n';
import { useApp } from '@/lib/store';
import { searchMediaPage, type SearchFilters } from '@/lib/tmdb';
import type { MediaItem } from '@/lib/types';
import { MediaCard } from '@/components/media/MediaCard';
const genres = [['','All Genres'],['28','Action'],['12','Adventure'],['16','Animation'],['35','Comedy'],['80','Crime'],['99','Documentary'],['18','Drama'],['10751','Family'],['9648','Mystery']];
export function SearchScreen() {
  const translateUi = useTranslation();
  const { query, setQuery, openDetails, settings } = useApp();
  const [type,setType] = useState<'movie'|'tv'>(()=>typeof window!=='undefined'&&new URL(location.href).searchParams.get('searchType')==='tv'?'tv':'movie');
  const [genre,setGenre] = useState(()=>typeof window!=='undefined'?new URL(location.href).searchParams.get('genre')||'':'');
  const [year,setYear] = useState(()=>typeof window!=='undefined'?new URL(location.href).searchParams.get('year')||'':'');
  const [sort,setSort] = useState(()=>typeof window!=='undefined'?new URL(location.href).searchParams.get('sort')||'popularity.desc':'popularity.desc');
  const [items,setItems] = useState<MediaItem[]>([]);
  const [page,setPage] = useState(1);
  const [more,setMore] = useState(false);
  const [loading,setLoading] = useState(true);
  const [error,setError] = useState(false);
  const [retry,setRetry] = useState(0);
  const generation = useRef(0);
  const filters: SearchFilters = {query,type,genre,year,sort};
  const signature = JSON.stringify({...filters,language:settings.language});
  useEffect(()=>{
    const url=new URL(location.href);
    for(const [key,value] of Object.entries({searchType:type,genre,year,sort})) { if(value)url.searchParams.set(key,value);else url.searchParams.delete(key); }
    history.replaceState(history.state,'',url);
  },[type,genre,year,sort]);
  useEffect(() => {
    const id = ++generation.current;
    setPage(1);setItems([]);setMore(false);setLoading(true);setError(false);
    const timer = setTimeout(() => { void searchMediaPage(filters,settings.language).then(result => {
      if (generation.current !== id) return;
      setItems(result.items);setMore(result.hasMore);
    }).catch(() => {if(generation.current===id)setError(true);}).finally(()=>{if(generation.current===id)setLoading(false);}); },query.trim()?260:0);
    return ()=>{clearTimeout(timer);generation.current++;};
    // Filter signature includes every request parameter.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  },[signature,retry]);
  const loadMore = async () => {
    const id=generation.current;setLoading(true);setError(false);
    try {const result=await searchMediaPage(filters,settings.language,page+1);
      if(id!==generation.current)return;
      setItems(previous=>{const seen=new Set(previous.map(i=>`${i.mediaType}:${i.id}`));return [...previous,...result.items.filter(i=>!seen.has(`${i.mediaType}:${i.id}`))];});
      setPage(page+1);setMore(result.hasMore);
    } catch {if(id===generation.current)setError(true);} finally {if(id===generation.current)setLoading(false);}
  };
  return <div className={`screen has-search-hero ${settings.cardLayoutMode==='poster'?'poster-results':''}`}>
    <section className="search-hero"><span className="search-icon-shell"><Search size={28}/></span><input value={query} onChange={e=>setQuery(e.target.value)} placeholder={translateUi('Search movies and series')} aria-label={translateUi('Search movies and series')}/></section>
    <div className="search-filters">
      <select aria-label={translateUi('Type')} value={type} onChange={e=>{setType(e.target.value as 'movie'|'tv');setGenre('');setSort('popularity.desc');}}><option value="movie">{translateUi('Movies')}</option><option value="tv">{translateUi('TV Shows')}</option></select>
      <select aria-label={translateUi('Genre')} value={genre} onChange={e=>setGenre(e.target.value)}>{genres.filter(([id])=>type==='movie'||!['28','12'].includes(id)).map(([id,label])=><option value={id} key={id}>{translateUi(label)}</option>)}{type==='tv'&&<option value="10759">{translateUi('Action & Adventure')}</option>}</select>
      <select aria-label={translateUi('Year')} value={year} onChange={e=>setYear(e.target.value)}><option value="">{translateUi('Any year')}</option>{Array.from({length:100},(_,i)=>new Date().getFullYear()+1-i).map(y=><option key={y} value={y}>{y}</option>)}</select>
      {!query.trim()&&<select aria-label={translateUi('Sort')} value={sort} onChange={e=>setSort(e.target.value)}><option value="popularity.desc">{translateUi('Popular')}</option><option value="vote_average.desc">{translateUi('Top rated')}</option><option value={type==='movie'?'primary_release_date.desc':'first_air_date.desc'}>{translateUi('Newest')}</option></select>}
      {(query||genre||year)&&<button className="secondary" onClick={()=>{setQuery('');setGenre('');setYear('');setSort('popularity.desc');}}>{translateUi('Reset filters')}</button>}
    </div>
    <h2 className="search-results-title">{query.trim()?translateUi('Search results'):translateUi('Discover')}</h2>
    {error&&<div className="library-error" role="alert">{translateUi('Search is temporarily unavailable. Please try again.')} <button className="secondary" onClick={()=>items.length?void loadMore():setRetry(v=>v+1)}>{translateUi('Retry')}</button></div>}
    {!loading&&!error&&!items.length&&<div className="watchlist-empty"><Search size={34}/><p>{translateUi('No matching titles')}</p></div>}
    <div className="grid-results">{items.map(item=><MediaCard key={`${item.mediaType}:${item.id}`} item={item} onOpen={openDetails} posterMode={settings.cardLayoutMode==='poster'}/>)}</div>
    <div className="search-pagination">{loading?<span role="status"><LoaderCircle size={20}/>{translateUi('Loading')}</span>:more&&<button className="secondary" onClick={()=>void loadMore()}>{translateUi('Load more')}</button>}</div>
  </div>;
}
