# Build context is the repo root (see docker-compose.yml) so the build
# can access shared @angora/config workspace dependencies.

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

# Compile TypeScript to JavaScript. The `...` suffix builds this bot's workspace
# dependencies first — @angora/secrets emits the dist/ + .d.ts that tsc here resolves.
RUN pnpm --filter angora-discord-bot... run build

# Extract standalone production dependencies to /deploy-output
WORKDIR /workspace
RUN pnpm --filter=angora-discord-bot deploy --prod --legacy /deploy-output

# Stage 2: Clean Production Runtime Container
FROM node:24-alpine AS production-stage
WORKDIR /app

# Copy production node_modules and compiled dist code from build-stage
COPY --from=build-stage /deploy-output/node_modules ./node_modules
COPY --from=build-stage /deploy-output/package.json ./package.json
COPY --from=build-stage /workspace/apps/bots/discord/dist ./dist

EXPOSE 3001
CMD ["node", "dist/index.js"]
