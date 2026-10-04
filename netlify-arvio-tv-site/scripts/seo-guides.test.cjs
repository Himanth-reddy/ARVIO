const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const siteRoot = path.resolve(__dirname, '..');
const origin = 'https://arvio.tv';
const updated = '2026-10-04';
const routes = [
  { route: '/android-tv-media-hub/', pt: '/pt-br/central-midia-android-tv/', es: '/es/centro-multimedia-android-tv/' },
  { route: '/arvio-web/', pt: '/pt-br/arvio-web/', es: '/es/arvio-web/' },
  { route: '/fire-tv-media-player/', pt: '/pt-br/arvio-fire-tv/', es: '/es/arvio-fire-tv/' }
];
const entities = { amp: '&', lt: '<', gt: '>', quot: '"', apos: "'", nbsp: ' ', ndash: '–', mdash: '—', lsquo: '‘', rsquo: '’', ldquo: '“', rdquo: '”', copy: '©', hellip: '…' };

function decode(value) {
  return String(value).replace(/&(#x[\da-f]+|#\d+|[a-z]+);/giu, (whole, name) => {
    if (name.startsWith('#')) return String.fromCodePoint(parseInt(name.slice(/^#x/i.test(name) ? 2 : 1), /^#x/i.test(name) ? 16 : 10));
    return entities[name] ?? whole;
  });
}

// Small tokenizer for these static documents: attribute order/quotes and nested
// inline markup do not affect checks, and script contents remain raw JSON.
function parse(source) {
  const root = { tag: '#document', attrs: {}, children: [] };
  const stack = [root];
  const voids = new Set(['area', 'base', 'br', 'col', 'embed', 'hr', 'img', 'input', 'link', 'meta', 'param', 'source', 'track', 'wbr']);
  const tags = /<!--[\s\S]*?-->|<![^>]*>|<\/?[a-z][a-z\d:-]*(?:"[^"]*"|'[^']*'|[^'">])*>/giu;
  let cursor = 0;
  let match;
  while ((match = tags.exec(source))) {
    stack.at(-1).children.push({ text: source.slice(cursor, match.index) });
    const token = match[0];
    cursor = tags.lastIndex;
    if (token.startsWith('<!')) continue;
    const tag = /^<\/?([a-z][a-z\d:-]*)/iu.exec(token)[1].toLowerCase();
    if (token.startsWith('</')) {
      const index = stack.findLastIndex((node) => node.tag === tag);
      if (index > 0) stack.length = index;
      continue;
    }
    const attrs = {};
    const attributeText = token.slice(tag.length + 1, token.endsWith('/>') ? -2 : -1);
    for (const attr of attributeText.matchAll(/([^\s=/>]+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+)))?/gu)) {
      attrs[attr[1].toLowerCase()] = decode(attr[2] ?? attr[3] ?? attr[4] ?? '');
    }
    const node = { tag, attrs, children: [] };
    stack.at(-1).children.push(node);
    if (tag === 'script' || tag === 'style') {
      const close = new RegExp(`</${tag}\\s*>`, 'gi');
      close.lastIndex = cursor;
      const end = close.exec(source);
      assert.ok(end, `Unclosed ${tag}`);
      node.children.push({ text: source.slice(cursor, end.index) });
      cursor = tags.lastIndex = close.lastIndex;
    } else if (!voids.has(tag) && !token.endsWith('/>')) stack.push(node);
  }
  stack.at(-1).children.push({ text: source.slice(cursor) });
  return root;
}

function select(node, predicate) {
  return (node.children ?? []).flatMap((child) => child.tag ? [...(predicate(child) ? [child] : []), ...select(child, predicate)] : []);
}
const byTag = (node, tag) => select(node, (child) => child.tag === tag);
const rawText = (node) => node.text ?? (node.children ?? []).map(rawText).join('');
const normalize = (text) => decode(text).replace(/\s+/gu, ' ').trim();
const visibleText = (node) => normalize(rawText(node));
const relContains = (node, value) => (node.attrs.rel ?? '').split(/\s+/u).includes(value);
function one(nodes, message) {
  assert.equal(nodes.length, 1, message);
  return nodes[0];
}
function localFile(url) {
  const resolved = path.resolve(siteRoot, `.${decodeURIComponent(url.pathname)}`);
  assert.ok(resolved === siteRoot || resolved.startsWith(`${siteRoot}${path.sep}`), `Path outside site: ${url.href}`);
  assert.ok(fs.existsSync(resolved), `Missing local target: ${url.href}`);
  const file = fs.statSync(resolved).isDirectory() ? path.join(resolved, 'index.html') : resolved;
  assert.ok(fs.existsSync(file) && fs.statSync(file).isFile(), `Missing local file: ${url.href}`);
  return file;
}
const parsedFiles = new Map();
function documentAt(file) {
  if (!parsedFiles.has(file)) parsedFiles.set(file, parse(fs.readFileSync(file, 'utf8')));
  return parsedFiles.get(file);
}
const pages = routes.map((config) => {
  const canonical = new URL(config.route, origin).href;
  return { ...config, canonical, doc: documentAt(localFile(new URL(canonical))) };
});

test('revised English guides have distinct nonempty titles', () => {
  const titles = pages.map(({ route, doc }) => {
    const title = visibleText(one(byTag(doc, 'title'), `${route}: one title`));
    assert.ok(title, `${route}: empty title`);
    return title;
  });
  assert.equal(new Set(titles).size, pages.length, 'Guide titles must be unique');
});

for (const { route, canonical, pt, es, doc } of pages) {
  test(`${route} metadata, language and alternate URLs`, () => {
    assert.equal(one(byTag(doc, 'html'), 'One html element').attrs.lang, 'en');
    const meta = byTag(doc, 'meta');
    const description = one(meta.filter((node) => node.attrs.name === 'description'), 'One description').attrs.content;
    assert.ok(description?.trim(), 'Description must not be empty');
    const links = byTag(doc, 'link');
    assert.equal(one(links.filter((node) => relContains(node, 'canonical')), 'One canonical').attrs.href, canonical);
    const expected = { en: canonical, 'pt-BR': origin + pt, es: origin + es, 'x-default': canonical };
    const alternates = links.filter((node) => node.attrs.hreflang);
    assert.deepEqual(alternates.map((node) => node.attrs.hreflang).sort(), Object.keys(expected).sort(), 'Exactly one alternate per language');
    for (const alternate of alternates) {
      assert.ok(relContains(alternate, 'alternate'));
      assert.equal(alternate.attrs.href, expected[alternate.attrs.hreflang]);
    }
    const title = visibleText(one(byTag(doc, 'title'), 'One title'));
    for (const [key, value] of Object.entries({ 'og:title': title, 'twitter:title': title, 'og:description': description, 'twitter:description': description, 'og:url': canonical })) {
      assert.equal(one(meta.filter((node) => node.attrs.property === key || node.attrs.name === key), `One ${key}`).attrs.content, value);
    }
  });

  test(`${route} JSON-LD agrees with the heading and visible FAQ`, () => {
    const heading = visibleText(one(byTag(doc, 'h1'), 'Exactly one h1'));
    assert.ok(heading, 'Heading must not be empty');
    const blocks = byTag(doc, 'script').filter((node) => node.attrs.type === 'application/ld+json');
    assert.ok(blocks.length, 'Missing JSON-LD');
    const graph = blocks.flatMap((node) => {
      const parsed = JSON.parse(rawText(node));
      return Array.isArray(parsed) ? parsed.flatMap((item) => item['@graph'] ?? [item]) : parsed['@graph'] ?? [parsed];
    });
    const article = one(graph.filter((node) => [node['@type']].flat().includes('TechArticle')), 'One TechArticle');
    assert.equal(article.dateModified, updated);
    assert.equal(normalize(article.headline), heading);
    assert.equal(article.url, canonical);
    const faq = one(graph.filter((node) => [node['@type']].flat().includes('FAQPage')), 'One FAQPage');
    const lists = select(doc, (node) => (node.attrs.class ?? '').split(/\s+/u).includes('faq-list'));
    const details = lists.flatMap((list) => byTag(list, 'details'));
    assert.ok(details.length, 'Missing visible FAQ');
    const visible = details.map((detail) => {
      const summary = one(byTag(detail, 'summary'), 'One summary per FAQ');
      return {
        question: visibleText(summary),
        answer: normalize(detail.children.filter((node) => node !== summary).map(rawText).join(' '))
      };
    });
    assert.ok(Array.isArray(faq.mainEntity), 'FAQ mainEntity must be an array');
    const structured = faq.mainEntity.map((question) => {
      assert.equal(question['@type'], 'Question');
      assert.equal(question.acceptedAnswer?.['@type'], 'Answer');
      return { question: normalize(question.name), answer: visibleText(parse(question.acceptedAnswer.text)) };
    });
    assert.deepEqual(structured, visible, 'Structured FAQ must match visible questions and answers in order');
  });

  test(`${route} internal links and fragments resolve`, () => {
    for (const node of select(doc, (entry) => Object.hasOwn(entry.attrs, 'href'))) {
      const url = new URL(node.attrs.href, canonical);
      if (url.origin !== origin || url.pathname === '/privacy' || url.pathname.startsWith('/go/')) continue;
      const file = localFile(url);
      if (!url.hash) continue;
      const fragment = decodeURIComponent(url.hash.slice(1));
      const targets = select(documentAt(file), (entry) => entry.attrs.id === fragment || (entry.tag === 'a' && entry.attrs.name === fragment));
      assert.ok(targets.length, `Missing fragment: ${url.href}`);
    }
  });

  test(`${route} images exist and reserve layout dimensions`, () => {
    const images = byTag(doc, 'img');
    assert.ok(images.length, 'Missing guide images');
    for (const image of images) {
      assert.ok(image.attrs.src, 'Image missing src');
      const url = new URL(image.attrs.src, canonical);
      assert.equal(url.origin, origin, `Expected a checked-in image: ${url.href}`);
      localFile(url);
      assert.ok(/^[1-9]\d*$/u.test(image.attrs.width ?? ''), `${image.attrs.src}: missing positive width`);
      assert.ok(/^[1-9]\d*$/u.test(image.attrs.height ?? ''), `${image.attrs.src}: missing positive height`);
      assert.ok(image.attrs.alt?.trim(), `${image.attrs.src}: missing alt text`);
    }
    for (const meta of byTag(doc, 'meta').filter((node) => node.attrs.property === 'og:image' || node.attrs.name === 'twitter:image')) {
      const url = new URL(meta.attrs.content, canonical);
      assert.equal(url.origin, origin);
      localFile(url);
    }
  });
}

test('sitemap includes each revised guide once with its modification date', () => {
  const sitemap = parse(fs.readFileSync(path.join(siteRoot, 'sitemap.xml'), 'utf8'));
  for (const { canonical } of pages) {
    const entry = one(byTag(sitemap, 'url').filter((node) => byTag(node, 'loc').some((loc) => visibleText(loc) === canonical)), `${canonical}: one sitemap entry`);
    assert.equal(visibleText(one(byTag(entry, 'lastmod'), `${canonical}: one lastmod`)), updated);
  }
});
