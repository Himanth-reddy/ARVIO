import { serverSelfhostRuntimeConfig } from "@/lib/selfhostRuntimeConfig";

// Never prerender these values or cache them in a proxy between deployments.
export const dynamic = "force-dynamic";

export function GET() {
  const selfHosted = process.env.NEXT_PUBLIC_SELF_HOSTED === "true";
  const body = selfHosted
    ? `window.__ARVIO_SELFHOST_CONFIG__ = ${JSON.stringify(serverSelfhostRuntimeConfig(process.env)).replace(/</g, "\\u003c")};\n`
    : "/* Runtime self-host configuration is unavailable on the hosted service. */\n";
  return new Response(body, {
    status: selfHosted ? 200 : 404,
    headers: {
      "content-type": "application/javascript; charset=utf-8",
      "cache-control": "private, no-store, max-age=0",
      "x-content-type-options": "nosniff"
    }
  });
}
