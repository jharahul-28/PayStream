# Multi-stage build — builder image is discarded after compilation
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Copy only the files needed for Maven dependency resolution first (layer caching)
COPY pom.xml ./pom.xml
COPY ../pom.xml ../pom.xml

# Copy source
COPY src ./src

# Build without running tests (tests run separately in CI)
RUN ./mvnw -f ../pom.xml package -pl paystream-auth-service -am -DskipTests

# ---------------------------------------------------------------------------
# Runtime image — minimal JRE, non-root user
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S paystream && adduser -S paystream -G paystream

WORKDIR /app

COPY --from=builder /build/target/*.jar app.jar

USER paystream

EXPOSE 8081

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+UseG1GC", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
