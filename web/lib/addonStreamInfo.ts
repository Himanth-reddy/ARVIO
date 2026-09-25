type AddonStreamEntry = {
  name?: string | null;
  title?: string | null;
  source?: string | null;
  description?: string | null;
  url?: string | null;
  externalUrl?: string | null;
  infoHash?: string | null;
};

const mediaExtension = /\.(?:mp4|m4v|mkv|webm|mov|avi|m3u8?|mpd|ts|m2ts|m4s|mp3|m4a|aac|ogg|flac)$/i;
const diagnostic = /^(?:no (?:playable )?(?:streams?|sources?) (?:found|available)(?: for (?:this )?(?:title|movie|episode))?|match (?:was )?not found(?: or (?:has )?ended)?|(?:this )?(?:match|event) (?:has )?ended)[.!]?$/i;

/** Definite addon notices are not media, even when presented in a stream list.
 * Unknown/extensionless media URLs remain eligible; titles alone never reject a
 * real media URL. Keep torrent identities and Discord CDN video attachments.
 */
export function isInformationalAddonStream(stream: AddonStreamEntry): boolean {
  if (stream.infoHash) return false;
  const target = stream.url || stream.externalUrl;
  const labels = [stream.name, stream.title, stream.source, stream.description].filter((value): value is string => typeof value === "string");
  const isDiagnostic = labels.some(value => diagnostic.test(value.trim().replace(/^[^\p{L}\p{N}]+/u, "")));
  if (!target) return isDiagnostic;
  let url: URL;
  try { url = new URL(target.startsWith("//") ? `https:${target}` : target); } catch { return isDiagnostic; }
  if (!/^https?:$/.test(url.protocol)) return isDiagnostic;
  if (mediaExtension.test(url.pathname)) return false;
  const host = url.hostname.toLowerCase().replace(/^www\./, "");
  if (host === "discord.gg" || (host === "discord.com" || host === "discordapp.com") && /^\/(?:invite|channels)(?:\/|$)/i.test(url.pathname)) return true;
  if (["ko-fi.com", "buymeacoffee.com", "paypal.me", "patreon.com"].includes(host)) return true;
  if (host === "pengu.uk" && /^\/donate\/?$/.test(url.pathname)) return true;
  if (host === "hdhub.thevolecitor.qzz.io" && url.pathname === "/donation.html") return true;
  if (host === "paypal.com" && /^\/(?:donate|paypalme|cgi-bin)(?:\/|$)/i.test(url.pathname)) return true;
  // Sports providers use Google's bare homepage as a no-match placeholder.
  // Require both that exact destination and an explicit diagnostic message.
  if (isDiagnostic && host === "google.com" && url.pathname === "/" && !url.search && !url.hash) return true;
  // Plain diagnostic documents are never playable media. Do not classify an
  // opaque /stream URL this way: a film may legitimately share a notice's title.
  return isDiagnostic && /\.(?:html?|txt|json|png|jpe?g|gif|svg)$/i.test(url.pathname);
}
