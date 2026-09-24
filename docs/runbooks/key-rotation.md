# Replacing the login-signing key (`JWT_PRIVATE_KEY`)

Do this every 12 months, when a person who knew the key leaves, or immediately if it may have leaked.

Login tokens are valid for 15 minutes, so a planned rotation causes no sign-outs if the old key stays trusted for a
short while. Every token carries the name of the key that signed it (`JWT_KEY_ID`).

## Planned rotation

1. On a trusted machine make the new key (see [key-management.md](key-management.md)) and choose a new name,
   for example `2027-09`.
2. Get the **public** half of the *old* key (the one currently in `.env`):
   put the old private key in a file `old.pem` (with real line breaks) and run
   `openssl rsa -in old.pem -pubout | awk 'NF {printf "%s\\n", $0}'`. Delete `old.pem` afterwards.
3. Edit `.env`:
   - `JWT_PRIVATE_KEY` = the new private key, `JWT_KEY_ID` = the new name
   - `JWT_PREVIOUS_KEY_ID` = the old name, `JWT_PREVIOUS_PUBLIC_KEY` = the old public key from step 2
4. `docker compose --env-file .env up -d` (the backend restarts; sign-ins keep working).
5. After one hour (all old tokens have expired; refresh tokens are not affected by the signing key) empty
   `JWT_PREVIOUS_KEY_ID` and `JWT_PREVIOUS_PUBLIC_KEY` and run `docker compose --env-file .env up -d` again.
6. Update the offline copy of the key ([key-management.md](key-management.md)).

## If the key leaked (do not wait for step 5)

Leave the "previous" settings empty. Everyone is signed out at once, and any token made with the leaked key stops
working. Then follow [incident-response.md](incident-response.md).
