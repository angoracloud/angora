# Build context is the repo root (see docker-compose.yml), not this
# directory, so the build can see the shared @angora/config workspace package
# that tsconfig.json / eslint.config.mjs / vite.config.ts extend.

# Stage 1: Build the application
FROM node:24-alpine AS build
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
