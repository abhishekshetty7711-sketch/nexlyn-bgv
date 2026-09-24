package com.nexlyn.bgv.cases;

import java.util.List;

/**
 * What is still wrong or doubtful about a case (CLAUDE.md section 7.1). Errors block generating or
 * submitting a report; warnings can be accepted after a confirmation.
 */
public record CaseValidation(List<Issue> errors, List<Issue> warnings) {

    public record Issue(String section, String field, String message) {
    }
}
