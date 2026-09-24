package com.nexlyn.bgv.auth.internal.service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Result of the password step of login. Callers must answer {@link InvalidCredentials} with one
 * generic message so an attacker cannot tell a wrong password from an unknown or disabled account.
 */
public sealed interface LoginOutcome {

    /** Password was correct. {@code mfaEnabled=false} means the admin must set up 2FA next. */
    record PasswordVerified(UUID adminId, boolean mfaEnabled) implements LoginOutcome {
    }

    record InvalidCredentials() implements LoginOutcome {
    }

    record Locked(Instant until) implements LoginOutcome {
    }

    record RateLimited(Duration retryAfter) implements LoginOutcome {
    }
}
