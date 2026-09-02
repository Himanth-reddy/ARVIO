const test = require("node:test");
const assert = require("node:assert/strict");
const { _test } = require("../netlify/functions/account-sync-push");

test("newer tracking routing survives a stale device push", () => {
  const existing = {
    payload: {
      mdbListSyncByProfile: {
        profile: { provider: "TRAKT", watchlistReadMode: "TRAKT", updatedAt: 200 }
      }
    }
  };
  const incoming = {
    mdbListSyncByProfile: {
      profile: { provider: "TRAKT", watchlistReadMode: "BOTH", updatedAt: 100 }
    }
  };

  const result = _test.preserveTrackingRouting(existing, incoming);
  assert.equal(result.mdbListSyncByProfile.profile.watchlistReadMode, "TRAKT");
  assert.equal(result.mdbListSyncByProfile.profile.updatedAt, 200);
});

test("credential domains merge independently from routing", () => {
  const existing = {
    payload: {
      mdbListSyncByProfile: {
        profile: {
          watchlistReadMode: "TRAKT",
          updatedAt: 100,
          simklAccessToken: "new-token",
          simklCredentialUpdatedAt: 300
        }
      }
    }
  };
  const incoming = {
    mdbListSyncByProfile: {
      profile: {
        watchlistReadMode: "SIMKL",
        updatedAt: 200,
        simklAccessToken: "old-token",
        simklCredentialUpdatedAt: 150
      }
    }
  };

  const result = _test.preserveTrackingRouting(existing, incoming);
  assert.equal(result.mdbListSyncByProfile.profile.watchlistReadMode, "SIMKL");
  assert.equal(result.mdbListSyncByProfile.profile.simklAccessToken, "new-token");
});

test("a newer explicit Trakt disconnect beats an older cloud token", () => {
  const existing = {
    payload: {
      traktTokens: { profile: { accessToken: "token", updatedAt: 100 } }
    }
  };
  const incoming = { traktTokens: { profile: { updatedAt: 200 } } };

  const result = _test.preserveTraktTokens(existing, incoming);
  assert.deepEqual(result.traktTokens.profile, { updatedAt: 200 });
});

test("a missing Trakt token from a stale snapshot cannot erase cloud auth", () => {
  const existing = {
    payload: {
      traktTokens: { profile: { accessToken: "token", updatedAt: 200 } }
    }
  };

  const result = _test.preserveTraktTokens(existing, { traktTokens: {} });
  assert.equal(result.traktTokens.profile.accessToken, "token");
});
