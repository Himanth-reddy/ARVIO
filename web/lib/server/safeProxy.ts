import { lookup } from "node:dns/promises";
import { Agent, fetch as undiciFetch } from "undici";
import ipaddr from "ipaddr.js";

export function isPublicAddress(address: string) {
  try { return ipaddr.process(address.replace(/^\[|\]$/g, "")).range() === "unicast"; }
  catch { return false; }
}

const MAX_BYTES = 128 * 1024 * 1024;
const ALLOWED_HEADERS = new Set(["accept", "accept-language", "authorization", "content-type", "range", "user-agent", "referer", "origin", "x-emby-token", "x-emby-authorization", "x-plex-token"]);

export function safeProxyHeaders(headers: HeadersInit = {}) {
  const result = new Headers();
  new Headers(headers).forEach((value, key) => {
    if (ALLOWED_HEADERS.has(key) && value.length <= 8192) result.set(key, value);
  });
  return result;
}

/** Validate every hop and pin the validated DNS answer to prevent rebinding. */
export async function safeProxyFetch(target: URL, init: RequestInit): Promise<Response> {
  let current = target;
  let headers = safeProxyHeaders(init.headers);
  const signal = AbortSignal.timeout(25_000);
  for (let hop = 0; hop <= 4; hop++) {
    if (!['https:', 'http:'].includes(current.protocol) || current.username || current.password) throw new Error("Blocked proxy target");
    const hostname = current.hostname.replace(/^\[|\]$/g, "");
    const addresses = await lookup(hostname, { all: true, verbatim: true });
    const privateAllowed = process.env.ALLOW_PRIVATE_PROXY === "true" && !process.env.NETLIFY;
    if (!addresses.length || (!privateAllowed && addresses.some((entry) => !isPublicAddress(entry.address)))) throw new Error("Blocked proxy target");
    const address = addresses[0];
    const agent = new Agent({ connect: { lookup: (_hostname, options, callback) => {
      if (options.all) callback(null, [address]);
      else callback(null, address.address, address.family);
    } } });
    let response;
    try {
      response = await undiciFetch(current, {
        method: init.method ?? "GET", headers: Object.fromEntries(headers),
        body: init.body as string | undefined, redirect: "manual", dispatcher: agent, signal
      });
    } catch (error) { await agent.destroy(); throw error; }
    if ([301, 302, 303, 307, 308].includes(response.status)) {
      const location = response.headers.get("location");
      await response.body?.cancel(); await agent.destroy();
      if (!location || hop === 4) throw new Error("Invalid proxy redirect");
      const next = new URL(location, current);
      if (current.protocol === 'https:' && next.protocol !== 'https:') throw new Error("Insecure proxy redirect");
      if (current.origin !== next.origin) {
        // A target may redirect, but it may not forward another server's credentials.
        headers = new Headers({ accept: headers.get("accept") ?? "*/*" });
      }
      if (response.status === 303 || ((response.status === 301 || response.status === 302) && init.method === 'POST')) init = { ...init, method: 'GET', body: undefined };
      current = next;
      continue;
    }
    const type = response.headers.get("content-type") ?? "";
    const allowMedia = process.env.ALLOW_NETLIFY_MEDIA_PROXY === "true" || process.env.NEXT_PUBLIC_ALLOW_NETLIFY_MEDIA_PROXY === "true";
    if ((!allowMedia && /^(video\/|audio\/(?!.*mpegurl))/i.test(type)) || Number(response.headers.get("content-length")) > MAX_BYTES) {
      await response.body?.cancel(); await agent.destroy();
      throw new Error("Proxy response is not allowed or exceeds the size limit");
    }
    const reader = response.body?.getReader();
    if (!reader) { await agent.destroy(); return new Response(null, { status: response.status }); }
    let bytes = 0;
    const body = new ReadableStream<Uint8Array>({
      async pull(controller) {
        try {
          const chunk = await reader.read();
          if (chunk.done) { controller.close(); await agent.destroy(); return; }
          bytes += chunk.value.byteLength;
          if (bytes > MAX_BYTES) throw new Error("Proxy response exceeds the size limit");
          controller.enqueue(chunk.value);
        } catch (error) { controller.error(error); await reader.cancel().catch(() => undefined); await agent.destroy(); }
      },
      async cancel() { await reader.cancel().catch(() => undefined); await agent.destroy(); }
    });
    const resultHeaders = new Headers(Object.fromEntries(response.headers));
    resultHeaders.delete("content-encoding"); resultHeaders.delete("content-length");
    resultHeaders.set("x-arvio-final-url", current.toString());
    return new Response(body, { status: response.status, headers: resultHeaders });
  }
  throw new Error("Too many redirects");
}

const requests = new Map<string, { start: number; count: number }>();
export function withinProxyBudget(key: string, now = Date.now()) {
  const previous = requests.get(key);
  if (!previous || now - previous.start >= 60_000) {
    if (requests.size >= 4000) requests.delete(requests.keys().next().value!);
    requests.set(key, { start: now, count: 1 }); return true;
  }
  return ++previous.count <= 180;
}
