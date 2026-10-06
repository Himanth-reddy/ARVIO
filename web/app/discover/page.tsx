"use client";

import React, { useState, useEffect, useRef } from "react";
import {
  Copy,
  Check,
  Download,
  HelpCircle,
  Code,
  ListVideo,
  Loader2,
  Plus,
  X,
  Globe,
  User,
  BookOpen,
  AlertCircle,
  Settings,
  UserCircle,
  LogOut,
  CheckCircle2,
  Trash2,
  ExternalLink,
  RefreshCw,
  Layers,
  Cloud,
  Eye,
  Tv,
  Upload,
  FileJson,
  FileText,
  ClipboardPaste,
  FolderUp,
  Sparkles,
  Link as LinkIcon
} from "lucide-react";
import { config, getAuthPortalUrl } from "@/lib/config";
import { AppProvider, useApp } from "@/lib/store";
import {
  CatalogPack,
  ParsedCatalogPack,
  parsePackJson,
  fetchAndParsePack,
  installPackToCatalogs,
  removePackFromCatalogs,
  isPackInstalled,
  getInstalledPacksSummary
} from "@/lib/catalogPacks";
import { ProfileAvatarVisual } from "@/components/profile/ProfileAvatar";


const STATIC_FALLBACK_PACKS: CatalogPack[] = [
  {
    id: "cinema-essentials",
    name: "Cinema Essentials",
    author: "ARVIO Team",
    version: "1.0.0",
    description: "All the trending movies, popular lists, and upcoming releases you need for a perfect movie night.",
    url: "/packs/cinema-essentials.json",
    catalogs: ["Trending in Movies", "Top 10 Movies Today", "Top Movies This Week", "Coming Soon"]
  },
  {
    id: "tv-binge",
    name: "TV Show Binge Pack",
    author: "ARVIO Team",
    version: "1.0.0",
    description: "Never miss an episode. Popular, trending, and latest airing series in one convenient bundle.",
    url: "/packs/tv-binge.json",
    catalogs: ["Trending in Shows", "Top 10 Shows Today", "Latest Airing"]
  },
  {
    id: "anime-kdrama",
    name: "Otaku & K-Drama Hub",
    author: "Community",
    version: "1.1.2",
    description: "The ultimate pack for anime lovers and K-Drama fans. Auto-updated lists of trending episodes and releases.",
    url: "/packs/anime-kdrama.json",
    catalogs: ["Trending in Anime", "New in K-Dramas"]
  },
  {
    id: "classics-franchises",
    name: "Action & Franchise Classics",
    author: "Cinephile",
    version: "1.0.5",
    description: "Full box-sets and classic franchise collections, including James Bond, Harry Potter, Lord of the Rings, and Jurassic Park.",
    url: "/packs/classics-franchises.json",
    catalogs: [
      "James Bond Collection",
      "Harry Potter Collection",
      "The Matrix Collection",
      "Lord of the Rings and Hobbit Collection",
      "Jurassic Park Collection"
    ]
  }
];

function DiscoverPageContent() {
  const {
    auth,
    signOut,
    activeProfile,
    profiles,
    selectProfile,
    settings,
    updateSettings,
    setToast,
    avatarImages
  } = useApp();

  const [packs, setPacks] = useState<CatalogPack[]>(STATIC_FALLBACK_PACKS);
  const [loading, setLoading] = useState(true);
  const [copiedId, setCopiedId] = useState<string | null>(null);

  // Pack installation states
  const [installingId, setInstallingId] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  // Modals
  const [showSettingsModal, setShowSettingsModal] = useState(false);
  const [showSubmitModal, setShowSubmitModal] = useState(false);
  const [showImportModal, setShowImportModal] = useState(false);
  const [previewPack, setPreviewPack] = useState<CatalogPack | null>(null);

  // Custom Import States (Direct Paste / File Upload / URL)
  const [importTab, setImportTab] = useState<"paste" | "file" | "url">("paste");
  const [pastedJson, setPastedJson] = useState("");
  const [selectedFile, setSelectedFile] = useState<{ name: string; size: number } | null>(null);
  const [inputUrl, setInputUrl] = useState("");
  const [parsedImportPack, setParsedImportPack] = useState<ParsedCatalogPack | null>(null);
  const [customPackName, setCustomPackName] = useState("");
  const [importParsing, setImportParsing] = useState(false);
  const [importError, setImportError] = useState<string | null>(null);
  const [isDraggingFile, setIsDraggingFile] = useState(false);

  // Submit Form States
  const [formName, setFormName] = useState("");
  const [formAuthor, setFormAuthor] = useState("");
  const [formUrl, setFormUrl] = useState("");
  const [formDesc, setFormDesc] = useState("");
  const [submitLoading, setSubmitLoading] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submitSuccess, setSubmitSuccess] = useState(false);

  // References for dialog light-dismiss
  const settingsDialogRef = useRef<HTMLDialogElement | null>(null);
  const submitDialogRef = useRef<HTMLDialogElement | null>(null);
  const previewDialogRef = useRef<HTMLDialogElement | null>(null);
  const importDialogRef = useRef<HTMLDialogElement | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  // Dialog open/close controller using standard HTMLDialogElement showModal
  useEffect(() => {
    const dlg = settingsDialogRef.current;
    if (!dlg) return;
    if (showSettingsModal && !dlg.open) {
      dlg.showModal();
    } else if (!showSettingsModal && dlg.open) {
      dlg.close();
    }
  }, [showSettingsModal]);

  useEffect(() => {
    const dlg = submitDialogRef.current;
    if (!dlg) return;
    if (showSubmitModal && !dlg.open) {
      dlg.showModal();
    } else if (!showSubmitModal && dlg.open) {
      dlg.close();
    }
  }, [showSubmitModal]);

  useEffect(() => {
    const dlg = previewDialogRef.current;
    if (!dlg) return;
    if (previewPack && !dlg.open) {
      dlg.showModal();
    } else if (!previewPack && dlg.open) {
      dlg.close();
    }
  }, [previewPack]);

  useEffect(() => {
    const dlg = importDialogRef.current;
    if (!dlg) return;
    if (showImportModal && !dlg.open) {
      dlg.showModal();
    } else if (!showImportModal && dlg.open) {
      dlg.close();
    }
  }, [showImportModal]);

  // Click-outside backdrop fallback for browsers lacking native closedby support
  const handleDialogBackdropClick = (
    e: React.MouseEvent<HTMLDialogElement>,
    onClose: () => void
  ) => {
    const dlg = e.currentTarget;
    if (e.target !== dlg) return;
    const rect = dlg.getBoundingClientRect();
    const isInside =
      rect.top <= e.clientY &&
      e.clientY <= rect.top + rect.height &&
      rect.left <= e.clientX &&
      e.clientX <= rect.left + rect.width;
    if (!isInside) onClose();
  };

  async function loadPacks() {
    try {
      const res = await fetch(`${config.netlifyBackendUrl}/catalog-packs-list`);
      if (res.ok) {
        const data = await res.json();
        const parsed = data.map((item: any) => ({
          ...item,
          catalogs: typeof item.catalogs === "string" ? JSON.parse(item.catalogs) : item.catalogs
        }));
        if (Array.isArray(parsed) && parsed.length > 0) {
          setPacks(parsed);
          setLoading(false);
          return;
        }
      }
    } catch (err) {
      console.warn("Netlify backend packs fetch failed, falling back to static packs.json:", err);
    }

    // Fallback: fetch from /packs.json
    try {
      const res = await fetch("/packs.json");
      if (res.ok) {
        const data = await res.json();
        if (Array.isArray(data) && data.length > 0) {
          setPacks(data);
        }
      }
    } catch (err) {
      console.error("Local packs.json fetch failed, using default hardcoded list:", err);
    }
    setLoading(false);
  }

  useEffect(() => {
    void loadPacks();
  }, []);

  const getAbsoluteUrl = (url: string) => {
    if (!url) return "";
    if (url.startsWith("/")) {
      if (typeof window !== "undefined") {
        return `${window.location.origin}${url}`;
      }
      return `https://arvio.app${url}`;
    }
    return url;
  };

  const copyToClipboard = (id: string, text: string) => {
    navigator.clipboard.writeText(text).then(() => {
      setCopiedId(id);
      setTimeout(() => setCopiedId(null), 2000);
    });
  };

  const getDeepLink = (packUrl: string) => {
    return `arvio://install-pack?url=${encodeURIComponent(getAbsoluteUrl(packUrl))}`;
  };

  const handleSignInRedirect = () => {
    if (typeof window === "undefined") return;
    const redirectUri = window.location.href;
    const portalUrl = getAuthPortalUrl();
    window.location.href = `${portalUrl}?redirect_uri=${encodeURIComponent(redirectUri)}`;
  };

  // Install a catalog pack directly into the active profile
  const handleInstallPack = async (pack: CatalogPack) => {
    const packKey = pack.id || pack.url;
    setInstallingId(packKey);
    setActionError(null);

    try {
      const targetUrl = getAbsoluteUrl(pack.url);
      const parsed = await fetchAndParsePack(targetUrl, pack);

      const currentCatalogs = settings.catalogs || [];
      const { nextCatalogs, count } = installPackToCatalogs(currentCatalogs, parsed);

      const standard = nextCatalogs.filter((catalog) => catalog.sourceType !== "home-server");
      const homeServer = nextCatalogs.filter((catalog) => catalog.sourceType === "home-server");

      updateSettings({
        catalogs: nextCatalogs,
        hiddenCatalogIds: standard.filter((catalog) => !catalog.enabled).map((catalog) => catalog.id),
        hiddenHomeServerCatalogIds: homeServer.filter((catalog) => !catalog.enabled).map((catalog) => catalog.id)
      });

      const profileName = activeProfile?.name || "your profile";
      setToast(`"${parsed.packName}" installed (${count} rows) to ${profileName}!`);
    } catch (err: any) {
      console.error("Pack installation error:", err);
      const msg = err.message || "Failed to install catalog pack.";
      setActionError(msg);
      setToast(msg);
    } finally {
      setInstallingId(null);
    }
  };

  // Uninstall a pack from the active profile
  const handleUninstallPack = (packId: string, packName: string) => {
    try {
      const currentCatalogs = settings.catalogs || [];
      const nextCatalogs = removePackFromCatalogs(currentCatalogs, packId);

      const standard = nextCatalogs.filter((catalog) => catalog.sourceType !== "home-server");
      const homeServer = nextCatalogs.filter((catalog) => catalog.sourceType === "home-server");

      updateSettings({
        catalogs: nextCatalogs,
        hiddenCatalogIds: standard.filter((catalog) => !catalog.enabled).map((catalog) => catalog.id),
        hiddenHomeServerCatalogIds: homeServer.filter((catalog) => !catalog.enabled).map((catalog) => catalog.id)
      });

      setToast(`"${packName}" removed from your profile.`);
    } catch (err: any) {
      setToast("Failed to remove catalog pack.");
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!formName.trim() || !formUrl.trim() || !formDesc.trim()) {
      setSubmitError("Please fill out all required fields.");
      return;
    }

    if (!formUrl.startsWith("https://")) {
      setSubmitError("Manifest URL must start with https://");
      return;
    }

    setSubmitLoading(true);
    setSubmitError(null);

    try {
      const res = await fetch(`${config.netlifyBackendUrl}/catalog-packs-submit`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          name: formName.trim(),
          url: formUrl.trim(),
          author: formAuthor.trim() || "Anonymous",
          description: formDesc.trim()
        })
      });

      if (!res.ok) {
        const errorJson = await res.json().catch(() => null);
        throw new Error(errorJson?.message || `Failed to submit: ${res.statusText}`);
      }

      setSubmitSuccess(true);
      setFormName("");
      setFormAuthor("");
      setFormUrl("");
      setFormDesc("");
      setTimeout(() => {
        setSubmitSuccess(false);
        setShowSubmitModal(false);
      }, 3000);
    } catch (err: any) {
      setSubmitError(err.message || "An unexpected error occurred during submission.");
    } finally {
      setSubmitLoading(false);
    }
  };

  // Process raw JSON input (from paste, editor change, or file read)
  const handleProcessJsonInput = async (rawText: string, sourceName = "custom-input") => {
    setPastedJson(rawText);
    setImportError(null);

    const trimmed = rawText.trim();
    if (!trimmed) {
      setParsedImportPack(null);
      return;
    }

    try {
      setImportParsing(true);
      const parsed = await parsePackJson(trimmed, sourceName);
      setParsedImportPack(parsed);
      setCustomPackName(parsed.packName);
      setImportError(null);
    } catch (err: any) {
      setParsedImportPack(null);
      setImportError(err.message || "Invalid JSON catalog pack format.");
    } finally {
      setImportParsing(false);
    }
  };

  // Format pasted JSON
  const handleFormatPastedJson = () => {
    try {
      const obj = JSON.parse(pastedJson);
      const formatted = JSON.stringify(obj, null, 2);
      setPastedJson(formatted);
    } catch {
      // ignore formatting if invalid
    }
  };

  // Handle file selection / drop
  const handleProcessFile = async (file: File) => {
    if (!file) return;
    if (!file.name.endsWith(".json") && file.type && !file.type.includes("json")) {
      setImportError("Please upload a .json file.");
      return;
    }

    try {
      setImportParsing(true);
      setImportError(null);
      const text = await file.text();
      const parsed = await parsePackJson(text, file.name);
      setSelectedFile({ name: file.name, size: file.size });
      setPastedJson(text);
      setParsedImportPack(parsed);
      setCustomPackName(parsed.packName);
    } catch (err: any) {
      setSelectedFile(null);
      setParsedImportPack(null);
      setImportError(err.message || "Failed to parse JSON file.");
    } finally {
      setImportParsing(false);
      setIsDraggingFile(false);
    }
  };

  const handleFileInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) void handleProcessFile(file);
  };

  const handleFileDrop = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setIsDraggingFile(false);
    const file = e.dataTransfer.files?.[0];
    if (file) void handleProcessFile(file);
  };

  // Handle URL fetch
  const handleFetchUrl = async () => {
    const target = inputUrl.trim();
    if (!target) {
      setImportError("Please enter a valid URL.");
      return;
    }

    try {
      setImportParsing(true);
      setImportError(null);
      const parsed = await fetchAndParsePack(target);
      setParsedImportPack(parsed);
      setCustomPackName(parsed.packName);
      setPastedJson("");
      setSelectedFile(null);
    } catch (err: any) {
      setParsedImportPack(null);
      setImportError(err.message || "Failed to fetch catalog pack from URL.");
    } finally {
      setImportParsing(false);
    }
  };

  // Execute installation of custom pack into active profile
  const handleExecuteCustomInstall = () => {
    if (!parsedImportPack) return;

    try {
      const finalName = customPackName.trim() || parsedImportPack.packName;
      // Stamp final custom pack name onto catalogs
      const packToInstall: ParsedCatalogPack = {
        ...parsedImportPack,
        packName: finalName,
        catalogs: parsedImportPack.catalogs.map((c) => ({
          ...c,
          packName: finalName
        }))
      };

      const currentCatalogs = settings.catalogs || [];
      const { nextCatalogs, count } = installPackToCatalogs(currentCatalogs, packToInstall);

      const standard = nextCatalogs.filter((catalog) => catalog.sourceType !== "home-server");
      const homeServer = nextCatalogs.filter((catalog) => catalog.sourceType === "home-server");

      updateSettings({
        catalogs: nextCatalogs,
        hiddenCatalogIds: standard.filter((catalog) => !catalog.enabled).map((catalog) => catalog.id),
        hiddenHomeServerCatalogIds: homeServer.filter((catalog) => !catalog.enabled).map((catalog) => catalog.id)
      });

      const profileName = activeProfile?.name || "your profile";
      setToast(`"${finalName}" installed (${count} rows) to ${profileName}!`);
      setShowImportModal(false);
      setParsedImportPack(null);
      setPastedJson("");
      setSelectedFile(null);
      setInputUrl("");
    } catch (err: any) {
      setImportError(err.message || "Failed to install catalog pack.");
    }
  };

  const installedPacksSummary = getInstalledPacksSummary(settings.catalogs || []);

  return (
    <div className="discover-container">
      {/* Top Header Navigation */}
      <header className="discover-header">
        <div className="header-top">
          <div className="brand-cluster">
            <a href="/" className="logo-section" title="Go to ARVIO Web App">
              <img src="/arvio-logo.svg" alt="ARVIO Logo" className="logo" />
              <span className="logo-text">ARVIO</span>
            </a>
            <span className="section-pill">Catalog Packs</span>
          </div>

          <div className="header-actions">
            <a href="/" className="nav-link-btn" title="Open ARVIO Web App">
              <ExternalLink size={15} />
              <span>Web App</span>
            </a>

            <button
              type="button"
              className="import-trigger-button"
              onClick={() => {
                setImportError(null);
                setParsedImportPack(null);
                setPastedJson("");
                setSelectedFile(null);
                setInputUrl("");
                setShowImportModal(true);
              }}
              title="Install catalog pack via JSON paste, file upload, or URL"
            >
              <FolderUp size={15} />
              <span>Install Custom Pack</span>
            </button>

            <button
              type="button"
              className="submit-trigger-button"
              onClick={() => {
                setSubmitError(null);
                setSubmitSuccess(false);
                setShowSubmitModal(true);
              }}
            >
              <Plus size={15} />
              <span>Submit Pack</span>
            </button>

            {/* User Settings & Account Controls */}
            {auth ? (
              <button
                type="button"
                className="user-profile-btn"
                onClick={() => setShowSettingsModal(true)}
                title="Account & Profile Settings"
                aria-haspopup="dialog"
              >
                <div className="user-avatar-wrap">
                  {activeProfile ? (
                    <ProfileAvatarVisual profile={activeProfile} avatarImages={avatarImages} />
                  ) : (
                    <UserCircle size={22} />
                  )}
                </div>
                <div className="user-details">
                  <span className="user-name">{activeProfile?.name || "Profile"}</span>
                  <span className="user-cloud-status">
                    <Cloud size={10} className="icon-cloud-active" /> Synced
                  </span>
                </div>
                <Settings size={16} className="gear-icon" />
              </button>
            ) : (
              <div className="guest-actions">
                <button
                  type="button"
                  className="auth-portal-btn"
                  onClick={handleSignInRedirect}
                  title="Sign in with your ARVIO Cloud account"
                >
                  <Cloud size={15} />
                  <span>Sign In</span>
                </button>
                <button
                  type="button"
                  className="user-profile-btn guest-btn"
                  onClick={() => setShowSettingsModal(true)}
                  title="Local Settings & Profiles"
                  aria-haspopup="dialog"
                >
                  <Settings size={18} />
                </button>
              </div>
            )}
          </div>
        </div>

        <div className="hero-section">
          <h1 className="hero-title">Catalog Pack Discovery</h1>
          <p className="hero-subtitle">
            Curate your streaming interface with verified community catalog packs. Install themed folders, popular lists, and complete franchises into your profile with one click.
          </p>

          <div className="hero-quick-actions">
            <button
              type="button"
              className="quick-action-pill"
              onClick={() => {
                setImportTab("paste");
                setImportError(null);
                setShowImportModal(true);
              }}
              title="Paste raw JSON manifest or collection"
            >
              <ClipboardPaste size={14} className="pill-icon" />
              <span>Paste JSON</span>
            </button>
            <button
              type="button"
              className="quick-action-pill"
              onClick={() => {
                setImportTab("file");
                setImportError(null);
                setShowImportModal(true);
              }}
              title="Upload .json file directly from your computer"
            >
              <Upload size={14} className="pill-icon" />
              <span>Upload JSON File</span>
            </button>
            <button
              type="button"
              className="quick-action-pill"
              onClick={() => {
                setImportTab("url");
                setImportError(null);
                setShowImportModal(true);
              }}
              title="Import from a remote manifest or collection URL"
            >
              <Globe size={14} className="pill-icon" />
              <span>From URL</span>
            </button>
          </div>

          {activeProfile && (
            <div className="profile-context-banner">
              <span className="context-label">Installing packs to profile:</span>
              <strong className="context-profile-name">{activeProfile.name}</strong>
              <button
                type="button"
                className="switch-profile-quick-link"
                onClick={() => setShowSettingsModal(true)}
              >
                Switch
              </button>
              {installedPacksSummary.length > 0 && (
                <span className="installed-count-pill">
                  {installedPacksSummary.length} pack{installedPacksSummary.length === 1 ? "" : "s"} active
                </span>
              )}
            </div>
          )}

          {actionError && (
            <div className="banner-alert error">
              <AlertCircle size={16} />
              <span>{actionError}</span>
              <button type="button" onClick={() => setActionError(null)} className="alert-close">×</button>
            </div>
          )}
        </div>
      </header>

      {/* Grid of Catalog Packs */}
      {loading ? (
        <div className="loading-container">
          <Loader2 size={36} className="animate-spin text-accent" />
          <span>Fetching verified catalog packs...</span>
        </div>
      ) : (
        <main className="discover-grid">
          {packs.map((pack) => {
            const packKey = pack.id || pack.url;
            const isInstalled = isPackInstalled(settings.catalogs || [], pack.id || pack.url, pack.name);
            const isBusy = installingId === packKey;

            return (
              <article key={packKey} className={`pack-card ${isInstalled ? "is-installed" : ""}`}>
                <div className="pack-card-header">
                  <div className="title-row">
                    <h2 className="pack-title">{pack.name}</h2>
                    {isInstalled && (
                      <span className="installed-tag" title="Installed on active profile">
                        <CheckCircle2 size={13} /> Active
                      </span>
                    )}
                  </div>
                  <div className="pack-meta">
                    <span className="pack-author">by {pack.author || "Anonymous"}</span>
                    <span className="pack-version">v{pack.version || "1.0.0"}</span>
                  </div>
                </div>

                <p className="pack-description">{pack.description}</p>

                {/* Included Catalogs / Rows */}
                <div className="catalogs-section">
                  <div className="catalogs-section-header">
                    <h3 className="section-label">
                      <ListVideo size={14} className="icon-inline" /> Included Rows ({pack.catalogs?.length || 0})
                    </h3>
                    <button
                      type="button"
                      className="preview-trigger-link"
                      onClick={() => setPreviewPack(pack)}
                      title="Inspect included rows"
                    >
                      <Eye size={12} /> View Details
                    </button>
                  </div>
                  <ul className="catalogs-list">
                    {pack.catalogs?.slice(0, 4).map((catalog, idx) => {
                      const name = typeof catalog === "string" ? catalog : catalog.name || catalog.title || `Row ${idx + 1}`;
                      return (
                        <li key={idx} className="catalog-item-tag">
                          {name}
                        </li>
                      );
                    })}
                    {pack.catalogs && pack.catalogs.length > 4 && (
                      <li className="catalog-item-tag more">+{pack.catalogs.length - 4} more</li>
                    )}
                  </ul>
                </div>

                {/* Pack Actions */}
                <div className="pack-actions">
                  {isInstalled ? (
                    <div className="installed-action-group">
                      <button
                        type="button"
                        className="install-button update"
                        onClick={() => handleInstallPack(pack)}
                        disabled={isBusy}
                        title="Reinstall or update catalog pack rows"
                      >
                        {isBusy ? (
                          <>
                            <Loader2 size={15} className="animate-spin" /> Updating...
                          </>
                        ) : (
                          <>
                            <RefreshCw size={15} /> Update Pack
                          </>
                        )}
                      </button>
                      <button
                        type="button"
                        className="uninstall-button"
                        onClick={() => {
                          const idToRemove = pack.id ? `pack_${pack.id}` : "";
                          handleUninstallPack(idToRemove || pack.name, pack.name);
                        }}
                        title="Remove pack from profile"
                      >
                        <Trash2 size={15} />
                      </button>
                    </div>
                  ) : (
                    <button
                      type="button"
                      className="install-button primary"
                      onClick={() => handleInstallPack(pack)}
                      disabled={isBusy}
                      title={`Install ${pack.name} to ${activeProfile?.name || "your profile"}`}
                    >
                      {isBusy ? (
                        <>
                          <Loader2 size={15} className="animate-spin" /> Installing...
                        </>
                      ) : (
                        <>
                          <Download size={15} /> Install Pack
                        </>
                      )}
                    </button>
                  )}

                  <div className="secondary-action-group">
                    <button
                      type="button"
                      onClick={() => copyToClipboard(packKey, getAbsoluteUrl(pack.url))}
                      className="copy-button"
                      title="Copy manifest JSON URL"
                    >
                      {copiedId === packKey ? <Check size={14} className="text-green" /> : <Copy size={14} />}
                      <span>{copiedId === packKey ? "Copied" : "Copy URL"}</span>
                    </button>

                    <a
                      href={getDeepLink(pack.url)}
                      className="deep-link-btn"
                      title="Install directly to ARVIO app via deep link"
                    >
                      <Tv size={14} />
                      <span>Android TV</span>
                    </a>
                  </div>
                </div>
              </article>
            );
          })}
        </main>
      )}

      {/* User Settings Modal */}
      <dialog
        ref={settingsDialogRef}
        className="modal-dialog user-settings-dialog"
        aria-labelledby="settings-dialog-title"
        onCancel={() => setShowSettingsModal(false)}
        onClick={(e) => handleDialogBackdropClick(e, () => setShowSettingsModal(false))}
      >
        <div className="modal-dialog-content">
          <div className="modal-header">
            <h2 id="settings-dialog-title" className="modal-title">
              <Settings size={20} className="icon-accent" />
              <span>User Settings &amp; Account</span>
            </h2>
            <button
              type="button"
              className="modal-close"
              onClick={() => setShowSettingsModal(false)}
              aria-label="Close dialog"
            >
              <X size={18} />
            </button>
          </div>

          <div className="settings-dialog-body">
            {/* Account Status Card */}
            <div className="account-status-card">
              <div className="account-status-top">
                <div className="account-icon-wrap">
                  <UserCircle size={36} />
                </div>
                <div className="account-meta">
                  <span className="account-label">ARVIO Account</span>
                  {auth ? (
                    <>
                      <strong className="account-email">{auth.email}</strong>
                      <span className="account-cloud-badge connected">
                        <CheckCircle2 size={12} /> ARVIO Cloud Synced
                      </span>
                    </>
                  ) : (
                    <>
                      <strong className="account-email">Local Browser Storage</strong>
                      <span className="account-cloud-badge disconnected">
                        <AlertCircle size={12} /> Not Synced
                      </span>
                    </>
                  )}
                </div>
              </div>

              {auth ? (
                <div className="account-controls">
                  <button
                    type="button"
                    className="account-signout-btn"
                    onClick={() => {
                      signOut();
                      setShowSettingsModal(false);
                      setToast("Signed out from ARVIO Cloud.");
                    }}
                  >
                    <LogOut size={14} /> Sign Out
                  </button>
                </div>
              ) : (
                <div className="account-connect-box">
                  <p className="account-connect-desc">
                    Connect your ARVIO Cloud account to sync catalog packs, custom collections, and watchlist across all your devices.
                  </p>
                  <button
                    type="button"
                    className="primary-auth-button"
                    onClick={handleSignInRedirect}
                  >
                    <Cloud size={16} /> Sign In with ARVIO Cloud
                  </button>
                </div>
              )}
            </div>

            {/* Profile Selection */}
            <div className="settings-section">
              <h3 className="section-subheading">
                <User size={16} /> Active Profile
              </h3>
              <p className="section-desc">
                Catalog packs and collections are installed per-profile. Choose which profile you are currently managing:
              </p>
              <div className="profiles-picker-grid">
                {profiles.map((p) => {
                  const isActive = p.id === activeProfile?.id;
                  return (
                    <button
                      key={p.id}
                      type="button"
                      className={`profile-pill ${isActive ? "active" : ""}`}
                      onClick={() => selectProfile(p)}
                    >
                      <div className="profile-pill-avatar">
                        <ProfileAvatarVisual profile={p} avatarImages={avatarImages} />
                      </div>
                      <span className="profile-pill-name">{p.name}</span>
                      {isActive && <Check size={14} className="profile-active-check" />}
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Installed Packs List */}
            <div className="settings-section">
              <div className="section-header-split">
                <h3 className="section-subheading">
                  <Layers size={16} /> Installed Packs on {activeProfile?.name || "Profile"}
                </h3>
                <span className="count-tag">{installedPacksSummary.length} installed</span>
              </div>

              {installedPacksSummary.length === 0 ? (
                <div className="empty-packs-notice">
                  <p>No catalog packs or collections installed on this profile yet.</p>
                  <span>Select any pack on the Discovery page to install it with one click.</span>
                </div>
              ) : (
                <div className="installed-packs-list">
                  {installedPacksSummary.map((item) => (
                    <div key={item.packId} className="installed-pack-row">
                      <div className="installed-pack-info">
                        <strong className="installed-pack-title">{item.packName}</strong>
                        <div className="installed-pack-meta">
                          <span className="badge-type">{item.type === "collections" ? "Collection" : "Catalog Pack"}</span>
                          <span>{item.count} row{item.count === 1 ? "" : "s"}</span>
                        </div>
                      </div>
                      <button
                        type="button"
                        className="pack-remove-btn"
                        onClick={() => handleUninstallPack(item.packId, item.packName)}
                        title={`Remove ${item.packName}`}
                      >
                        <Trash2 size={16} />
                      </button>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* Quick Links */}
            <div className="settings-footer-actions">
              <a href="/" className="footer-action-link">
                <ExternalLink size={14} /> Open ARVIO Web App
              </a>
              <a href="/?section=settings" className="footer-action-link">
                <Settings size={14} /> Open Full Web Settings
              </a>
            </div>
          </div>
        </div>
      </dialog>

      {/* Row Preview Modal */}
      <dialog
        ref={previewDialogRef}
        className="modal-dialog preview-dialog"
        aria-labelledby="preview-dialog-title"
        onCancel={() => setPreviewPack(null)}
        onClick={(e) => handleDialogBackdropClick(e, () => setPreviewPack(null))}
      >
        <div className="modal-dialog-content">
          <div className="modal-header">
            <h2 id="preview-dialog-title" className="modal-title">
              <ListVideo size={20} className="icon-accent" />
              <span>{previewPack?.name || "Pack"} Rows Preview</span>
            </h2>
            <button
              type="button"
              className="modal-close"
              onClick={() => setPreviewPack(null)}
              aria-label="Close dialog"
            >
              <X size={18} />
            </button>
          </div>

          <div className="preview-dialog-body">
            <p className="preview-desc">{previewPack?.description}</p>
            <div className="catalogs-preview-container">
              <h4 className="preview-list-heading">
                Included Catalogs ({previewPack?.catalogs?.length || 0}):
              </h4>
              <ul className="preview-catalogs-grid">
                {previewPack?.catalogs?.map((catalog, idx) => {
                  const name = typeof catalog === "string" ? catalog : catalog.name || catalog.title || `Row ${idx + 1}`;
                  const url = typeof catalog === "string" ? null : catalog.url;
                  return (
                    <li key={idx} className="preview-catalog-card">
                      <div className="preview-card-index">{idx + 1}</div>
                      <div className="preview-card-content">
                        <strong>{name}</strong>
                        {url && <span className="preview-url-text">{url}</span>}
                      </div>
                    </li>
                  );
                })}
              </ul>
            </div>
            <div className="preview-actions">
              {previewPack && (
                <button
                  type="button"
                  className="install-button primary full-width"
                  onClick={() => {
                    const target = previewPack;
                    setPreviewPack(null);
                    handleInstallPack(target);
                  }}
                >
                  <Download size={16} /> Install Pack Now
                </button>
              )}
            </div>
          </div>
        </div>
      </dialog>

      {/* Submission Modal Overlay */}
      <dialog
        ref={submitDialogRef}
        className="modal-dialog submit-dialog"
        aria-labelledby="submit-dialog-title"
        onCancel={() => setShowSubmitModal(false)}
        onClick={(e) => handleDialogBackdropClick(e, () => setShowSubmitModal(false))}
      >
        <div className="modal-dialog-content">
          <div className="modal-header">
            <h2 id="submit-dialog-title" className="modal-title">
              <BookOpen size={20} className="icon-accent" />
              <span>Submit a Catalog Pack</span>
            </h2>
            <button
              type="button"
              className="modal-close"
              onClick={() => setShowSubmitModal(false)}
              aria-label="Close dialog"
            >
              <X size={18} />
            </button>
          </div>

          <div className="submit-dialog-body">
            {submitSuccess ? (
              <div className="submit-feedback success">
                <Check size={48} className="text-green feedback-icon animate-bounce" />
                <h2>Submission Successful!</h2>
                <p>
                  Thank you! Your catalog pack has been submitted for review. It will become visible on the Discovery page once approved.
                </p>
              </div>
            ) : (
              <form onSubmit={handleSubmit} className="submission-form">
                <p className="form-intro">
                  Packs are moderated to ensure safety, formatting correctness, and server reliability.
                </p>

                {submitError && (
                  <div className="form-error">
                    <AlertCircle size={16} />
                    <span>{submitError}</span>
                  </div>
                )}

                <div className="form-field">
                  <label htmlFor="pack-name"><BookOpen size={14} /> Pack Name *</label>
                  <input
                    type="text"
                    id="pack-name"
                    required
                    placeholder="e.g. Action Blockbusters Bundle"
                    value={formName}
                    onChange={(e) => setFormName(e.target.value)}
                  />
                </div>

                <div className="form-field">
                  <label htmlFor="pack-url"><Globe size={14} /> Manifest JSON URL *</label>
                  <input
                    type="text"
                    id="pack-url"
                    required
                    placeholder="e.g. https://raw.githubusercontent.com/.../pack.json"
                    value={formUrl}
                    onChange={(e) => setFormUrl(e.target.value)}
                  />
                  <span className="field-hint">Must be a public HTTPS URL serving valid JSON.</span>
                </div>

                <div className="form-field">
                  <label htmlFor="pack-author"><User size={14} /> Author / Creator</label>
                  <input
                    type="text"
                    id="pack-author"
                    placeholder="e.g. @cinephile_dev"
                    value={formAuthor}
                    onChange={(e) => setFormAuthor(e.target.value)}
                  />
                </div>

                <div className="form-field">
                  <label htmlFor="pack-desc">Description *</label>
                  <textarea
                    id="pack-desc"
                    required
                    rows={3}
                    placeholder="Describe the content and purpose of this pack..."
                    value={formDesc}
                    onChange={(e) => setFormDesc(e.target.value)}
                  />
                </div>

                <button
                  type="submit"
                  className="form-submit-button"
                  disabled={submitLoading}
                >
                  {submitLoading ? (
                    <>
                      <Loader2 size={16} className="animate-spin" /> Submitting...
                    </>
                  ) : (
                    "Submit for Review"
                  )}
                </button>
              </form>
            )}
          </div>
        </div>
      </dialog>

      {/* Custom Pack Import Modal (Direct Paste / File Upload / URL) */}
      <dialog
        ref={importDialogRef}
        className="modal-dialog import-dialog"
        aria-labelledby="import-dialog-title"
        onCancel={() => setShowImportModal(false)}
        onClick={(e) => handleDialogBackdropClick(e, () => setShowImportModal(false))}
      >
        <div className="modal-dialog-content import-modal-content">
          <div className="modal-header">
            <h2 id="import-dialog-title" className="modal-title">
              <FolderUp size={22} className="icon-accent" />
              <span>Install Custom Catalog Pack</span>
            </h2>
            <button
              type="button"
              className="modal-close"
              onClick={() => setShowImportModal(false)}
              aria-label="Close dialog"
            >
              <X size={18} />
            </button>
          </div>

          <p className="modal-intro-text">
            Add custom catalog packs or collections directly to your profile. You can paste JSON, drag &amp; drop a <code>.json</code> file, or fetch from a public URL.
          </p>

          {/* Segmented Switcher */}
          <div className="import-tabs-bar" role="tablist">
            <button
              type="button"
              role="tab"
              aria-selected={importTab === "paste"}
              className={`import-tab-btn ${importTab === "paste" ? "active" : ""}`}
              onClick={() => { setImportTab("paste"); setImportError(null); }}
            >
              <ClipboardPaste size={15} />
              <span>Paste JSON</span>
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={importTab === "file"}
              className={`import-tab-btn ${importTab === "file" ? "active" : ""}`}
              onClick={() => { setImportTab("file"); setImportError(null); }}
            >
              <Upload size={15} />
              <span>Upload JSON File</span>
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={importTab === "url"}
              className={`import-tab-btn ${importTab === "url" ? "active" : ""}`}
              onClick={() => { setImportTab("url"); setImportError(null); }}
            >
              <Globe size={15} />
              <span>From URL</span>
            </button>
          </div>

          {/* Tab 1: Paste JSON */}
          {importTab === "paste" && (
            <div className="tab-panel paste-panel">
              <div className="paste-toolbar">
                <span className="toolbar-hint">Paste raw catalog pack or collection JSON:</span>
                <div className="toolbar-actions">
                  <button
                    type="button"
                    className="toolbar-btn"
                    onClick={async () => {
                      try {
                        const clip = await navigator.clipboard.readText();
                        if (clip) void handleProcessJsonInput(clip, "pasted-json");
                      } catch {
                        // ignore permission denial
                      }
                    }}
                    title="Paste from clipboard"
                  >
                    <ClipboardPaste size={13} />
                    <span>Paste</span>
                  </button>
                  <button
                    type="button"
                    className="toolbar-btn"
                    onClick={handleFormatPastedJson}
                    disabled={!pastedJson.trim()}
                    title="Format JSON"
                  >
                    <span>Format</span>
                  </button>
                  <button
                    type="button"
                    className="toolbar-btn"
                    onClick={() => {
                      setPastedJson("");
                      setParsedImportPack(null);
                      setImportError(null);
                    }}
                    disabled={!pastedJson.trim()}
                  >
                    <span>Clear</span>
                  </button>
                </div>
              </div>
              <textarea
                className="code-textarea"
                rows={9}
                value={pastedJson}
                onChange={(e) => void handleProcessJsonInput(e.target.value, "pasted-json")}
                placeholder={`{\n  "name": "My Custom Sci-Fi Pack",\n  "catalogs": [\n    { "name": "Best Sci-Fi Movies", "url": "https://mdblist.com/lists/..." }\n  ]\n}`}
                spellCheck={false}
              />
            </div>
          )}

          {/* Tab 2: File Upload */}
          {importTab === "file" && (
            <div className="tab-panel file-panel">
              <div
                className={`dropzone ${isDraggingFile ? "dragging" : ""} ${selectedFile ? "has-file" : ""}`}
                onDragOver={(e) => { e.preventDefault(); setIsDraggingFile(true); }}
                onDragLeave={() => setIsDraggingFile(false)}
                onDrop={handleFileDrop}
                onClick={() => fileInputRef.current?.click()}
              >
                <input
                  type="file"
                  ref={fileInputRef}
                  accept=".json,application/json"
                  style={{ display: "none" }}
                  onChange={handleFileInputChange}
                />
                {selectedFile ? (
                  <div className="file-info-box">
                    <FileJson size={36} className="file-icon-active" />
                    <div className="file-meta">
                      <strong className="file-name">{selectedFile.name}</strong>
                      <span className="file-size">{(selectedFile.size / 1024).toFixed(1)} KB</span>
                    </div>
                    <button
                      type="button"
                      className="file-remove-btn"
                      onClick={(e) => {
                        e.stopPropagation();
                        setSelectedFile(null);
                        setParsedImportPack(null);
                        setPastedJson("");
                        if (fileInputRef.current) fileInputRef.current.value = "";
                      }}
                      title="Remove file"
                    >
                      <X size={16} />
                    </button>
                  </div>
                ) : (
                  <div className="dropzone-content">
                    <FolderUp size={38} className="dropzone-icon" />
                    <div className="dropzone-text">
                      <strong>Click to browse</strong> or drag &amp; drop your <code>.json</code> file
                    </div>
                    <span className="dropzone-subtext">Supports ARVIO Catalog Packs and Custom Collections JSON</span>
                  </div>
                )}
              </div>
            </div>
          )}

          {/* Tab 3: URL */}
          {importTab === "url" && (
            <div className="tab-panel url-panel">
              <div className="url-input-wrap">
                <input
                  type="url"
                  className="url-text-input"
                  placeholder="https://example.com/catalog-pack.json"
                  value={inputUrl}
                  onChange={(e) => setInputUrl(e.target.value)}
                  onKeyDown={(e) => { if (e.key === "Enter") void handleFetchUrl(); }}
                />
                <button
                  type="button"
                  className="fetch-url-btn"
                  onClick={() => void handleFetchUrl()}
                  disabled={importParsing || !inputUrl.trim()}
                >
                  {importParsing ? <Loader2 size={16} className="animate-spin" /> : <Globe size={16} />}
                  <span>Fetch Pack</span>
                </button>
              </div>
            </div>
          )}

          {/* Error Banner */}
          {importError && (
            <div className="import-error-banner">
              <AlertCircle size={16} />
              <span>{importError}</span>
            </div>
          )}

          {/* Parsed Pack Preview Box */}
          {parsedImportPack && (
            <div className="import-preview-box">
              <div className="preview-top-row">
                <div className="preview-pack-title-row">
                  <span className="preview-status-pill">
                    <CheckCircle2 size={13} className="text-green" />
                    {parsedImportPack.type === "collections" ? "Custom Collections" : "Catalog Pack"}
                  </span>
                  <div className="pack-name-edit-wrap">
                    <input
                      type="text"
                      className="pack-name-input"
                      value={customPackName}
                      onChange={(e) => setCustomPackName(e.target.value)}
                      placeholder="Pack Display Name"
                      title="Edit pack display name"
                    />
                  </div>
                </div>
                <span className="preview-row-count">
                  {parsedImportPack.catalogs.length} {parsedImportPack.catalogs.length === 1 ? "catalog" : "catalogs"} found
                </span>
              </div>

              {/* Catalogs list preview */}
              <div className="preview-catalog-list">
                {parsedImportPack.catalogs.slice(0, 8).map((cat, i) => (
                  <div key={cat.id || i} className="preview-catalog-item">
                    <span className="preview-cat-num">{i + 1}</span>
                    <span className="preview-cat-name">{cat.name || cat.title || "Row"}</span>
                    {cat.sourceType && (
                      <span className="preview-source-badge">{cat.sourceType}</span>
                    )}
                  </div>
                ))}
                {parsedImportPack.catalogs.length > 8 && (
                  <div className="preview-more-rows">
                    + {parsedImportPack.catalogs.length - 8} more catalog rows
                  </div>
                )}
              </div>

              {/* Install target profile bar */}
              <div className="target-profile-bar">
                <span className="target-label">Target Profile:</span>
                <div className="target-profile-pill">
                  {activeProfile ? (
                    <ProfileAvatarVisual profile={activeProfile} avatarImages={avatarImages} />
                  ) : (
                    <UserCircle size={18} />
                  )}
                  <strong className="target-profile-name">{activeProfile?.name || "Profile"}</strong>
                </div>
                {profiles.length > 1 && (
                  <select
                    className="profile-select-inline"
                    value={activeProfile?.id || ""}
                    onChange={(e) => {
                      const found = profiles.find((p) => p.id === e.target.value);
                      if (found) selectProfile(found);
                    }}
                  >
                    {profiles.map((p) => (
                      <option key={p.id} value={p.id}>
                        {p.name}
                      </option>
                    ))}
                  </select>
                )}
              </div>

              {/* Action Buttons */}
              <div className="import-actions">
                <button
                  type="button"
                  className="execute-install-btn"
                  onClick={handleExecuteCustomInstall}
                  disabled={importParsing}
                >
                  <Download size={16} />
                  <span>
                    Install Pack ({parsedImportPack.catalogs.length} catalogs) to {activeProfile?.name || "Profile"}
                  </span>
                </button>
              </div>
            </div>
          )}
        </div>
      </dialog>

      {/* FAQ & Manifest Documentation */}
      <section className="faq-section">
        <h2 className="faq-heading"><HelpCircle size={22} className="icon-inline" /> Frequently Asked Questions</h2>
        <div className="faq-grid">
          <div className="faq-item">
            <h3>How does in-browser installation work?</h3>
            <p>
              Clicking <strong>Install Pack</strong> automatically parses the pack manifest and integrates the rows into your active profile. If you are signed in with ARVIO Cloud, the pack automatically syncs across all your linked devices (Android TV, mobile, and web).
            </p>
          </div>
          <div className="faq-item">
            <h3>How do I install directly on Android TV?</h3>
            <p>
              You can click the <strong>Android TV</strong> deep link button to launch ARVIO on your device, or copy the manifest URL and paste it under <strong>Settings &gt; Catalogs &gt; Import Catalog Pack</strong> in the Android TV app.
            </p>
          </div>
        </div>
      </section>

      <section className="manifest-example-section">
        <h2 className="manifest-heading"><Code size={22} className="icon-inline" /> Manifest JSON Structure</h2>
        <pre className="code-block">
          <code>{`{
  "id": "my-custom-pack",
  "name": "My Custom Catalog Pack",
  "author": "CreatorName",
  "version": "1.0.0",
  "description": "A brief description of the pack contents.",
  "catalogs": [
    {
      "name": "Trending Sci-Fi Movies",
      "url": "https://example.com/scifi-movies.json"
    },
    {
      "name": "Upcoming Action Series",
      "url": "https://example.com/action-series.json"
    }
  ]
}`}</code>
        </pre>
      </section>

      <footer className="discover-footer">
        <p>ARVIO Media Hub &copy; {new Date().getFullYear()}. Cloud sync powered by ARVIO Cloud.</p>
      </footer>

      {/* Scoped Styling with rich dark aesthetics */}
      <style jsx>{`
        .discover-container {
          max-width: 1240px;
          margin: 0 auto;
          padding: 40px 24px 80px;
          color: #ededed;
          font-family: "Inter", -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
          min-height: 100vh;
        }

        .discover-header {
          margin-bottom: 48px;
        }

        .header-top {
          display: flex;
          align-items: center;
          justify-content: space-between;
          margin-bottom: 36px;
          flex-wrap: wrap;
          gap: 16px;
          padding-bottom: 24px;
          border-bottom: 1px solid rgba(255, 255, 255, 0.08);
        }

        .brand-cluster {
          display: flex;
          align-items: center;
          gap: 14px;
        }

        .logo-section {
          display: flex;
          align-items: center;
          gap: 10px;
          text-decoration: none;
        }

        .logo {
          width: 36px;
          height: 36px;
        }

        .logo-text {
          font-size: 22px;
          font-weight: 800;
          letter-spacing: 0.08em;
          color: #ffffff;
        }

        .section-pill {
          font-size: 11px;
          font-weight: 700;
          text-transform: uppercase;
          letter-spacing: 0.1em;
          padding: 4px 10px;
          border-radius: 20px;
          background: rgba(255, 255, 255, 0.06);
          color: rgba(237, 237, 237, 0.75);
          border: 1px solid rgba(255, 255, 255, 0.12);
        }

        .header-actions {
          display: flex;
          align-items: center;
          gap: 12px;
          flex-wrap: wrap;
        }

        .nav-link-btn {
          display: inline-flex;
          align-items: center;
          gap: 6px;
          padding: 8px 14px;
          border-radius: 10px;
          background: rgba(255, 255, 255, 0.06);
          border: 1px solid rgba(255, 255, 255, 0.1);
          color: #ededed;
          font-size: 13px;
          font-weight: 600;
          text-decoration: none;
          transition: background 150ms ease, border-color 150ms ease, color 150ms ease;
        }

        .nav-link-btn:hover {
          background: rgba(255, 255, 255, 0.12);
          border-color: rgba(255, 255, 255, 0.2);
          color: #ffffff;
        }

        .submit-trigger-button {
          display: inline-flex;
          align-items: center;
          gap: 6px;
          padding: 8px 14px;
          border-radius: 10px;
          background: rgba(255, 255, 255, 0.06);
          border: 1px solid rgba(255, 255, 255, 0.1);
          color: #ededed;
          font-size: 13px;
          font-weight: 600;
          cursor: pointer;
          transition: background 150ms ease, border-color 150ms ease, color 150ms ease;
        }

        .submit-trigger-button:hover {
          background: rgba(255, 255, 255, 0.12);
          border-color: rgba(255, 255, 255, 0.2);
          color: #ffffff;
        }

        .user-profile-btn {
          display: inline-flex;
          align-items: center;
          gap: 10px;
          padding: 6px 14px 6px 8px;
          border-radius: 12px;
          background: rgba(255, 255, 255, 0.06);
          border: 1px solid rgba(255, 255, 255, 0.12);
          color: #ffffff;
          cursor: pointer;
          transition: background 150ms ease, border-color 150ms ease;
        }

        .user-profile-btn:hover {
          background: rgba(255, 255, 255, 0.12);
          border-color: rgba(255, 255, 255, 0.25);
        }

        .user-avatar-wrap {
          width: 28px;
          height: 28px;
          border-radius: 8px;
          overflow: hidden;
          display: flex;
          align-items: center;
          justify-content: center;
        }

        .user-details {
          display: flex;
          flex-direction: column;
          align-items: flex-start;
          line-height: 1.1;
        }

        .user-name {
          font-size: 13px;
          font-weight: 700;
          color: #ededed;
        }

        .user-cloud-status {
          font-size: 10px;
          font-weight: 600;
          color: #00d588;
          display: flex;
          align-items: center;
          gap: 3px;
          margin-top: 2px;
        }

        .guest-actions {
          display: flex;
          align-items: center;
          gap: 8px;
        }

        .auth-portal-btn {
          display: inline-flex;
          align-items: center;
          gap: 8px;
          padding: 8px 16px;
          border-radius: 10px;
          background: #ffffff;
          color: #000000;
          font-size: 13px;
          font-weight: 700;
          border: 0;
          cursor: pointer;
          box-shadow: 0 2px 10px rgba(0, 0, 0, 0.3);
          transition: transform 140ms ease, background 140ms ease, box-shadow 140ms ease;
        }

        .auth-portal-btn:hover {
          background: #e6e6e8;
          transform: translateY(-1px);
          box-shadow: 0 4px 14px rgba(0, 0, 0, 0.4);
        }

        .guest-btn {
          padding: 8px 10px;
        }

        .hero-title {
          font-size: 38px;
          font-weight: 800;
          margin: 0 0 12px 0;
          color: #ffffff;
          letter-spacing: -0.025em;
        }

        .hero-subtitle {
          font-size: 16px;
          color: rgba(237, 237, 237, 0.65);
          max-width: 720px;
          line-height: 1.6;
          margin: 0 0 20px 0;
        }

        .profile-context-banner {
          display: inline-flex;
          align-items: center;
          gap: 8px;
          padding: 6px 14px;
          border-radius: 10px;
          background: rgba(255, 255, 255, 0.04);
          border: 1px solid rgba(255, 255, 255, 0.08);
          font-size: 13px;
        }

        .context-label {
          color: rgba(237, 237, 237, 0.5);
        }

        .context-profile-name {
          color: #ededed;
          font-weight: 600;
        }

        .switch-profile-quick-link {
          background: none;
          border: none;
          color: rgba(237, 237, 237, 0.85);
          font-size: 12px;
          font-weight: 600;
          cursor: pointer;
          text-decoration: underline;
          padding: 0 2px;
          transition: color 140ms ease;
        }

        .switch-profile-quick-link:hover {
          color: #ffffff;
        }

        .installed-count-pill {
          margin-left: 6px;
          padding: 2px 8px;
          border-radius: 12px;
          background: rgba(0, 213, 136, 0.1);
          color: #00d588;
          border: 1px solid rgba(0, 213, 136, 0.25);
          font-size: 11px;
          font-weight: 700;
        }

        .banner-alert {
          margin-top: 18px;
          padding: 12px 16px;
          border-radius: 10px;
          display: flex;
          align-items: center;
          gap: 10px;
          font-size: 14px;
        }

        .banner-alert.error {
          background: rgba(231, 76, 60, 0.1);
          border: 1px solid rgba(231, 76, 60, 0.25);
          color: #ff6b6b;
        }

        .alert-close {
          margin-left: auto;
          background: none;
          border: none;
          color: inherit;
          font-size: 18px;
          cursor: pointer;
          opacity: 0.7;
          transition: opacity 140ms ease;
        }

        .alert-close:hover {
          opacity: 1;
        }

        .loading-container {
          display: flex;
          flex-direction: column;
          align-items: center;
          justify-content: center;
          gap: 16px;
          padding: 80px 0;
          color: rgba(237, 237, 237, 0.5);
        }

        .text-accent {
          color: #00d588;
        }

        .animate-spin {
          animation: spin 1s linear infinite;
        }

        @keyframes spin {
          from { transform: rotate(0deg); }
          to { transform: rotate(360deg); }
        }

        .discover-grid {
          display: grid;
          grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
          gap: 24px;
          margin-bottom: 70px;
        }

        .pack-card {
          background: #0d0d10;
          border: 1px solid rgba(255, 255, 255, 0.08);
          border-radius: 16px;
          padding: 24px;
          display: flex;
          flex-direction: column;
          box-shadow: 0 4px 20px rgba(0, 0, 0, 0.4);
          transition: transform 180ms ease, border-color 180ms ease, box-shadow 180ms ease;
        }

        .pack-card:hover {
          transform: translateY(-2px);
          border-color: rgba(255, 255, 255, 0.18);
          box-shadow: 0 12px 32px rgba(0, 0, 0, 0.6);
        }

        .pack-card.is-installed {
          border-color: rgba(0, 213, 136, 0.3);
          background: linear-gradient(180deg, rgba(0, 213, 136, 0.03) 0%, #0d0d10 35%);
        }

        .pack-card-header {
          margin-bottom: 14px;
        }

        .title-row {
          display: flex;
          align-items: center;
          justify-content: space-between;
          gap: 10px;
          margin-bottom: 6px;
        }

        .pack-title {
          font-size: 19px;
          font-weight: 700;
          color: #ffffff;
          margin: 0;
          letter-spacing: -0.01em;
        }

        .installed-tag {
          display: inline-flex;
          align-items: center;
          gap: 4px;
          padding: 3px 8px;
          border-radius: 6px;
          background: rgba(0, 213, 136, 0.1);
          color: #00d588;
          font-size: 11px;
          font-weight: 700;
          border: 1px solid rgba(0, 213, 136, 0.25);
          white-space: nowrap;
        }

        .pack-meta {
          display: flex;
          align-items: center;
          gap: 10px;
          font-size: 12px;
        }

        .pack-author {
          color: rgba(237, 237, 237, 0.45);
        }

        .pack-version {
          background: rgba(255, 255, 255, 0.06);
          color: rgba(237, 237, 237, 0.7);
          border: 1px solid rgba(255, 255, 255, 0.08);
          padding: 2px 6px;
          border-radius: 6px;
          font-weight: 600;
          font-size: 11px;
        }

        .pack-description {
          font-size: 14px;
          color: rgba(237, 237, 237, 0.7);
          line-height: 1.55;
          margin: 0 0 20px 0;
          flex-grow: 1;
        }

        .catalogs-section {
          background: rgba(255, 255, 255, 0.03);
          border: 1px solid rgba(255, 255, 255, 0.06);
          border-radius: 12px;
          padding: 14px;
          margin-bottom: 22px;
        }

        .catalogs-section-header {
          display: flex;
          align-items: center;
          justify-content: space-between;
          margin-bottom: 10px;
        }

        .section-label {
          font-size: 11px;
          font-weight: 700;
          color: rgba(237, 237, 237, 0.45);
          margin: 0;
          text-transform: uppercase;
          letter-spacing: 0.08em;
          display: flex;
          align-items: center;
          gap: 6px;
        }

        .preview-trigger-link {
          background: none;
          border: none;
          color: rgba(237, 237, 237, 0.8);
          font-size: 12px;
          font-weight: 600;
          cursor: pointer;
          display: inline-flex;
          align-items: center;
          gap: 4px;
          padding: 2px 6px;
          border-radius: 6px;
          transition: background 150ms ease, color 150ms ease;
        }

        .preview-trigger-link:hover {
          background: rgba(255, 255, 255, 0.08);
          color: #ffffff;
        }

        .catalogs-list {
          list-style: none;
          padding: 0;
          margin: 0;
          display: flex;
          flex-wrap: wrap;
          gap: 6px;
        }

        .catalog-item-tag {
          font-size: 12px;
          background: rgba(255, 255, 255, 0.05);
          color: rgba(237, 237, 237, 0.85);
          padding: 4px 10px;
          border-radius: 6px;
          border: 1px solid rgba(255, 255, 255, 0.08);
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
          max-width: 100%;
        }

        .catalog-item-tag.more {
          background: rgba(255, 255, 255, 0.08);
          color: rgba(237, 237, 237, 0.9);
          border-color: rgba(255, 255, 255, 0.15);
          font-weight: 600;
        }

        .pack-actions {
          display: flex;
          flex-direction: column;
          gap: 10px;
        }

        .installed-action-group {
          display: grid;
          grid-template-columns: 1fr auto;
          gap: 8px;
        }

        .install-button {
          display: inline-flex;
          align-items: center;
          justify-content: center;
          gap: 8px;
          padding: 11px 16px;
          border-radius: 10px;
          font-size: 14px;
          font-weight: 700;
          cursor: pointer;
          border: 0;
          transition: transform 140ms ease, box-shadow 140ms ease, background 140ms ease;
        }

        .install-button.primary {
          background: #ffffff;
          color: #000000;
          box-shadow: 0 2px 10px rgba(0, 0, 0, 0.35);
        }

        .install-button.primary:hover:not(:disabled) {
          background: #e6e6e8;
          transform: translateY(-1px);
          box-shadow: 0 4px 14px rgba(0, 0, 0, 0.45);
        }

        .install-button.update {
          background: rgba(0, 213, 136, 0.12);
          color: #00d588;
          border: 1px solid rgba(0, 213, 136, 0.3);
        }

        .install-button.update:hover:not(:disabled) {
          background: rgba(0, 213, 136, 0.22);
          transform: translateY(-1px);
        }

        .uninstall-button {
          display: inline-flex;
          align-items: center;
          justify-content: center;
          padding: 10px 14px;
          border-radius: 10px;
          background: rgba(231, 76, 60, 0.1);
          color: #ff6b6b;
          border: 1px solid rgba(231, 76, 60, 0.22);
          cursor: pointer;
          transition: background 140ms ease, color 140ms ease;
        }

        .uninstall-button:hover {
          background: rgba(231, 76, 60, 0.2);
        }

        .install-button:disabled {
          opacity: 0.5;
          cursor: not-allowed;
        }

        .secondary-action-group {
          display: grid;
          grid-template-columns: 1fr 1fr;
          gap: 8px;
        }

        .copy-button,
        .deep-link-btn {
          display: inline-flex;
          align-items: center;
          justify-content: center;
          gap: 6px;
          padding: 8px 12px;
          border-radius: 8px;
          background: rgba(255, 255, 255, 0.05);
          border: 1px solid rgba(255, 255, 255, 0.08);
          color: rgba(237, 237, 237, 0.7);
          font-size: 12px;
          font-weight: 600;
          text-decoration: none;
          cursor: pointer;
          transition: background 140ms ease, color 140ms ease, border-color 140ms ease;
        }

        .copy-button:hover,
        .deep-link-btn:hover {
          background: rgba(255, 255, 255, 0.1);
          border-color: rgba(255, 255, 255, 0.16);
          color: #ffffff;
        }

        .text-green {
          color: #00d588;
        }

        /* Dialog & Modal styling using modern CSS & HTMLDialog standards */
        .modal-dialog {
          background: transparent;
          border: 0;
          padding: 0;
          max-width: 560px;
          width: 90vw;
          margin: auto;
          color: #ededed;
          outline: none;
        }

        .modal-dialog::backdrop {
          background: rgba(0, 0, 0, 0.8);
          backdrop-filter: blur(12px);
          -webkit-backdrop-filter: blur(12px);
        }

        .modal-dialog-content {
          background: #111114;
          border: 1px solid rgba(255, 255, 255, 0.12);
          border-radius: 18px;
          box-shadow: 0 24px 60px rgba(0, 0, 0, 0.85);
          overflow: hidden;
          display: flex;
          flex-direction: column;
          max-height: 85vh;
        }

        .modal-header {
          display: flex;
          align-items: center;
          justify-content: space-between;
          padding: 18px 22px;
          border-bottom: 1px solid rgba(255, 255, 255, 0.08);
        }

        .modal-title {
          font-size: 18px;
          font-weight: 700;
          margin: 0;
          display: flex;
          align-items: center;
          gap: 10px;
          color: #ffffff;
        }

        .icon-accent {
          color: #00d588;
        }

        .modal-close {
          background: rgba(255, 255, 255, 0.06);
          border: 1px solid rgba(255, 255, 255, 0.1);
          color: rgba(237, 237, 237, 0.6);
          width: 32px;
          height: 32px;
          border-radius: 8px;
          display: flex;
          align-items: center;
          justify-content: center;
          cursor: pointer;
          transition: background 140ms ease, color 140ms ease;
        }

        .modal-close:hover {
          background: rgba(255, 255, 255, 0.12);
          color: #ffffff;
        }

        .settings-dialog-body,
        .preview-dialog-body,
        .submit-dialog-body {
          padding: 24px;
          overflow-y: auto;
          display: flex;
          flex-direction: column;
          gap: 24px;
        }

        .account-status-card {
          background: rgba(255, 255, 255, 0.03);
          border: 1px solid rgba(255, 255, 255, 0.08);
          border-radius: 14px;
          padding: 18px;
        }

        .account-status-top {
          display: flex;
          align-items: center;
          gap: 14px;
        }

        .account-icon-wrap {
          color: #00d588;
        }

        .account-meta {
          display: flex;
          flex-direction: column;
          gap: 2px;
        }

        .account-label {
          font-size: 11px;
          font-weight: 700;
          text-transform: uppercase;
          color: rgba(237, 237, 237, 0.45);
          letter-spacing: 0.08em;
        }

        .account-email {
          font-size: 16px;
          font-weight: 700;
          color: #ffffff;
        }

        .account-cloud-badge {
          display: inline-flex;
          align-items: center;
          gap: 4px;
          font-size: 11px;
          font-weight: 600;
          margin-top: 4px;
        }

        .account-cloud-badge.connected {
          color: #00d588;
        }

        .account-cloud-badge.disconnected {
          color: #ff6b6b;
        }

        .account-controls {
          margin-top: 14px;
          padding-top: 12px;
          border-top: 1px solid rgba(255, 255, 255, 0.08);
          display: flex;
          justify-content: flex-end;
        }

        .account-signout-btn {
          display: inline-flex;
          align-items: center;
          gap: 6px;
          padding: 6px 12px;
          border-radius: 8px;
          background: rgba(231, 76, 60, 0.1);
          border: 1px solid rgba(231, 76, 60, 0.22);
          color: #ff6b6b;
          font-size: 12px;
          font-weight: 600;
          cursor: pointer;
          transition: background 140ms ease;
        }

        .account-signout-btn:hover {
          background: rgba(231, 76, 60, 0.2);
        }

        .account-connect-box {
          margin-top: 14px;
          padding-top: 12px;
          border-top: 1px solid rgba(255, 255, 255, 0.08);
        }

        .account-connect-desc {
          font-size: 13px;
          color: rgba(237, 237, 237, 0.6);
          line-height: 1.5;
          margin: 0 0 12px 0;
        }

        .primary-auth-button {
          width: 100%;
          display: flex;
          align-items: center;
          justify-content: center;
          gap: 8px;
          padding: 11px 16px;
          border-radius: 10px;
          background: #ffffff;
          color: #000000;
          font-size: 14px;
          font-weight: 700;
          border: 0;
          cursor: pointer;
          box-shadow: 0 2px 10px rgba(0, 0, 0, 0.35);
          transition: background 140ms ease, transform 120ms ease;
        }

        .primary-auth-button:hover {
          background: #e6e6e8;
          transform: translateY(-1px);
        }

        .settings-section {
          display: flex;
          flex-direction: column;
          gap: 12px;
        }

        .section-subheading {
          font-size: 14px;
          font-weight: 700;
          color: #ffffff;
          margin: 0;
          display: flex;
          align-items: center;
          gap: 8px;
        }

        .section-desc {
          font-size: 13px;
          color: rgba(237, 237, 237, 0.6);
          margin: 0;
          line-height: 1.4;
        }

        .section-header-split {
          display: flex;
          align-items: center;
          justify-content: space-between;
        }

        .count-tag {
          font-size: 11px;
          font-weight: 700;
          background: rgba(255, 255, 255, 0.06);
          color: rgba(237, 237, 237, 0.7);
          padding: 2px 8px;
          border-radius: 10px;
        }

        .profiles-picker-grid {
          display: grid;
          grid-template-columns: repeat(auto-fill, minmax(130px, 1fr));
          gap: 10px;
        }

        .profile-pill {
          display: flex;
          align-items: center;
          gap: 8px;
          padding: 8px 12px;
          border-radius: 10px;
          background: rgba(255, 255, 255, 0.04);
          border: 1px solid rgba(255, 255, 255, 0.08);
          color: #ededed;
          cursor: pointer;
          transition: border-color 140ms ease, background 140ms ease;
        }

        .profile-pill:hover {
          background: rgba(255, 255, 255, 0.08);
          border-color: rgba(255, 255, 255, 0.16);
        }

        .profile-pill.active {
          border-color: rgba(0, 213, 136, 0.4);
          background: rgba(0, 213, 136, 0.1);
        }

        .profile-pill-avatar {
          width: 22px;
          height: 22px;
          border-radius: 6px;
          overflow: hidden;
        }

        .profile-pill-name {
          font-size: 13px;
          font-weight: 600;
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }

        .profile-active-check {
          color: #00d588;
          margin-left: auto;
        }

        .empty-packs-notice {
          padding: 20px;
          text-align: center;
          border-radius: 10px;
          background: rgba(255, 255, 255, 0.02);
          border: 1px dashed rgba(255, 255, 255, 0.1);
          color: rgba(237, 237, 237, 0.5);
          font-size: 13px;
        }

        .empty-packs-notice p {
          margin: 0 0 4px 0;
          color: #ededed;
          font-weight: 600;
        }

        .installed-packs-list {
          display: flex;
          flex-direction: column;
          gap: 8px;
          max-height: 200px;
          overflow-y: auto;
        }

        .installed-pack-row {
          display: flex;
          align-items: center;
          justify-content: space-between;
          padding: 10px 14px;
          border-radius: 10px;
          background: rgba(255, 255, 255, 0.03);
          border: 1px solid rgba(255, 255, 255, 0.06);
        }

        .installed-pack-title {
          font-size: 13px;
          color: #ededed;
          display: block;
          font-weight: 600;
        }

        .installed-pack-meta {
          display: flex;
          align-items: center;
          gap: 8px;
          font-size: 11px;
          color: rgba(237, 237, 237, 0.45);
          margin-top: 2px;
        }

        .badge-type {
          background: rgba(255, 255, 255, 0.08);
          color: rgba(237, 237, 237, 0.8);
          padding: 1px 6px;
          border-radius: 4px;
          font-weight: 600;
        }

        .pack-remove-btn {
          background: transparent;
          border: 0;
          color: rgba(237, 237, 237, 0.45);
          cursor: pointer;
          padding: 6px;
          border-radius: 6px;
          transition: color 140ms ease, background 140ms ease;
        }

        .pack-remove-btn:hover {
          color: #ff6b6b;
          background: rgba(231, 76, 60, 0.1);
        }

        .settings-footer-actions {
          display: flex;
          align-items: center;
          justify-content: space-between;
          padding-top: 16px;
          border-top: 1px solid rgba(255, 255, 255, 0.08);
          gap: 12px;
        }

        .footer-action-link {
          display: inline-flex;
          align-items: center;
          gap: 6px;
          color: rgba(237, 237, 237, 0.6);
          font-size: 13px;
          font-weight: 500;
          text-decoration: none;
          transition: color 140ms ease;
        }

        .footer-action-link:hover {
          color: #ffffff;
        }

        /* Preview Dialog */
        .preview-dialog {
          max-width: 600px;
        }

        .preview-desc {
          font-size: 14px;
          color: rgba(237, 237, 237, 0.65);
          margin: 0;
          line-height: 1.5;
        }

        .preview-list-heading {
          font-size: 13px;
          font-weight: 700;
          color: #ededed;
          text-transform: uppercase;
          letter-spacing: 0.06em;
          margin: 0 0 10px 0;
        }

        .preview-catalogs-grid {
          list-style: none;
          padding: 0;
          margin: 0;
          display: flex;
          flex-direction: column;
          gap: 8px;
          max-height: 240px;
          overflow-y: auto;
        }

        .preview-catalog-card {
          display: flex;
          align-items: flex-start;
          gap: 12px;
          padding: 10px 14px;
          border-radius: 10px;
          background: rgba(255, 255, 255, 0.03);
          border: 1px solid rgba(255, 255, 255, 0.06);
        }

        .preview-card-index {
          font-size: 12px;
          font-weight: 700;
          color: #00d588;
          background: rgba(0, 213, 136, 0.1);
          width: 22px;
          height: 22px;
          border-radius: 6px;
          display: flex;
          align-items: center;
          justify-content: center;
          flex-shrink: 0;
        }

        .preview-card-content {
          display: flex;
          flex-direction: column;
          gap: 2px;
          overflow: hidden;
        }

        .preview-url-text {
          font-size: 11px;
          color: rgba(237, 237, 237, 0.45);
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }

        .full-width {
          width: 100%;
        }

        /* Submission Form styling */
        .submission-form {
          display: flex;
          flex-direction: column;
          gap: 16px;
        }

        .form-intro {
          font-size: 13px;
          color: rgba(237, 237, 237, 0.65);
          margin: 0;
        }

        .form-error {
          padding: 10px 14px;
          border-radius: 8px;
          background: rgba(231, 76, 60, 0.1);
          border: 1px solid rgba(231, 76, 60, 0.25);
          color: #ff6b6b;
          display: flex;
          align-items: center;
          gap: 8px;
          font-size: 13px;
        }

        .form-field {
          display: flex;
          flex-direction: column;
          gap: 6px;
        }

        .form-field label {
          font-size: 12px;
          font-weight: 600;
          color: rgba(237, 237, 237, 0.65);
          display: flex;
          align-items: center;
          gap: 6px;
        }

        .form-field input,
        .form-field textarea {
          padding: 10px 14px;
          border-radius: 10px;
          border: 1px solid rgba(255, 255, 255, 0.12);
          background: #08080a;
          color: #ffffff;
          font-family: inherit;
          font-size: 14px;
          outline: none;
          transition: border-color 140ms ease;
        }

        .form-field input:focus,
        .form-field textarea:focus {
          border-color: rgba(255, 255, 255, 0.35);
        }

        .field-hint {
          font-size: 11px;
          color: rgba(237, 237, 237, 0.45);
        }

        .form-submit-button {
          margin-top: 8px;
          padding: 12px;
          border-radius: 10px;
          background: #ffffff;
          color: #000000;
          font-size: 14px;
          font-weight: 700;
          border: 0;
          cursor: pointer;
          display: flex;
          align-items: center;
          justify-content: center;
          gap: 8px;
          box-shadow: 0 2px 10px rgba(0, 0, 0, 0.35);
          transition: background 140ms ease, transform 120ms ease;
        }

        .form-submit-button:hover:not(:disabled) {
          background: #e6e6e8;
          transform: translateY(-1px);
        }

        .form-submit-button:disabled {
          opacity: 0.5;
          cursor: not-allowed;
        }

        .submit-feedback.success {
          text-align: center;
          padding: 20px 0;
        }

        .feedback-icon {
          margin: 0 auto 16px;
        }

        /* FAQ and Examples */
        .faq-section {
          border-top: 1px solid rgba(255, 255, 255, 0.08);
          padding-top: 50px;
          margin-bottom: 50px;
        }

        .faq-heading,
        .manifest-heading {
          font-size: 20px;
          font-weight: 700;
          color: #ffffff;
          display: flex;
          align-items: center;
          gap: 10px;
          margin-bottom: 24px;
        }

        .faq-grid {
          display: grid;
          grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
          gap: 20px;
        }

        .faq-item {
          background: #0d0d10;
          border: 1px solid rgba(255, 255, 255, 0.06);
          border-radius: 14px;
          padding: 22px;
        }

        .faq-item h3 {
          font-size: 15px;
          font-weight: 700;
          color: #ffffff;
          margin: 0 0 10px 0;
        }

        .faq-item p {
          font-size: 14px;
          color: rgba(237, 237, 237, 0.65);
          line-height: 1.6;
          margin: 0;
        }

        .manifest-example-section {
          border-top: 1px solid rgba(255, 255, 255, 0.08);
          padding-top: 50px;
          margin-bottom: 50px;
        }

        .code-block {
          background: #08080a;
          border: 1px solid rgba(255, 255, 255, 0.1);
          border-radius: 12px;
          padding: 20px;
          font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
          font-size: 13px;
          color: #ededed;
          overflow-x: auto;
          line-height: 1.5;
        }

        /* Custom Import Button & Hero Quick Actions */
        .import-trigger-button {
          display: inline-flex;
          align-items: center;
          gap: 6px;
          padding: 8px 14px;
          border-radius: 10px;
          background: rgba(255, 255, 255, 0.08);
          border: 1px solid rgba(255, 255, 255, 0.14);
          color: #ffffff;
          font-size: 13px;
          font-weight: 600;
          cursor: pointer;
          transition: background 150ms ease, border-color 150ms ease, transform 120ms ease;
        }

        .import-trigger-button:hover {
          background: rgba(255, 255, 255, 0.14);
          border-color: rgba(255, 255, 255, 0.25);
          transform: translateY(-1px);
        }

        .hero-quick-actions {
          display: flex;
          align-items: center;
          gap: 10px;
          flex-wrap: wrap;
          margin-top: 18px;
        }

        .quick-action-pill {
          display: inline-flex;
          align-items: center;
          gap: 7px;
          padding: 7px 14px;
          border-radius: 20px;
          background: rgba(255, 255, 255, 0.06);
          border: 1px solid rgba(255, 255, 255, 0.1);
          color: #ededed;
          font-size: 12px;
          font-weight: 600;
          cursor: pointer;
          transition: background 140ms ease, border-color 140ms ease, color 140ms ease, transform 120ms ease;
        }

        .quick-action-pill:hover {
          background: rgba(255, 255, 255, 0.12);
          border-color: rgba(255, 255, 255, 0.2);
          color: #ffffff;
          transform: translateY(-1px);
        }

        /* Custom Import Modal */
        .import-dialog {
          max-width: 680px;
          width: 95vw;
        }

        .import-modal-content {
          display: flex;
          flex-direction: column;
          gap: 16px;
        }

        .modal-intro-text {
          font-size: 13px;
          color: rgba(237, 237, 237, 0.65);
          margin: 0;
          line-height: 1.5;
        }

        .modal-intro-text code {
          background: rgba(255, 255, 255, 0.06);
          border: 1px solid rgba(255, 255, 255, 0.1);
          padding: 2px 6px;
          border-radius: 4px;
          color: #ededed;
          font-size: 12px;
        }

        .import-tabs-bar {
          display: flex;
          gap: 6px;
          padding: 4px;
          background: #08080a;
          border-radius: 12px;
          border: 1px solid rgba(255, 255, 255, 0.08);
        }

        .import-tab-btn {
          flex: 1;
          display: flex;
          align-items: center;
          justify-content: center;
          gap: 7px;
          padding: 9px 12px;
          border-radius: 9px;
          background: transparent;
          border: 0;
          color: rgba(237, 237, 237, 0.6);
          font-size: 13px;
          font-weight: 600;
          cursor: pointer;
          transition: color 140ms ease, background 140ms ease;
        }

        .import-tab-btn:hover {
          color: #ffffff;
          background: rgba(255, 255, 255, 0.04);
        }

        .import-tab-btn.active {
          background: rgba(255, 255, 255, 0.12);
          color: #ffffff;
        }

        .tab-panel {
          display: flex;
          flex-direction: column;
          gap: 10px;
        }

        /* Paste Panel */
        .paste-toolbar {
          display: flex;
          align-items: center;
          justify-content: space-between;
          font-size: 12px;
          color: rgba(237, 237, 237, 0.5);
        }

        .toolbar-actions {
          display: flex;
          gap: 6px;
        }

        .toolbar-btn {
          display: inline-flex;
          align-items: center;
          gap: 4px;
          padding: 4px 10px;
          border-radius: 6px;
          background: rgba(255, 255, 255, 0.06);
          border: 1px solid rgba(255, 255, 255, 0.1);
          color: #ededed;
          font-size: 11px;
          font-weight: 600;
          cursor: pointer;
          transition: background 140ms ease, color 140ms ease;
        }

        .toolbar-btn:hover:not(:disabled) {
          background: rgba(255, 255, 255, 0.12);
          color: #ffffff;
        }

        .toolbar-btn:disabled {
          opacity: 0.4;
          cursor: not-allowed;
        }

        .code-textarea {
          width: 100%;
          box-sizing: border-box;
          font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
          font-size: 13px;
          line-height: 1.5;
          color: #ededed;
          background: #08080a;
          border: 1px solid rgba(255, 255, 255, 0.12);
          border-radius: 10px;
          padding: 12px 14px;
          outline: none;
          resize: vertical;
          transition: border-color 140ms ease;
        }

        .code-textarea:focus {
          border-color: rgba(255, 255, 255, 0.35);
        }

        /* File Dropzone */
        .dropzone {
          border: 1.5px dashed rgba(255, 255, 255, 0.18);
          border-radius: 14px;
          padding: 32px 20px;
          text-align: center;
          cursor: pointer;
          background: rgba(255, 255, 255, 0.02);
          transition: border-color 160ms ease, background 160ms ease;
        }

        .dropzone:hover,
        .dropzone.dragging {
          border-color: rgba(255, 255, 255, 0.35);
          background: rgba(255, 255, 255, 0.04);
        }

        .dropzone.has-file {
          border-style: solid;
          border-color: rgba(0, 213, 136, 0.35);
          background: rgba(0, 213, 136, 0.03);
          padding: 20px;
        }

        .dropzone-content {
          display: flex;
          flex-direction: column;
          align-items: center;
          gap: 8px;
        }

        .dropzone-icon {
          color: rgba(237, 237, 237, 0.6);
        }

        .dropzone-text {
          font-size: 14px;
          color: #ededed;
        }

        .dropzone-text strong {
          color: #ffffff;
        }

        .dropzone-subtext {
          font-size: 12px;
          color: rgba(237, 237, 237, 0.45);
        }

        .file-info-box {
          display: flex;
          align-items: center;
          gap: 14px;
          text-align: left;
        }

        .file-icon-active {
          color: #00d588;
          flex-shrink: 0;
        }

        .file-meta {
          flex: 1;
          display: flex;
          flex-direction: column;
          gap: 2px;
          overflow: hidden;
        }

        .file-name {
          font-size: 14px;
          color: #ffffff;
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }

        .file-size {
          font-size: 11px;
          color: rgba(237, 237, 237, 0.45);
        }

        .file-remove-btn {
          background: rgba(231, 76, 60, 0.1);
          border: 1px solid rgba(231, 76, 60, 0.22);
          color: #ff6b6b;
          border-radius: 8px;
          padding: 6px;
          cursor: pointer;
          display: flex;
          align-items: center;
          justify-content: center;
          transition: background 140ms ease;
        }

        .file-remove-btn:hover {
          background: rgba(231, 76, 60, 0.2);
        }

        /* URL Input */
        .url-input-wrap {
          display: flex;
          gap: 10px;
        }

        .url-text-input {
          flex: 1;
          padding: 10px 14px;
          border-radius: 10px;
          border: 1px solid rgba(255, 255, 255, 0.12);
          background: #08080a;
          color: #ffffff;
          font-size: 14px;
          outline: none;
          transition: border-color 140ms ease;
        }

        .url-text-input:focus {
          border-color: rgba(255, 255, 255, 0.35);
        }

        .fetch-url-btn {
          display: inline-flex;
          align-items: center;
          gap: 6px;
          padding: 10px 18px;
          border-radius: 10px;
          background: rgba(255, 255, 255, 0.08);
          border: 1px solid rgba(255, 255, 255, 0.14);
          color: #ffffff;
          font-size: 13px;
          font-weight: 600;
          cursor: pointer;
          white-space: nowrap;
          transition: background 140ms ease, border-color 140ms ease;
        }

        .fetch-url-btn:hover:not(:disabled) {
          background: rgba(255, 255, 255, 0.14);
          border-color: rgba(255, 255, 255, 0.25);
        }

        .fetch-url-btn:disabled {
          opacity: 0.5;
          cursor: not-allowed;
        }

        .import-error-banner {
          display: flex;
          align-items: center;
          gap: 8px;
          padding: 10px 14px;
          border-radius: 8px;
          background: rgba(231, 76, 60, 0.1);
          border: 1px solid rgba(231, 76, 60, 0.25);
          color: #ff6b6b;
          font-size: 13px;
        }

        /* Import Preview Box */
        .import-preview-box {
          display: flex;
          flex-direction: column;
          gap: 12px;
          padding: 16px;
          border-radius: 12px;
          background: rgba(255, 255, 255, 0.03);
          border: 1px solid rgba(255, 255, 255, 0.08);
        }

        .preview-top-row {
          display: flex;
          align-items: center;
          justify-content: space-between;
          flex-wrap: wrap;
          gap: 10px;
        }

        .preview-pack-title-row {
          display: flex;
          align-items: center;
          gap: 10px;
          flex: 1;
        }

        .preview-status-pill {
          display: inline-flex;
          align-items: center;
          gap: 5px;
          font-size: 11px;
          font-weight: 700;
          padding: 3px 8px;
          border-radius: 6px;
          background: rgba(0, 213, 136, 0.1);
          color: #00d588;
          border: 1px solid rgba(0, 213, 136, 0.25);
          white-space: nowrap;
        }

        .pack-name-edit-wrap {
          flex: 1;
        }

        .pack-name-input {
          width: 100%;
          padding: 6px 10px;
          border-radius: 6px;
          border: 1px solid rgba(255, 255, 255, 0.15);
          background: #08080a;
          color: #ffffff;
          font-size: 14px;
          font-weight: 700;
          outline: none;
          transition: border-color 140ms ease;
        }

        .pack-name-input:focus {
          border-color: rgba(255, 255, 255, 0.35);
        }

        .preview-row-count {
          font-size: 12px;
          color: rgba(237, 237, 237, 0.45);
          font-weight: 600;
        }

        .preview-catalog-list {
          display: flex;
          flex-direction: column;
          gap: 6px;
          max-height: 180px;
          overflow-y: auto;
          padding-right: 4px;
        }

        .preview-catalog-item {
          display: flex;
          align-items: center;
          gap: 10px;
          padding: 6px 10px;
          border-radius: 6px;
          background: rgba(255, 255, 255, 0.03);
          border: 1px solid rgba(255, 255, 255, 0.05);
          font-size: 12px;
        }

        .preview-cat-num {
          font-size: 10px;
          font-weight: 700;
          color: #00d588;
          width: 18px;
          text-align: center;
        }

        .preview-cat-name {
          flex: 1;
          color: #ededed;
          font-weight: 500;
        }

        .preview-source-badge {
          font-size: 10px;
          padding: 2px 6px;
          border-radius: 4px;
          background: rgba(255, 255, 255, 0.08);
          color: rgba(237, 237, 237, 0.5);
          text-transform: uppercase;
        }

        .preview-more-rows {
          text-align: center;
          font-size: 11px;
          color: rgba(237, 237, 237, 0.45);
          padding: 4px;
        }

        .target-profile-bar {
          display: flex;
          align-items: center;
          gap: 10px;
          padding-top: 8px;
          border-top: 1px solid rgba(255, 255, 255, 0.06);
          font-size: 12px;
          color: rgba(237, 237, 237, 0.5);
        }

        .target-label {
          font-weight: 600;
        }

        .target-profile-pill {
          display: flex;
          align-items: center;
          gap: 6px;
          background: rgba(255, 255, 255, 0.06);
          padding: 4px 10px;
          border-radius: 12px;
          color: #ffffff;
        }

        .target-profile-name {
          font-weight: 700;
        }

        .profile-select-inline {
          padding: 4px 8px;
          border-radius: 6px;
          background: #08080a;
          border: 1px solid rgba(255, 255, 255, 0.15);
          color: #ededed;
          font-size: 12px;
          outline: none;
        }

        .import-actions {
          margin-top: 4px;
        }

        .execute-install-btn {
          width: 100%;
          display: flex;
          align-items: center;
          justify-content: center;
          gap: 8px;
          padding: 12px;
          border-radius: 10px;
          background: #ffffff;
          color: #000000;
          font-size: 14px;
          font-weight: 700;
          border: 0;
          cursor: pointer;
          box-shadow: 0 2px 10px rgba(0, 0, 0, 0.35);
          transition: background 140ms ease, transform 120ms ease;
        }

        .execute-install-btn:hover:not(:disabled) {
          background: #e6e6e8;
          transform: translateY(-1px);
        }

        .execute-install-btn:disabled {
          opacity: 0.5;
          cursor: not-allowed;
        }

        .discover-footer {
          border-top: 1px solid rgba(255, 255, 255, 0.08);
          padding-top: 30px;
          text-align: center;
          font-size: 13px;
          color: rgba(237, 237, 237, 0.45);
        }

        @media (max-width: 768px) {
          .discover-container {
            padding: 24px 16px 60px;
          }

          .header-top {
            flex-direction: column;
            align-items: flex-start;
          }

          .header-actions {
            width: 100%;
            justify-content: space-between;
          }

          .hero-title {
            font-size: 30px;
          }

          .discover-grid {
            grid-template-columns: 1fr;
          }
        }
      `}</style>
    </div>
  );
}

export default function DiscoverPage() {
  return (
    <AppProvider>
      <DiscoverPageContent />
    </AppProvider>
  );
}
