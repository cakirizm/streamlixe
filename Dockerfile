FROM node:22-bookworm-slim AS dependencies
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

# node:*-bookworm-slim does not ship the ca-certificates package, so /etc/ssl/certs is empty.
# Node's own fetch/https works anyway because Node bundles its own compiled-in root store, but
# workerd (the Cloudflare Workers runtime used by `vinext dev`/`vinext start` via Wrangler) relies
# on the OS trust store to validate TLS peers. Without it, every outbound HTTPS fetch from a Worker
# (e.g. TMDB) fails with "unable to get local issuer certificate" even though the certificate is
# genuinely valid. Installing ca-certificates (and refreshing the bundle) fixes this at the root
# instead of disabling verification.
FROM node:22-bookworm-slim AS ca-certs
RUN apt-get update \
  && apt-get install -y --no-install-recommends ca-certificates \
  && update-ca-certificates \
  && rm -rf /var/lib/apt/lists/*

FROM node:22-bookworm-slim AS development
WORKDIR /app
ENV NODE_ENV=development
COPY --from=ca-certs /etc/ssl/certs /etc/ssl/certs
COPY --from=ca-certs /usr/share/ca-certificates /usr/share/ca-certificates
COPY --from=dependencies /app/node_modules ./node_modules
COPY . .
EXPOSE 3000
CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0", "--port", "3000"]

FROM node:22-bookworm-slim AS builder
WORKDIR /app
ENV NODE_ENV=production
COPY --from=ca-certs /etc/ssl/certs /etc/ssl/certs
COPY --from=ca-certs /usr/share/ca-certificates /usr/share/ca-certificates
COPY --from=dependencies /app/node_modules ./node_modules
COPY . .
RUN npm run build

FROM node:22-bookworm-slim AS production
WORKDIR /app
ENV NODE_ENV=production
ENV PORT=3000
COPY --from=ca-certs /etc/ssl/certs /etc/ssl/certs
COPY --from=ca-certs /usr/share/ca-certificates /usr/share/ca-certificates
COPY --from=builder /app ./
EXPOSE 3000
CMD ["npm", "run", "start", "--", "--host", "0.0.0.0", "--port", "3000"]
