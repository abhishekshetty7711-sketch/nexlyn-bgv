package com.nexlyn.bgv.reports.internal.render;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Finds a Chromium-based browser to print with. Order: the configured path, the {@code CHROMIUM_PATH}
 * environment variable, then the usual install places (Chrome or Edge on Windows, chromium / Chrome on
 * Linux). When nothing is found Playwright uses the Chromium it installs itself (the Docker image has one).
 */
public final class BrowserLocator {

    private static final List<String> COMMON_PLACES = List.of(
            "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
            "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe",
            "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
            "C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe",
            "/usr/bin/chromium",
            "/usr/bin/chromium-browser",
            "/usr/bin/google-chrome",
            "/usr/bin/google-chrome-stable");

    private BrowserLocator() {
    }

    public static Optional<Path> find(String configured) {
        if (configured != null && !configured.isBlank()) {
            Path path = Path.of(configured.trim());
            return Files.isRegularFile(path) ? Optional.of(path) : Optional.empty();
        }
        String fromEnvironment = System.getenv("CHROMIUM_PATH");
        if (fromEnvironment != null && !fromEnvironment.isBlank() && Files.isRegularFile(Path.of(fromEnvironment.trim()))) {
            return Optional.of(Path.of(fromEnvironment.trim()));
        }
        return COMMON_PLACES.stream().map(Path::of).filter(Files::isRegularFile).findFirst();
    }
}
