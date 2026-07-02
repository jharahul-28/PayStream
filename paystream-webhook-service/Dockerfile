# Multi-stage build — JDK builder discarded; only JRE in runtime image
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build
COPY pom.xml ./pom.xml
COPY ../pom.xml ../pom.xml
COPY src ./src
RUN ./mvnw -f ../pom.xml package -pl paystream-webhook-service -am -DskipTests

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S paystream && adduser -S paystream -G paystream

WORKDIR /app

COPY --from=builder /build/target/*.jar app.jar

USER paystream

EXPOSE 8087

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+UseG1GC", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
