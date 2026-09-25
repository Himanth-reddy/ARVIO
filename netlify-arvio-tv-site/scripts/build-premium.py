"""Build the public Premium guide using the app's existing translations."""
import json
from pathlib import Path

site = Path(__file__).resolve().parents[1]
root = site.parent
out = site / 'premium'
out.mkdir(exist_ok=True)
keys = {
    'download': 'Download this source',
    'downloadBody': 'Watch or download directly on Windows, Mac and mobile',
    'sources': 'Sources',
    'intro': 'Take your existing ARVIO setup to Windows, Mac, iPhone, iPad and smart-TV browsers. Your profiles, libraries, addons and progress stay connected through ARVIO Cloud.',
    'sync': 'Same profiles, libraries and watch progress',
    'play': 'Browser playback and one-click VLC',
    'free': 'Android and TV app remains completely free',
    'join': 'Subscribe on Ko-fi',
    'period': '/ month',
    'notice': 'ARVIO is a media hub for sources you configure. Catalog entries do not grant viewing rights. Connect only services and media you are authorized to use.',
    'language': 'App Language',
    'home': 'Home', 'library': 'Library', 'server': 'Homeserver', 'tv': 'Live TV', 'sports': 'Sports',
}
hosting = json.loads((site / 'scripts/premium-hosting.json').read_text(encoding='utf-8'))
manifest = json.loads((root / 'web/lib/i18n/manifest.json').read_text())
languages = json.loads((root / 'web/lib/i18n/languages.json').read_text(encoding='utf-8'))
languages.append({'code': 'en-GB', 'label': 'English (UK)'})
feature_copy = {
    'en': {
        'hosting': 'Live TV. Your favourite sources. Your ARVIO, in a browser. We handle the hosting — you enjoy the setup you already love.',
        'coffee': 'A cup of coffee a month. A big difference for an independent developer. Your membership helps pay for hosting and continued development.',
        'tv': 'Live TV in your browser',
        'tvBody': 'Bring your own IPTV service. Browse the programme guide and play supported channels right in ARVIO Web.',
        'download': 'Your sources. Ready to download.',
        'downloadBody': 'Save supported sources from the source picker. Download on desktop, or hand off to VLC on iPhone and iPad.',
        'libraryBody': 'Your watchlists, personal lists and watch progress — connected through ARVIO Cloud.',
        'serverBody': 'Bring your Jellyfin, Plex, Emby or Silo library into the same familiar interface.',
        'sportsBody': 'Explore sports events and find sources from your connected services.',
        'free': 'Android & TV stay free. Self-hosting stays free.',
        'join': 'Support ARVIO · Get Premium',
    },
    'nl': {
        'intro': 'Neem je ARVIO mee naar Windows, Mac, iPhone, iPad en smart-tv-browsers. Je profielen, bibliotheken, add-ons en kijkvoortgang blijven verbonden via ARVIO Cloud.',
        'hosting': 'Live-tv. Jouw favoriete bronnen. Jouw ARVIO, in de browser. Wij regelen de hosting — jij geniet van je vertrouwde setup.',
        'coffee': 'Een kop koffie per maand. Een groot verschil voor een onafhankelijke ontwikkelaar. Met je lidmaatschap help je de hosting en verdere ontwikkeling te betalen.',
        'tv': 'Live-tv in je browser',
        'tvBody': 'Koppel je eigen IPTV-dienst. Bekijk de tv-gids en speel ondersteunde zenders rechtstreeks af in ARVIO Web.',
        'download': 'Jouw bronnen. Klaar om te downloaden.',
        'downloadBody': 'Bewaar ondersteunde bronnen vanuit de bronkeuze. Download op je computer of open de download in VLC op iPhone en iPad.',
        'libraryBody': 'Je kijklijsten, persoonlijke lijsten en kijkvoortgang — verbonden via ARVIO Cloud.',
        'serverBody': 'Breng je Jellyfin-, Plex-, Emby- of Silo-bibliotheek samen in dezelfde vertrouwde interface.',
        'sportsBody': 'Ontdek sportevenementen en vind bronnen via je gekoppelde diensten.',
        'free': 'Android en tv blijven gratis. Zelf hosten ook.',
        'join': 'Steun ARVIO · Kies Premium',
        'play': 'Afspelen in je browser of openen in VLC',
        'sync': 'Je profielen, bibliotheken en kijkvoortgang blijven verbonden',
    },
}
data = {}
for entry in languages:
    code = entry['code']
    base = code.split('-')[0]
    locale = code if code in manifest else ('nb' if base == 'no' else base)
    dictionary = {} if base == 'en' else json.loads((root / f'web/public/i18n/{locale}.json').read_text(encoding='utf-8'))
    normalized = {k.strip().lower(): v for k, v in dictionary.items()}
    translations = {key: normalized.get(value.lower(), value) for key, value in keys.items()}
    # The main copy must never silently fall back to English.
    if base != 'en':
        for key in ('intro', 'sync', 'play', 'free', 'join', 'period', 'notice'):
            assert keys[key].lower() in normalized, (code, key)
    translations['hosting'] = hosting.get(locale, hosting.get(base))
    assert translations['hosting'], code
    translations.update({
        'coffee': translations['hosting'],
        'tvBody': translations['play'],
        'libraryBody': translations['sync'],
        'serverBody': 'Jellyfin · Plex · Emby · Silo',
        'sportsBody': translations['tv'],
    })
    if base in ('en', 'nl'):
        translations.update(feature_copy[base])
    data[code] = {'label': entry['label'], **translations}
(out / 'languages.json').write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding='utf-8')
print(f'Built Premium copy for {len(data)} language variants.')

