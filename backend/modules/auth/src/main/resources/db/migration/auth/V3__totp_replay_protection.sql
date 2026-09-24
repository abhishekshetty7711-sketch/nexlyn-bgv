-- Remember the last accepted TOTP time step so the same one-time code cannot be used twice
-- (RFC 6238 section 5.2). NULL = no code accepted yet.
ALTER TABLE totp_secrets ADD COLUMN last_used_step BIGINT;
