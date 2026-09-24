package com.nexlyn.bgv.cases.internal.service;

import com.nexlyn.bgv.common.enums.CheckStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * What the case workspace needs to know about a case's verification checks. Checks arrive in Phase 4;
 * until then every case has none. Phase 4 replaces {@link NoChecks} with the real source.
 */
public interface ChecksSummary {

    /** The status of each check of the case, in report order. */
    List<CheckStatus> statusesOf(UUID caseId);

    @Component
    class NoChecks implements ChecksSummary {
        @Override
        public List<CheckStatus> statusesOf(UUID caseId) {
            return List.of();
        }
    }
}
