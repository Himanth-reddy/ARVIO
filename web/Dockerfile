# syntax=docker/dockerfile:1
FROM node:22-bookworm-slim AS dependencies
WORKDIR /app
ENV NEXT_TELEMETRY_DISABLED=1
COPY package.json package-lock.json ./
RUN npm ci

FROM dependencies AS builder
ENV NEXT_PUBLIC_SELF_HOSTED=true \
    NEXT_PUBLIC_PAYWALL_ENABLED=false \
    ARVIO_TRANSLATION_MODE=prebuilt \
    ARVIO_STANDALONE=true
# Build a generic, credential-free image. Optional public application IDs are
# loaded by the self-host-only /api/selfhost-config bootstrap at runtime.
# The web-only context validates checked-in i18n assets; hosted/source builds
# still generate these assets from Android resources by default.
COPY . .
RUN npm run build && node scripts/collect-licenses.mjs

FROM node:22-bookworm-slim AS runner
WORKDIR /app
ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    NEXT_PUBLIC_SELF_HOSTED=true \
    NEXT_PUBLIC_PAYWALL_ENABLED=false \
    HOSTNAME=0.0.0.0 \
    PORT=3000
COPY --from=builder --chown=node:node /app/.next/standalone ./
COPY --from=builder --chown=node:node /app/.next/static ./.next/static
COPY --from=builder --chown=node:node /app/public ./public
COPY --from=builder --chown=node:node /app/LICENSE ./licenses/ARVIO-LICENSE
COPY --from=builder --chown=node:node /app/.next/third-party-notices ./licenses/third-party
USER node
EXPOSE 3000
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s \
  CMD node -e "fetch('http://127.0.0.1:3000/version.json',{signal:AbortSignal.timeout(4000)}).then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"
CMD ["node", "server.js"]
