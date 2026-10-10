# Secret settings

The super-admin settings API never returns the original AI API key, SMS secret,
SMTP password, payment private key, webhook secret, or payment API secret.
Each field returns an empty string and a corresponding `Configured` boolean.
Absent, empty, or masked update values preserve the saved secret. A nonempty
value replaces it. `clearSecrets` explicitly lists fields to clear; conflicting
clear and replacement requests are rejected. Public keys and certificates are
not secrets and remain editable, collapsed by default.

These six system configuration values use AES-256-GCM with per-field authenticated
encryption and random nonces. Legacy plaintext is migrated on application startup.
Internal consumers receive decrypted values; generic configuration responses are
masked and configuration logs do not include values.
New operation-log payloads redact these fields; historical log responses are
also redacted. Valid JSON settings audit records are migrated in bounded batches
on startup, removing only the secret values while preserving other audit details.
Historical backups or non-JSON legacy records can still contain previous plaintext
credentials; rotate any previously exposed credentials.

The master key is stored outside the database, by default at
`${user.home}/.photo-exhibition/config.key` with owner-only permissions. Override
with `app.security.config-key-file` (or `APP_SECURITY_CONFIG_KEY_FILE`). Mount a
persistent private path in containers. Back up this file separately and restore
it when moving/restoring the database. All instances must share the same key.
Losing or replacing it makes existing encrypted credentials unreadable; the
application fails closed rather than silently replacing a missing decryption key.
Public keys and provider-specific storage configuration JSON are outside this
system-settings encryption scope.

## Verification

Java 11 targeted tests cover encryption, random nonces, missing/wrong keys,
tampering, legacy migration, masked responses, update semantics and audit
redaction. Frontend validation uses `npx vite build`.

Live MySQL/API and Chrome verification covers all six settings, preserving
existing values, explicit clearing, replacement, rejected conflicting requests,
ordinary-account and anonymous access denial, real save-button interactions,
exact multiline PEM roundtrip, collapsed public keys, and desktop/mobile layouts.
Temporary settings and account fixtures are restored/removed after verification.
