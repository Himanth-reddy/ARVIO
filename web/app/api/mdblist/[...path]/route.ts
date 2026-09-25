import { NextRequest, NextResponse } from "next/server";

// Proxies MDBList (https://api.mdblist.com) so browser calls avoid CORS and the
// user's credentials are attached server-side.
// - OAuth Bearer token can arrive via `authorization: Bearer <token>` or `x-mdblist-token`.
// - Legacy API key arrives via `x-mdblist-key`.
// Clean separation: OAuth uses Bearer ONLY (no ?apikey=). Legacy API key uses ?apikey= ONLY (no Bearer).
async function handler(request: NextRequest, context: { params: Promise<{ path: string[] }> }) {
  const { path } = await context.params;
  const input = new URL(request.url);
  const method = request.method;
  const body = method === "GET" || method === "HEAD" ? undefined : await request.text();

  const authHeader = request.headers.get("authorization") ?? "";
  const tokenHeader = request.headers.get("x-mdblist-token") ?? "";
  const bearerToken = authHeader.toLowerCase().startsWith("bearer ")
    ? authHeader.slice(7).trim()
    : tokenHeader.trim();
  const apiKey = request.headers.get("x-mdblist-key")?.trim() ?? "";

  if (!bearerToken && !apiKey) {
    return NextResponse.json({ error: "Missing MDBList credentials" }, { status: 400 });
  }

  const trailingSlash = input.pathname.endsWith("/") ? "/" : "";
  const target = new URL(`https://api.mdblist.com/${path.join("/")}${trailingSlash}`);
  input.searchParams.forEach((value, key) => {
    if (key !== "apikey" && key !== "access_token") target.searchParams.set(key, value);
  });

  const upstreamHeaders = new Headers();
  if (bearerToken) {
    upstreamHeaders.set("authorization", `Bearer ${bearerToken}`);
  } else {
    target.searchParams.set("apikey", apiKey);
  }
  if (body) {
    upstreamHeaders.set("content-type", "application/json");
  }

  const response = await fetch(target, {
    method,
    headers: upstreamHeaders,
    body,
    cache: "no-store"
  });

  const responseHeaders = new Headers();
  responseHeaders.set("content-type", response.headers.get("content-type") ?? "application/json");
  responseHeaders.set("cache-control", "no-store");

  return new NextResponse(response.body, {
    status: response.status,
    headers: responseHeaders
  });
}

export const GET = handler;
export const POST = handler;
export const DELETE = handler;
