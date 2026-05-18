# syntax=docker/dockerfile:1.7

# ---------- Stage 1: build FE ----------
FROM node:20-alpine AS web
WORKDIR /web
COPY web/package.json web/package-lock.json* ./
RUN npm ci --no-audit --no-fund || npm install --no-audit --no-fund
COPY web/ ./
RUN npm run build

# ---------- Stage 2: build JAR ----------
FROM eclipse-temurin:25-jdk AS jar
WORKDIR /src
COPY settings.gradle.kts build.gradle.kts gradle.properties gradlew ./
COPY gradle ./gradle
RUN ./gradlew --no-daemon --version >/dev/null 2>&1 || true
COPY src ./src
COPY --from=web /web/dist ./src/main/resources/static
RUN ./gradlew --no-daemon bootJar -x copyWebDist -x npmBuild -x npmInstall

# ---------- Stage 3: runtime ----------
FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd -r -u 1000 -g 0 kafka-lens && \
    mkdir -p /app/data && \
    chown -R kafka-lens:0 /app
COPY --from=jar /src/build/libs/kafka-lens.jar /app/kafka-lens.jar
USER 1000
EXPOSE 8080
ENV JAVA_OPTS=""
ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar /app/kafka-lens.jar"]
