# Refresh Token Rotation Flow

**US-005** — Token rotation with reuse detection.

## Security Model

- Each `/refresh` call issues a **new** access token + **new** refresh token
- The **old** refresh token is immediately revoked (`revoked_at` set, `replaced_by` pointing to new)
- If a **revoked** token is presented again → all tokens for that user are revoked (chain compromise)
- Expired tokens → 401, must re-login via `/login`

## Sequence Diagram

```mermaid
sequenceDiagram
    participant C as Client
    participant S as Server
    participant DB as PostgreSQL

    C->>S: POST /v1/auth/refresh {refreshToken: refresh1}
    S->>S: hash(refresh1) → hash1
    S->>DB: SELECT * FROM refresh_tokens WHERE token_hash = hash1

    alt token not found
        DB-->>S: empty
        S-->>C: 401 {type: invalid-refresh-token}
    else token revoked (revoked_at != null)
        DB-->>S: RefreshToken {revoked_at: <timestamp>}
        S->>DB: UPDATE refresh_tokens SET revoked_at = now() WHERE user_id = ? AND revoked_at IS NULL
        S-->>C: 401 {type: refresh-token-reuse}
    else token expired (expires_at < now)
        DB-->>S: RefreshToken {expires_at: <past>}
        S-->>C: 401 {type: invalid-refresh-token}
    else token valid
        DB-->>S: RefreshToken {user_id, ...}
        S->>S: generate refresh2 (256-bit random, base64url)
        S->>DB: INSERT refresh_tokens (user_id, hash(refresh2), expires_at)
        DB-->>S: RefreshToken {id: newId}
        S->>DB: UPDATE refresh_tokens SET revoked_at=now(), replaced_by=newId WHERE token_hash=hash1
        S->>S: generateAccessToken(userId, roles) → access2
        S-->>C: 200 {accessToken: access2, refreshToken: refresh2, expiresIn: 900}
    end
```

## DB Schema (V2 migration)

```
refresh_tokens
├── id           UUID PK
├── user_id      UUID FK → users.id (ON DELETE CASCADE)
├── token_hash   VARCHAR(255) UNIQUE  ← SHA-256, never plaintext
├── expires_at   TIMESTAMP
├── revoked_at   TIMESTAMP NULL       ← set on revocation
├── replaced_by  UUID NULL FK → refresh_tokens.id  ← forensic chain
└── created_at   TIMESTAMP
```

## Security Notes

- Token is stored as SHA-256 hash, never plaintext
- On reuse detection, ALL active tokens for that user are revoked (not just the reused one)
- `replaced_by` chain allows forensic trace of token lineage
- BCrypt not used for refresh token hash (high-entropy random → SHA-256 sufficient)
