import type { CatalogConfig } from "./types";
import { parseCustomCollections, mergeImportedCollections } from "./customCollections";

export interface CatalogPack {
  id?: string;
  name: string;
  author?: string;
  version?: string;
  description?: string;
  url: string;
  catalogs?: Array<string | { name: string; url?: string; title?: string }>;
  status?: "pending" | "approved";
}

export interface ParsedCatalogPack {
  type: "catalog-pack" | "collections";
  packId: string;
  packName: string;
  catalogs: CatalogConfig[];
}

export interface InstalledPackSummary {
  packId: string;
  packName: string;
  count: number;
  type: "catalog-pack" | "collections";
}

export async function sha256Short(input: string): Promise<string> {
  if (typeof crypto !== "undefined" && crypto.subtle) {
    try {
      const bytes = new TextEncoder().encode(input);
      const digest = new Uint8Array(await crypto.subtle.digest("SHA-256", bytes));
      return Array.from(digest.slice(0, 6), (b) => b.toString(16).padStart(2, "0")).join("");
    } catch {
      // Fall through to JS hash
    }
  }
  let hash = 0;
  for (let i = 0; i < input.length; i++) {
    hash = (hash << 5) - hash + input.charCodeAt(i);
    hash |= 0;
  }
  return Math.abs(hash).toString(16).padStart(8, "0").slice(0, 12);
}

function detectSourceType(url: string): CatalogConfig["sourceType"] {
  const lower = url.toLowerCase();
  if (lower.includes("trakt.tv")) return "trakt";
  if (lower.includes("tmdb.org") || lower.includes("themoviedb.org")) return "tmdb";
  return "mdblist";
}

function sanitizePackId(rawId: string): string {
  return rawId.toLowerCase().replace(/[^a-z0-9_-]/g, "_").slice(0, 32);
}

/**
 * Parses raw JSON string into a ParsedCatalogPack.
 * Supports both Catalog Pack Manifests and Custom Collections JSON.
 * Works uniformly for pasted text, uploaded files, and fetched URLs.
 */
export async function parsePackJson(
  jsonText: string,
  sourceIdentifier: string = "custom-input",
  fallbackMeta?: Partial<CatalogPack>
): Promise<ParsedCatalogPack> {
  const trimmed = jsonText.trim();
  if (!trimmed) throw new Error("JSON content is empty");

  let parsed: any;
  try {
    parsed = JSON.parse(trimmed);
  } catch (e: any) {
    throw new Error(`Invalid JSON: ${e?.message || "Syntax error"}`);
  }

  // 1. Check if this is a Custom Collections export (Nuvio / ARVIO collections format)
  const isCollections =
    (Array.isArray(parsed) && parsed.some((c) => c && typeof c === "object" && (Array.isArray(c.folders) || c.kind === "COLLECTION_RAIL"))) ||
    (parsed && typeof parsed === "object" && (Array.isArray(parsed.collections) || Array.isArray(parsed.folders)));

  if (isCollections) {
    const configs = await parseCustomCollections(trimmed, sourceIdentifier);
    const hash = await sha256Short(trimmed);
    const packId = configs[0]?.packId || `usercol_${hash}`;
    const packName =
      configs[0]?.packName ||
      parsed.name ||
      fallbackMeta?.name ||
      (sourceIdentifier.endsWith(".json") ? sourceIdentifier.replace(/\.json$/i, "") : "Custom Collection");

    return {
      type: "collections",
      packId,
      packName,
      catalogs: configs
    };
  }

  // 2. Otherwise handle as a Catalog Pack Manifest
  const manifestId = parsed.id
    ? sanitizePackId(String(parsed.id))
    : await sha256Short(sourceIdentifier.startsWith("http") ? sourceIdentifier : trimmed);
  const derivedPackId = `pack_${manifestId}`;
  const packName =
    parsed.name?.trim() ||
    fallbackMeta?.name?.trim() ||
    (sourceIdentifier.endsWith(".json") ? sourceIdentifier.replace(/\.json$/i, "") : "Catalog Pack");

  const rawCatalogs = Array.isArray(parsed.catalogs) ? parsed.catalogs : [];
  if (!rawCatalogs.length) {
    throw new Error("Catalog pack contains no catalog rows");
  }

  const catalogConfigs: CatalogConfig[] = [];

  for (let idx = 0; idx < rawCatalogs.length; idx++) {
    const item = rawCatalogs[idx];
    const itemName = typeof item === "string" ? item : item?.name || item?.title || `Row ${idx + 1}`;
    const itemUrl = typeof item === "string" ? "" : (item?.url || "").trim();

    if (!itemUrl) continue;

    const sourceType = detectSourceType(itemUrl);
    const itemHash = await sha256Short(`${derivedPackId}|${itemUrl}`);
    const stableId = `custom_${derivedPackId}_${itemHash}`;

    catalogConfigs.push({
      id: stableId,
      name: itemName.trim(),
      title: itemName.trim(),
      sourceType,
      sourceUrl: itemUrl,
      mediaType: "all",
      isPreinstalled: false,
      enabled: true,
      packId: derivedPackId,
      packName
    });
  }

  if (!catalogConfigs.length) {
    throw new Error("Catalog pack does not contain any valid URLs to import");
  }

  return {
    type: "catalog-pack",
    packId: derivedPackId,
    packName,
    catalogs: catalogConfigs
  };
}

/**
 * Fetches and parses a Catalog Pack or Custom Collections document from a URL.
 */
export async function fetchAndParsePack(
  packUrl: string,
  fallbackMeta?: Partial<CatalogPack>
): Promise<ParsedCatalogPack> {
  const urlToFetch = packUrl.trim();
  if (!urlToFetch) throw new Error("Pack URL is required");

  let res: Response;
  try {
    res = await fetch(urlToFetch);
  } catch (err) {
    throw new Error(`Failed to fetch pack from "${urlToFetch}": ${err instanceof Error ? err.message : String(err)}`);
  }

  if (!res.ok) {
    throw new Error(`Failed to download pack: HTTP ${res.status} ${res.statusText}`);
  }

  const jsonText = await res.text();
  return parsePackJson(jsonText, urlToFetch, fallbackMeta);
}

/**
 * Installs a parsed pack into the user's catalog list.
 */
export function installPackToCatalogs(
  current: CatalogConfig[],
  pack: ParsedCatalogPack
): { nextCatalogs: CatalogConfig[]; count: number } {
  if (pack.type === "collections") {
    const merged = mergeImportedCollections(current, pack.catalogs);
    const count = pack.catalogs.filter((c) => c.kind === "COLLECTION").length || pack.catalogs.length;
    return { nextCatalogs: merged, count };
  }

  // Catalog pack: remove existing items from this pack or matching source URLs
  const packUrlSet = new Set(pack.catalogs.map((c) => c.sourceUrl?.toLowerCase()).filter(Boolean));
  const filtered = current.filter(
    (c) => c.packId !== pack.packId && (!c.sourceUrl || !packUrlSet.has(c.sourceUrl.toLowerCase()))
  );

  // Prepend newly added catalog rows at the top
  const nextCatalogs = [...pack.catalogs, ...filtered];
  return { nextCatalogs, count: pack.catalogs.length };
}

/**
 * Removes all catalogs associated with a pack ID.
 */
export function removePackFromCatalogs(current: CatalogConfig[], packId: string): CatalogConfig[] {
  if (!packId) return current;
  return current.filter(
    (c) => c.packId !== packId && (!c.collectionRailKey || !c.collectionRailKey.includes(packId))
  );
}

/**
 * Checks if a pack is already installed in the catalogs list.
 */
export function isPackInstalled(
  current: CatalogConfig[],
  packIdOrUrl: string,
  packName?: string
): boolean {
  if (!Array.isArray(current) || !current.length) return false;
  const cleanId = packIdOrUrl ? sanitizePackId(packIdOrUrl) : "";
  const cleanName = packName?.trim().toLowerCase();

  return current.some((c) => {
    if (!c.packId) return false;
    if (cleanId && (c.packId === cleanId || c.packId === `pack_${cleanId}` || c.packId.includes(cleanId))) {
      return true;
    }
    if (cleanName && c.packName && c.packName.trim().toLowerCase() === cleanName) {
      return true;
    }
    return false;
  });
}

/**
 * Returns a summary of all packs currently installed in the profile catalogs.
 */
export function getInstalledPacksSummary(catalogs: CatalogConfig[]): InstalledPackSummary[] {
  if (!Array.isArray(catalogs)) return [];
  const map = new Map<string, { packName: string; count: number; isCollections: boolean }>();

  for (const c of catalogs) {
    if (!c.packId) continue;
    const isCol = c.packId.startsWith("usercol_") || String(c.kind).toUpperCase() === "COLLECTION_RAIL";
    const existing = map.get(c.packId);
    const rowName = c.packName || c.name || "Pack";

    if (existing) {
      existing.count += 1;
    } else {
      map.set(c.packId, {
        packName: rowName,
        count: 1,
        isCollections: isCol
      });
    }
  }

  return Array.from(map.entries()).map(([packId, data]) => ({
    packId,
    packName: data.packName,
    count: data.count,
    type: data.isCollections ? "collections" : "catalog-pack"
  }));
}
