package com.nexlyn.bgv.cases.internal.checktype;

import com.nexlyn.bgv.cases.internal.checktype.CheckTypeDefinition.FieldDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class CheckTypeRegistryTest {

    private final CheckTypeRegistry registry = new CheckTypeRegistry();

    @Test
    void loadsAllEighteenTypesFromTheSpecInOrder() {
        List<String> codes = registry.all().stream().map(CheckTypeDefinition::code).toList();
        assertThat(codes).containsExactly("AADHAAR", "PAN", "COURT", "POLICE", "ADDRESS", "EMPLOYMENT", "EDUCATION",
                "REFERENCE", "UAN", "CREDIT", "DRUG_TEST", "DIRECTORSHIP", "GAP_REVIEW", "WORLD_CHECK", "OIG",
                "ADVERSE_MEDIA", "SOCIAL_MEDIA", "RESUME_REVIEW");
    }

    @Test
    void everyTypeHasItsOwnFieldsInsteadOfSharingTheAadhaarList() {
        // The reference tool showed "Aadhaar Number" on employment checks (CLAUDE.md 6.4 problem 2).
        for (CheckTypeDefinition type : registry.all()) {
            if (!type.code().equals("AADHAAR")) {
                assertThat(type.fields()).as(type.code()).noneMatch(f -> f.key().equals("aadhaar_number"));
            }
        }
        assertThat(registry.find("EMPLOYMENT").orElseThrow().fields()).extracting(FieldDefinition::key)
                .contains("company", "designation", "date_of_joining", "eligible_for_rehire");
        assertThat(registry.find("DRUG_TEST").orElseThrow().fields()).extracting(FieldDefinition::key)
                .contains("panel_1", "panel_5", "overall_result");
    }

    @Test
    void aadhaarAndPanNumbersAreSensitiveRequiredAndNeverPrefilled() {
        for (String code : List.of("AADHAAR", "PAN", "UAN")) {
            FieldDefinition number = registry.find(code).orElseThrow().fields().get(0);
            assertThat(number.sensitive()).as(code).isTrue();
            assertThat(number.required()).as(code).isTrue();
            assertThat(number.prefill()).as(code).isNull();
        }
        assertThat(registry.find("AADHAAR").orElseThrow().fields().get(0).type()).isEqualTo(FieldType.AADHAAR);
        assertThat(registry.find("PAN").orElseThrow().fields().get(0).type()).isEqualTo(FieldType.PAN);
    }

    @Test
    void onlyAadhaarPanAndUanHoldSensitiveFields() {
        Set<String> sensitive = registry.all().stream()
                .filter(t -> t.fields().stream().anyMatch(FieldDefinition::sensitive))
                .map(CheckTypeDefinition::code).collect(Collectors.toSet());
        assertThat(sensitive).containsExactlyInAnyOrder("AADHAAR", "PAN", "UAN");
    }

    @Test
    void courtChecksStartWithAnAttestationAndTheirExtraDetails() {
        CheckTypeDefinition court = registry.find("COURT").orElseThrow();
        assertThat(court.attestationDefault()).isTrue();
        assertThat(court.details()).extracting(CheckTypeDefinition.DetailDefault::label).containsExactly("Court Type", "Jurisdiction");
        assertThat(court.details().get(1).defaultValue()).isEqualTo("Permanent Address");
        registry.all().stream().filter(t -> !t.code().equals("COURT")).forEach(t -> assertThat(t.attestationDefault()).as(t.code()).isFalse());
    }

    @Test
    void verificationTypeDefaultsToStandardButElectronicForAadhaarAndPan() {
        assertThat(registry.find("AADHAAR").orElseThrow().verificationTypeDefault()).isEqualTo("Electronic");
        assertThat(registry.find("PAN").orElseThrow().verificationTypeDefault()).isEqualTo("Electronic");
        assertThat(registry.find("EMPLOYMENT").orElseThrow().verificationTypeDefault()).isEqualTo("Standard");
        assertThat(registry.find("COURT").orElseThrow().verificationTypeDefault()).isEqualTo("Standard");
    }

    @Test
    void identityCourtAddressEmploymentAndEducationGroupTheirTypesAndEveryOtherTypeStandsAlone() {
        assertThat(registry.find("AADHAAR").orElseThrow().groupKey()).isEqualTo("identity");
        assertThat(registry.find("PAN").orElseThrow().groupKey()).isEqualTo("identity");
        assertThat(registry.find("COURT").orElseThrow().groupKey()).isEqualTo("court");
        assertThat(registry.find("ADDRESS").orElseThrow().groupKey()).isEqualTo("address");
        assertThat(registry.find("EMPLOYMENT").orElseThrow().groupKey()).isEqualTo("employment");
        assertThat(registry.find("EDUCATION").orElseThrow().groupKey()).isEqualTo("education");
        assertThat(registry.find("POLICE").orElseThrow().groupKey()).isEqualTo("POLICE");
        assertThat(registry.find("WORLD_CHECK").orElseThrow().groupKey()).isEqualTo("WORLD_CHECK");
    }

    @Test
    void prefilledFieldsPointAtRealCandidateProperties() {
        for (CheckTypeDefinition type : registry.all()) {
            type.fields().stream().filter(f -> f.prefill() != null)
                    .forEach(f -> assertThat(CheckTypeRegistry.PREFILL_PROPERTIES).as(type.code() + "." + f.key()).contains(f.prefill()));
        }
        assertThat(registry.find("AADHAAR").orElseThrow().field("full_name").prefill()).isEqualTo("fullName");
        assertThat(registry.find("AADHAAR").orElseThrow().field("father_name").labelByParentType()).isTrue();
        assertThat(registry.find("EMPLOYMENT").orElseThrow().field("employee_id").prefill()).isEqualTo("employeeId");
    }

    @Test
    void selectsHaveOptionsAndRepeatablesHaveColumns() {
        assertThat(registry.find("ADDRESS").orElseThrow().field("verification_mode").options()).containsExactly("Field", "Postal", "Digital");
        FieldDefinition gaps = registry.find("GAP_REVIEW").orElseThrow().field("gaps");
        assertThat(gaps.type()).isEqualTo(FieldType.REPEATABLE);
        assertThat(gaps.itemFields()).extracting(CheckTypeDefinition.ItemField::key).containsExactly("from", "to", "reason");
    }

    @Test
    void searchStyleChecksShareTheSameFieldShape() {
        for (String code : List.of("WORLD_CHECK", "OIG", "ADVERSE_MEDIA", "SOCIAL_MEDIA", "RESUME_REVIEW")) {
            assertThat(registry.find(code).orElseThrow().fields()).extracting(FieldDefinition::key)
                    .containsExactly("sources_searched", "search_date", "hits_found", "result_summary");
        }
    }

    @Test
    void unknownCodesAreSimplyAbsent() {
        assertThat(registry.find("NOPE")).isEmpty();
        assertThat(registry.find(null)).isEmpty();
    }
}
