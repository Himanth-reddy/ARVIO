# ARVIO Web — Unraid Community Applications templates

This is the dedicated template feed for the independent ARVIO browser client.
Submit **https://github.com/ProdigyV21/ARVIO-Unraid** to Community Applications,
not the Android application source repository. Keeping this feed template-only
prevents Android resources and manifests from being mistaken for app templates.

**Current status: the container image is not published yet. Do not install or
submit this feed until the release gates below pass.** This repository prepares
the metadata; it is not an install-ready listing or Community Applications approval.

| Application | Image | Template |
| --- | --- | --- |
| ARVIO-Web (preview) | `ghcr.io/prodigyv21/arvio-web:unraid-preview` | [arvio-web.xml](templates/arvio-web.xml) |

The application source, Dockerfile, build workflow and container tests are in
[ProdigyV21/ARVIO](https://github.com/ProdigyV21/ARVIO). This feed's metadata uses
Apache-2.0; dependencies in the image retain their own licenses. A valid XML
file alone does not prove that an image is published, installable or accepted
by Community Applications.

## Before installation or submission

The preview must pass its source/redistribution review and be public and
anonymously pullable before it can be submitted as installable. Confirm the
current status in the [application's Unraid guide](https://github.com/ProdigyV21/ARVIO/blob/main/unraid/README.md).
Do not substitute another publisher's image or treat a passing metadata scan
as Community Applications approval.

## Installation

ARVIO-Web is a browser media hub, **not** a media server, transcoder or Android
APK. No media, subscriptions or ARVIO Cloud sync are included. You supply your
own authorized sources and a TMDB API v3 key. Optional provider application
credentials belong to you; the image contains no ARVIO owner's integration keys.

After Community Applications approves the listing, search for **ARVIO-Web**.
For maintainer testing before approval, use the [raw template](https://raw.githubusercontent.com/ProdigyV21/ARVIO-Unraid/main/templates/arvio-web.xml)
in Unraid's Docker template editor.

1. Keep **bridge** networking and **unprivileged** mode. Host port **8133** maps
   to container port **3000**. No appdata, media-share or Docker-socket mount is
   needed: profiles/settings/history stay in each browser's site data.
2. Supply your own 32-character **TMDB API v3 key**, not a read-access bearer token.
3. Leave **Allow private home-server proxy** false unless the installation is
   private and protected. Read the full [security/setup guide](https://github.com/ProdigyV21/ARVIO/blob/main/unraid/README.md)
   before enabling access to LAN Plex, Jellyfin or Emby addresses.
4. Open WebUI, create/select a local profile and configure sources in Settings.
   A trusted HTTPS origin is needed for full browser capabilities.
5. Restart after changing optional provider application credentials.

There is no built-in server authentication. **Never expose the HTTP port or
unrestricted private proxy to the internet.** Use a private network/VPN or an
authenticated HTTPS reverse proxy protecting **all routes, including `/api/*`**.
Masking an Unraid field does not encrypt saved templates or container variables.

Changing the browser/origin, clearing site data or using another device can
show a fresh profile. A container backup is not a browser profile backup.
Playback depends on provider access, CORS, codecs, DRM, browser and device;
installing on Unraid does not add transcoding or grant media access.

## Support and feed maintenance

Report bugs at [ARVIO issues](https://github.com/ProdigyV21/ARVIO/issues), including
Unraid version, image tag/digest, browser and a redacted error. Do not share
keys, tokens or private server URLs. This is an ARVIO-maintained template, not
an endorsement by Unraid, Plex, Jellyfin or Emby.

The maintainer exports these files from the app source using
`scripts/export-unraid-feed.ps1` into a new empty directory and reviews them
before committing to this feed. Keep the root `ca_profile.xml`, a non-empty
`Profile`, root `LICENSE` and exactly one `templates/arvio-web.xml` with its
canonical `TemplateURL`. Do not copy Android XML or unrelated application code.
After each metadata update run **Validate**, then **Scan** in the
[submission workspace](https://ca.unraid.net/submit/new).
