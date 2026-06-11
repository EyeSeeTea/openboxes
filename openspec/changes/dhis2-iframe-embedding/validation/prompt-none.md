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

## Open item — confirm live (cannot be settled from source)

Spring-security #18647 reports a `prompt=none` regression in spring-security
**7.x**; DHIS2 2.42 is on the **6.5.x / SAS 1.5.x** line, so it should not
apply — but verify the actual `login_required` / silent-`code` redirects on the
target DHIS2 instance before committing to the silent-SSO design. See the
three-case manual test (no session / session-not-consented / session-consented).
