const test = require("node:test");
const assert = require("node:assert/strict");
const { applyAddonWipeGuard: guard } = require("../netlify/functions/_backend");

const addon = (id) => ({ id, manifestUrl: `https://${id}.example/manifest.json` });
const snapshot = (ids, addonsUpdatedAt) => {
  const payload = { addons: ids.map(addon), addonsByProfile: { p1: ids.map(addon) } };
  if (addonsUpdatedAt !== undefined) payload.addonsUpdatedAt = addonsUpdatedAt;
  return payload;
};
const ids = (list) => list.map((a) => a.id);
const removal = (updatedAt) => ({ removed: true, updatedAt });
const explicit = (list, time, changes = {}) => ({ ...snapshot(list, time), addonChanges: changes });

test("deliberate removal of several addons at once (3 -> 1) is kept", () => {
  const existing = { payload: snapshot(["opensubtitles", "a", "b"], 1000) };
  const { payload, guarded } = guard(existing, explicit(["opensubtitles"], 2000, { a: removal(2000), b: removal(2000) }));
  assert.equal(guarded, false);
  assert.deepEqual(ids(payload.addons), ["opensubtitles"]);
  assert.deepEqual(ids(payload.addonsByProfile.p1), ["opensubtitles"]);
  assert.equal(payload.addonsUpdatedAt, 2000);
});

test("shrink without a newer stamp is still merged back and keeps the stored stamp", () => {
  const existing = { payload: snapshot(["opensubtitles", "a", "b"], 1000) };
  for (const stamp of [undefined, 0, 500, 1000, 2000]) {
    const { payload, guarded } = guard(existing, snapshot(["opensubtitles"], stamp));
    assert.equal(guarded, true);
    assert.deepEqual(ids(payload.addons).sort(), ["a", "b", "opensubtitles"]);
    assert.deepEqual(ids(payload.addonsByProfile.p1).sort(), ["a", "b", "opensubtitles"]);
    assert.equal(payload.addonsUpdatedAt, 1000);
  }
});

test("legacy cloud accepts explicit removals", () => {
  const existing = { payload: snapshot(["opensubtitles", "a", "b"]) };
  const { payload, guarded } = guard(existing, explicit(["opensubtitles"], 2000, { a: removal(2000), b: removal(2000) }));
  assert.equal(guarded, false);
  assert.deepEqual(ids(payload.addons), ["opensubtitles"]);
});

test("legacy pushes on both sides keep the original protection", () => {
  const existing = { payload: snapshot(["opensubtitles", "a", "b"]) };
  const { payload, guarded } = guard(existing, snapshot(["opensubtitles"]));
  assert.equal(guarded, true);
  assert.deepEqual(ids(payload.addons).sort(), ["a", "b", "opensubtitles"]);
  assert.equal("addonsUpdatedAt" in payload, false);
});

test("non-numeric stamps count as missing", () => {
  const existing = { payload: snapshot(["opensubtitles", "a", "b"], 1000) };
  const { guarded } = guard(existing, snapshot(["opensubtitles"], "not-a-number"));
  assert.equal(guarded, true);
});

test("small removals pass untouched regardless of stamp", () => {
  const existing = { payload: snapshot(["opensubtitles", "a", "b"], 1000) };
  const { payload, guarded } = guard(existing, snapshot(["opensubtitles", "a"], 500));
  assert.equal(guarded, false);
  assert.deepEqual(ids(payload.addons), ["opensubtitles", "a"]);
  assert.equal(payload.addonsUpdatedAt, 500);
});

test("missing per-profile map is still preserved", () => {
  const existing = { payload: snapshot(["opensubtitles", "a", "b"], 1000) };
  const incoming = { addons: [addon("opensubtitles"), addon("a")], addonsUpdatedAt: 2000 };
  const { payload, guarded } = guard(existing, incoming);
  assert.equal(guarded, true);
  assert.deepEqual(ids(payload.addonsByProfile.p1), ["opensubtitles", "a", "b"]);
});

test("stale device toggle with a newer timestamp cannot wipe cloud additions", () => {
  const existing = { payload: snapshot(["opensubtitles", "a", "b"], 1000) };
  const { payload } = guard(existing, explicit(["opensubtitles"], 2000));
  assert.deepEqual(ids(payload.addons), ["opensubtitles", "a", "b"]);
  assert.deepEqual(ids(payload.addonsByProfile.p1), ["opensubtitles", "a", "b"]);
});

test("explicit removal cannot remove an addon unknown to the deleting device", () => {
  const existing = { payload: explicit(["opensubtitles", "a", "b"], 1000) };
  const { payload } = guard(existing, explicit(["opensubtitles"], 2000, { a: removal(2000) }));
  assert.deepEqual(ids(payload.addons), ["opensubtitles", "b"]);
});

test("legacy stale upload cannot resurrect a deleted addon even with a newer timestamp", () => {
  const existing = { payload: explicit(["opensubtitles"], 2000, { a: removal(2000) }) };
  const { payload } = guard(existing, snapshot(["opensubtitles", "a"], 3000));
  assert.deepEqual(ids(payload.addons), ["opensubtitles"]);
  assert.equal(payload.addonChanges.a.removed, true);
});

test("intentional reinstall wins and a delayed removal retry cannot undo it", () => {
  const existing = { payload: explicit(["opensubtitles"], 2000, { a: removal(2000) }) };
  const added = guard(existing, explicit(["opensubtitles", "a"], 3000, { a: { removed: false, updatedAt: 3000 } })).payload;
  assert.deepEqual(ids(added.addons), ["opensubtitles", "a"]);
  const retried = guard({ payload: added }, explicit(["opensubtitles"], 4000, { a: removal(2000) })).payload;
  assert.deepEqual(ids(retried.addons), ["opensubtitles", "a"]);
  assert.equal(retried.addonChanges.a.removed, false);
});

test("removal filters every profile and root copy", () => {
  const old = explicit(["opensubtitles", "a"], 1000);
  old.addonsByProfile.p2 = [addon("a"), addon("b")];
  const { payload } = guard({ payload: old }, explicit(["opensubtitles"], 2000, { a: removal(2000) }));
  assert.deepEqual(ids(payload.addons), ["opensubtitles", "b"]);
  for (const list of Object.values(payload.addonsByProfile)) assert.ok(!ids(list).includes("a"));
});

test("invalid removal records and OpenSubtitles removal are ignored", () => {
  const old = snapshot(["opensubtitles", "a", "b"], 1000);
  const { payload } = guard({ payload: old }, explicit([], 2000, {
    opensubtitles: removal(2000), a: removal("2000"), b: { removed: "true", updatedAt: 2000 }
  }));
  assert.deepEqual(ids(payload.addons), ["opensubtitles", "a", "b"]);
});

test("first upload filters explicit removals and replay is idempotent", () => {
  const input = explicit(["opensubtitles", "a"], 2000, { a: removal(2000) });
  const first = guard(null, input).payload;
  assert.deepEqual(ids(first.addons), ["opensubtitles"]);
  assert.deepEqual(guard({ payload: first }, input).payload, first);
});

test("shared addon toggle is not overwritten by an older profile copy", () => {
  const old = explicit(["opensubtitles", "a"], 1000);
  const incoming = explicit(["opensubtitles", "a"], 2000);
  incoming.addons[1].isEnabled = false;
  incoming.addonsByProfile.p2 = [addon("a")];
  const { payload } = guard({ payload: old }, incoming);
  assert.equal(payload.addons.find(a => a.id === "a").isEnabled, false);
  for (const list of Object.values(payload.addonsByProfile)) assert.equal(list.find(a => a.id === "a").isEnabled, false);
});
