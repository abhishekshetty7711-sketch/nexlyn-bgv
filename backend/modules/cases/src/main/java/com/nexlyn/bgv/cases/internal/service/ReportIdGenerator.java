package com.nexlyn.bgv.cases.internal.service;

import com.nexlyn.bgv.cases.internal.repository.CaseRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Report IDs of the form {@code NX-YYYY-NNNN} (CLAUDE.md {@literal §10}): a counter per year, taken
 * in one atomic database statement so two admins creating cases at the same moment never get the
 * same number. IDs can be edited by hand, so a generated ID that happens to be taken is skipped.
 * The year follows Indian time, where the business runs.
 */
@Component
public class ReportIdGenerator {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    private final JdbcTemplate jdbc;
    private final CaseRepository cases;
    private final Clock clock;

    public ReportIdGenerator(JdbcTemplate jdbc, CaseRepository cases, Clock clock) {
        this.jdbc = jdbc;
        this.cases = cases;
        this.clock = clock;
    }

    /** Takes the next free number for the current year. Call inside the transaction that saves the case. */
    public String next() {
        int year = ZonedDateTime.now(clock.withZone(BUSINESS_ZONE)).getYear();
        while (true) {
            Integer number = jdbc.queryForObject(
                    "INSERT INTO cases.report_id_sequences (year, next_value) VALUES (?, 2)"
                            + " ON CONFLICT (year) DO UPDATE SET next_value = report_id_sequences.next_value + 1"
                            + " RETURNING next_value - 1",
                    Integer.class, year);
            String candidate = "NX-%d-%04d".formatted(year, number);
            if (!cases.existsByReportIdIgnoreCase(candidate)) {
                return candidate;
            }
        }
    }
}
