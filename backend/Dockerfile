# syntax=docker/dockerfile:1

# ---------- Build stage ----------
# Builds the Spring Boot jar. Nothing from this stage ends up in the final
# runtime image (no Maven, no source code, no build cache).
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Copy wrapper + POM first so dependency resolution is cached across builds
# whenever only application source changes.
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN chmod +x mvnw && ./mvnw -q dependency:go-offline

COPY src src
RUN ./mvnw -q -DskipTests package

# ---------- Runtime stage ----------
FROM eclipse-temurin:21-jre-alpine AS runtime

# Run as a non-root user.
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar

# Reasonable Docker/local development default; override via SPRING_PROFILES_ACTIVE.
ENV SPRING_PROFILES_ACTIVE=docker

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=5s --start-period=40s --retries=5 \
    CMD wget -q -O - http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]


