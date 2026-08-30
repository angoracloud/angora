# Maven comes with the image rather than via apt-get, which pulled ~200MB on
# every cache miss. Pinned to an exact tag, per the repo's convention.
FROM maven:3.9.16-eclipse-temurin-25 AS build
WORKDIR /app

# Dependencies resolve in their own layer, before any source is copied, so a
# source edit doesn't re-download the whole tree. Don't move COPY src above this.
COPY pom.xml .
RUN mvn -B dependency:go-offline

# Source last, so editing it only invalidates the compile below.
COPY src ./src
# Deliberately not `-o`: go-offline misses some plugin dependencies, and offline
# mode would turn a short top-up download into a build failure.
RUN mvn -B package -DskipTests

# Runtime stage
FROM eclipse-temurin:25-jre-jammy
WORKDIR /app
# The healthcheck uses curl, which the temurin JRE image ships neither of (nor
# wget). Without it the backend never reports healthy, and every service that
# waits on `service_healthy` never starts.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/target/backend.jar .
EXPOSE 8080
CMD ["java", "-jar", "backend.jar"]
