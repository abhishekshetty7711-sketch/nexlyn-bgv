package com.nexlyn.bgv.reports.internal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Report settings ({@code nexlyn.reports.*}).
 *
 * @param chromiumPath          the Chrome / Chromium / Edge program to print with; blank = the browser Playwright installed
 * @param chromiumNoSandbox     start the browser with --no-sandbox (needed inside a Docker container that runs as root)
 * @param maxConcurrentRenders  reports rendered at the same time (a browser page each; default 2, CLAUDE.md section 13)
 * @param queueCapacity         reports waiting for a turn before new requests are refused (default 20)
 * @param maxImageSide          longest side in pixels a picture is scaled down to in the PDF (default 2400)
 */
@ConfigurationProperties(prefix = "nexlyn.reports")
public record ReportsProperties(String chromiumPath, Boolean chromiumNoSandbox, Integer maxConcurrentRenders, Integer queueCapacity, Integer maxImageSide) {

    public boolean noSandbox() {
        return Boolean.TRUE.equals(chromiumNoSandbox);
    }

    public int concurrentRenders() {
        return maxConcurrentRenders == null || maxConcurrentRenders < 1 ? 2 : maxConcurrentRenders;
    }

    public int queue() {
        return queueCapacity == null || queueCapacity < 1 ? 20 : queueCapacity;
    }

    public int imageSide() {
        return maxImageSide == null || maxImageSide < 600 ? 2400 : maxImageSide;
    }
}
