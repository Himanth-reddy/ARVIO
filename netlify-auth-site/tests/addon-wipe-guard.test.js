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

test("deliberate removal of several addons at once (3 -> 1) is kept", () => {
  const existing = { payload: snapshot(["opensubtitles", "a", "b"], 1000) };
  const { payload, guarded } = guard(existing, snapshot(["opensubtitles"], 2000));
  assert.equal(guarded, false);
  assert.deepEqual(ids(payload.addons), ["opensubtitles"]);
  assert.deepEqual(ids(payload.addonsByProfile.p1), ["opensubtitles"]);
  assert.equal(payload.addonsUpdatedAt, 2000);
});

test("shrink without a newer stamp is still merged back and keeps the stored stamp", () => {
  const existing = { payload: snapshot(["opensubtitles", "a", "b"], 1000) };
  for (const stamp of [undefined, 0, 500, 1000]) {
    const { payload, guarded } = guard(existing, snapshot(["opensubtitles"], stamp));
    assert.equal(guarded, true);
    assert.deepEqual(ids(payload.addons).sort(), ["a", "b", "opensubtitles"]);
    assert.deepEqual(ids(payload.addonsByProfile.p1).sort(), ["a", "b", "opensubtitles"]);
    assert.equal(payload.addonsUpdatedAt, 1000);
  }
});

test("legacy cloud without a stamp still accepts a stamped deliberate removal", () => {
  const existing = { payload: snapshot(["opensubtitles", "a", "b"]) };
  const { payload, guarded } = guard(existing, snapshot(["opensubtitles"], 2000));
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
