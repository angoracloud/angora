# Build context is the repo root (see docker-compose.yml), not this
# directory, so the build can see the shared `@angora/config` and
# `@angora/secrets` workspace packages that this bot's config extends and
# its code imports.

# Stage 1: Build & Prune Dependencies
FROM node:24-alpine AS build-stage
WORKDIR /workspace

RUN npm install -g pnpm
COPY pnpm-workspace.yaml package.json pnpm-lock.yaml ./
COPY packages/config ./packages/config
COPY packages/secrets ./packages/secrets
COPY apps/frontend ./apps/frontend
COPY apps/bots ./apps/bots
RUN pnpm install --frozen-lockfile

# The `...` suffix builds this bot's workspace dependencies first —
# @angora/secrets emits the dist/ + .d.ts that tsc here resolves.
RUN pnpm --filter angora-slack-bot... run build

# Extract standalone production dependencies to /deploy-output. This bot's only
# runtime dependency is the workspace's own @angora/secrets, but it still needs a
# real node_modules to resolve it — the runtime image used to ship dist/ alone.
RUN pnpm --filter=angora-slack-bot deploy --prod --legacy /deploy-output

# Stage 2: Clean Production Runtime Container
FROM node:24-alpine AS production-stage
WORKDIR /app

COPY --from=build-stage /deploy-output/node_modules ./node_modules
COPY --from=build-stage /deploy-output/package.json ./package.json
COPY --from=build-stage /workspace/apps/bots/slack/dist ./dist

EXPOSE 3002
CMD ["node", "dist/index.js"]
