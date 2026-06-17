## Why

The `dhis2-auth` SSO flow (archived `2026-06-04-dhis2-oauth-core`) was built and
validated against DHIS2 v40, which ships the legacy UAA OAuth2 provider at
`/uaa/oauth/*`. DHIS2 **2.42** removed that provider (PR #17634 / DHIS2-15246)
and replaced it with a **Spring Authorization Server** (OAuth 2.1 / OIDC) at
`/oauth2/*` — so the existing flow returns 404 against any v42 instance. We need
v42-compatible login without breaking the working v40 path. Full version
archaeology and the endpoint/flow delta live in
`.claude/.context/notes/dhis2-oauth-v40-vs-v42.md`.

## What Changes

- Add a **v42 (Spring Authorization Server / OAuth 2.1 / OIDC) profile** to the
  DHIS2 OAuth client, selected by config. The v40/UAA path is kept **byte-for-byte
  unchanged**; deployments choose the profile per environment.
- **PKCE (S256)**: the v42 path generates a code verifier + S256 challenge, stores
  the verifier in the session, sends `code_challenge`/`code_challenge_method` on
  the authorize redirect, and sends `code_verifier` on token exchange.
- **`client_secret_post` token auth (opt-in)**: the v42 path can send client
  credentials in the token-request body instead of the HTTP Basic header.
- **Endpoints/scopes via config**: v42 deployments point at `/oauth2/authorize`
  and `/oauth2/token`, with scopes `openid username`. (No `email` scope — it
  breaks token mint for users without an email.)
- **Username-based identity (the v42 change).** Live testing against DHIS2 2.42.4.1
  proved the SAS token is **rejected on all `/api/*` endpoints (401)**, including
  `/api/me`; the OIDC layer exposes only the `sub` claim (the username). So the
  v42 path identifies the user by **username** read from the `id_token` `sub`,
  not the DHIS2 UID. v40/v41 still fetch the UID via `/api/me` — unchanged.
- **Additive `Dhis2UserLink` schema change**: `dhis2_uid` becomes nullable (v42
  rows have no UID), `dhis2_username` becomes the unique, case-sensitive key, and
  a `deactivated_at` tombstone column is added. Registration branches on the data
  (`uid == null` → username path; else the existing UID path). v42 synthesizes
  placeholder names (OB requires first/last); the admin fills them at approval.
- No change to session establishment, the pending-access gate, the admin filter,
  or the login-page button. Those are profile-agnostic.

## Capabilities

### New Capabilities
<!-- None — this extends the existing dhis2-auth capability. -->

### Modified Capabilities
- `dhis2-auth`: the "DHIS2 OAuth login option", "Authorization Code flow
  completes", and identity-fetch requirements gain a v42 profile — PKCE,
  optional `client_secret_post`, `/oauth2/*` endpoints, and **username-based
  identity** (OIDC `sub`). The v40 behavior remains a supported profile;
  session establishment and pending-access visibility are unchanged.

## Impact

- **Custom backend code** under `org.pih.warehouse.custom.dhis2auth`:
  - `Dhis2OAuthService.groovy` — v42 handshake (PKCE in `buildAuthorizeUrl`,
    `code_verifier`/optional `client_secret_post` in `exchangeCode`) plus a new
    v42 identity method that reads `sub` from the `id_token` and returns
    `Dhis2User(uid: null, username: sub, ...)`. `fetchMe` (`/api/me`) is reused
    verbatim for v40/v41 only. `exchangeCode`/`AccessToken` gain an `idToken`.
  - `Dhis2OAuthController.groovy` — already stores/passes the PKCE verifier;
    `callback` resolves identity by profile (v40 → `fetchMe`; v42 → `sub`).
  - `Dhis2RegistrationService.groovy` — branch on `uid == null` (username
    register/link with placeholder names + tombstone-aware reuse) vs. the
    existing UID path. `Dhis2UserLink` gains `deactivated_at`.
- **Migration** under `grails-app/migrations/custom/`: make `dhis2_uid`
  nullable, make `dhis2_username` unique + not-null with a case-sensitive
  collation, add `deactivated_at`. Additive and reversible.
- **Config** (no upstream-code edit): keys under `openboxes.custom.dhis2.oauth.*`
  — `profile` (`v40` | `v42`), `clientAuth` (`basic` | `post`), existing
  `authorizeUrl`/`tokenUrl`/`scopes`/`redirectUri`. No `userinfoUrl`/`userUrl`
  for v42. Template documented in `docker/openboxes.client-template.yml`.
- **Tests**: extend `Dhis2OAuthServiceSpec`/`Dhis2OAuthControllerSpec` for the
  v42 handshake + `sub` extraction, and `Dhis2RegistrationService` for the
  username path, alongside the existing v40 assertions.
- **No new dependencies**, no new Grails plugins, **one additive schema change**,
  no new upstream touch points.
- **Operational**: v42 requires a DHIS2 sysadmin to enable the SAS and register
  the OB client with three API PATCHes (scopes, `clientAuthenticationMethods`,
  bcrypt `clientSecret`) the admin UI has no fields for. v42 users are identified
  by username only (no UID/name/email); the service-account UID upgrade path is
  recorded in design.md for any deployment that later needs it.
