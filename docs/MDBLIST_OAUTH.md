# MDBList OAuth release checks

MDBList API-key login remains supported. An absent or placeholder client ID opens
the API-key dialog; a failed device-code request also offers that fallback.

## Configuration

- Register ARVIO as a public device-flow client with MDBList. Confirm that both
  device authorization and refresh grants work without a client secret.
- Android local builds: set `MDBLIST_CLIENT_ID` in the untracked
  `secrets.properties`. GitHub signed builds read the repository secret with that
  name. This is the public client identifier, not a client secret.
- Web deployments: the deployment workflow passes the same public identifier as
  `NEXT_PUBLIC_MDBLIST_CLIENT_ID`. Self-hosted servers may instead configure
  `MDBLIST_CLIENT_ID` at runtime. The refresh proxy returns 503 if neither is set.
- Never place an OAuth client secret in Android, browser code, or these settings.

## Required live verification

Automated tests use synthetic credentials. They do not demonstrate a registered
client's access to MDBList. Before enabling this flow for users, verify:

1. TV device-code authorization succeeds, including remote navigation to Cancel
   and the API-key fallback. Mobile authorization also succeeds.
2. The granted scopes include read and write. Read the watchlist, then add/remove
   a test item and confirm it on MDBList.
3. A refresh grant succeeds without a client secret. A rotated refresh token is
   persisted and synced; an omitted refresh token keeps the previous one.
4. Cloud restore on another profile/device and web supports watchlist, watched
   history, writes, and renewal without converting a token into an API key.
5. Cancel, disconnect, and profile changes during authentication/renewal do not
   reconnect the account or replace another profile's credentials.

The presence of a GitHub secret alone does not confirm these checks.
