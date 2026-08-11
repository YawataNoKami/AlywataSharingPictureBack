# syntax=docker/dockerfile:1

# ---- Build stage ----
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build

# Leverage layer caching: resolve dependencies before copying source code.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-alpine AS runtime

# Run as a non-root user for defense in depth.
RUN addgroup -S photoapp && adduser -S photoapp -G photoapp
WORKDIR /app

COPY --from=build /build/target/*.jar app.jar

RUN chown -R photoapp:photoapp /app
USER photoapp

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
