const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

process.env.ARVIO_AUTH_SECRET = "test-only-secret-that-is-longer-than-32-bytes";
process.env.APP_ANON_KEY = "test-app-key";

const entitlements = require("../netlify/functions/_entitlements");
const funnel = require("../netlify/functions/_premium-funnel");
const trialEmails = require("../netlify/functions/_trial-emails");
const entitlementStatus = require("../netlify/functions/entitlement-status");

test("funnel conversion matches identities instead of dividing unrelated totals", () => {
  const key = (date, account, event) => `events/date/${date}/account/${account}/${event}.json`;
  const result = funnel._test.summarizePremiumKeys([
    key('2026-08-30', 'a', 'account_connected'), key('2026-08-30', 'a', 'trial_started'),
    key('2026-08-31', 'a', 'subscription_started'), key('2026-09-01', 'unrelated', 'subscription_started'),
    key('2026-09-01', 'b', 'trial_started'), key('2026-08-30', 'b', 'subscription_started'),
    key('2026-09-05', 'new', 'trial_started'), key('2026-08-31', 'a', 'subscription_started')
  ], ['2026-08-30', '2026-08-31', '2026-09-01', '2026-09-05'], '2026-09-06T00:00:00Z');
  assert.equal(result.uniqueAccounts.subscription_started, 3);
  assert.equal(result.conversion.trialToPaid, 0.3333);
  assert.equal(result.trialCohort.paidByReportEnd, 1);
  assert.equal(result.trialCohort.atLeastThreeCompleteDaysObserved, 2);
  assert.equal(result.trialCohort.maturePaidByReportEnd, 1);
  assert.equal(result.includesPartialToday, false);
});
test("browser analytics cannot claim a subscription, renewal or trial success", () => {
  for (const event of ['subscription_started', 'subscription_renewed', 'trial_started', 'trial_email_welcome_sent', 'billing_email_verified']) {
    assert.equal(funnel.CLIENT_PREMIUM_EVENTS.has(event), false);
  }
  assert.equal(funnel.CLIENT_PREMIUM_EVENTS.has('checkout_opened'), true);
});

test("different billing emails join only with verified, unambiguous ownership", () => {
  const keys = ['events/date/2026-09-01/account/cloud/trial_started.json', 'events/date/2026-09-02/account/billing/subscription_started.json'];
  const dates = ['2026-09-01', '2026-09-02'];
  const report = links => funnel._test.summarizePremiumKeys(keys, dates, '2026-09-06T00:00:00Z', links);
  assert.equal(report([]).trialCohort.paidByReportEnd, 0);
  assert.equal(report([{ billingKey: 'billing', accountKey: 'cloud' }]).trialCohort.paidByReportEnd, 1);
  assert.equal(report([{ billingKey: 'billing', accountKey: 'cloud' }, { billingKey: 'billing', accountKey: 'other' }]).trialCohort.paidByReportEnd, 0);
});

test("new trials last three days while existing records retain their own expiry", () => {
  assert.equal(entitlements.TRIAL_DAYS, 3);
  assert.equal(entitlements.TRIAL_MS, 3 * 24 * 60 * 60 * 1000);
  assert.equal(entitlements.evaluateEntitlement(null).trialDurationDays, 3);

  const expiresAt = new Date(Date.now() + 30_000).toISOString();
  const state = entitlements.evaluateEntitlement({
    status: "active",
    source: "trial",
    expiresAt,
    trialUsed: true
  });
  assert.equal(state.entitled, true);
  assert.equal(state.expiresAt, expiresAt);
});

test("trial email jobs encrypt addresses and produce exactly three service messages", () => {
  const email = "person@example.org";
  const sealed = trialEmails._test.sealEmail(email);
  assert.equal(sealed.includes(email), false);
  assert.equal(trialEmails._test.openEmail(sealed), email);
  assert.deepEqual(trialEmails.JOB_TYPES, ["welcome", "reminder", "expired"]);

  const expiresAt = "2026-08-22T12:00:00.000Z";
  assert.match(trialEmails._test.trialEmailContent("welcome", expiresAt).subject, /3-day/i);
  assert.match(trialEmails._test.trialEmailContent("welcome", expiresAt).text, /Windows, Mac/i);
  assert.match(trialEmails._test.trialEmailContent("reminder", expiresAt).subject, /tomorrow/i);
  assert.match(trialEmails._test.trialEmailContent("expired", expiresAt).text, /final email/i);
});

test("premium funnel metadata is bounded and excludes complex values", () => {
  const metadata = funnel._test.sanitizeMetadata({
    source: "website\nspoofed",
    duration_days: 3,
    successful: true,
    nested: { private: "value" },
    "bad key": "ignored"
  });
  assert.deepEqual(metadata, {
    source: "website spoofed",
    duration_days: 3,
    successful: true
  });
});

test("trial requests tolerate missing, object, and base64 encoded bodies", () => {
  const requestAction = entitlementStatus._test.requestAction;
  assert.equal(requestAction({ body: JSON.stringify({ action: "start-trial" }) }), "start-trial");
  assert.equal(requestAction({ body: { action: "START-TRIAL" } }), "start-trial");
  assert.equal(requestAction({ body: "" }), "start-trial");
  assert.equal(requestAction({ body: "not-json" }), "start-trial");
  assert.equal(requestAction({
    body: Buffer.from(JSON.stringify({ action: "start-trial" })).toString("base64"),
    isBase64Encoded: true
  }), "start-trial");
});

test("public Premium presentation has one tracked route and factual social proof", () => {
  const root = path.join(__dirname, "..", "..");
  const html = fs.readFileSync(path.join(root, "netlify-arvio-tv-site", "index.html"), "utf8");
  const redirects = fs.readFileSync(path.join(root, "netlify-arvio-tv-site", "netlify.toml"), "utf8");
  assert.match(html, /Try Premium free for 3 days/i);
  assert.match(html, /Get Premium on Ko-fi/i);
  assert.match(html, /10,000\+ users/i);
  assert.match(html, /10\+ contributors/i);
  assert.match(html, /Windows, Mac and mobile/i);
  assert.match(html, /Save compatible files when your provider permits downloads/i);
  assert.match(html, /Android APK is free and open source/i);
  assert.match(html, /Optional add-on · Open-source web app/i);
  assert.match(html, /github\.com\/ProdigyV21\/ARVIO\/tree\/main\/web/);
  assert.match(html, /href="\/go\/premium\/hero"/);
  assert.match(html, /href="\/go\/membership\/nav"/);
  assert.match(html, /href="\/go\/membership\/spotlight"/);
  assert.match(redirects, /from = "\/go\/premium"/);
  assert.match(redirects, /from = "\/go\/premium\/hero"/);
  assert.match(redirects, /from = "\/go\/membership\/nav"/);
  const { premiumDestination } = require('../../netlify-arvio-tv-site/premium-redirect');
  assert.match(redirects, /to = "\/premium-redirect.html"\s+status = 200/);
  assert.match(premiumDestination('/go/membership/spotlight'), /ko-fi\.com\/arvio\/tiers.*utm_content=spotlight/);
  assert.match(premiumDestination('/go/premium/hero'), /intent=trial/);

  const login = fs.readFileSync(path.join(root, "web", "components", "login", "LoginScreen.tsx"), "utf8");
  assert.match(login, /TRIAL_INTENT_KEY/);
  assert.match(login, /get\("intent"\) === "trial"/);
});

test("website handoff measurement preserves every CTA and cannot redirect off the allowlist", () => {
  const { premiumDestination } = require('../../netlify-arvio-tv-site/premium-redirect');
  for (const placement of ['', '/nav', '/hero', '/spotlight', '/preview', '/details', '/faq', '/footer']) {
    const target = new URL(premiumDestination('/go/premium' + placement));
    assert.equal(target.origin, 'https://web.arvio.tv');
    assert.equal(target.searchParams.get('intent'), 'trial');
  }
  for (const placement of ['nav', 'spotlight', 'details', 'footer']) {
    assert.equal(new URL(premiumDestination('/go/membership/' + placement)).origin, 'https://ko-fi.com');
  }
  for (const path of ['/go/premium/https://evil.invalid', '/go/premium//evil.invalid', '/go/membership', '/go/premium?to=https://evil.invalid']) assert.equal(premiumDestination(path), null);
  const html = fs.readFileSync(path.join(__dirname, '../../netlify-arvio-tv-site/premium-redirect.html'), 'utf8');
  assert.match(html, /noindex,nofollow/);
  assert.match(html, /<noscript>/);
});
