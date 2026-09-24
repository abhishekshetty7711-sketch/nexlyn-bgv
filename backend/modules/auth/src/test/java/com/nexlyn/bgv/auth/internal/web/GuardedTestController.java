package com.nexlyn.bgv.auth.internal.web;

import com.nexlyn.bgv.auth.CaseAccessPolicy;
import com.nexlyn.bgv.auth.CaseAction;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/** Test-only endpoints that stand in for the real, permission-guarded endpoints of later phases. */
@RestController
class GuardedTestController {

    private final CaseAccessPolicy policy;

    GuardedTestController(CaseAccessPolicy policy) {
        this.policy = policy;
    }

    @GetMapping("/api/test/needs-user-manage")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    Map<String, String> needsUserManage() {
        return Map.of("ok", "true");
    }

    @GetMapping("/api/test/cases/{id}")
    Map<String, String> readCase(@PathVariable UUID id) {
        policy.check(id, CaseAction.READ);
        return Map.of("ok", "true");
    }

    @GetMapping("/api/test/cases/{id}/update")
    Map<String, String> updateCase(@PathVariable UUID id) {
        policy.check(id, CaseAction.UPDATE);
        return Map.of("ok", "true");
    }

    @GetMapping("/api/test/cases/{id}/delete")
    Map<String, String> deleteCase(@PathVariable UUID id) {
        policy.check(id, CaseAction.DELETE);
        return Map.of("ok", "true");
    }
}
