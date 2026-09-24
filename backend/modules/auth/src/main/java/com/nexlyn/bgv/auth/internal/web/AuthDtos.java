package com.nexlyn.bgv.auth.internal.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Request and response bodies of {@code /api/auth/**}. Secrets are never echoed back except where noted. */
final class AuthDtos {

    private AuthDtos() {
    }

    record LoginRequest(
            @NotBlank @Size(max = 254) String email,
            @NotBlank @Size(max = 1024) String password) {
    }

    record AcceptInvitationRequest(
            @NotBlank @Size(max = 200) String inviteToken,
            @NotBlank @Size(max = 200) String fullName,
            @NotBlank @Size(max = 1024) String password) {
    }

    /** {@code status} is {@code 2FA_REQUIRED} or {@code 2FA_SETUP_REQUIRED}. */
    record ChallengeResponse(String status, String challengeToken, long expiresInSeconds) {
    }

    record SetupRequest(@NotBlank @Size(max = 4096) String challengeToken) {
    }

    /** Shown once during enrolment: the Base32 secret and the {@code otpauth://} link for a QR code. */
    record SetupResponse(String secret, String otpauthUri) {
    }

    record CodeRequest(
            @NotBlank @Size(max = 4096) String challengeToken,
            @NotBlank @Size(max = 32) String code) {
    }

    /** {@code backupCodes} is only present right after 2FA enrolment and is never shown again. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record TokenResponse(String accessToken, String tokenType, long expiresInSeconds, List<String> backupCodes) {
    }
}
