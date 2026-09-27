# syntax=docker/dockerfile:1
# The backend image: the application plus the Chromium the report PDFs are printed with.
# Build from the backend/ folder:  docker build -f bgv-app/Dockerfile -t nexlyn-bgv-backend .

# ---- build: the application jar, and the Playwright driver jars needed to install Chromium ----------------
FROM maven:3-eclipse-temurin-24 AS build
WORKDIR /build
COPY pom.xml ./pom.xml
COPY bgv-common ./bgv-common
COPY modules ./modules
COPY bgv-app ./bgv-app
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -f pom.xml -pl bgv-app -am -DskipTests install \
 && mvn -B -f pom.xml -pl modules/reports dependency:copy-dependencies \
        -DincludeArtifactIds=playwright,driver,driver-bundle,gson -DoutputDirectory=/build/playwright-lib

# ---- runtime ---------------------------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy AS runtime

# Chromium (with the system libraries it needs) is installed by Playwright itself, into a fixed folder that
# every user can read. Noto fonts are added so names in Indian scripts print instead of showing empty boxes.
ENV PLAYWRIGHT_BROWSERS_PATH=/opt/ms-playwright
COPY --from=build /build/playwright-lib /opt/playwright-lib
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl ca-certificates fonts-noto-core fonts-noto-color-emoji \
 && java -cp "/opt/playwright-lib/*" com.microsoft.playwright.CLI install --with-deps chromium \
 && rm -rf /var/lib/apt/lists/* /tmp/*

# Never run as root. The home folder is where Playwright unpacks its driver at start-up.
RUN groupadd --system --gid 10001 nexlyn \
 && useradd --system --uid 10001 --gid nexlyn --create-home --home-dir /home/nexlyn nexlyn
WORKDIR /app
COPY --from=build --chown=nexlyn:nexlyn /build/bgv-app/target/bgv-app.jar app.jar
USER nexlyn

# Inside a container the container itself is the sandbox; Chromium's own sandbox needs privileges the container
# (correctly) does not have. See docs/runbooks/deploy-update.md for the container restrictions used in production.
# Nothing is downloaded when the app runs: the browser was installed above, at build time.
# At start-up the app prints a sample page with THIS image's browser and refuses to start unless the brand block, title,
# footer and text come out in the bundled report fonts (Inter, Selawik). Set NEXLYN_REPORTS_FONT_CHECK=warn or off to relax.
ENV NEXLYN_REPORTS_FONT_CHECK=fail \
    PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 \
    CHROMIUM_NO_SANDBOX=true \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
