# Build context is the repo root (see docker-compose.yml), so the build can see
# the shared @angora/config and @angora/secrets workspace packages.

FROM node:24-alpine AS build-stage
WORKDIR /workspace

RUN npm install -g pnpm
COPY pnpm-workspace.yaml package.json pnpm-lock.yaml ./
COPY packages/config ./packages/config
COPY packages/secrets ./packages/secrets
COPY apps/frontend ./apps/frontend
COPY apps/bots ./apps/bots
RUN pnpm install --frozen-lockfile

# The `...` suffix builds workspace dependencies first: @angora/secrets emits the
# dist/ + .d.ts that tsc resolves here.
RUN pnpm --filter angora-slack-bot... run build

# Extract production dependencies to /deploy-output. @angora/secrets is the only
# one, but resolving it still needs a real node_modules; this image shipped dist/
# alone before.
RUN pnpm --filter=angora-slack-bot deploy --prod --legacy /deploy-output

FROM node:24-alpine AS production-stage
WORKDIR /app

COPY --from=build-stage /deploy-output/node_modules ./node_modules
COPY --from=build-stage /deploy-output/package.json ./package.json
COPY --from=build-stage /workspace/apps/bots/slack/dist ./dist

EXPOSE 3002
CMD ["node", "dist/index.js"]
