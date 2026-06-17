# Validation — `prompt=none` (silent SSO) support in DHIS2

Source-level investigation of whether DHIS2's authorize endpoint honors the
OIDC `prompt=none` parameter, which is the mechanism for zero-click SSO when OB
is embedded in a DHIS2 iframe and the user already has a live DHIS2 session.

## Verdict

| | v42 (Spring Authorization Server) | v40 (legacy UAA) |
|---|---|---|
| `prompt=none` supported | **Yes** | **No** |
| Session + consented → silent `code` | Yes (immediate 302 with `?code=`) | No silent path |
| No session → `login_required` | Yes (OIDC error redirect) | No — renders login page, ignores param |
| Consent blocker | DHIS2 defaults `requireAuthorizationConsent=true` | No DHIS2-exposed auto-approve knob |

**Conclusion:** zero-click iframe SSO is feasible on **v42 only**. v40 deployments
must fall back to log-in-once-in-frame (the `SameSite=None` session cookie then
keeps the user logged in on later loads).

## v42 — Spring Authorization Server

- DHIS2 2.42 ships `spring-authorization-server 1.5.5` (`dhis-2/pom.xml`). OIDC
  `prompt=none` support landed in SAS 1.4.0 GA (2024-11-19), so it is present.
- Behavior is library-provided and spec-compliant
  (`OAuth2AuthorizationCodeRequestAuthenticationProvider`, tag 1.5.5):
  - not authenticated + `prompt=none` → `login_required`
  - consent required + `prompt=none` → `consent_required`
  - consent skipped when client `requireAuthorizationConsent=false`
- DHIS2 does **not** customize the prompt logic (`AuthorizationServerConfig`);
  it uses the stock SAS endpoint.
- **Gotcha:** `Dhis2OAuth2ClientServiceImpl` defaults new clients to
  `requireAuthorizationConsent(true)`. For true silent auth the OB client must
  be registered with consent disabled, or the user must consent once (DHIS2
  persists consent).
- Requires `scope=openid` (prompt validator only engages for OIDC requests) —
  the v42 profile already sends `openid username`.

## v40 — legacy `spring-security-oauth2`

- DHIS2 ≤2.41 uses `spring-security-oauth2 2.5.2.RELEASE` (EOL). The legacy
  `AuthorizationEndpoint` never implemented OIDC `prompt`. `prompt=none` is
  silently ignored: no session → login page (not `login_required`); no
  DHIS2-exposed `autoApprove` on the `OAuth2Client` domain.

## Live confirmation — CONFIRMED ✓

Tested 2026-06-11 against a live DHIS2 **2.42.4.1** instance (Spring
Authorization Server). Client `openboxes-dev`: `scopes=openid,username`,
`require-authorization-consent=true`, `require-proof-key=false`,
`redirectUris=http://localhost:8080/openboxes/oauth/dhis2/callback`.

Authorize request: `response_type=code`, `prompt=none`, PKCE `S256`,
`scope=openid username`.

| Case | Setup | Result (`Location`) |
|---|---|---|
| A — no session | unauthenticated request | `…/callback?error=login_required&error_description=OAuth 2.0 Parameter: prompt&state=test123` ✓ |
| C — live session | session via `POST /api/auth/login` | `…/callback?code=rJ-Pk_6W…&state=test123` — **silent code, no UI** ✓ |

Notes:
- `prompt=none` is honored: no session yields the OIDC `login_required` error
  redirect (not a rendered login page) — exactly what the break-out flow (D5)
  keys off.
- With a live session the authorize endpoint returned an authorization `code`
  with zero UI. Consent did not block because the admin user had already
  consented for this client; a never-consented user with
  `require-authorization-consent=true` would get `error=consent_required` until
  consent is recorded or the client is set to `false`.
- HTTP Basic auth on the authorize request does NOT establish a SAS session
  (returned `login_required`) — only a real login-session cookie counts.
- Token exchange was not exercised (plaintext client secret not on hand); the
  silent `code` issuance is sufficient to confirm the flow.
- spring-security #18647 (a `prompt=none` regression on the **7.x** line) does
  NOT manifest here — DHIS2 2.42.4.1 is on the 6.5.x / SAS 1.5.x line.

**Verdict: silent SSO is viable on v42 — Phase 5 is safe to implement.**
