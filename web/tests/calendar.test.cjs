const test = require('node:test');
const assert = require('node:assert/strict');
const { load } = require('./load.cjs');
const calendar = load('lib/calendar.ts');
const movie = { id: 1, mediaType: 'movie', title: 'Saved movie' };
const show = { id: 2, mediaType: 'tv', title: 'Saved series' };
const options = { start: '2026-10-01', end: '2026-10-31', language: 'en', region: 'NL' };
const json = value => JSON.parse(JSON.stringify(value));
function loader(tmdb = async () => ({}), resolveTmdbId = async () => null) {
  return load('lib/calendarLoader.ts', { './calendar': calendar, './tmdb': { tmdb, resolveTmdbId, mapTmdbItem: (details, mediaType) => ({ id: details.id, title: details.title || details.name, mediaType }) } });
}

test('month grid starts Monday, contains today and has five or six full weeks across leap years', () => {
  for (const [year, month, size] of [[2026, 9, 35], [2026, 2, 42], [2024, 1, 35]]) {
    const days = calendar.calendarMonthDays(new Date(year, month, 1, 12));
    assert.equal(days.length, size);
    assert.equal(days[0].getDay(), 1);
    assert.equal(days.at(-1).getDay(), 0);
  }
  assert.equal(calendar.parseCalendarDate('2026-02-30'), null);
  assert.ok(calendar.parseCalendarDate('2024-02-29'));
});

test('source merge is by media type and TMDB ID, retains memberships and rejects local placeholder IDs', () => {
  const rows = calendar.mergeCalendarTitles([{ source: 'arvio', items: [movie, show, { ...movie, id: -99 }] }, { source: 'trakt', items: [{ ...movie, traktId: 9 }, { ...show, id: 1 }] }]);
  assert.equal(rows.length, 3);
  assert.deepEqual(json(rows[0].sources), ['arvio', 'trakt']);
  assert.equal(rows[0].item.traktId, 9);
  assert.equal(rows[2].item.mediaType, 'tv');
});

test('movie release types dedupe theatrical/limited, keep cinema/digital distinct and never invent midnight times', () => {
  const module = loader();
  const rows = module.movieCalendarReleases({ item: movie, sources: ['arvio'] }, { id: 1, release_dates: { results: [{ iso_3166_1: 'NL', release_dates: [{ type: 2, release_date: '2026-10-16T00:00:00.000Z' }, { type: 3, release_date: '2026-10-16T00:00:00.000Z' }, { type: 4, release_date: '2026-10-16T00:00:00.000Z' }, { type: 5, release_date: '2026-02-30' }] }] } }, 'NL');
  assert.deepEqual(json(rows.map(row => row.kind)), ['cinema', 'digital']);
  assert.ok(rows.every(row => row.date === '2026-10-16' && !row.timestamp && calendar.calendarTime(row) === 'Time TBA'));
});

test('release fallback uses labeled US metadata or untyped primary date rather than an arbitrary country', () => {
  const module = loader();
  const rows = module.movieCalendarReleases({ item: movie, sources: ['arvio'] }, { id: 1, release_date: '2026-10-12', release_dates: { results: [{ iso_3166_1: 'DE', release_dates: [{ type: 3, release_date: '2026-10-10' }] }] } }, 'NL');
  assert.equal(rows[0].date, '2026-10-12'); assert.equal(rows[0].kind, 'release'); assert.equal(rows[0].region, undefined);
});

test('authoritative timestamps use local date through midnight and DST, date-only metadata has no time', () => {
  const previous = process.env.TZ;
  process.env.TZ = 'Europe/Amsterdam';
  try {
    assert.equal(calendar.authoritativeEpisodeTime('2026-10-15T23:30:00Z').date, '2026-10-16');
    assert.equal(calendar.calendarTime({ timestamp: '2026-10-15T23:30:00Z' }, 'en'), '01:30');
    assert.equal(calendar.calendarTime({ timestamp: '2026-10-25T02:30:00Z' }, 'en'), '03:30');
    assert.equal(calendar.authoritativeEpisodeTime('2026-10-16'), null);
    assert.equal(calendar.authoritativeEpisodeTime('2026-10-16T00:00:00'), null);
  } finally { if (previous === undefined) delete process.env.TZ; else process.env.TZ = previous; }
});

test('ARVIO-only account loads releases without any tracker reader, preserving typed movie releases', async () => {
  const calls = [];
  const module = loader(async path => { calls.push(path); return { id: 1, title: movie.title, release_dates: { results: [{ iso_3166_1: 'NL', release_dates: [{ type: 3, release_date: '2026-10-16T00:00:00Z' }] }] } }; });
  const result = await module.loadCalendar({ ...options, sources: [{ source: 'arvio', read: async () => [movie] }] });
  assert.deepEqual(calls, ['movie/1']);
  assert.equal(result.releases.length, 1); assert.equal(result.failedTitles, 0);
  assert.deepEqual(json(result.releases[0].sources), ['arvio']);
});

test('source and season failures retain other releases and report partial data', async () => {
  const module = loader(async path => {
    if (path === 'tv/2') return { id: 2, name: show.title, seasons: [{ id: 5, season_number: 1, air_date: '2026-09-01' }], next_episode_to_air: { season_number: 1, episode_number: 8, air_date: '2026-10-16' } };
    throw new Error('offline');
  });
  const result = await module.loadCalendar({ ...options, sources: [{ source: 'arvio', read: async () => [show] }, { source: 'trakt', read: async () => { throw new Error('offline'); } }] });
  assert.equal(result.releases.length, 1); assert.equal(result.failedTitles, 1);
  assert.deepEqual(json(result.failedSources), ['trakt']);
  assert.equal(calendar.calendarTime(result.releases[0]), 'Time TBA');
});

test('overlapping seasons, specials and unknown dates are not silently dropped; ended series avoid needless requests', async () => {
  const module = loader(async path => ({ id: 2, name: show.title, status: 'Ended', last_episode_to_air: { season_number: 1, episode_number: 5, air_date: '2025-10-10' }, seasons: [{ id: 1, season_number: 1 }] }));
  const candidates = module.calendarSeasonCandidates([{ id: 1, season_number: 0 }, { id: 2, season_number: 1, air_date: '2024-01-01' }, { id: 3, season_number: 2, air_date: '2025-01-01' }, { id: 4, season_number: 3, air_date: '2027-01-01' }], options.start, options.end);
  assert.deepEqual(json(candidates.map(row => row.season_number)), [0, 1, 2]);
  const result = await module.loadCalendar({ ...options, sources: [{ source: 'arvio', read: async () => [show] }] });
  assert.equal(result.releases.length, 0); assert.equal(result.failedTitles, 0);
});

test('identity resolution does not mutate shared watchlists and can merge external memberships', async () => {
  const unknown = { ...movie, id: -12, imdbId: 'tt0123456' };
  const module = loader(async () => ({ id: 1, title: movie.title, release_date: '2026-10-10' }), async () => 1);
  const result = await module.loadCalendar({ ...options, sources: [{ source: 'arvio', read: async () => [movie] }, { source: 'mdblist', read: async () => [unknown] }] });
  assert.equal(unknown.id, -12); assert.equal(result.titleCount, 1); assert.equal(result.releases.length, 1);
  assert.deepEqual(json(result.releases[0].sources), ['arvio', 'mdblist']);
});

test('completed titles render before a slow title finishes and aborted months stop requesting episode times', async () => {
  let finishSlow;
  const slow = new Promise(resolve => { finishSlow = resolve; });
  let firstPaint;
  const painted = new Promise(resolve => { firstPaint = resolve; });
  const module = loader(async path => path === 'movie/3' ? slow : { id: 1, title: movie.title, release_date: '2026-10-10' });
  const request = module.loadCalendar({ ...options, sources: [{ source: 'arvio', read: async () => [movie, { ...movie, id: 3 }] }], onProgress: result => { if (result.releases.length === 1) firstPaint(); } });
  await painted;
  finishSlow({ id: 3, title: 'Slow movie', release_date: '2026-10-12' });
  assert.equal((await request).releases.length, 2);
  const controller = new AbortController();
  const tv = loader(async path => path === 'tv/2' ? { id: 2, name: show.title, seasons: [{ id: 1, season_number: 1 }] } : { episodes: [1, 2, 3].map(episode_number => ({ season_number: 1, episode_number, air_date: '2026-10-16' })) });
  let timeCalls = 0;
  await tv.loadCalendar({ ...options, sources: [{ source: 'arvio', read: async () => [show] }], signal: controller.signal, episodeTime: async () => { timeCalls++; controller.abort(); return null; } });
  assert.equal(timeCalls, 1);
});

test('public Trakt episode timestamps work without a connected account or OAuth calls', async () => {
  const { TraktClient } = load('lib/trakt.ts', { './config': { config: { traktClientId: 'public-app-id' } }, './http': {} });
  const client = new TraktClient();
  const paths = [];
  client.trakt = async path => { paths.push(path); return path.startsWith('/search/') ? [{ show: { ids: { tmdb: 2, trakt: 44 } } }] : [{ season: 1, number: 8, first_aired: '2026-10-16T19:00:00Z' }]; };
  const episodes = await client.calendarSeason(2, 1);
  assert.equal(client.isConnected, false);
  assert.deepEqual(paths, ['/search/tmdb/2?type=show', '/shows/44/seasons/1?extended=full']);
  assert.equal(episodes[0].first_aired, '2026-10-16T19:00:00Z');
});
