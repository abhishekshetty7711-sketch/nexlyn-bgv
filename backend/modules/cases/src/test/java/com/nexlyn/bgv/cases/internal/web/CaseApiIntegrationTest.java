package com.nexlyn.bgv.cases.internal.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexlyn.bgv.cases.CasesIntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

class CaseApiIntegrationTest extends CasesIntegrationTestBase {

    UUID acme;
    UUID analystA;
    UUID analystB;
    String tokenA;
    String tokenB;

    @BeforeEach
    void fixtures() throws Exception {
        acme = UUID.fromString(newClient("Acme Corp").get("id").asText());
        analystA = newAdmin("a@example.com", "Ann Analyst");
        analystB = newAdmin("b@example.com", "Bob Analyst");
        tokenA = tokenFor(analystA, "a@example.com", "CASE_CREATE", "CASE_READ_ASSIGNED", "CASE_UPDATE");
        tokenB = tokenFor(analystB, "b@example.com", "CASE_CREATE", "CASE_READ_ASSIGNED", "CASE_UPDATE");
    }

    // ---- helpers ------------------------------------------------------------------------------------

    /** A map that allows null values (Map.of does not). */
    private static Map<String, Object> map(Object... keyValues) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            result.put((String) keyValues[i], keyValues[i + 1]);
        }
        return result;
    }

    private JsonNode newClient(String name) throws Exception {
        MvcResult created = send(post("/api/clients"), superToken(), map("name", name, "displayName", name));
        assertThat(status(created)).isEqualTo(201);
        return body(created);
    }

    private JsonNode createCase(String token) throws Exception {
        MvcResult created = send(post("/api/cases"), token, map("clientId", acme.toString()));
        assertThat(status(created)).as(created.getResponse().getContentAsString()).isEqualTo(201);
        return body(created);
    }

    private JsonNode createCaseAsOwner() throws Exception {
        return createCase(superToken());
    }

    private String path(JsonNode c, String suffix) {
        return "/api/cases/" + c.get("id").asText() + suffix;
    }

    private long version(JsonNode c) {
        return c.get("version").asLong();
    }

    private MvcResult save(String token, JsonNode c, String section, Object... fields) throws Exception {
        Map<String, Object> request = map("version", version(c));
        request.putAll(map(fields));
        return send(put(path(c, "/" + section)), token, request);
    }

    private JsonNode saved(String token, JsonNode c, String section, Object... fields) throws Exception {
        MvcResult result = save(token, c, section, fields);
        assertThat(status(result)).as(section + ": " + result.getResponse().getContentAsString()).isEqualTo(200);
        return body(result);
    }

    private JsonNode reload(JsonNode c) throws Exception {
        return body(send(get(path(c, "")), superToken(), null));
    }

    // ---- creating cases ---------------------------------------------------------------------------------

    @Test
    void newCasesGetSequentialReportIdsAndSensibleDefaults() throws Exception {
        int year = LocalDate.now(ZoneId.of("Asia/Kolkata")).getYear();
        JsonNode first = createCase(tokenA);
        JsonNode second = createCase(tokenA);

        assertThat(first.get("reportId").asText()).isEqualTo("NX-%d-0001".formatted(year));
        assertThat(second.get("reportId").asText()).isEqualTo("NX-%d-0002".formatted(year));
        assertThat(first.get("lifecycle").asText()).isEqualTo("DRAFT");
        assertThat(first.get("editable").asBoolean()).isTrue();
        assertThat(first.get("client").get("name").asText()).isEqualTo("Acme Corp");
        assertThat(first.get("issueDate").asText()).isEqualTo(LocalDate.now(ZoneId.of("Asia/Kolkata")).toString());
        assertThat(first.get("candidate").get("country").asText()).isEqualTo("India");
        assertThat(first.get("candidate").get("parentType").asText()).isEqualTo("FATHER");
        assertThat(first.get("candidate").get("hasPhoto").asBoolean()).isFalse();
        assertThat(first.get("period").get("show").asBoolean()).isTrue();
        assertThat(first.get("overview").get("statusTitle").asText()).isEqualTo("Completed");
        assertThat(first.get("overview").get("effective").get("overallStatus").asText()).isEqualTo("Pending");
        assertThat(first.get("settings").get("layoutCards").asInt()).isEqualTo(4);
        assertThat(first.get("settings").get("dateFormat").asText()).isEqualTo("NUMERIC");
        assertThat(first.get("settings").get("watermarkEnabled").asBoolean()).isFalse();
        assertThat(first.get("settings").get("watermarkText").asText()).isEqualTo("NEXLYN VERIFIED");
        assertThat(first.get("savedSections").has("report-info")).isTrue();
        assertThat(first.get("savedSections").has("candidate")).isFalse();
        // The creator is the preparer, so they can see their own case.
        assertThat(first.get("assignments")).hasSize(1);
        assertThat(first.get("assignments").get(0).get("role").asText()).isEqualTo("PREPARER");
        assertThat(first.get("assignments").get(0).get("fullName").asText()).isEqualTo("Ann Analyst");
    }

    @Test
    void creatingNeedsThePermissionAndAnActiveClient() throws Exception {
        assertThat(status(send(post("/api/cases"), null, map("clientId", acme.toString())))).isEqualTo(401);
        assertThat(status(send(post("/api/cases"), tokenFor(analystA, "a@example.com", "CASE_READ_ALL"),
                map("clientId", acme.toString())))).isEqualTo(403);
        assertThat(status(send(post("/api/cases"), tokenA, map("clientId", UUID.randomUUID().toString())))).isEqualTo(400);
        assertThat(status(send(post("/api/cases"), tokenA, map()))).isEqualTo(400);

        JsonNode idle = newClient("Idle Ltd");
        send(put("/api/clients/" + idle.get("id").asText()), superToken(),
                map("version", idle.get("version").asLong(), "name", "Idle Ltd", "displayName", "Idle Ltd", "active", false));
        assertThat(status(send(post("/api/cases"), tokenA, map("clientId", idle.get("id").asText())))).isEqualTo(400);
    }

    @Test
    void creatingIsAuditedWithTheCaseId() throws Exception {
        JsonNode c = createCase(tokenA);
        List<String> actions = jdbc.queryForList("SELECT action FROM auth.audit_log WHERE case_id = ?::uuid",
                String.class, c.get("id").asText());
        assertThat(actions).contains("CASE_CREATED");
    }

    @Test
    void reportIdsStayUniqueWhenManyAdminsCreateCasesAtTheSameMoment() throws Exception {
        int count = 12;
        String token = superToken();
        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Callable<String> task = () -> {
                    MvcResult created = send(post("/api/cases"), token, map("clientId", acme.toString()));
                    assertThat(status(created)).isEqualTo(201);
                    return body(created).get("reportId").asText();
                };
                results.add(pool.submit(task));
            }
            Set<String> ids = new HashSet<>();
            for (Future<String> result : results) {
                ids.add(result.get());
            }
            assertThat(ids).hasSize(count).allMatch(id -> id.matches("NX-\\d{4}-\\d{4}"));
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void aGeneratedIdThatIsAlreadyTakenIsSkipped() throws Exception {
        JsonNode first = createCaseAsOwner();
        int year = LocalDate.now(ZoneId.of("Asia/Kolkata")).getYear();
        // Someone renames the first case to the ID the counter would produce next.
        saved(superToken(), first, "report-info", "reportId", "NX-%d-0002".formatted(year),
                "issueDate", "2026-01-01", "clientId", acme.toString());
        JsonNode second = createCaseAsOwner();
        assertThat(second.get("reportId").asText()).isEqualTo("NX-%d-0003".formatted(year));
    }

    // ---- who can see what ---------------------------------------------------------------------------------

    @Test
    void anAnalystOnlySeesAndTouchesTheirOwnCases() throws Exception {
        JsonNode mine = createCase(tokenA);
        JsonNode theirs = createCase(tokenB);

        JsonNode listA = body(send(get("/api/cases"), tokenA, null));
        assertThat(listA.get("total").asInt()).isEqualTo(1);
        assertThat(listA.get("items").get(0).get("reportId").asText()).isEqualTo(mine.get("reportId").asText());

        assertThat(status(send(get(path(mine, "")), tokenA, null))).isEqualTo(200);
        assertThat(status(send(get(path(theirs, "")), tokenA, null))).as("someone else's case").isEqualTo(403);
        assertThat(status(send(get("/api/cases/" + UUID.randomUUID()), tokenA, null))).as("a guessed id").isEqualTo(403);
        assertThat(status(send(put(path(theirs, "/remarks")), tokenA, map("version", version(theirs), "analystRemarks", "x")))).isEqualTo(403);
        assertThat(status(send(get(path(theirs, "/progress")), tokenA, null))).isEqualTo(403);
        assertThat(status(send(get(path(theirs, "/validation")), tokenA, null))).isEqualTo(403);
        assertThat(status(send(delete(path(theirs, "")), tokenA, null))).isEqualTo(403);
    }

    @Test
    void anAdminWhoCanReadAllSeesEveryCaseButStillNeedsPermissionToWrite() throws Exception {
        JsonNode c = createCase(tokenA);
        createCase(tokenB);
        String auditor = tokenFor(ownerId, OWNER, "CASE_READ_ALL");

        assertThat(body(send(get("/api/cases"), auditor, null)).get("total").asInt()).isEqualTo(2);
        assertThat(status(send(get(path(c, "")), auditor, null))).isEqualTo(200);
        assertThat(status(send(put(path(c, "/remarks")), auditor, map("version", version(c), "analystRemarks", "x")))).isEqualTo(403);
        assertThat(status(send(get("/api/cases/" + UUID.randomUUID()), auditor, null))).as("no such case").isEqualTo(404);
    }

    @Test
    void everyEndpointRefusesAnonymousCallers() throws Exception {
        JsonNode c = createCaseAsOwner();
        for (String path : List.of("/api/cases", path(c, ""), path(c, "/progress"), path(c, "/validation"), "/api/assignable-admins")) {
            assertThat(status(send(get(path), null, null))).as(path).isEqualTo(401);
        }
        assertThat(status(send(put(path(c, "/remarks")), null, map("version", 0)))).isEqualTo(401);
    }

    // ---- section 1: report info ---------------------------------------------------------------------------

    @Test
    void reportInfoCanBeChangedAndTheVersionMoves() throws Exception {
        JsonNode c = createCaseAsOwner();
        JsonNode other = newClient("Beta Ltd");

        JsonNode updated = saved(superToken(), c, "report-info", "reportId", "  ACME/2026-77 ", "issueDate", "2026-03-15",
                "clientId", other.get("id").asText(), "companyDisplayName", "Beta\nHoldings", "dueDate", "2026-04-01");

        assertThat(updated.get("reportId").asText()).isEqualTo("ACME/2026-77");
        assertThat(updated.get("issueDate").asText()).isEqualTo("2026-03-15");
        assertThat(updated.get("client").get("name").asText()).isEqualTo("Beta Ltd");
        assertThat(updated.get("companyDisplayName").asText()).isEqualTo("Beta\nHoldings");
        assertThat(updated.get("dueDate").asText()).isEqualTo("2026-04-01");
        assertThat(version(updated)).isGreaterThan(version(c));

        JsonNode cleared = saved(superToken(), updated, "report-info", "reportId", "ACME/2026-77", "issueDate", "2026-03-15",
                "clientId", other.get("id").asText(), "companyDisplayName", "  ");
        assertThat(cleared.get("companyDisplayName").isNull()).as("a blank override falls back to the client's name").isTrue();
    }

    @Test
    void reportIdsMustBeWellFormedAndUnique() throws Exception {
        JsonNode first = createCaseAsOwner();
        JsonNode second = createCaseAsOwner();
        String su = superToken();

        for (String bad : new String[]{"", "ab", "has space", "semi;colon", "x".repeat(31), "-leading"}) {
            assertThat(status(save(su, second, "report-info", "reportId", bad, "issueDate", "2026-01-01", "clientId", acme.toString())))
                    .as("'%s'", bad).isEqualTo(400);
        }
        MvcResult clash = save(su, second, "report-info", "reportId", first.get("reportId").asText().toLowerCase(),
                "issueDate", "2026-01-01", "clientId", acme.toString());
        assertThat(status(clash)).as("same ID in another case, ignoring case").isEqualTo(409);
        assertThat(status(save(su, second, "report-info", "reportId", second.get("reportId").asText(),
                "issueDate", "2026-01-01", "clientId", acme.toString()))).as("keeping your own ID is fine").isEqualTo(200);
    }

    @Test
    void reportInfoNeedsAnIssueDateAndAnExistingClient() throws Exception {
        JsonNode c = createCaseAsOwner();
        assertThat(status(save(superToken(), c, "report-info", "reportId", "NX-9-0001", "issueDate", null, "clientId", acme.toString()))).isEqualTo(400);
        assertThat(status(save(superToken(), c, "report-info", "reportId", "NX-9-0001", "issueDate", "2026-01-01", "clientId", UUID.randomUUID().toString()))).isEqualTo(400);
        assertThat(status(save(superToken(), c, "report-info", "reportId", "NX-9-0001", "issueDate", "2026-01-01", "clientId", null))).isEqualTo(400);
    }

    // ---- section 2: candidate ------------------------------------------------------------------------------

    @Test
    void candidateDetailsAreCleanedAndPhonesAreNormalised() throws Exception {
        JsonNode c = createCaseAsOwner();
        JsonNode updated = saved(superToken(), c, "candidate", "fullName", "  Asha Rao ", "parentType", "GUARDIAN",
                "parentName", "Ravi Rao", "employeeId", "EMP-100", "dob", "1994-05-17", "phone", "98765 43210",
                "street", "12 MG Road", "city", "Bengaluru", "state", "Karnataka", "pin", "560001", "country", "");

        JsonNode candidate = updated.get("candidate");
        assertThat(candidate.get("fullName").asText()).isEqualTo("Asha Rao");
        assertThat(candidate.get("parentType").asText()).isEqualTo("GUARDIAN");
        assertThat(candidate.get("phone").asText()).isEqualTo("+919876543210");
        assertThat(candidate.get("phoneDisplay").asText()).isEqualTo("+91 98765 43210");
        assertThat(candidate.get("dob").asText()).isEqualTo("1994-05-17");
        assertThat(candidate.get("country").asText()).as("blank falls back to India").isEqualTo("India");
        assertThat(updated.get("savedSections").has("candidate")).isTrue();

        JsonNode cleared = saved(superToken(), updated, "candidate", "fullName", "Asha Rao", "phone", "", "parentName", "   ");
        assertThat(cleared.get("candidate").get("phone").isNull()).isTrue();
        assertThat(cleared.get("candidate").get("parentName").isNull()).isTrue();
    }

    @Test
    void badCandidateValuesAreRefused() throws Exception {
        JsonNode c = createCaseAsOwner();
        String su = superToken();
        assertThat(status(save(su, c, "candidate", "fullName", "A", "phone", "12345"))).as("phone").isEqualTo(400);
        assertThat(status(save(su, c, "candidate", "fullName", "A", "pin", "060001"))).as("pin").isEqualTo(400);
        assertThat(status(save(su, c, "candidate", "fullName", "A", "pin", "56"))).as("short pin").isEqualTo(400);
        assertThat(status(save(su, c, "candidate", "fullName", "A", "dob", LocalDate.now().plusDays(1).toString()))).as("future dob").isEqualTo(400);
        assertThat(status(save(su, c, "candidate", "fullName", "A", "dob", "1850-01-01"))).as("ancient dob").isEqualTo(400);
        assertThat(status(save(su, c, "candidate", "fullName", "x".repeat(201)))).as("too long").isEqualTo(400);
        assertThat(status(save(su, c, "candidate", "fullName", "A", "parentType", "UNCLE"))).as("unknown parent type").isEqualTo(400);
    }

    // ---- sections 3, 5, 6, 7 ---------------------------------------------------------------------------------

    @Test
    void theVerificationPeriodMustRunForwardsButMayBeHidden() throws Exception {
        JsonNode c = createCaseAsOwner();
        assertThat(status(save(superToken(), c, "verification-period", "show", true, "start", "2026-05-10", "end", "2026-05-01"))).isEqualTo(400);

        JsonNode ok = saved(superToken(), c, "verification-period", "show", false, "start", "2026-05-01", "end", "2026-05-10");
        assertThat(ok.get("period").get("show").asBoolean()).isFalse();
        assertThat(ok.get("period").get("start").asText()).as("dates are kept even when hidden").isEqualTo("2026-05-01");
        assertThat(saved(superToken(), ok, "verification-period", "show", true).get("period").get("end").isNull()).isTrue();
    }

    @Test
    void theStatusPillUsesPresetTextUnlessEditedAndOverridesReplaceAutomaticNumbers() throws Exception {
        JsonNode c = createCaseAsOwner();
        JsonNode preset = saved(superToken(), c, "overview", "statusPreset", "UNABLE");
        assertThat(preset.get("overview").get("statusTitle").asText()).isEqualTo("Unable to Verify");
        assertThat(preset.get("overview").get("statusSubtitle").asText()).isEqualTo("Unable to Complete Verification");

        JsonNode custom = saved(superToken(), preset, "overview", "statusPreset", "DISCREPANCY", "statusTitle", "Needs attention",
                "totalOverride", 9, "completedOverride", 7, "overallStatusOverride", "Partly complete");
        JsonNode overview = custom.get("overview");
        assertThat(overview.get("statusTitle").asText()).isEqualTo("Needs attention");
        assertThat(overview.get("statusSubtitle").asText()).isEqualTo("Discrepancy Found in Verification");
        assertThat(overview.get("auto").get("total").asInt()).isZero();
        assertThat(overview.get("effective").get("total").asInt()).isEqualTo(9);
        assertThat(overview.get("effective").get("completed").asInt()).isEqualTo(7);
        assertThat(overview.get("effective").get("overallStatus").asText()).isEqualTo("Partly complete");

        JsonNode back = saved(superToken(), custom, "overview", "statusPreset", "CLOSED");
        assertThat(back.get("overview").get("effective").get("overallStatus").asText()).as("cleared override falls back").isEqualTo("Pending");

        assertThat(status(save(superToken(), back, "overview", "statusPreset", "COMPLETED", "totalOverride", -1))).isEqualTo(400);
        assertThat(status(save(superToken(), back, "overview", "statusPreset", null))).isEqualTo(400);
        assertThat(status(save(superToken(), back, "overview", "statusPreset", "SHINY"))).isEqualTo(400);
    }

    @Test
    void remarksKeepBoldTextAndNeutraliseEverythingElse() throws Exception {
        JsonNode c = createCaseAsOwner();
        JsonNode updated = saved(superToken(), c, "remarks",
                "analystRemarks", "All <B>clear</B>. <script>alert(1)</script> Tom & Jerry",
                "finalRecommendation", "<img src=x onerror=alert(1)><strong>Proceed</strong>");

        String remarks = updated.get("remarks").get("analystRemarks").asText();
        assertThat(remarks).isEqualTo("All <strong>clear</strong>. &lt;script&gt;alert(1)&lt;/script&gt; Tom &amp; Jerry");
        assertThat(updated.get("remarks").get("finalRecommendation").asText()).doesNotContain("<img").contains("<strong>Proceed</strong>");

        JsonNode again = saved(superToken(), updated, "remarks", "analystRemarks", remarks, "finalRecommendation", null);
        assertThat(again.get("remarks").get("analystRemarks").asText()).as("saving twice changes nothing").isEqualTo(remarks);
        assertThat(again.get("remarks").get("finalRecommendation").isNull()).isTrue();
        assertThat(status(save(superToken(), again, "remarks", "analystRemarks", "x".repeat(20001)))).isEqualTo(400);
    }

    @Test
    void reportSettingsAreValidated() throws Exception {
        JsonNode c = createCaseAsOwner();
        String su = superToken();
        assertThat(status(save(su, c, "settings", "layoutCards", 5, "dateFormat", "NUMERIC", "watermarkEnabled", false))).isEqualTo(400);
        assertThat(status(save(su, c, "settings", "layoutCards", 4, "dateFormat", "FANCY", "watermarkEnabled", false))).isEqualTo(400);
        assertThat(status(save(su, c, "settings", "layoutCards", 4, "dateFormat", "NUMERIC", "watermarkEnabled", true, "watermarkText", " "))).isEqualTo(400);
        assertThat(status(save(su, c, "settings", "layoutCards", 4, "dateFormat", "NUMERIC", "watermarkEnabled", true, "watermarkText", "x".repeat(41)))).isEqualTo(400);

        JsonNode ok = saved(su, c, "settings", "layoutCards", 6, "dateFormat", "TEXT", "watermarkEnabled", true, "watermarkText", "CONFIDENTIAL");
        assertThat(ok.get("settings").get("layoutCards").asInt()).isEqualTo(6);
        assertThat(ok.get("settings").get("dateFormat").asText()).isEqualTo("TEXT");
        assertThat(ok.get("settings").get("watermarkText").asText()).isEqualTo("CONFIDENTIAL");

        JsonNode off = saved(su, ok, "settings", "layoutCards", 6, "dateFormat", "TEXT", "watermarkEnabled", false);
        assertThat(off.get("settings").get("watermarkEnabled").asBoolean()).isFalse();
        assertThat(off.get("settings").get("watermarkText").asText()).as("the text is remembered").isEqualTo("CONFIDENTIAL");
    }

    // ---- locking ----------------------------------------------------------------------------------------------

    @Test
    void everySaveNeedsTheLatestVersionSoNobodyOverwritesAColleague() throws Exception {
        JsonNode c = createCaseAsOwner();
        JsonNode first = saved(superToken(), c, "remarks", "analystRemarks", "first");

        MvcResult stale = save(superToken(), c, "remarks", "analystRemarks", "stale edit"); // still holds the old version
        assertThat(status(stale)).isEqualTo(409);
        assertThat(code(stale)).isEqualTo("CONFLICT");
        assertThat(reload(c).get("remarks").get("analystRemarks").asText()).isEqualTo("first");

        assertThat(status(send(put(path(c, "/remarks")), superToken(), map("analystRemarks", "no version")))).isEqualTo(400);
        assertThat(status(save(superToken(), first, "remarks", "analystRemarks", "second"))).isEqualTo(200);
    }

    @Test
    void savingDifferentSectionsAlsoMovesTheVersion() throws Exception {
        JsonNode c = createCaseAsOwner();
        JsonNode afterCandidate = saved(superToken(), c, "candidate", "fullName", "A");
        assertThat(status(save(superToken(), c, "settings", "layoutCards", 4, "dateFormat", "NUMERIC", "watermarkEnabled", false)))
                .as("a colleague saved the candidate meanwhile").isEqualTo(409);
        assertThat(status(save(superToken(), afterCandidate, "settings", "layoutCards", 4, "dateFormat", "NUMERIC", "watermarkEnabled", false))).isEqualTo(200);
    }

    @Test
    void aCaseInReviewOrLaterCannotBeEditedButCanStillBeRead() throws Exception {
        JsonNode c = createCaseAsOwner();
        for (String state : List.of("IN_REVIEW", "APPROVED", "FINALIZED")) {
            jdbc.update("UPDATE cases.cases SET lifecycle = ? WHERE id = ?::uuid", state, c.get("id").asText());
            JsonNode current = reload(c);
            assertThat(current.get("editable").asBoolean()).as(state).isFalse();
            MvcResult refused = save(superToken(), current, "remarks", "analystRemarks", "edit");
            assertThat(status(refused)).as(state).isEqualTo(409);
            assertThat(refused.getResponse().getContentAsString()).contains("locked");
        }
        jdbc.update("UPDATE cases.cases SET lifecycle = 'CHANGES_REQUESTED' WHERE id = ?::uuid", c.get("id").asText());
        assertThat(status(save(superToken(), reload(c), "remarks", "analystRemarks", "edit"))).as("sent back to the preparer").isEqualTo(200);
    }

    // ---- progress and validation --------------------------------------------------------------------------------

    private Map<String, JsonNode> sectionsOf(JsonNode progress) {
        Map<String, JsonNode> byKey = new LinkedHashMap<>();
        progress.get("sections").forEach(s -> byKey.put(s.get("key").asText(), s));
        return byKey;
    }

    @Test
    void progressShowsWhichSectionsAreSavedAndWhichNeedAttention() throws Exception {
        JsonNode c = createCaseAsOwner();
        JsonNode fresh = body(send(get(path(c, "/progress")), superToken(), null));
        Map<String, JsonNode> sections = sectionsOf(fresh);

        assertThat(sections.keySet()).containsExactly("report-info", "candidate", "verification-period", "checks",
                "overview", "remarks", "settings", "generate");
        assertThat(sections.get("report-info").get("state").asText()).isEqualTo("SAVED");
        assertThat(sections.get("candidate").get("state").asText()).isEqualTo("NOT_STARTED");
        assertThat(sections.get("checks").get("state").asText()).isEqualTo("NOT_STARTED");
        assertThat(fresh.get("percent").asInt()).isEqualTo(14); // 1 of 7
        assertThat(fresh.get("totalChecks").asInt()).isZero();
        assertThat(fresh.get("checksByStatus").get("VERIFIED").asInt()).isZero();

        JsonNode withCandidate = saved(superToken(), c, "candidate", "fullName", "Asha Rao");
        Map<String, JsonNode> after = sectionsOf(body(send(get(path(withCandidate, "/progress")), superToken(), null)));
        assertThat(after.get("candidate").get("state").asText()).as("saved but the employee id is still missing").isEqualTo("WARNING");
        assertThat(after.get("candidate").get("issues").asInt()).isGreaterThanOrEqualTo(2);

        JsonNode complete = saved(superToken(), withCandidate, "candidate", "fullName", "Asha Rao", "employeeId", "E1",
                "parentName", "R", "dob", "1990-01-01", "phone", "9876543210");
        JsonNode progress = body(send(get(path(complete, "/progress")), superToken(), null));
        assertThat(sectionsOf(progress).get("candidate").get("issues").asInt()).as("only the missing photo remains").isEqualTo(1);
        assertThat(progress.get("percent").asInt()).isEqualTo(29); // 2 of 7
    }

    @Test
    void validationSeparatesBlockingErrorsFromWarnings() throws Exception {
        JsonNode c = createCaseAsOwner();
        JsonNode result = body(send(get(path(c, "/validation")), superToken(), null));

        List<String> errors = new ArrayList<>();
        result.get("errors").forEach(e -> errors.add(e.get("section").asText() + "/" + e.get("field").asText()));
        assertThat(errors).containsExactlyInAnyOrder("candidate/fullName", "candidate/employeeId", "checks/checks");

        List<String> warnings = new ArrayList<>();
        result.get("warnings").forEach(w -> warnings.add(w.get("section").asText() + "/" + w.get("field").asText()));
        assertThat(warnings).containsExactlyInAnyOrder("candidate/parentName", "candidate/dob", "candidate/phone",
                "candidate/photo", "verification-period/start", "verification-period/end", "remarks/analystRemarks",
                "remarks/finalRecommendation");
        assertThat(result.get("warnings").toString()).contains("Father's name is missing");
    }

    @Test
    void validationFollowsWhatHasBeenFilledIn() throws Exception {
        JsonNode c = createCaseAsOwner();
        JsonNode step1 = saved(superToken(), c, "candidate", "fullName", "Asha", "employeeId", "E1", "parentType", "GUARDIAN",
                "dob", "1990-01-01", "phone", "9876543210");
        JsonNode step2 = saved(superToken(), step1, "verification-period", "show", true, "start", "2026-01-01", "end", "2026-02-01");
        JsonNode step3 = saved(superToken(), step2, "remarks", "analystRemarks", "ok", "finalRecommendation", "proceed");

        JsonNode result = body(send(get(path(step3, "/validation")), superToken(), null));
        List<String> errors = new ArrayList<>();
        result.get("errors").forEach(e -> errors.add(e.get("field").asText()));
        assertThat(errors).as("only the checks (Phase 4) are missing").containsExactly("checks");
        List<String> warnings = new ArrayList<>();
        result.get("warnings").forEach(w -> warnings.add(w.get("field").asText()));
        assertThat(warnings).containsExactlyInAnyOrder("parentName", "photo");
        assertThat(result.get("warnings").toString()).contains("Guardian's name is missing");

        JsonNode hidden = saved(superToken(), step3, "verification-period", "show", false);
        assertThat(body(send(get(path(hidden, "/validation")), superToken(), null)).get("warnings").toString())
                .as("a hidden period needs no dates").doesNotContain("period");
    }

    // ---- assignments ------------------------------------------------------------------------------------------------

    @Test
    void assigningNeedsThePermissionAndGrantsAccess() throws Exception {
        JsonNode c = createCase(tokenA);
        assertThat(status(send(get(path(c, "")), tokenB, null))).isEqualTo(403);
        assertThat(status(send(post(path(c, "/assignments")), tokenA, map("adminId", analystB.toString(), "role", "REVIEWER"))))
                .as("an analyst cannot assign").isEqualTo(403);

        MvcResult assigned = send(post(path(c, "/assignments")), superToken(), map("adminId", analystB.toString(), "role", "REVIEWER"));
        assertThat(status(assigned)).isEqualTo(200);
        JsonNode people = body(assigned).get("assignments");
        assertThat(people).hasSize(2);
        assertThat(people.get(1).get("fullName").asText()).isEqualTo("Bob Analyst");
        assertThat(people.get(1).get("role").asText()).isEqualTo("REVIEWER");

        assertThat(status(send(get(path(c, "")), tokenB, null))).as("the reviewer can now open it").isEqualTo(200);
        assertThat(body(send(get("/api/cases"), tokenB, null)).get("total").asInt()).isEqualTo(1);
        assertThat(status(send(post(path(c, "/assignments")), superToken(), map("adminId", analystB.toString(), "role", "REVIEWER")))).as("idempotent").isEqualTo(200);
        assertThat(body(send(get(path(c, "")), superToken(), null)).get("assignments")).hasSize(2);

        MvcResult removed = send(delete(path(c, "/assignments/" + analystB)), superToken(), null);
        assertThat(status(removed)).isEqualTo(200);
        assertThat(body(removed).get("assignments")).hasSize(1);
        assertThat(status(send(get(path(c, "")), tokenB, null))).as("access ends with the assignment").isEqualTo(403);
        assertThat(auditActionsSinceStart()).contains("CASE_ASSIGNED", "CASE_UNASSIGNED");
    }

    @Test
    void nobodyCanBePreparerAndReviewerOfTheSameCase() throws Exception {
        JsonNode c = createCase(tokenA); // Ann is the preparer
        MvcResult both = send(post(path(c, "/assignments")), superToken(), map("adminId", analystA.toString(), "role", "REVIEWER"));
        assertThat(status(both)).isEqualTo(409);
        assertThat(both.getResponse().getContentAsString()).contains("preparer and reviewer");
    }

    @Test
    void onlyActiveAdminsCanBeAssigned() throws Exception {
        JsonNode c = createCaseAsOwner();
        assertThat(status(send(post(path(c, "/assignments")), superToken(), map("adminId", UUID.randomUUID().toString(), "role", "REVIEWER")))).isEqualTo(400);
        jdbc.update("UPDATE auth.admins SET status = 'DISABLED' WHERE id = ?", analystB);
        assertThat(status(send(post(path(c, "/assignments")), superToken(), map("adminId", analystB.toString(), "role", "REVIEWER")))).isEqualTo(400);
        assertThat(status(send(post(path(c, "/assignments")), superToken(), map("adminId", analystA.toString())))).as("role missing").isEqualTo(400);

        JsonNode assignable = body(send(get("/api/assignable-admins"), superToken(), null));
        assertThat(assignable).extracting(n -> n.get("email").asText()).contains("a@example.com").doesNotContain("b@example.com");
        assertThat(status(send(get("/api/assignable-admins"), tokenA, null))).isEqualTo(403);
    }

    // ---- the list -----------------------------------------------------------------------------------------------------

    @Test
    void theListCanBeFilteredSearchedAndPaged() throws Exception {
        JsonNode beta = newClient("Beta Ltd");
        JsonNode one = createCaseAsOwner();
        JsonNode two = createCaseAsOwner();
        JsonNode three = createCaseAsOwner();
        saved(superToken(), one, "candidate", "fullName", "Asha Rao", "employeeId", "EMP-100");
        saved(superToken(), two, "candidate", "fullName", "Vikram Shah", "employeeId", "X_200");
        saved(superToken(), three, "candidate", "fullName", "100% Legend", "employeeId", "Z9");
        saved(superToken(), reload(two), "report-info", "reportId", "SPECIAL-77", "issueDate", "2026-01-01", "clientId", beta.get("id").asText());
        jdbc.update("UPDATE cases.cases SET lifecycle = 'IN_REVIEW' WHERE id = ?::uuid", three.get("id").asText());
        send(post(path(one, "/assignments")), superToken(), map("adminId", analystB.toString(), "role", "REVIEWER"));

        String su = superToken();
        assertThat(body(send(get("/api/cases"), su, null)).get("total").asInt()).isEqualTo(3);
        assertThat(body(send(get("/api/cases?status=IN_REVIEW"), su, null)).get("items")).hasSize(1);
        assertThat(body(send(get("/api/cases?client=" + beta.get("id").asText()), su, null)).get("items").get(0).get("reportId").asText()).isEqualTo("SPECIAL-77");
        assertThat(body(send(get("/api/cases?assignee=" + analystB), su, null)).get("items")).hasSize(1);
        assertThat(body(send(get("/api/cases?q=special"), su, null)).get("items")).as("report id").hasSize(1);
        assertThat(body(send(get("/api/cases?q=ASHA"), su, null)).get("items")).as("name, any case").hasSize(1);
        assertThat(body(send(get("/api/cases?q=emp-100"), su, null)).get("items")).as("employee id").hasSize(1);
        assertThat(body(send(get("/api/cases").param("q", "%"), su, null)).get("items")).as("a % is text, not a wildcard").hasSize(1);
        assertThat(body(send(get("/api/cases?q=_"), su, null)).get("items")).as("an underscore is text too").hasSize(1);
        assertThat(body(send(get("/api/cases?q=nomatch"), su, null)).get("total").asInt()).isZero();

        JsonNode row = body(send(get("/api/cases?q=asha"), su, null)).get("items").get(0);
        assertThat(row.get("candidateName").asText()).isEqualTo("Asha Rao");
        assertThat(row.get("clientName").asText()).isEqualTo("Acme Corp");
        assertThat(row.get("assignments")).hasSize(2);

        JsonNode page = body(send(get("/api/cases?size=2&page=1"), su, null));
        assertThat(page.get("items")).hasSize(1);
        assertThat(page.get("total").asInt()).isEqualTo(3);
        assertThat(body(send(get("/api/cases?size=100000"), su, null)).get("size").asInt()).as("size is capped").isEqualTo(100);
        assertThat(status(send(get("/api/cases?status=NOPE"), su, null))).isEqualTo(400);
    }

    @Test
    void theListIsNewestChangeFirst() throws Exception {
        JsonNode one = createCaseAsOwner();
        JsonNode two = createCaseAsOwner();
        assertThat(body(send(get("/api/cases"), superToken(), null)).get("items").get(0).get("id").asText()).isEqualTo(two.get("id").asText());

        saved(superToken(), one, "remarks", "analystRemarks", "touched");
        assertThat(body(send(get("/api/cases"), superToken(), null)).get("items").get(0).get("id").asText()).isEqualTo(one.get("id").asText());
    }

    // ---- deleting ---------------------------------------------------------------------------------------------------------

    @Test
    void deletingHidesTheCaseEverywhereAndNeverReusesItsReportId() throws Exception {
        JsonNode c = createCase(tokenA);
        String reportId = c.get("reportId").asText();
        assertThat(status(send(delete(path(c, "")), tokenA, null))).as("needs CASE_DELETE").isEqualTo(403);

        assertThat(status(send(delete(path(c, "")), superToken(), null))).isEqualTo(204);
        assertThat(status(send(get(path(c, "")), superToken(), null))).isEqualTo(404);
        assertThat(status(save(superToken(), c, "remarks", "analystRemarks", "x"))).isEqualTo(404);
        assertThat(body(send(get("/api/cases"), superToken(), null)).get("total").asInt()).isZero();
        assertThat(body(send(get("/api/cases"), tokenA, null)).get("total").asInt()).isZero();
        assertThat(auditActionsSinceStart()).contains("CASE_DELETED");

        JsonNode next = createCaseAsOwner();
        assertThat(next.get("reportId").asText()).isNotEqualTo(reportId);
        assertThat(status(save(superToken(), next, "report-info", "reportId", reportId, "issueDate", "2026-01-01", "clientId", acme.toString())))
                .as("a deleted case's ID stays reserved").isEqualTo(409);
    }

    @Test
    void aFinalizedReportCannotBeDeletedOrReassigned() throws Exception {
        JsonNode c = createCaseAsOwner();
        jdbc.update("UPDATE cases.cases SET lifecycle = 'FINALIZED' WHERE id = ?::uuid", c.get("id").asText());
        assertThat(status(send(delete(path(c, "")), superToken(), null))).isEqualTo(409);
        assertThat(status(send(post(path(c, "/assignments")), superToken(), map("adminId", analystA.toString(), "role", "REVIEWER")))).isEqualTo(409);
        assertThat(status(send(get(path(c, "")), superToken(), null))).isEqualTo(200);
    }

    // ---- audit ------------------------------------------------------------------------------------------------------------

    @Test
    void sectionSavesAreAuditedWithoutPersonalContactDetails() throws Exception {
        JsonNode c = createCaseAsOwner();
        saved(superToken(), c, "candidate", "fullName", "Asha Rao", "phone", "9876543210", "employeeId", "E1");

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT action, before::text AS before, after::text AS after, actor_email FROM auth.audit_log"
                        + " WHERE case_id = ?::uuid AND action = 'CASE_SECTION_SAVED:candidate'", c.get("id").asText());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("actor_email")).isEqualTo(OWNER);
        assertThat(rows.get(0).get("after").toString()).contains("Asha Rao").doesNotContain("9876543210");
        assertThat(rows.get(0).get("before").toString()).contains("employeeId");
    }
}
