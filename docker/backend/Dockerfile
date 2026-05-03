# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace

COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY gradle ./gradle
COPY shared ./shared
COPY infrastructure ./infrastructure
COPY services ./services

ARG MODULE_PATH
RUN test -n "$MODULE_PATH"
RUN chmod +x ./gradlew \
    && ./gradlew ":${MODULE_PATH}:bootJar" -Dorg.gradle.java.home="$JAVA_HOME" --no-daemon --console=plain \
    && MODULE_DIR="$(echo "$MODULE_PATH" | tr ':' '/')" \
    && JAR_PATH="$(find "$MODULE_DIR/build/libs" -type f -name "*.jar" ! -name "*plain.jar" | head -n 1)" \
    && test -n "$JAR_PATH" \
    && cp "$JAR_PATH" /workspace/app.jar

FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache curl \
    && addgroup -S topdim \
    && adduser -S topdim -G topdim

WORKDIR /app

COPY --from=build /workspace/app.jar /app/app.jar

USER topdim

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.security.egd=file:/dev/./urandom"
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
  CMD curl -fsS "http://127.0.0.1:${SERVER_PORT:-8080}/actuator/health" || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
