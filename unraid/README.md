# ARVIO Web on Unraid

This package installs the **independent browser client**, not the Android APK,
a media server or a transcoder. You supply your own authorized media sources
and API credentials. No ARVIO membership is required. The optional managed
service at web.arvio.tv is separate.

## Preview status

The template targets `ghcr.io/prodigyv21/arvio-web:unraid-preview` for
Linux x86-64 (`linux/amd64`). This is a preview, not a claim of Community Apps
acceptance. Before submitting, verify that the image is public and anonymously
pullable and that the template and this guide exist on the repository's default
branch. A draft PR or a Dockerfile alone does not meet those gates.

**Public image publication is currently gated by an unresolved third-party
source/redistribution-license review.** The source project uses Apache-2.0, but
that does not replace the licenses and corresponding-source requirements of
dependencies bundled in the container. Added license notices alone do not close
this gate. Do not publish the preview or submit it as installable before the
maintainer resolves the review described below.

## Install

After Community Apps approves the listing, search for **ARVIO-Web** in Apps.
Until then, the maintainer can test the XML from `templates/arvio-web.xml` using
Unraid's Docker template editor. Do not install a template from an unknown source.

1. Supply your own **TMDB API v3 key** from
   [TMDB's API settings](https://www.themoviedb.org/settings/api). This is the
   32-character hexadecimal v3 key, not the read-access bearer token.
2. Keep bridge networking and unprivileged mode. The default host port is
   **8133**, mapped to container port **3000**; choose another unused host port
   if necessary. No media share, Docker socket or host directory mount is needed.
3. Keep **Allow private home-server proxy** false initially. Read the security
   section below before enabling it.
4. Apply the template and open WebUI. Create/select a local profile and configure
   your sources in Settings. Use a trusted HTTPS URL for full browser support.
5. Optional Trakt, Simkl and Telegram application credentials are advanced fields.
   Use applications you own. Restart after changing them; no image rebuild is
   required. A generic image never contains the ARVIO owner's API keys.

## Important browser and storage limitations

- Profiles, settings, history and source credentials are stored in **that
  browser's site data**. They are not in an Unraid appdata volume. Changing the
  URL/origin, clearing site data or using another browser can show a fresh profile.
- This independent client does not provide ARVIO Cloud account/profile sync.
  Trakt/Simkl can synchronize their own supported watch/list data when configured;
  they are not a backup of the entire ARVIO installation.
- Keep a backup/export of browser settings where supported. Backing up the
  container filesystem does not back up browser profiles.
- Playback depends on source access, CORS, codec, DRM, browser and device support.
  Installing on Unraid does not add server-side transcoding or grant media access.
- A plain HTTP LAN-IP address is not a secure browser context. Some media,
  storage and web-app capabilities require trusted HTTPS; do not bypass browser
  certificate warnings.

## Security and private Jellyfin/Plex/Emby servers

There is no built-in authentication protecting this independent web server.
Do not forward its HTTP port to the internet. Use a VPN/private trusted network
or an authenticated HTTPS reverse proxy that protects **every path**, including
`/api/*`. Authentication on the home page alone is not sufficient.

Server-side access to private home-server addresses is blocked by default.
If you need the API proxy to reach a LAN Jellyfin/Plex/Emby server, first protect
the installation as above, then explicitly set `ALLOW_PRIVATE_PROXY=true`.
Enabling this on an exposed installation can let strangers make requests into
your private network. A provider login inside the ARVIO UI does not authenticate
incoming requests to the web server itself.

The TMDB key and OAuth secrets stay server-side. Optional public application IDs,
Telegram application credentials and the resolver URL are delivered to your
browser by a no-store self-host configuration endpoint. Do not put passwords,
user access tokens, session strings or URL-embedded credentials in those fields.
Masking a field in Unraid does not encrypt the Docker template or environment.
Redact keys and tokens from support screenshots, logs and exported templates.

## Updates and support

Push and pull-request runs **build and test only; they never publish an image**.
Publication requires an explicit `workflow_dispatch` run with
`publish_preview=true`, after the maintainer has closed the source/licensing gate.
The workflow then publishes the exact tested image as `unraid-preview` and as an
commit-named `sha-<12-character-commit>` tag; it does not publish `latest`. Tags
are mutable, including a commit tag if the same source is built again; only an
image digest identifies the exact image. These
workflow rules describe a release mechanism, not proof that any tag is already
public. Verify anonymous pulls separately. Pin a tested image digest when
reproducibility matters. Updating/recreating this stateless container should not
clear data in the browser, but changing the origin does.

Report problems at [ARVIO GitHub issues](https://github.com/ProdigyV21/ARVIO/issues).
Include Unraid version, image tag/digest, browser, whether HTTPS is used and a
redacted error. Do not upload credentials or private server addresses.

## Maintainer checks before submission

1. Run `pwsh -File scripts/check-unraid.ps1`.
   Run `pwsh -File scripts/test-unraid-contract.ps1` for adversarial metadata
   regressions. These are project-specific checks, not Unraid's own scanner.
2. Build without credentials: `docker build -t arvio-web:unraid-test web`.
3. Run `node scripts/check-unraid-container.cjs arvio-web:unraid-test`.
4. Close the source/redistribution-license review below **before** any public
   image publication. Then explicitly authorize a publishing workflow run.
5. Confirm the registry package is public and an unauthenticated pull succeeds.
6. Test a clean install, WebUI port mapping, restart/update and private home-server
   access on a real Unraid host. Docker Desktop/CI smoke tests are not Unraid tests.
7. Publish the metadata on the default branch. In the
   [Unraid submission workspace](https://ca.unraid.net/submit/new), sign in, add
   the repository, run **Validate**, then **Scan**, and review the preview.
8. The owner must approve the final submission and associated terms. Record the
   actual submission result; do not label an unsubmitted package as listed.

### Outstanding source and redistribution review

The full-featured image's closure is not yet verified for Telegram's GPL-licensed
`@cryptography/aes` dependency, the exact MPL-licensed Mediabunny source, and the
FFmpeg source/build provenance behind the embedded codec WebAssembly. Packaging
license texts and a dependency manifest is useful, but is not evidence that all
required corresponding source and build materials are supplied.

The maintainer must choose and verify either a compliant full-featured source
distribution, or a narrower preview that excludes the affected Telegram and
additional software-codec components. No feature-removal choice has been made
here, and the project/dependency licenses must not be relabelled to bypass the
review. Until that decision and verification are complete, keep image publication
and Community Apps submission paused.

Official contracts: [submission help](https://ca.unraid.net/submit/help),
[Docker XML](https://ca.unraid.net/submit/help/repository-xml),
[repository profile](https://ca.unraid.net/submit/help/repository-info-xml).
