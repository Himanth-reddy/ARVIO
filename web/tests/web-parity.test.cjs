const test=require('node:test');const assert=require('node:assert/strict');const {load,storage}=require('./load.cjs');

test('addon edits survive a failed save and the latest snapshot wins during an in-flight save',async()=>{
 const disk=storage();let fail=true,release;const sent=[];
 const api=load('lib/addonOutbox.ts',{'./storage':disk,'./cloud':{saveCloudAddons:async(_auth,addons,_profile,options)=>{sent.push({addons,options});if(fail)throw Error('offline');if(release===undefined)await new Promise(r=>release=r);}}});
 const auth={session:{userId:'a'}};api.queueAddons(auth,[{id:'one'}],'p');
 await assert.rejects(api.flushAddonOutbox(auth));assert.equal(api.hasPendingAddons(auth),true);
 fail=false;const pending=api.flushAddonOutbox(auth);api.queueAddons(auth,[{id:'two'}],'p',['one']);release();await pending;
 assert.equal(api.hasPendingAddons(auth),false);assert.equal(sent.at(-1).addons[0].id,'two');assert.equal(sent.at(-1).options.removedIds[0],'one');
});
test('addon outboxes are account scoped and refuse a false saved state when storage fails',()=>{
 const disk=storage();const api=load('lib/addonOutbox.ts',{'./storage':disk,'./cloud':{}});const a={session:{userId:'a'}},b={session:{userId:'b'}};
 api.queueAddons(a,[],'p',['old']);assert.equal(api.hasPendingAddons(b),false);
 const broken=load('lib/addonOutbox.ts',{'./storage':{loadStored:()=>[],saveStored:()=>{}},'./cloud':{}});
 assert.throws(()=>broken.queueAddons(a,[],'p'),/storage is full/);
});
test('browser autoplay rejects external and uncached sources and prefers direct playback',()=>{
 const api=load('lib/browserAutoplay.ts',{'./debrid':{isUncachedDebridStream:s=>s.uncached},'./streamCompatibility':{playbackPlan:s=>({route:s.external?'external':'here',method:s.method??'direct'})},'./sourceRank':{sourcePickerScore:s=>s.score??0}});
 const sources=[{url:'transcode',method:'transcode',score:100},{url:'external',external:true},{url:'torrent',uncached:true},{url:'direct'},{url:'remux',method:'remux'},{url:'direct'}];
 assert.equal(Array.from(api.browserAutoplayCandidates(sources),s=>s.url).join(','),'direct,remux,transcode');
 assert.equal(Array.from(api.browserAutoplayCandidates(sources,new Set(['direct'])),s=>s.url).join(','),'remux,transcode');
});
test('navigation round-trips shareable details and search while preserving unrelated URL parameters',()=>{
 const api=load('lib/webNavigation.ts');const route={section:'search',query:'Space & time',title:{id:42,mediaType:'movie'}};
 const url=api.writeWebRoute(new URL('https://web.example/?utm_source=test'),route);
 assert.equal(JSON.stringify(api.readWebRoute(url)),JSON.stringify(route));assert.equal(url.searchParams.get('utm_source'),'test');
 const invalid=api.readWebRoute(new URL('https://web.example/?view=invalid&q=hidden&title=movie:-3'));
 assert.equal(invalid.section,'home');assert.equal(invalid.query,'');assert.equal(invalid.title,null);
});
test('Stalker paginates channels, resolves temporary streams and isolates portal authentication',async()=>{
 const requests=[];const api=load('lib/stalker.ts',{'./http':{proxiedUrl:(url,headers)=>({url,headers}),jsonRequest:async target=>{
  const u=new URL(target.url);requests.push(target);const a=u.searchParams.get('action');
  if(a==='handshake')return {js:{token:'portal-token'}};if(a==='get_profile')return {js:{id:1}};
  if(a==='get_genres')return {js:[{id:'g',title:'News'}]};
  if(a==='get_all_channels'){const page=Number(u.searchParams.get('p'));return {js:{total_items:2,data:[{id:page,name:'Channel '+page,cmd:'ffmpeg http://localhost/ch/'+page,tv_genre_id:'g',use_http_tmp_link:1}]}};}
  if(a==='create_link')return {js:{cmd:'ffmpeg https://cdn.example/live.m3u8'}};
  if(a==='get_epg_info')return {js:{'1':[{name:'Now',start_timestamp:Date.now()/1000-100,stop_timestamp:Date.now()/1000+100}]}};
 }}});
 const channels=await api.loadStalkerChannels('https://portal.example/c/','00:1A:79:12:34:56');assert.equal(channels.length,2);assert.equal(channels[0].group,'News');
 const playable=await api.resolveStalkerChannel(channels[0]);assert.equal(playable.streamUrl,'https://cdn.example/live.m3u8');assert.equal(playable.requestHeaders.Cookie,undefined);assert.equal(playable.requestHeaders.Authorization,undefined);
 const guide=await api.loadStalkerGuide(channels);assert.equal(guide[channels[0].id].now.title,'Now');
 assert.equal(requests.filter(r=>new URL(r.url).searchParams.get('action')==='handshake').length,1);
 assert.match(requests[1].headers.Cookie,/mac=/);
});

test('search sends language, media filters and page numbers and distinguishes discovery from text search',async()=>{
 const calls=[];const api=load('lib/tmdb.ts',{'./config':{config:{}},'./storage':storage(),'./metadata/anizip':{},'./metadata/dispatcher':{},'./mediaImages':{tmdbImageUrl:()=>''},'./http':{proxiedUrl:x=>x,apiProxiedUrl:x=>x,jsonRequest:async url=>{calls.push(new URL(url));return {results:[{id:7,name:'A show',genre_ids:[18]},{id:8,name:'Other',genre_ids:[35]}],total_pages:3};}}},{window:{location:{origin:'https://web.example'}}});
 const result=await api.searchMediaPage({query:'space',type:'tv',genre:'18',year:'2020',sort:'popularity.desc'},'es-ES',2);
 assert.equal(result.items.length,1);assert.equal(result.items[0].mediaType,'tv');assert.equal(result.hasMore,true);
 assert.equal(calls[0].pathname,'/api/tmdb/search/tv');assert.equal(calls[0].searchParams.get('page'),'2');assert.equal(calls[0].searchParams.get('language'),'es-ES');assert.equal(calls[0].searchParams.get('first_air_date_year'),'2020');
 const last=await api.searchMediaPage({query:'',type:'movie',genre:'18',year:'',sort:'vote_average.desc'},'en-US',3);
 assert.equal(last.hasMore,false);assert.equal(calls[1].pathname,'/api/tmdb/discover/movie');assert.equal(calls[1].searchParams.get('with_genres'),'18');assert.equal(calls[1].searchParams.get('vote_count.gte'),'100');
});
