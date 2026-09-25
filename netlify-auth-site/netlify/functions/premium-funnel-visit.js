const { parseBody } = require("./_backend");
const { premiumFunnelStore } = require("./_premium-funnel");
const { recordJourneyEvent } = require("./_premium-measurement");

const ORIGINS = new Set(["https://arvio.tv", "https://www.arvio.tv"]);

exports.handler = async (event) => {
  const origin = event.headers?.origin || event.headers?.Origin || "";
  const headers = { "content-type": "application/json", "cache-control": "no-store", "vary": "Origin" };
  const reply = (statusCode, body) => ({ statusCode, headers, body: JSON.stringify(body) });
  if (!ORIGINS.has(origin)) return reply(403, { error: "origin_not_allowed" });
  Object.assign(headers, { "access-control-allow-origin": origin, "access-control-allow-methods": "POST,OPTIONS", "access-control-allow-headers": "content-type" });
  if (event.httpMethod === "OPTIONS") return reply(204, {});
  if (event.httpMethod !== "POST") return reply(405, { error: "method_not_allowed" });
  if (String(event.body || "").length > 4096) return reply(413, { error: "payload_too_large" });
  let body;
  try { body = parseBody(event); } catch { return reply(400, { error: "bad_payload" }); }
  try {
    const result = await recordJourneyEvent(premiumFunnelStore(event), event, body);
    return reply(result.status, result.error ? { error: result.error } : { ok: true });
  } catch (error) {
    // No URLs, IDs, tokens, payloads or exception messages enter the log.
    console.error("premium-funnel-visit unavailable", { name: /^[A-Za-z][A-Za-z0-9]{0,60}$/.test(error?.name || "") ? error.name : "Error" });
    return reply(503, { error: "measurement_unavailable" });
  }
};
