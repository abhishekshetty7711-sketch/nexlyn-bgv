package com.nexlyn.bgv.reports.internal.render;

import java.util.Map;

/**
 * The little SVG pictures of the report: summary-card icons (one per group of checks) and the status
 * badge icons. They are drawn as SVG, not as text symbols, so they print the same on every machine
 * (a font without the symbol would print an empty box).
 */
public final class Icons {

    private static final String OPEN = "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"#059669\" stroke-width=\"1.8\" stroke-linecap=\"round\" stroke-linejoin=\"round\">";
    private static final String CLOSE = "</svg>";

    /** The card icons of the reference tool for identity, court and address, plus one for every other check type. */
    private static final Map<String, String> CARD = Map.ofEntries(
            Map.entry("identity", OPEN + "<rect x=\"3\" y=\"4\" width=\"18\" height=\"16\" rx=\"2\"/><circle cx=\"9\" cy=\"10\" r=\"2.5\"/><line x1=\"14\" y1=\"9\" x2=\"19\" y2=\"9\"/><line x1=\"14\" y1=\"13\" x2=\"19\" y2=\"13\"/><line x1=\"7\" y1=\"17\" x2=\"17\" y2=\"17\"/>" + CLOSE),
            Map.entry("court", OPEN + "<path d=\"M3 22h18\"/><path d=\"M6 18V10\"/><path d=\"M10 18V10\"/><path d=\"M14 18V10\"/><path d=\"M18 18V10\"/><path d=\"M3 10l9-7 9 7\"/>" + CLOSE),
            Map.entry("address", OPEN + "<path d=\"M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z\"/><circle cx=\"12\" cy=\"10\" r=\"3\"/>" + CLOSE),
            Map.entry("employment", OPEN + "<rect x=\"2\" y=\"7\" width=\"20\" height=\"14\" rx=\"2\"/><path d=\"M8 7V5a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2M2 14h20\"/>" + CLOSE),
            Map.entry("education", OPEN + "<path d=\"M22 10v6M2 10l10-5 10 5-10 5z\"/><path d=\"M6 12v5c3 3 9 3 12 0v-5\"/>" + CLOSE),
            Map.entry("REFERENCE", OPEN + "<path d=\"M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2\"/><circle cx=\"9\" cy=\"7\" r=\"4\"/><path d=\"M23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75\"/>" + CLOSE),
            Map.entry("POLICE", OPEN + "<path d=\"M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z\"/><path d=\"M9 12l2 2 4-4\"/>" + CLOSE),
            Map.entry("UAN", OPEN + "<rect x=\"2\" y=\"3\" width=\"20\" height=\"14\" rx=\"2\"/><line x1=\"8\" y1=\"21\" x2=\"16\" y2=\"21\"/><line x1=\"12\" y1=\"17\" x2=\"12\" y2=\"21\"/><path d=\"M7 8h2M7 12h6M11 8h6\"/>" + CLOSE),
            Map.entry("CREDIT", OPEN + "<rect x=\"2\" y=\"5\" width=\"20\" height=\"14\" rx=\"2\"/><line x1=\"2\" y1=\"10\" x2=\"22\" y2=\"10\"/><line x1=\"6\" y1=\"15\" x2=\"10\" y2=\"15\"/>" + CLOSE),
            Map.entry("DRUG_TEST", OPEN + "<path d=\"M9 3h6\"/><path d=\"M10 3v6L4.5 19a2 2 0 0 0 1.7 3h11.6a2 2 0 0 0 1.7-3L14 9V3\"/><line x1=\"7\" y1=\"15\" x2=\"17\" y2=\"15\"/>" + CLOSE),
            Map.entry("DIRECTORSHIP", OPEN + "<rect x=\"4\" y=\"2\" width=\"16\" height=\"20\" rx=\"2\"/><path d=\"M9 22v-4h6v4\"/><path d=\"M8 6h.01M12 6h.01M16 6h.01M8 10h.01M12 10h.01M16 10h.01M8 14h.01M12 14h.01M16 14h.01\"/>" + CLOSE),
            Map.entry("GAP_REVIEW", OPEN + "<rect x=\"3\" y=\"4\" width=\"18\" height=\"18\" rx=\"2\"/><line x1=\"16\" y1=\"2\" x2=\"16\" y2=\"6\"/><line x1=\"8\" y1=\"2\" x2=\"8\" y2=\"6\"/><line x1=\"3\" y1=\"10\" x2=\"21\" y2=\"10\"/><path d=\"M12 13v3l2 1\"/>" + CLOSE),
            Map.entry("WORLD_CHECK", OPEN + "<circle cx=\"12\" cy=\"12\" r=\"10\"/><line x1=\"2\" y1=\"12\" x2=\"22\" y2=\"12\"/><path d=\"M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z\"/>" + CLOSE),
            Map.entry("OIG", OPEN + "<path d=\"M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z\"/><line x1=\"12\" y1=\"8\" x2=\"12\" y2=\"12\"/><line x1=\"12\" y1=\"16\" x2=\"12.01\" y2=\"16\"/>" + CLOSE),
            Map.entry("ADVERSE_MEDIA", OPEN + "<path d=\"M4 22h16a2 2 0 0 0 2-2V4a2 2 0 0 0-2-2H8a2 2 0 0 0-2 2v16a2 2 0 0 1-2 2zm0 0a2 2 0 0 1-2-2v-9c0-1.1.9-2 2-2h2\"/><path d=\"M18 14h-8M15 18h-5M10 6h8v4h-8z\"/>" + CLOSE),
            Map.entry("SOCIAL_MEDIA", OPEN + "<circle cx=\"12\" cy=\"12\" r=\"4\"/><path d=\"M16 8v5a3 3 0 0 0 6 0v-1a10 10 0 1 0-3.92 7.94\"/>" + CLOSE),
            Map.entry("RESUME_REVIEW", OPEN + "<path d=\"M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z\"/><polyline points=\"14 2 14 8 20 8\"/><path d=\"M16 13H8M16 17H8M10 9H8\"/>" + CLOSE));

    private static final String FALLBACK = CARD.get("identity");

    /** Badge icons: small, drawn in the badge's own text colour (currentColor). */
    private static final String BADGE = "<svg width=\"11\" height=\"11\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"3\" stroke-linecap=\"round\" stroke-linejoin=\"round\">";
    private static final Map<String, String> STATUS = Map.of(
            "check", BADGE + "<polyline points=\"20 6 9 17 4 12\"/></svg>",
            "cross", BADGE + "<line x1=\"18\" y1=\"6\" x2=\"6\" y2=\"18\"/><line x1=\"6\" y1=\"6\" x2=\"18\" y2=\"18\"/></svg>",
            "info", BADGE + "<circle cx=\"12\" cy=\"12\" r=\"10\"/><line x1=\"12\" y1=\"8\" x2=\"12\" y2=\"12\"/><line x1=\"12\" y1=\"16\" x2=\"12.01\" y2=\"16\"/></svg>",
            "dash", BADGE + "<line x1=\"5\" y1=\"12\" x2=\"19\" y2=\"12\"/></svg>",
            "clock", BADGE + "<circle cx=\"12\" cy=\"12\" r=\"10\"/><polyline points=\"12 6 12 12 16 14\"/></svg>",
            "refresh", BADGE + "<polyline points=\"23 4 23 10 17 10\"/><path d=\"M20.49 15a9 9 0 1 1-2.12-9.36L23 10\"/></svg>");

    private Icons() {
    }

    /** The card icon of a group of checks (unknown groups get the identity icon). */
    public static String card(String groupKey) {
        return CARD.getOrDefault(groupKey, FALLBACK);
    }

    public static String status(String key) {
        return STATUS.getOrDefault(key, STATUS.get("dash"));
    }
}
