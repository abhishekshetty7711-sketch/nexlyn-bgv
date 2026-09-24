package com.nexlyn.bgv.reports.internal.render;

import java.util.List;

/**
 * The eight service cards of the last page, worded exactly as in the reference tool (CLAUDE.md section 17
 * item 5: ported as-is until told otherwise). {@code svg} holds the shapes drawn inside a 24x24 icon.
 */
public final class ServicesCatalog {

    public record Service(String title, String text, String svg) {
    }

    public static final List<Service> SERVICES = List.of(
            new Service("IDENTITY VERIFICATION",
                    "Aadhaar Card and PAN verification through authorized government databases for genuine identity confirmation.",
                    "<rect x=\"3\" y=\"4\" width=\"18\" height=\"16\" rx=\"2\"/><circle cx=\"9\" cy=\"10\" r=\"2\"/><path d=\"M15 8h3M15 12h3M5 18s1-2 4-2 4 2 4 2\"/>"),
            new Service("COURT RECORD",
                    "Civil and criminal case search across district courts, high courts, and tribunals nationwide.",
                    "<path d=\"M3 21h18M5 21V9l7-5 7 5v12M9 12h6M9 16h6\"/>"),
            new Service("ADDRESS VERIFICATION",
                    "Physical address validation through door-to-door visits with photo evidence and neighborhood inquiry.",
                    "<path d=\"M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z\"/><circle cx=\"12\" cy=\"10\" r=\"3\"/>"),
            new Service("EMPLOYMENT VERIFICATION",
                    "Past employer confirmation of tenure, designation, salary, and reason for leaving via HR validation.",
                    "<rect x=\"2\" y=\"7\" width=\"20\" height=\"14\" rx=\"2\"/><path d=\"M8 7V5a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2M2 14h20\"/>"),
            new Service("EDUCATION VERIFICATION",
                    "Academic credentials verified directly with universities and institutions to confirm authenticity.",
                    "<path d=\"M22 10v6M2 10l10-5 10 5-10 5z\"/><path d=\"M6 12v5c3 3 9 3 12 0v-5\"/>"),
            new Service("REFERENCE CHECK",
                    "Professional reference interviews with managers and peers for workplace performance and conduct insights.",
                    "<path d=\"M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2\"/><circle cx=\"9\" cy=\"7\" r=\"4\"/><path d=\"M23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75\"/>"),
            new Service("POLICE VERIFICATION",
                    "Criminal records check at local police stations and national crime database. Fully source-traced.",
                    "<path d=\"M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z\"/><path d=\"M9 12l2 2 4-4\"/>"),
            new Service("UAN / EPFO CHECK",
                    "Employment history via EPFO portal — exposes moonlighting, validates tenure officially. Turnaround 2–3 days.",
                    "<rect x=\"2\" y=\"3\" width=\"20\" height=\"14\" rx=\"2\"/><line x1=\"8\" y1=\"21\" x2=\"16\" y2=\"21\"/><line x1=\"12\" y1=\"17\" x2=\"12\" y2=\"21\"/><path d=\"M7 8h2M7 12h6M11 8h6\"/>"));

    private ServicesCatalog() {
    }
}
