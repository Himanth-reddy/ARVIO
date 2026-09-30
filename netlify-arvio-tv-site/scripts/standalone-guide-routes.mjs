// English guides with no translated counterpart yet. Keep their real language
// explicit in localized directories rather than adding inaccurate hreflang.
export const standaloneGuideRoutes = [
  { route: "/collections-catalogs/", updated: "2026-09-29", labels: {
    pt: ["Personalização · Em inglês", "Criar coleções e adicionar catálogos", "Modelos JSON, hospedagem gratuita e importação por URL para personalizar a página inicial."],
    es: ["Personalización · En inglés", "Crear colecciones y añadir catálogos", "Plantillas JSON, alojamiento gratuito e importación por URL para personalizar el inicio."]
  } },
  { route: "/self-host-arvio-web/", updated: "2026-09-30", labels: {
    pt: ["Navegador gratuito · Em inglês", "Hospede com Docker ou Node.js", "Comandos de instalação, suas chaves de API, perfis locais, acesso pela rede e atualizações."],
    es: ["Navegador gratuito · En inglés", "Autoalojamiento con Docker o Node.js", "Comandos de instalación, tus claves de API, perfiles locales, acceso por red y actualizaciones."]
  } },
  { route: "/browser-playback-guide/", updated: "2026-09-30", labels: {
    pt: ["Reprodução no navegador · Em inglês", "Teste sua primeira fonte", "Identifique problemas de acesso e formato e teste TV ao vivo ou servidores no seu dispositivo."],
    es: ["Reproducción en navegador · En inglés", "Prueba tu primera fuente", "Identifica problemas de acceso y formato y prueba TV en directo o servidores en tu dispositivo."]
  } }
];
