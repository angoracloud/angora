# Build stage
# Maven comes with the image rather than via apt-get, which previously pulled
# ~200MB on every cache miss. Pinned to an exact tag, per the repo's version
# pinning convention.
FROM maven:3.9.16-eclipse-temurin-25 AS build
WORKDIR /app

# Dependencies resolve in their own layer, before any source is copied. Docker
# reuses this layer for as long as pom.xml is unchanged, so an ordinary source
# edit no longer re-downloads the whole dependency tree from Maven Central.
# Copying src/ first (as this file used to) invalidated everything on every edit.
COPY pom.xml .
RUN mvn -B dependency:go-offline

# Source last, so editing it only invalidates the compile below.
COPY src ./src
# Deliberately not `-o`: dependency:go-offline doesn't fetch quite everything
# (some plugin dependencies resolve only during the real build), and forcing
# offline mode here would turn that into a build failure rather than a short
# top-up download.
RUN mvn -B package -DskipTests

# Runtime stage
FROM eclipse-temurin:25-jre-jammy
WORKDIR /app
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*
# docker-compose.yml's healthcheck for this service shells out to curl, which the
# temurin JRE image doesn't ship (nor wget). Without this the backend could never
# report healthy, and since frontend and all three bots wait on
# `depends_on: condition: service_healthy`, none of them would ever start.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/target/backend.jar .
EXPOSE 8080
CMD ["java", "-jar", "backend.jar"]
