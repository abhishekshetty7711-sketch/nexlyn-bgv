package com.nexlyn.bgv.auth.internal.service;

/** Where a request came from, for lockout, rate limiting and the audit trail. */
public record ClientInfo(String ip, String userAgent) {
}
