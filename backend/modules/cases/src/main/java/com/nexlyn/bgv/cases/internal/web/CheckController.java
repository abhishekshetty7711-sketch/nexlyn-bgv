package com.nexlyn.bgv.cases.internal.web;

import com.nexlyn.bgv.cases.internal.checktype.CheckTypeDefinition;
import com.nexlyn.bgv.cases.internal.checktype.CheckTypeRegistry;
import com.nexlyn.bgv.cases.internal.domain.FreeSectionKind;
import com.nexlyn.bgv.cases.internal.service.CheckService;
import com.nexlyn.bgv.cases.internal.service.CheckViews.CheckView;
import com.nexlyn.bgv.cases.internal.service.CheckViews.RevealedValue;
import com.nexlyn.bgv.common.enums.CheckStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * {@code /api/check-types} and {@code /api/cases/{id}/checks/**} (CLAUDE.md {@literal §9.2}). Thin:
 * permissions, the per-case access rule and all validation live in {@link CheckService}.
 */
@RestController
@RequestMapping("/api")
public class CheckController {

    record AddCheckRequest(@NotBlank @Size(max = 40) String type) {
    }

    record FieldRequest(@NotBlank @Size(max = 60) String key, @Size(max = 12000) String value, Boolean verifiedTick,
                        Boolean manual, Boolean clear) {
    }

    record DetailRequest(@Size(max = 200) String label, @Size(max = 1000) String value) {
    }

    record SaveCheckRequest(
            @NotNull Long version,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 2000) String summaryDescription,
            @Size(max = 2000) String thisCardVerifies,
            @NotNull CheckStatus status,
            @Size(max = 50) String verificationType,
            LocalDate requestedDate,
            LocalDate completedDate,
            @Size(max = 20000) String remarks,
            boolean hasAttestation,
            @Size(max = 50) String barCouncilNo,
            @Size(max = 5000) String disclaimer,
            @Size(max = 100) List<@Valid FieldRequest> fields,
            @Size(max = 30) List<@Valid DetailRequest> details,
            Boolean commentsOnNextPage) {
    }

    record OrderRequest(@NotNull @Size(max = 100) List<UUID> ids) {
    }

    record FreeSectionRequest(@NotNull FreeSectionKind kind, @Size(max = 5000) String text, UUID documentId) {
    }

    record FreeSectionTextRequest(@Size(max = 5000) String text) {
    }

    private final CheckService checks;
    private final CheckTypeRegistry registry;

    public CheckController(CheckService checks, CheckTypeRegistry registry) {
        this.checks = checks;
        this.registry = registry;
    }

    /** The definitions the frontend draws its check forms from. Any signed-in admin may read them. */
    @GetMapping("/check-types")
    public List<CheckTypeDefinition> checkTypes() {
        return registry.all();
    }

    @GetMapping("/cases/{id}/checks")
    public ResponseEntity<List<CheckView>> list(@PathVariable UUID id) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(checks.list(id));
    }

    @PostMapping("/cases/{id}/checks")
    public ResponseEntity<CheckView> add(@PathVariable UUID id, @Valid @RequestBody AddCheckRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.CACHE_CONTROL, "no-store").body(checks.add(id, body.type()));
    }

    @PutMapping("/cases/{id}/checks/{checkId}")
    public ResponseEntity<CheckView> save(@PathVariable UUID id, @PathVariable UUID checkId, @Valid @RequestBody SaveCheckRequest b) {
        CheckService.SaveCheckInput input = new CheckService.SaveCheckInput(b.title(), b.summaryDescription(), b.thisCardVerifies(),
                b.status(), b.verificationType(), b.requestedDate(), b.completedDate(), b.remarks(), b.hasAttestation(),
                b.barCouncilNo(), b.disclaimer(),
                b.fields() == null ? null : b.fields().stream().map(f -> new CheckService.FieldInput(f.key(), f.value(), f.verifiedTick(), f.manual(), f.clear())).toList(),
                b.details() == null ? null : b.details().stream().map(d -> new CheckService.DetailInput(d.label(), d.value())).toList(),
                b.commentsOnNextPage());
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(checks.save(id, checkId, b.version(), input));
    }

    @PatchMapping("/cases/{id}/checks/order")
    public List<CheckView> reorder(@PathVariable UUID id, @Valid @RequestBody OrderRequest body) {
        return checks.reorder(id, body.ids());
    }

    @DeleteMapping("/cases/{id}/checks/{checkId}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @PathVariable UUID checkId) {
        checks.delete(id, checkId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/cases/{id}/checks/{checkId}/free-sections")
    public ResponseEntity<CheckView> addFreeSection(@PathVariable UUID id, @PathVariable UUID checkId,
                                                    @Valid @RequestBody FreeSectionRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(checks.addFreeSection(id, checkId, body.kind(), body.text(), body.documentId()));
    }

    @PutMapping("/cases/{id}/checks/{checkId}/free-sections/{sectionId}")
    public CheckView updateFreeSection(@PathVariable UUID id, @PathVariable UUID checkId, @PathVariable UUID sectionId,
                                       @Valid @RequestBody FreeSectionTextRequest body) {
        return checks.updateFreeSection(id, checkId, sectionId, body.text());
    }

    @DeleteMapping("/cases/{id}/checks/{checkId}/free-sections/{sectionId}")
    public CheckView deleteFreeSection(@PathVariable UUID id, @PathVariable UUID checkId, @PathVariable UUID sectionId) {
        return checks.deleteFreeSection(id, checkId, sectionId);
    }

    /** The one endpoint that returns a real Aadhaar / PAN / UAN. Needs PII_UNMASK; audited; never cached. */
    @GetMapping("/cases/{id}/checks/{checkId}/fields/{key}/reveal")
    public ResponseEntity<RevealedValue> reveal(@PathVariable UUID id, @PathVariable UUID checkId, @PathVariable String key) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(checks.reveal(id, checkId, key));
    }
}
