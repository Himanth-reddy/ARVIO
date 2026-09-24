const { json, options } = require("./_backend");
const { premiumFunnelReport } = require("./_premium-funnel");

exports.handler = async (event) => {
  const cors = options(event);
  if (cors) return cors;
  if (event.httpMethod !== "GET") return json(405, { error: "method_not_allowed" });
  const secret = process.env.ADMIN_SECRET || "";
  if (!secret || event.headers["x-admin-secret"] !== secret) {
    return json(401, { error: "unauthorized" });
  }
  try {
    const days = event.queryStringParameters?.days ||
      new URLSearchParams(event.rawQuery || event.rawQueryString || "").get("days") || 30;
    const endDate = event.queryStringParameters?.end_date || new URLSearchParams(event.rawQuery || event.rawQueryString || "").get("end_date") || undefined;
    return json(200, await premiumFunnelReport(event, days, endDate));
  } catch (error) {
    if (error.statusCode === 400) return json(400, { error: "invalid_report_end_date" });
    console.error("premium-funnel-report failed", error);
    return json(500, { error: "premium_report_failed" });
  }
};
