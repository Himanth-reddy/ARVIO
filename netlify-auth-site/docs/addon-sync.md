# Add-on removal sync

`addonChanges` is an account-level map keyed by add-on ID. Each entry contains
`updatedAt` (positive epoch milliseconds) and `removed` (boolean). Only explicit
install/reinstall and removal actions create entries. Toggle, refresh, reorder,
cloud restore, and incomplete snapshots must not imply a removal or reinstall.

The backend merges entries by their individual timestamps. Removal wins a tie.
Lists are unioned and then filtered by removal records across root and profile
copies. Records remain in the snapshot so delayed uploads cannot resurrect a
removed add-on. A newer explicit install record allows reinstalling it. These
records contain IDs and timestamps, not URLs or credentials.

Android persists the records alongside the shared list in DataStore and exports
the list, records, and set timestamp atomically. Cloud application merges local
pending records instead of discarding them. Web persists original operation
timestamps in its existing outbox so retries do not become new edits.

## Rollout

- Deploy this auth backend, then publish the updated Android app and web app.
- Merging alone does not update already-installed APKs or deploy this backend.
- With legacy clients on both sides, retain the existing conservative shrink
  guard. A newer set timestamp alone is never proof of a removal.
- Once explicit records exist, old clients can still upload additions and
  metadata but cannot express intentional removals or reinstall recorded deletions. Use an
  updated client for those actions. Do not promise legacy APKs the new behavior.
- This adds no polling, network endpoint, database table, or per-operation write.
  Records travel in the existing account snapshot requests.

Validation includes multi-removal, stale toggles, delayed retries, reinstalls,
all profile copies, malformed records, and OpenSubtitles protection. Run
`npm test` in this directory, web tests/typecheck, and Android add-on/cloud tests.
