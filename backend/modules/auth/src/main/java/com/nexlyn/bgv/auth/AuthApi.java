package com.nexlyn.bgv.auth;

import java.util.Optional;

/** What other modules may ask the auth module (CLAUDE.md {@literal §4.2} rule 2). */
public interface AuthApi {

    /** The admin making the current request, or empty outside an authenticated request. */
    Optional<AdminPrincipal> currentAdmin();

    /** The current admin, or throws {@code AccessDeniedException} if nobody is signed in. */
    AdminPrincipal requireCurrentAdmin();
}
