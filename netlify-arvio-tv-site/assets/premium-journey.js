/* Short-lived, first-party campaign measurement. No cookies or browser storage. */
(function () {
  'use strict';
  if (navigator.doNotTrack === '1' || navigator.globalPrivacyControl === true) return;
  const here = new URL(window.location.href);
  if (!['arvio.tv', 'www.arvio.tv', 'localhost', '127.0.0.1'].includes(here.hostname)) return;
  const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
  const incoming = here.searchParams.get('arvio_journey') || '';
  const journey = uuid.test(incoming) ? incoming.toLowerCase() : window.crypto?.randomUUID?.();
  if (!journey) return;
  const clean = value => /^[a-z0-9._-]{1,80}$/i.test(value || '') ? value.toLowerCase() : '';
  let referrer = '';
  try { referrer = clean(new URL(document.referrer).hostname); } catch {}
  const campaign = {
    source: clean(here.searchParams.get('utm_source')) || referrer || 'direct',
    medium: clean(here.searchParams.get('utm_medium')) || (referrer ? 'referral' : 'direct'),
    campaign: clean(here.searchParams.get('utm_campaign')) || 'premium',
    content: clean(here.searchParams.get('utm_content')) || '',
  };
  const page = /\/premium\/?$/.test(here.pathname) ? 'premium' : 'home';
  const sent = new Set();
  function record(eventName, placement) {
    if (sent.has(eventName)) return;
    sent.add(eventName);
    try {
      fetch('https://auth.arvio.tv/.netlify/functions/premium-funnel-visit', {
        method: 'POST', mode: 'cors', credentials: 'omit', keepalive: true,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ event_name: eventName, journey_id: journey,
          metadata: { ...campaign, content: campaign.content || placement || page, page } }),
      }).catch(() => {});
    } catch {}
  }
  function classify(link) {
    let url;
    try { url = new URL(link.href, here); } catch { return null; }
    if (!['https:', 'http:'].includes(url.protocol)) return null;
    if (url.origin === here.origin && /\/premium\/?$/.test(url.pathname)) return { url, kind: 'internal' };
    if (url.hostname === 'web.arvio.tv') return { url, kind: 'web_clicked' };
    if (url.hostname === 'ko-fi.com' && /^\/arvio\/tiers\/?$/.test(url.pathname)) return { url, kind: 'membership_clicked' };
    return null;
  }
  for (const link of document.querySelectorAll('a[href]')) {
    const target = classify(link);
    if (!target || target.kind === 'membership_clicked') continue;
    target.url.searchParams.set('arvio_journey', journey);
    for (const [name, value] of Object.entries(campaign)) {
      if (value) target.url.searchParams.set('utm_' + name, value);
    }
    link.href = target.url.href;
  }
  function clicked(event) {
    if (event.type === 'auxclick' && event.button !== 1) return;
    const link = event.target.closest?.('a[href]');
    if (!link) return;
    const target = classify(link);
    if (!target || target.kind === 'internal') return;
    const placement = clean(link.dataset.premiumPlacement) || (link.closest('footer') ? 'footer' : link.closest('header') ? 'nav' : page);
    record(target.kind, placement);
  }
  document.addEventListener('click', clicked, { capture: true });
  document.addEventListener('auxclick', clicked, { capture: true });
  if (page === 'premium') record('premium_page_view');
})();
