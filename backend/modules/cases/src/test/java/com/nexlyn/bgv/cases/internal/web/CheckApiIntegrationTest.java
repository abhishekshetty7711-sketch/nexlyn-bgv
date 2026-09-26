package com.nexlyn.bgv.cases.internal.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexlyn.bgv.cases.CasesIntegrationTestBase;
import com.nexlyn.bgv.common.validation.Verhoeff;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

class CheckApiIntegrationTest extends CasesIntegrationTestBase {

    static final String VALID_AADHAAR = validAadhaar();
    static final String PAN = "ABCDE1234F";

    UUID clientId;
    UUID analystId;
    String analyst;          // CHECK_UPDATE on assigned cases, no PII or attestation rights
    String analystWithPii;   // the same plus PII_UNMASK
    JsonNode theCase;
    String caseId;

    static String validAadhaar() {
        String body = "23456789012";
        return body + Verhoeff.checkDigit(body);
    }

    @BeforeEach
    void fixtures() throws Exception {
        MvcResult client = send(post("/api/clients"), superToken(), obj("name", "Acme Corp", "displayName", "Acme Corp"));
        clientId = UUID.fromString(body(client).get("id").asText());
        analystId = newAdmin("analyst@example.com", "Ann Analyst");
        analyst = tokenFor(analystId, "analyst@example.com", "CASE_CREATE", "CASE_READ_ASSIGNED", "CASE_UPDATE", "CHECK_UPDATE");
        analystWithPii = tokenFor(analystId, "analyst@example.com", "CASE_CREATE", "CASE_READ_ASSIGNED", "CASE_UPDATE", "CHECK_UPDATE", "PII_UNMASK");
        theCase = createCase(analyst);
        caseId = theCase.get("id").asText();
    }

    // ---- helpers ------------------------------------------------------------------------------------------

    private JsonNode createCase(String token) throws Exception {
        MvcResult created = send(post("/api/cases"), token, obj("clientId", clientId.toString()));
        assertThat(status(created)).isEqualTo(201);
        return body(created);
    }

    private String checksPath(String suffix) {
        return "/api/cases/" + caseId + "/checks" + suffix;
    }

    private JsonNode addCheck(String token, String type) throws Exception {
        MvcResult added = send(post(checksPath("")), token, obj("type", type));
        assertThat(status(added)).as("add %s: %s", type, added.getResponse().getContentAsString()).isEqualTo(201);
        return body(added);
    }

    private JsonNode listChecks() throws Exception {
        return body(send(get(checksPath("")), superToken(), null));
    }

    private JsonNode field(JsonNode check, String key) {
        for (JsonNode f : check.get("fields")) {
            if (f.get("key").asText().equals(key)) {
                return f;
            }
        }
        throw new AssertionError("no field " + key);
    }

    private Map<String, Object> fieldInput(String key, Object value) {
        return obj("key", key, "value", value);
    }

    /** Saves a check with sensible card values and the given field inputs. */
    private MvcResult save(String token, JsonNode check, Object... extra) throws Exception {
        Map<String, Object> request = obj("version", check.get("version").asLong(), "title", check.get("title").asText(),
                "status", check.get("status").asText(), "hasAttestation", check.get("hasAttestation").asBoolean(),
                "barCouncilNo", check.get("barCouncilNo").isNull() ? null : check.get("barCouncilNo").asText(),
                "disclaimer", check.get("disclaimer").isNull() ? null : check.get("disclaimer").asText());
        request.putAll(obj(extra));
        return send(put(checksPath("/" + check.get("id").asText())), token, request);
    }

    private JsonNode saved(String token, JsonNode check, Object... extra) throws Exception {
        MvcResult result = save(token, check, extra);
        assertThat(status(result)).as(result.getResponse().getContentAsString()).isEqualTo(200);
        return body(result);
    }

    private JsonNode saveCandidate(Object... fields) throws Exception {
        JsonNode current = body(send(get("/api/cases/" + caseId), superToken(), null));
        Map<String, Object> request = obj("version", current.get("version").asLong());
        request.putAll(obj(fields));
        MvcResult result = send(put("/api/cases/" + caseId + "/candidate"), superToken(), request);
        assertThat(status(result)).as(result.getResponse().getContentAsString()).isEqualTo(200);
        return body(result);
    }

    // ---- the type definitions --------------------------------------------------------------------------------

    @Test
    void theCheckTypeDefinitionsAreAvailableToAnySignedInAdmin() throws Exception {
        assertThat(status(send(get("/api/check-types"), null, null))).isEqualTo(401);
        MvcResult result = send(get("/api/check-types"), tokenFor(analystId, "analyst@example.com", "AUDIT_READ"), null);
        assertThat(status(result)).isEqualTo(200);
        JsonNode types = body(result);
        assertThat(types).hasSize(18);
        JsonNode aadhaar = types.get(0);
        assertThat(aadhaar.get("code").asText()).isEqualTo("AADHAAR");
        assertThat(aadhaar.get("fields").get(0).get("type").asText()).as("types are lower case in JSON").isEqualTo("aadhaar");
        assertThat(aadhaar.get("fields").get(0).get("sensitive").asBoolean()).isTrue();
        assertThat(aadhaar.get("iconGroup").asText()).isEqualTo("identity");
    }

    // ---- adding ---------------------------------------------------------------------------------------------------

    @Test
    void addingACheckUsesTheTypeDefaultsAndPrefillsFromTheCandidate() throws Exception {
        saveCandidate("fullName", "Asha Rao", "parentName", "Ravi Rao", "dob", "1994-05-17", "street", "12 MG Road",
                "city", "Bengaluru", "state", "Karnataka", "pin", "560001", "employeeId", "EMP-1");

        JsonNode aadhaar = addCheck(analyst, "AADHAAR");
        assertThat(aadhaar.get("title").asText()).isEqualTo("Identity Verification (Aadhaar)");
        assertThat(aadhaar.get("documentName").asText()).isEqualTo("Aadhaar Card");
        assertThat(aadhaar.get("groupKey").asText()).isEqualTo("identity");
        assertThat(aadhaar.get("status").asText()).isEqualTo("PENDING");
        assertThat(aadhaar.get("verificationType").asText()).isEqualTo("Electronic");
        assertThat(aadhaar.get("dateSync").asText()).isEqualTo("MASTER");
        assertThat(aadhaar.get("hasAttestation").asBoolean()).isFalse();
        assertThat(field(aadhaar, "full_name").get("value").asText()).isEqualTo("Asha Rao");
        assertThat(field(aadhaar, "full_name").get("source").asText()).isEqualTo("CANDIDATE");
        assertThat(field(aadhaar, "full_name").get("manual").asBoolean()).isFalse();
        assertThat(field(aadhaar, "dob").get("value").asText()).isEqualTo("1994-05-17");
        assertThat(field(aadhaar, "father_name").get("value").asText()).isEqualTo("Ravi Rao");
        assertThat(field(aadhaar, "pin").get("value").asText()).isEqualTo("560001");
        assertThat(field(aadhaar, "country").get("value").asText()).isEqualTo("India");
        assertThat(field(aadhaar, "aadhaar_number").get("hasValue").asBoolean()).as("sensitive fields are never prefilled").isFalse();

        JsonNode employment = addCheck(analyst, "EMPLOYMENT");
        assertThat(employment.get("verificationType").asText()).isEqualTo("Standard");
        assertThat(field(employment, "employee_id").get("value").asText()).isEqualTo("EMP-1");
        assertThat(field(employment, "company").get("source").asText()).isEqualTo("MANUAL");
        assertThat(employment.get("dateSync").asText()).isEqualTo("AUTO");
    }

    @Test
    void courtChecksGetTheirExtraDetailsAndAnAttestationOnlyForSomeoneAllowedToApplyOne() throws Exception {
        JsonNode byOwner = addCheck(superToken(), "COURT");
        assertThat(byOwner.get("details")).extracting(d -> d.get("label").asText()).containsExactly("Court Type", "Jurisdiction");
        assertThat(byOwner.get("details").get(1).get("value").asText()).isEqualTo("Permanent Address");
        assertThat(byOwner.get("hasAttestation").asBoolean()).isTrue();
        assertThat(byOwner.get("barCouncilNo").asText()).isEqualTo("KAR/670/06");
        assertThat(byOwner.get("disclaimer").asText()).startsWith("This report is based on information available in accessible court records");

        JsonNode byAnalyst = addCheck(analyst, "COURT");
        assertThat(byAnalyst.get("hasAttestation").asBoolean()).as("no ATTESTATION_APPLY, no seal").isFalse();
        assertThat(byAnalyst.get("barCouncilNo").isNull()).isTrue();
    }

    @Test
    void twoChecksOfTheSameTypeCanBeOnOneCaseAndKeepTheirOwnValues() throws Exception {
        // for example a candidate with two identity cards, or two employers: the type is not a unique key of a case
        JsonNode first = addCheck(analyst, "EMPLOYMENT");
        JsonNode second = addCheck(analyst, "EMPLOYMENT");
        assertThat(second.get("id").asText()).isNotEqualTo(first.get("id").asText());

        saved(analyst, first, "fields", List.of(fieldInput("company", "Globex Technologies")));
        saved(analyst, second, "fields", List.of(fieldInput("company", "Initech")));

        JsonNode checks = listChecks();
        assertThat(checks).hasSize(2);
        assertThat(checks.get(0).get("type").asText()).isEqualTo("EMPLOYMENT");
        assertThat(checks.get(1).get("type").asText()).isEqualTo("EMPLOYMENT");
        assertThat(field(checks.get(0), "company").get("value").asText()).isEqualTo("Globex Technologies");
        assertThat(field(checks.get(1), "company").get("value").asText()).isEqualTo("Initech");
        assertThat(checks.get(0).get("sortOrder").asInt()).isLessThan(checks.get(1).get("sortOrder").asInt());
    }

    @Test
    void everyOneOfTheEighteenTypesCanBeAddedAndSaved() throws Exception {
        JsonNode types = body(send(get("/api/check-types"), superToken(), null));
        assertThat(types).hasSize(18);
        for (JsonNode type : types) {
            JsonNode check = addCheck(analyst, type.get("code").asText());
            List<Map<String, Object>> inputs = new ArrayList<>();
            for (JsonNode f : type.get("fields")) {
                inputs.add(obj("key", f.get("key").asText(), "value", sampleValue(f), "manual", true, "verifiedTick", true));
            }
            JsonNode after = saved(analyst, check, "fields", inputs, "status", "VERIFIED", "summaryDescription", "Checked");
            assertThat(after.get("status").asText()).as(type.get("code").asText()).isEqualTo("VERIFIED");
            assertThat(after.get("fields")).as(type.get("code").asText()).hasSize(type.get("fields").size());
            for (JsonNode f : after.get("fields")) {
                assertThat(f.get("hasValue").asBoolean()).as(type.get("code").asText() + "." + f.get("key").asText()).isTrue();
                assertThat(f.get("verifiedTick").asBoolean()).isTrue();
            }
        }
        assertThat(listChecks()).hasSize(18);
    }

    private static Object sampleValue(JsonNode field) {
        return switch (field.get("type").asText()) {
            case "aadhaar" -> VALID_AADHAAR;
            case "pan" -> PAN;
            case "uan" -> "101234567890";
            case "date" -> "2026-03-01";
            case "number" -> "5";
            case "pin" -> "560001";
            case "phone" -> "9876543210";
            case "boolean" -> "true";
            case "select" -> field.get("options").get(0).asText();
            case "repeatable" -> "[{\"from\":\"2020-01-01\",\"to\":\"2020-02-01\",\"reason\":\"Travel\"}]";
            case "textarea" -> "Some longer text";
            default -> "Sample";
        };
    }

    @Test
    void unknownTypesAreRefusedAndOtherCasesChecksAreNotReachable() throws Exception {
        assertThat(status(send(post(checksPath("")), analyst, obj("type", "NOT_A_TYPE")))).isEqualTo(400);
        assertThat(status(send(post(checksPath("")), analyst, obj("type", "")))).isEqualTo(400);
        JsonNode mine = addCheck(analyst, "PAN");

        JsonNode other = createCase(superToken());
        MvcResult wrongCase = send(put("/api/cases/" + other.get("id").asText() + "/checks/" + mine.get("id").asText()), superToken(),
                obj("version", 0, "title", "x", "status", "PENDING", "hasAttestation", false));
        assertThat(status(wrongCase)).isEqualTo(404);
        assertThat(status(send(delete("/api/cases/" + other.get("id").asText() + "/checks/" + mine.get("id").asText()), superToken(), null))).isEqualTo(404);
    }

    // ---- prefill -----------------------------------------------------------------------------------------------------

    @Test
    void checksFollowTheCandidateUntilSomeoneTypesTheirOwnValue() throws Exception {
        JsonNode check = addCheck(analyst, "AADHAAR");
        saveCandidate("fullName", "First Name");
        assertThat(field(listChecks().get(0), "full_name").get("value").asText()).isEqualTo("First Name");

        // A hand-typed value sticks, even when the candidate changes.
        JsonNode typed = saved(analyst, listChecks().get(0), "fields", List.of(obj("key", "full_name", "value", "Typed By Hand", "manual", true)));
        assertThat(field(typed, "full_name").get("value").asText()).isEqualTo("Typed By Hand");
        assertThat(field(typed, "full_name").get("manual").asBoolean()).isTrue();
        assertThat(field(typed, "full_name").get("source").asText()).isEqualTo("MANUAL");
        saveCandidate("fullName", "Second Name");
        JsonNode after = listChecks().get(0);
        assertThat(field(after, "full_name").get("value").asText()).isEqualTo("Typed By Hand");
        assertThat(field(after, "city").get("value").isNull()).as("untouched fields keep following").isTrue();

        saveCandidate("fullName", "Second Name", "city", "Mysuru");
        assertThat(field(listChecks().get(0), "city").get("value").asText()).isEqualTo("Mysuru");

        // Asking to follow the candidate again brings the candidate's value back.
        JsonNode reset = saved(analyst, listChecks().get(0), "fields", List.of(obj("key", "full_name", "manual", false)));
        assertThat(field(reset, "full_name").get("value").asText()).isEqualTo("Second Name");
        assertThat(field(reset, "full_name").get("manual").asBoolean()).isFalse();
        assertThat(field(reset, "full_name").get("source").asText()).isEqualTo("CANDIDATE");
        assertThat(check.get("id").asText()).isEqualTo(reset.get("id").asText());
    }

    @Test
    void theFatherLabelBecomesGuardianWhenTheCandidateHasAGuardian() throws Exception {
        addCheck(analyst, "COURT");
        assertThat(field(listChecks().get(0), "father_name").get("label").asText()).isEqualTo("Father's Name");
        saveCandidate("parentType", "GUARDIAN", "parentName", "Uncle Sam");
        JsonNode court = listChecks().get(0);
        assertThat(field(court, "father_name").get("label").asText()).isEqualTo("Guardian's Name");
        assertThat(field(court, "father_name").get("value").asText()).isEqualTo("Uncle Sam");
    }

    // ---- sensitive values ---------------------------------------------------------------------------------------------

    @Test
    void anAadhaarNumberIsValidatedEncryptedAndOnlyEverShownMasked() throws Exception {
        JsonNode check = addCheck(analyst, "AADHAAR");
        String spaced = VALID_AADHAAR.substring(0, 4) + " " + VALID_AADHAAR.substring(4, 8) + " " + VALID_AADHAAR.substring(8);

        MvcResult result = save(analyst, check, "fields", List.of(fieldInput("aadhaar_number", spaced)));
        assertThat(status(result)).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString()).as("the response never carries the number").doesNotContain(VALID_AADHAAR);
        JsonNode number = field(body(result), "aadhaar_number");
        assertThat(number.get("value").asText()).isEqualTo("XXXX XXXX " + VALID_AADHAAR.substring(8));
        assertThat(number.get("hasValue").asBoolean()).isTrue();

        Map<String, Object> stored = jdbc.queryForMap("SELECT value, value_encrypted, value_last4 FROM cases.check_fields"
                + " WHERE field_key = 'aadhaar_number' AND check_id = ?::uuid", check.get("id").asText());
        assertThat(stored.get("value")).isEqualTo("XXXX XXXX " + VALID_AADHAAR.substring(8));
        assertThat(stored.get("value_last4")).isEqualTo(VALID_AADHAAR.substring(8));
        assertThat((String) stored.get("value_encrypted")).isNotBlank().doesNotContain(VALID_AADHAAR);

        assertThat(listChecks().toString()).doesNotContain(VALID_AADHAAR);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cases.check_fields WHERE value LIKE ? OR value_encrypted LIKE ?", Integer.class,
                "%" + VALID_AADHAAR + "%", "%" + VALID_AADHAAR + "%")).isZero();
    }

    @Test
    void aMistypedAadhaarPanOrUanIsRefusedWithoutEchoingIt() throws Exception {
        JsonNode aadhaar = addCheck(analyst, "AADHAAR");
        String wrongCheckDigit = VALID_AADHAAR.substring(0, 11) + (char) ('0' + ((VALID_AADHAAR.charAt(11) - '0' + 1) % 10));
        MvcResult bad = save(analyst, aadhaar, "fields", List.of(fieldInput("aadhaar_number", wrongCheckDigit)));
        assertThat(status(bad)).isEqualTo(400);
        assertThat(bad.getResponse().getContentAsString()).doesNotContain(wrongCheckDigit).contains("Aadhaar");
        assertThat(field(listChecks().get(0), "aadhaar_number").get("hasValue").asBoolean()).isFalse();

        JsonNode pan = addCheck(analyst, "PAN");
        MvcResult badPan = save(analyst, pan, "fields", List.of(fieldInput("pan_number", "SECRET12345")));
        assertThat(status(badPan)).isEqualTo(400);
        assertThat(badPan.getResponse().getContentAsString()).doesNotContain("SECRET12345");

        JsonNode uan = addCheck(analyst, "UAN");
        assertThat(status(save(analyst, uan, "fields", List.of(fieldInput("uan_number", "12345"))))).isEqualTo(400);
    }

    @Test
    void panIsUppercasedAndMaskedAndASensitiveValueSurvivesASaveThatDoesNotSendIt() throws Exception {
        JsonNode pan = addCheck(analyst, "PAN");
        JsonNode first = saved(analyst, pan, "fields", List.of(fieldInput("pan_number", "abcde1234f")));
        assertThat(field(first, "pan_number").get("value").asText()).isEqualTo("ABXXXXX34F");

        // The screen only ever holds the masked form, so a later save leaves the field out or blank: nothing changes.
        JsonNode blank = saved(analyst, first, "fields", List.of(fieldInput("pan_number", ""), fieldInput("full_name", "Someone")));
        assertThat(field(blank, "pan_number").get("hasValue").asBoolean()).isTrue();
        JsonNode omitted = saved(analyst, blank, "status", "VERIFIED");
        assertThat(field(omitted, "pan_number").get("hasValue").asBoolean()).isTrue();
        MvcResult revealed = send(get(checksPath("/" + pan.get("id").asText() + "/fields/pan_number/reveal")), analystWithPii, null);
        assertThat(body(revealed).get("value").asText()).isEqualTo("ABCDE1234F");

        JsonNode cleared = saved(analyst, omitted, "fields", List.of(obj("key", "pan_number", "clear", true)));
        assertThat(field(cleared, "pan_number").get("hasValue").asBoolean()).isFalse();
        assertThat(field(cleared, "pan_number").get("value").isNull()).isTrue();
    }

    @Test
    void revealingNeedsPiiUnmaskAccessToTheCaseAndIsAudited() throws Exception {
        JsonNode check = addCheck(analyst, "AADHAAR");
        saved(analyst, check, "fields", List.of(fieldInput("aadhaar_number", VALID_AADHAAR)));
        String path = checksPath("/" + check.get("id").asText() + "/fields/aadhaar_number/reveal");

        assertThat(status(send(get(path), null, null))).isEqualTo(401);
        assertThat(status(send(get(path), analyst, null))).as("no PII_UNMASK").isEqualTo(403);

        UUID stranger = newAdmin("stranger@example.com", "Stranger");
        assertThat(status(send(get(path), tokenFor(stranger, "stranger@example.com", "CASE_READ_ASSIGNED", "PII_UNMASK"), null)))
                .as("not assigned to the case").isEqualTo(403);

        MvcResult ok = send(get(path), analystWithPii, null);
        assertThat(status(ok)).isEqualTo(200);
        assertThat(body(ok).get("value").asText()).isEqualTo(VALID_AADHAAR);
        assertThat(ok.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");

        List<Map<String, Object>> audit = jdbc.queryForList("SELECT action, actor_email, case_id::text AS case_id, after::text AS after"
                + " FROM auth.audit_log WHERE action = 'PII_REVEALED' AND at >= ?", java.sql.Timestamp.from(testStart));
        assertThat(audit).hasSize(1);
        assertThat(audit.get(0).get("actor_email")).isEqualTo("analyst@example.com");
        assertThat(audit.get(0).get("case_id")).isEqualTo(caseId);
        assertThat(audit.get(0).get("after").toString()).contains("aadhaar_number").doesNotContain(VALID_AADHAAR);
    }

    @Test
    void revealingAnEmptyOrNonSensitiveFieldIsRefused() throws Exception {
        JsonNode check = addCheck(analyst, "AADHAAR");
        assertThat(status(send(get(checksPath("/" + check.get("id").asText() + "/fields/aadhaar_number/reveal")), analystWithPii, null))).isEqualTo(404);
        assertThat(status(send(get(checksPath("/" + check.get("id").asText() + "/fields/full_name/reveal")), analystWithPii, null))).isEqualTo(400);
        assertThat(status(send(get(checksPath("/" + check.get("id").asText() + "/fields/nope/reveal")), analystWithPii, null))).isEqualTo(400);
    }

    @Test
    void noAuditRowEverHoldsAFullIdentityNumber() throws Exception {
        JsonNode aadhaar = addCheck(analyst, "AADHAAR");
        saved(analyst, aadhaar, "fields", List.of(fieldInput("aadhaar_number", VALID_AADHAAR)));
        JsonNode pan = addCheck(analyst, "PAN");
        saved(analyst, pan, "fields", List.of(fieldInput("pan_number", PAN)));
        send(get(checksPath("/" + aadhaar.get("id").asText() + "/fields/aadhaar_number/reveal")), analystWithPii, null);

        String dump = jdbc.queryForList("SELECT concat_ws(' ', action, entity_id, before::text, after::text) FROM auth.audit_log", String.class).toString();
        assertThat(dump).doesNotContain(VALID_AADHAAR).doesNotContain(PAN).contains("aadhaar_number (sensitive)");
    }

    // ---- date master ----------------------------------------------------------------------------------------------------

    @Test
    void theFirstChecksDatesFlowToTheOthersUntilTheyAreSetByHand() throws Exception {
        JsonNode first = addCheck(analyst, "AADHAAR");
        JsonNode second = addCheck(analyst, "PAN");
        JsonNode third = addCheck(analyst, "COURT");

        saved(analyst, first, "requestedDate", "2026-03-01", "completedDate", "2026-03-05");
        JsonNode all = listChecks();
        assertThat(all.get(1).get("requestedDate").asText()).isEqualTo("2026-03-01");
        assertThat(all.get(2).get("completedDate").asText()).isEqualTo("2026-03-05");
        assertThat(all.get(1).get("dateSync").asText()).isEqualTo("AUTO");

        // The second check gets its own dates: manual from now on.
        JsonNode own = saved(analyst, all.get(1), "requestedDate", "2026-04-01", "completedDate", "2026-04-09");
        assertThat(own.get("dateSync").asText()).isEqualTo("MANUAL");
        saved(analyst, listChecks().get(0), "requestedDate", "2026-05-01", "completedDate", "2026-05-02");
        all = listChecks();
        assertThat(all.get(1).get("requestedDate").asText()).as("manual dates stay").isEqualTo("2026-04-01");
        assertThat(all.get(2).get("requestedDate").asText()).as("the third still follows").isEqualTo("2026-05-01");
        assertThat(all.get(2).get("dateSync").asText()).isEqualTo("AUTO");

        // Typing the master's dates again puts a check back on automatic.
        JsonNode back = saved(analyst, all.get(1), "requestedDate", "2026-05-01", "completedDate", "2026-05-02");
        assertThat(back.get("dateSync").asText()).isEqualTo("AUTO");
        assertThat(third.get("id").asText()).isEqualTo(all.get(2).get("id").asText());
        assertThat(second.get("id").asText()).isEqualTo(all.get(1).get("id").asText());
    }

    @Test
    void reorderingMakesTheNewFirstCheckTheMaster() throws Exception {
        JsonNode a = addCheck(analyst, "AADHAAR");
        JsonNode b = addCheck(analyst, "PAN");
        JsonNode c = addCheck(analyst, "COURT");

        MvcResult reordered = send(patch(checksPath("/order")), analyst, obj("ids", List.of(c.get("id").asText(), a.get("id").asText(), b.get("id").asText())));
        assertThat(status(reordered)).isEqualTo(200);
        JsonNode now = body(reordered);
        assertThat(now.get(0).get("id").asText()).isEqualTo(c.get("id").asText());
        assertThat(now.get(0).get("dateSync").asText()).isEqualTo("MASTER");
        assertThat(now.get(1).get("dateSync").asText()).isEqualTo("AUTO");
        assertThat(listChecks().get(2).get("id").asText()).isEqualTo(b.get("id").asText());
    }

    @Test
    void reorderNeedsEveryCheckExactlyOnce() throws Exception {
        JsonNode a = addCheck(analyst, "AADHAAR");
        addCheck(analyst, "PAN");
        String id = a.get("id").asText();
        assertThat(status(send(patch(checksPath("/order")), analyst, obj("ids", List.of(id))))).isEqualTo(400);
        assertThat(status(send(patch(checksPath("/order")), analyst, obj("ids", List.of(id, id))))).isEqualTo(400);
        assertThat(status(send(patch(checksPath("/order")), analyst, obj("ids", List.of(id, UUID.randomUUID().toString()))))).isEqualTo(400);
        assertThat(status(send(patch(checksPath("/order")), analyst, obj("ids", null)))).isEqualTo(400);
    }

    // ---- attestation --------------------------------------------------------------------------------------------------------

    @Test
    void onlySomeoneWithAttestationPermissionCanApplyOrChangeAnAttestation() throws Exception {
        JsonNode court = addCheck(analyst, "COURT");
        MvcResult tryOn = save(analyst, court, "hasAttestation", true);
        assertThat(status(tryOn)).isEqualTo(403);
        assertThat(code(tryOn)).isEqualTo("FORBIDDEN");
        assertThat(status(save(analyst, court, "status", "VERIFIED"))).as("an unchanged attestation needs no permission").isEqualTo(200);

        JsonNode current = listChecks().get(0);
        JsonNode on = saved(superToken(), current, "hasAttestation", true, "barCouncilNo", "KAR/999/11");
        assertThat(on.get("hasAttestation").asBoolean()).isTrue();
        assertThat(on.get("barCouncilNo").asText()).isEqualTo("KAR/999/11");
        assertThat(on.get("disclaimer").asText()).as("the default wording is filled in").isNotBlank();

        assertThat(status(save(analyst, on, "hasAttestation", false, "barCouncilNo", null, "disclaimer", null))).as("removing needs the permission too").isEqualTo(403);
        JsonNode off = saved(superToken(), on, "hasAttestation", false);
        assertThat(off.get("hasAttestation").asBoolean()).isFalse();
        assertThat(off.get("barCouncilNo").isNull()).isTrue();
        assertThat(auditActionsSinceStart()).contains("ATTESTATION_CHANGED");
    }

    // ---- the card, details, remarks ---------------------------------------------------------------------------------------------

    @Test
    void theCardItsExtraDetailsAndRemarksAreSavedAndCleaned() throws Exception {
        JsonNode check = addCheck(analyst, "EMPLOYMENT");
        JsonNode after = saved(analyst, check, "title", "  Employment - Infosys ", "summaryDescription", "  Worked 2018-2022 ",
                "thisCardVerifies", "Employment at Infosys", "status", "DISCREPANCY", "verificationType", "Standard",
                "remarks", "Joined <B>late</B> <script>x</script>",
                "details", List.of(obj("label", "Reference No", "value", "R-1"), obj("label", "", "value", ""), obj("label", " Note ", "value", " ok ")));

        assertThat(after.get("title").asText()).isEqualTo("Employment - Infosys");
        assertThat(after.get("summaryDescription").asText()).isEqualTo("Worked 2018-2022");
        assertThat(after.get("status").asText()).isEqualTo("DISCREPANCY");
        assertThat(after.get("remarks").asText()).isEqualTo("Joined <strong>late</strong> &lt;script&gt;x&lt;/script&gt;");
        assertThat(after.get("details")).extracting(d -> d.get("label").asText()).containsExactly("Reference No", "Note");
        assertThat(after.get("details").get(1).get("value").asText()).isEqualTo("ok");

        JsonNode kept = saved(analyst, after, "status", "DISCREPANCY"); // details omitted = unchanged
        assertThat(kept.get("details")).hasSize(2);
        JsonNode emptied = saved(analyst, kept, "status", "DISCREPANCY", "details", List.of());
        assertThat(emptied.get("details")).isEmpty();
    }

    @Test
    void badCardValuesAreRefused() throws Exception {
        JsonNode check = addCheck(analyst, "PAN");
        assertThat(status(save(analyst, check, "title", "  "))).isEqualTo(400);
        assertThat(status(save(analyst, check, "status", null))).isEqualTo(400);
        assertThat(status(save(analyst, check, "status", "SHINY"))).isEqualTo(400);
        assertThat(status(save(analyst, check, "requestedDate", "1850-01-01"))).isEqualTo(400);
        assertThat(status(save(analyst, check, "fields", List.of(fieldInput("no_such_field", "x"))))).isEqualTo(400);
        assertThat(status(save(analyst, check, "fields", List.of(fieldInput("full_name", "a"), fieldInput("full_name", "b"))))).isEqualTo(400);
        assertThat(status(save(analyst, check, "fields", List.of(fieldInput("dob", "17/05/1994"))))).isEqualTo(400);
        assertThat(status(save(analyst, check, "fields", List.of(fieldInput("pin", "12"))))).isEqualTo(400);
        List<Map<String, Object>> tooMany = new ArrayList<>();
        for (int i = 0; i < 31; i++) {
            tooMany.add(obj("label", "L" + i, "value", "v"));
        }
        assertThat(status(save(analyst, check, "details", tooMany))).isEqualTo(400);
    }

    @Test
    void repeatableRowsAreValidatedAndStoredCleanly() throws Exception {
        JsonNode gap = addCheck(analyst, "GAP_REVIEW");
        JsonNode ok = saved(analyst, gap, "fields", List.of(fieldInput("gaps",
                "[{\"from\":\"2020-01-01\",\"to\":\"2020-06-30\",\"reason\":\" Travel \"},{\"from\":\"\",\"to\":\"\",\"reason\":\"\"}]")));
        assertThat(field(ok, "gaps").get("value").asText()).isEqualTo("[{\"from\":\"2020-01-01\",\"to\":\"2020-06-30\",\"reason\":\"Travel\"}]");

        MvcResult bad = save(analyst, ok, "fields", List.of(fieldInput("gaps", "[{\"from\":\"yesterday\"}]")));
        assertThat(status(bad)).isEqualTo(400);
        assertThat(status(save(analyst, ok, "fields", List.of(fieldInput("gaps", "[{\"secret\":\"x\"}]"))))).isEqualTo(400);
    }

    @Test
    void savingWithAStaleVersionIsRefusedAndEvenAFieldsOnlyEditMovesTheVersion() throws Exception {
        JsonNode check = addCheck(analyst, "REFERENCE");
        JsonNode first = saved(analyst, check, "fields", List.of(fieldInput("referee_name", "Ravi")));
        assertThat(first.get("version").asLong()).isGreaterThan(check.get("version").asLong());

        MvcResult stale = save(analyst, check, "fields", List.of(fieldInput("referee_name", "Stale")));
        assertThat(status(stale)).isEqualTo(409);
        assertThat(field(listChecks().get(0), "referee_name").get("value").asText()).isEqualTo("Ravi");
        assertThat(status(send(put(checksPath("/" + check.get("id").asText())), analyst, obj("title", "x", "status", "PENDING")))).as("no version").isEqualTo(400);
    }

    @Test
    void editingChecksDoesNotMakeAnOpenCaseSectionStale() throws Exception {
        JsonNode before = body(send(get("/api/cases/" + caseId), superToken(), null));
        JsonNode check = addCheck(analyst, "PAN");
        saved(analyst, check, "status", "VERIFIED");
        JsonNode after = body(send(get("/api/cases/" + caseId), superToken(), null));
        assertThat(after.get("version").asLong()).isEqualTo(before.get("version").asLong());

        MvcResult remarks = send(put("/api/cases/" + caseId + "/remarks"), superToken(), obj("version", before.get("version").asLong(), "analystRemarks", "still saves"));
        assertThat(status(remarks)).isEqualTo(200);
    }

    // ---- access and locks ---------------------------------------------------------------------------------------------------------

    @Test
    void checkRoutesNeedThePermissionAndAccessToTheCase() throws Exception {
        JsonNode check = addCheck(analyst, "PAN");
        String checkPath = checksPath("/" + check.get("id").asText());
        UUID stranger = newAdmin("stranger@example.com", "Stranger");
        String strangerToken = tokenFor(stranger, "stranger@example.com", "CASE_READ_ASSIGNED", "CASE_UPDATE", "CHECK_UPDATE");
        String readerOnly = tokenFor(analystId, "analyst@example.com", "CASE_READ_ASSIGNED", "CASE_UPDATE");

        assertThat(status(send(get(checksPath("")), strangerToken, null))).as("not assigned").isEqualTo(403);
        assertThat(status(send(post(checksPath("")), strangerToken, obj("type", "PAN")))).isEqualTo(403);
        assertThat(status(send(put(checkPath), strangerToken, obj("version", 0, "title", "x", "status", "PENDING", "hasAttestation", false)))).isEqualTo(403);
        assertThat(status(send(delete(checkPath), strangerToken, null))).isEqualTo(403);

        assertThat(status(send(get(checksPath("")), readerOnly, null))).as("reading is fine").isEqualTo(200);
        assertThat(status(send(post(checksPath("")), readerOnly, obj("type", "PAN")))).as("no CHECK_UPDATE").isEqualTo(403);
        assertThat(status(send(get(checksPath("")), null, null))).isEqualTo(401);
        assertThat(status(send(post(checksPath("")), null, obj("type", "PAN")))).isEqualTo(401);
    }

    @Test
    void aLockedCaseRefusesEveryChangeToItsChecks() throws Exception {
        JsonNode check = addCheck(analyst, "PAN");
        String id = check.get("id").asText();
        jdbc.update("UPDATE cases.cases SET lifecycle = 'IN_REVIEW' WHERE id = ?::uuid", caseId);

        assertThat(status(send(post(checksPath("")), analyst, obj("type", "PAN")))).isEqualTo(409);
        assertThat(status(save(analyst, check, "status", "VERIFIED"))).isEqualTo(409);
        assertThat(status(send(delete(checksPath("/" + id)), analyst, null))).isEqualTo(409);
        assertThat(status(send(patch(checksPath("/order")), analyst, obj("ids", List.of(id))))).isEqualTo(409);
        assertThat(status(send(post(checksPath("/" + id + "/free-sections")), analyst, obj("kind", "TEXT", "text", "x")))).isEqualTo(409);
        assertThat(status(send(get(checksPath("")), analyst, null))).as("reading still works").isEqualTo(200);
    }

    // ---- deleting, free sections --------------------------------------------------------------------------------------------------

    @Test
    void deletingACheckRemovesItsFieldsDetailsAndFreeSections() throws Exception {
        JsonNode court = addCheck(analyst, "COURT");
        String id = court.get("id").asText();
        send(post(checksPath("/" + id + "/free-sections")), analyst, obj("kind", "TEXT", "text", "A note"));

        assertThat(status(send(delete(checksPath("/" + id)), analyst, null))).isEqualTo(204);
        assertThat(listChecks()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT (SELECT count(*) FROM cases.check_fields) + (SELECT count(*) FROM cases.check_details)"
                + " + (SELECT count(*) FROM cases.check_free_sections)", Integer.class)).isZero();
        assertThat(status(send(delete(checksPath("/" + id)), analyst, null))).isEqualTo(404);
        assertThat(auditActionsSinceStart()).contains("CHECK_DELETED");
    }

    @Test
    void freeTextSectionsCanBeAddedEditedAndRemovedButImagesWaitForDocuments() throws Exception {
        JsonNode check = addCheck(analyst, "POLICE");
        String base = checksPath("/" + check.get("id").asText() + "/free-sections");

        MvcResult added = send(post(base), analyst, obj("kind", "TEXT", "text", "  Visited the station  "));
        assertThat(status(added)).isEqualTo(201);
        JsonNode section = body(added).get("freeSections").get(0);
        assertThat(section.get("text").asText()).isEqualTo("Visited the station");
        String sectionId = section.get("id").asText();
        send(post(base), analyst, obj("kind", "TEXT", "text", "Second note"));

        MvcResult updated = send(put(base + "/" + sectionId), analyst, obj("text", "Changed"));
        assertThat(status(updated)).isEqualTo(200);
        assertThat(body(updated).get("freeSections").get(0).get("text").asText()).isEqualTo("Changed");
        assertThat(body(updated).get("freeSections")).hasSize(2);

        assertThat(status(send(post(base), analyst, obj("kind", "IMAGE", "text", null)))).as("images need documents").isEqualTo(400);
        assertThat(status(send(post(base), analyst, obj("kind", "TEXT", "text", "x".repeat(5001))))).as("too long").isEqualTo(400);
        assertThat(status(send(put(base + "/" + UUID.randomUUID()), analyst, obj("text", "x")))).isEqualTo(404);

        MvcResult removed = send(delete(base + "/" + sectionId), analyst, null);
        assertThat(status(removed)).isEqualTo(200);
        assertThat(body(removed).get("freeSections")).hasSize(1);
        assertThat(listChecks().get(0).get("freeSections")).hasSize(1);
    }

    @Test
    void theCommentsPageSwitchIsSavedReturnedAndLeftAloneWhenNotSent() throws Exception {
        JsonNode check = addCheck(analyst, "COURT");
        assertThat(check.get("commentsOnNextPage").asBoolean()).as("off by default").isFalse();

        JsonNode on = saved(analyst, check, "commentsOnNextPage", true);
        assertThat(on.get("commentsOnNextPage").asBoolean()).isTrue();
        assertThat(listChecks().get(0).get("commentsOnNextPage").asBoolean()).isTrue();

        JsonNode untouched = saved(analyst, on, "remarks", "Some comment");
        assertThat(untouched.get("commentsOnNextPage").asBoolean()).as("a save that does not mention it keeps it").isTrue();

        JsonNode off = saved(analyst, untouched, "commentsOnNextPage", false);
        assertThat(off.get("commentsOnNextPage").asBoolean()).isFalse();
    }

    @Test
    void aTextBlockMayBeLeftEmptyAsABlankSpaceForHandwriting() throws Exception {
        JsonNode check = addCheck(analyst, "POLICE");
        String base = checksPath("/" + check.get("id").asText() + "/free-sections");

        MvcResult blank = send(post(base), analyst, obj("kind", "TEXT", "text", "   "));
        assertThat(status(blank)).as("an empty text block is a blank space").isEqualTo(201);
        JsonNode section = body(blank).get("freeSections").get(0);
        assertThat(section.get("kind").asText()).isEqualTo("TEXT");
        assertThat(section.get("text").isNull()).as("stored without text").isTrue();

        assertThat(status(send(post(base), analyst, obj("kind", "TEXT")))).as("no text at all is the same").isEqualTo(201);
        String id = section.get("id").asText();
        MvcResult filled = send(put(base + "/" + id), analyst, obj("text", "Written later"));
        assertThat(body(filled).get("freeSections").get(0).get("text").asText()).isEqualTo("Written later");
        MvcResult cleared = send(put(base + "/" + id), analyst, obj("text", ""));
        assertThat(status(cleared)).as("a block can be emptied again").isEqualTo(200);
        assertThat(body(cleared).get("freeSections").get(0).get("text").isNull()).isTrue();
        assertThat(listChecks().get(0).get("freeSections")).hasSize(2);
    }

    // ---- what the checks do to the rest of the case ----------------------------------------------------------------------------------

    @Test
    void checksFeedTheOverviewProgressAndValidation() throws Exception {
        JsonNode aadhaar = addCheck(analyst, "AADHAAR");
        JsonNode pan = addCheck(analyst, "PAN");
        saved(analyst, aadhaar, "status", "VERIFIED", "requestedDate", "2026-03-01", "completedDate", "2026-03-02",
                "fields", List.of(fieldInput("aadhaar_number", VALID_AADHAAR)));
        // Saving the first check moved the later checks too (date master), so fetch the current PAN first.
        saved(analyst, listChecks().get(1), "status", "DISCREPANCY");
        assertThat(pan.get("id").asText()).isEqualTo(listChecks().get(1).get("id").asText());

        JsonNode view = body(send(get("/api/cases/" + caseId), superToken(), null));
        assertThat(view.get("overview").get("auto").get("total").asInt()).isEqualTo(2);
        assertThat(view.get("overview").get("auto").get("completed").asInt()).isEqualTo(2);
        assertThat(view.get("overview").get("auto").get("overallStatus").asText()).isEqualTo("Discrepancy");

        JsonNode progress = body(send(get("/api/cases/" + caseId + "/progress"), superToken(), null));
        assertThat(progress.get("totalChecks").asInt()).isEqualTo(2);
        assertThat(progress.get("checksByStatus").get("VERIFIED").asInt()).isEqualTo(1);
        assertThat(progress.get("checksByStatus").get("DISCREPANCY").asInt()).isEqualTo(1);
        JsonNode checksSection = null;
        for (JsonNode s : progress.get("sections")) {
            if (s.get("key").asText().equals("checks")) {
                checksSection = s;
            }
        }
        assertThat(checksSection.get("state").asText()).as("has checks, and PAN still lacks things").isEqualTo("WARNING");

        JsonNode validation = body(send(get("/api/cases/" + caseId + "/validation"), superToken(), null));
        List<String> errorFields = new ArrayList<>();
        validation.get("errors").forEach(e -> errorFields.add(e.get("field").asText()));
        assertThat(errorFields).as("a check exists now").doesNotContain("checks");
        String warnings = validation.get("warnings").toString();
        assertThat(warnings).contains("Identity Verification (PAN): requested date is missing.")
                .contains("Identity Verification (PAN): PAN Number is missing.")
                .doesNotContain("Identity Verification (Aadhaar): Aadhaar Number is missing")
                .doesNotContain("Identity Verification (Aadhaar): requested date");
    }

    @Test
    void aPendingCheckIsWarnedAboutAndTheOverviewShowsInProgress() throws Exception {
        addCheck(analyst, "OIG");
        JsonNode validation = body(send(get("/api/cases/" + caseId + "/validation"), superToken(), null));
        assertThat(validation.get("warnings").toString()).contains("OIG Exclusions: status is still Pending.");
        JsonNode view = body(send(get("/api/cases/" + caseId), superToken(), null));
        assertThat(view.get("overview").get("auto").get("overallStatus").asText()).isEqualTo("In Progress");
        assertThat(view.get("overview").get("auto").get("completed").asInt()).isZero();
    }

    @Test
    void checkChangesAreAuditedWithTheCaseAndWithoutFieldValues() throws Exception {
        JsonNode check = addCheck(analyst, "REFERENCE");
        saved(analyst, check, "status", "VERIFIED", "fields", List.of(fieldInput("referee_name", "Confidential Person")));

        List<Map<String, Object>> rows = jdbc.queryForList("SELECT action, after::text AS after FROM auth.audit_log WHERE case_id = ?::uuid"
                + " AND action IN ('CHECK_ADDED', 'CHECK_SAVED') ORDER BY at", caseId);
        assertThat(rows).extracting(r -> r.get("action")).containsExactly("CHECK_ADDED", "CHECK_SAVED");
        assertThat(rows.get(1).get("after").toString()).contains("referee_name").contains("VERIFIED").doesNotContain("Confidential Person");
    }
}
