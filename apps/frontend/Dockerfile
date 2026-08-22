# Build context is the repo root (see docker-compose.yml), not this
# directory, so the build can see the shared @angora/config workspace package
# that tsconfig.json / eslint.config.mjs / vite.config.ts extend.

# Stage 1: Build the application
FROM node:24-alpine AS build

# .git is excluded from the build context (see .dockerignore), so the real
# commit hash can't be read via `git rev-parse` inside the image the way
# vite.config.ts does for local `pnpm dev`/`pnpm build`. Pass it in instead:
# `docker-compose build --build-arg GIT_SHA=$(git rev-parse --short HEAD) frontend`
# (docker-compose.yml forwards a GIT_SHA env var to this arg automatically).
ARG GIT_SHA=unknown
ENV GIT_SHA=$GIT_SHA

WORKDIR /workspace
RUN npm install -g pnpm
COPY pnpm-workspace.yaml package.json pnpm-lock.yaml ./
COPY packages/config ./packages/config
COPY apps/frontend ./apps/frontend
COPY apps/bots ./apps/bots
RUN pnpm install --frozen-lockfile
WORKDIR /workspace/apps/frontend
RUN pnpm run build

# Stage 2: Serve with nginx
FROM nginx:1.30-alpine
COPY --from=build /workspace/apps/frontend/dist /usr/share/nginx/html
COPY apps/frontend/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 3000
