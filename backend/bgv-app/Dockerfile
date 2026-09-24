# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml ./pom.xml
COPY bgv-common ./bgv-common
COPY modules ./modules
COPY bgv-app ./bgv-app
RUN --mount=type=cache,target=/root/.m2 mvn -f pom.xml -pl bgv-app -am -DskipTests package

FROM eclipse-temurin:21-jre-jammy AS runtime
# NOTE(phase 6): install Chromium + its system deps here for Playwright-driven PDF rendering
# before the `reports` module ships (apt-get install chromium + fonts, or `npx playwright install --with-deps`).
WORKDIR /app
COPY --from=build /build/bgv-app/target/bgv-app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
