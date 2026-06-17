## Context

The archived `2026-06-04-dhis2-oauth-core` change built a hand-rolled OAuth2
Authorization Code client for DHIS2 SSO, validated end-to-end against a DHIS2
**v40** instance (legacy UAA provider at `/uaa/oauth/*`, identity at `/api/me`,
opaque token). The built code lives under `org.pih.warehouse.custom.dhis2auth`:

- `Dhis2OAuthService.groovy` — `buildAuthorizeUrl`, `exchangeCode`, `fetchMe`
  (`/api/me`, UID = `json.id`), PKCE helpers, `prepareAuthorize`.
- `Dhis2OAuthController.groovy` — `initiate` stores `state` + PKCE verifier;
  `callback` validates state, exchanges code, fetches identity, hands off to
  `Dhis2RegistrationService.findOrRegister` then `Dhis2SessionService`.
- `Dhis2RegistrationService`, `Dhis2SessionService`, `Dhis2UserLink`, admin
  filter, pending GSP — registration keys on the DHIS2 UID (`Dhis2UserLink.dhis2Uid`).

DHIS2 **2.42** removed the UAA provider (PR #17634 / DHIS2-15246) and replaced it
with a Spring Authorization Server (SAS), OAuth 2.1 / OIDC, at `/oauth2/*`.

**This design was revised after live testing against the DHIS2 2.42.4.1 server
(`http://172.16.0.99:18081`, SAS enabled).** The OAuth handshake works, but live
testing **overturned the previous D4 assumption** (that `/api/me` accepts a SAS
bearer token). It does not — see D4 below. Identity must come from the OIDC
`sub` claim (the username), not the DHIS2 UID. Receipts are cited inline as
`file:line` against the local checkout `/home/nonee/DEV/DHIS2/dhis2-core`
(branch `2.42`) and as live observations.

Constraints: JDK 8, Groovy 2.4, Grails 3.3.16, no Spring Security plugin,
upstream-isolation rules — all custom code under
`org.pih.warehouse.custom.dhis2auth`.

## Goals / Non-Goals

**Goals:**
- A v42 (SAS / OAuth 2.1 / OIDC) login path selectable by config, producing the
  same authenticated OB session and pending-access behavior as v40.
- The v40 path stays byte-for-byte unchanged; the choice is config.
- v42 identity keyed on the DHIS2 **username** (OIDC `sub`), the only identity
  field the SAS token reliably exposes to a relying party (D4).
- v42 is additive and easy to remove (D6).

**Non-Goals:**
- Reaching `/api/me` or any `/api/*` endpoint with the SAS token. Live testing
  proved it is rejected (D4); identity comes from the OIDC layer only.
- Fetching the DHIS2 UID, first/last name, or email for v42 users — structurally
  unavailable from the SAS token (D4). v40/v41 still fetch them via `/api/me`.
- Per-user DHIS2 `openId` mapping / "external authentication only" setup — the
  only documented way to make `/api/me` accept the token, rejected as invasive
  (D4 — Alternatives).
- JWT signature verification in OB — the `id_token` arrives over the TLS back
  channel directly from the token endpoint; OB reads its `sub` claim without
  verifying the signature.
- Refresh tokens, group→role / org-unit→location mapping, deactivation
  propagation, iframe embedding — unchanged from `dhis2-oauth-core` non-goals.

## Decisions

### D1: v42 is primarily a configuration change; code additions are opt-in hardening

The handshake (authorize → consent → code → token) reuses the v40 code with two
opt-in additions (PKCE + `client_secret_post`, D3). Verified live: token mint
against `openboxes-dev` succeeds, `/userinfo` accepts the token.

Decision: keep `custom.dhis2.oauth.profile` (`v40` default | `v42`). The `v42`
arm enables PKCE + `client_secret_post` for the handshake and **changes only the
identity step** (D4). The v40 arm is unchanged.

DHIS2-side client registration requires three settings the admin UI has no field
for, set via `PATCH /api/oAuth2Clients/{uid}` (and re-applied if the client is
edited in the UI, which wipes them): `scopes`, `clientAuthenticationMethods`,
and the `clientSecret` (stored plaintext; DHIS2 matches with bcrypt, so the
**bcrypt hash** goes in DHIS2 and the plaintext in OB). These are deployment
steps, not code.

### D2: Scopes — `openid` is sufficient; identity comes from the `sub` claim

The v42 identity is the username, read from the `id_token`'s `sub` claim, which
requires only the **`openid`** scope. The previous rationale ("`username` scope
required so `/api/me` resolves the bearer") is moot under D4 — OB never calls
`/api/me` on v42.

- **Default v42 scopes: `openid username`.** `openid` yields the `id_token`/`sub`;
  `username` is retained as harmless and forward-compatible (DHIS2's resolver
  would use it if `/api/me` is ever enabled per-user), but OB does not depend on
  a `username` *claim* — live testing showed the token carries only `sub`, not a
  separate `username` claim, and `sub` already equals the username.
- **Do NOT add `email` scope.** Token mint **throws** if the user has no email
  (`AuthorizationServerConfig.java:263-273`), hard-breaking login for emailless
  users (e.g. `admin`). OB does not need email for the username-keyed flow.

### D3: PKCE + `client_secret_post` as opt-in v42 handshake behavior

Unchanged from the prior design and still valid. The v42 arm generates an RFC
7636 S256 verifier/challenge (verifier stored in session beside `state`, cleared
after exchange) and supports `client_secret_post` via
`custom.dhis2.oauth.clientAuth` (`basic` default | `post`). Both client-auth
methods are accepted per-client (`Dhis2OAuth2ClientServiceImpl.java:323-338`).
Sending PKCE is harmless for confidential clients, so the v42 arm always sends
it. The v40 arm sends neither — unchanged.

### D4: Identity via the `sub` claim (username) — `/api/me` is NOT reachable on 2.42 SAS

**This supersedes the prior D4.** Live testing against 2.42.4.1 disproved the
assumption that `/api/me` accepts a SAS bearer token.

Observed live (real minted token):
- `/userinfo` → `200 {"sub":"admin"}` — the SAS accepts the token, but returns
  only `sub` (the username). No UID, name, or email.
- `/api/me` (and every `/api/*`) with the same token → **401** (302→login for
  browser requests). The user's real UID `M5zQapPyTZI` appears in **no** token,
  `id_token`, or `/userinfo` response.
- Decoded `id_token` claims: `sub, aud, azp, auth_time, iss, exp, iat, jti, sid`
  — `sub = "admin"`, nothing else identity-bearing. Decoded access-token claims:
  `sub, aud, nbf, scope, iss, exp, iat, jti`.

Why (source):
- DHIS2 sets the JWT `sub` to the username, not the UID
  (`Dhis2OAuth2ClientServiceImpl.java:124-126` and
  `Dhis2OAuth2AuthorizationServiceImpl.java:131` both read `sub` then
  `getUserByUsername(...)`). The only claims the token customizer can add are
  `username` and `email`, hard-coded (`AuthorizationServerConfig.java:107-108,
  263-289`) — never a `uid`/`id` claim.
- `/api/*` is guarded by a resource-server bearer filter
  (`DhisWebApiWebSecurityConfig.java:510-512`) whose resolver
  (`Dhis2JwtAuthenticationManagerResolver`) maps a token to a user by reading the
  **mapping claim** (`username` or `email`) and calling
  `createUserDetailsByUsername` / `createUserDetailsByOpenId`. It does **not**
  read `sub`. Our token carries neither a `username` nor an `email` claim → the
  mapping value is null → no user → 401. The auth-server chain (`/userinfo`,
  `/oauth2/*`) accepts the token; the web-API chain (`/api/**`) does not.
- DHIS2's own e2e test `OAuth2Test.testBearerTokenAuthWithMatchingUser` proves
  `/api/me` → 200 **only** after creating the user with `openId = <email>` and
  using `email` mapping; the sibling failure test asserts 401 with "Found no
  matching DHIS2 user for the mapping claim". This is the per-user-mapping path.

Decision: **v42 resolves identity from the `id_token` `sub` claim (the
username).** `Dhis2OAuthService` gains a v42 identity method that reads `sub`
from the `id_token` (base64url-decode of the claims segment; no signature
verification — back-channel, TLS) and returns a `Dhis2User(uid: null,
username: sub, displayName: null, email: null)`. The v40 arm's `fetchMe`
(`/api/me` + `json.id`) is **untouched**.

**Alternatives considered and rejected:**
- *Make `/api/me` work via per-user `openId` mapping + `email` scope* — requires
  setting `openId` on every SSO user, the `email` scope (emailless footgun), and
  (per DHIS2 docs) the "External authentication only" flag, which **disables
  that user's DHIS2 password login** (`DefaultUserService.java:610-614`). Deeply
  invasive per-user DHIS2 account surgery; rejected.
- *Service account → `/api/users?filter=username:eq:{sub}`* — recovers UID + name
  + email with one read-only credential, but requires provisioning and rotating a
  directory-read service account **in every DHIS2 instance**. Rejected as
  per-deployment operational drag. (Recorded as the upgrade path if a customer
  later requires the UID or auto-populated profile.)

### D5: Identity key = username; additive `Dhis2UserLink` schema change

The link must now hold username-keyed rows (v42) alongside UID-keyed rows
(v40/v41). One **additive, reversible** custom migration under
`grails-app/migrations/custom/`:

- `dhis2_uid` → **nullable** (v42 rows have no UID; v40/v41 rows still populate
  it). Drop the `NOT NULL`; keep it unique among non-null values.
- `dhis2_username` → **unique, NOT NULL**, with a **case-sensitive collation**
  (`utf8mb4_bin`). DHIS2 usernames are case-sensitive; matching case-insensitively
  could merge or split accounts.
- Add `deactivated_at TIMESTAMP NULL` — tombstone (D6 safeguard).

`Dhis2RegistrationService.findOrRegister` branches on the data, not the profile:
`uid != null` → existing `findByDhis2Uid` path (v40/v41, unchanged); `uid == null`
→ new `findByDhis2Username` path (v42). OB's `User` requires `firstName`/
`lastName`, so v42 registration synthesizes placeholders (`firstName = username`,
`lastName = ""` or a marker); the approving admin fills real values at activation
(these are inactive pending-access accounts — D3 of `dhis2-oauth-core`).

### D5a: Username is a safe identity key (immutable; recycling assumption documented)

Using the username as the permanent key is safe given two verified facts plus one
documented precondition:

- **Username is unique and not-null** — `User.hbm.xml:122`
  (`unique="true" not-null="true"`).
- **Username cannot be changed via the API, UI, or metadata import.** The user
  import bundle hook rejects any update that changes the username with
  `ErrorCode.E4056` ("Property `username` can not be changed") —
  `UserObjectBundleHook.java:107-114`. The UI locks the field; REST `PUT` and
  metadata import both funnel through this hook. Only a raw SQL `UPDATE` could
  change it.
- **Residual risk — username recycling:** a username freed by *deleting* a user
  could be reassigned to a different person, who would then inherit the original
  OB account. Confirmed with the DHIS2 sysadmin (Carlos): active/real users
  effectively cannot be deleted via the UI (deletion is blocked while the user
  owns references; the deletes that happen are mostly test users). So recycling
  onto a different real person is not a realistic path.

**Documented precondition:** *v42 username-keying assumes DHIS2 usernames are
never reassigned to a different person after deletion.* A deployment that
recycles usernames must instead use the service-account upgrade path (D4
Alternatives).

**Safeguard (defense-in-depth, D6):** when an SSO-linked OB user is deactivated,
**tombstone** the link (`deactivated_at`) rather than deleting the row; a new
first-login for a tombstoned username goes through a fresh admin approval with a
visible "a prior link existed" warning — never silent reuse. Log every link
create/resolve for forensics.

### D6: v42 is additive and easy to remove

- v42 is **config-gated** (`profile: v42`). Never setting it leaves the v42 path
  dormant and v40/v41 byte-identical.
- The v42 surface is localized: the `isV42()` identity branch + the `sub`
  resolver in `Dhis2OAuthService`, the `uid == null` branch in
  `Dhis2RegistrationService`, and the additive migration. Removing it (delete
  those branches, re-tighten the migration) leaves v40/v41 untouched.
- The only shared touch is the `Dhis2UserLink` table (both profiles write it),
  changed additively so v40/v41 rows behave identically.

## Upstream touch points

None. All code under `org.pih.warehouse.custom.dhis2auth`; the migration under
`grails-app/migrations/custom/`; the reference template in
`docker/openboxes.client-template.yml`. No upstream files modified.

## Risks / Trade-offs

- **`/api/me` unreachable on 2.42 SAS** → v42 users are identified by username
  only; no UID, name, or email auto-fill (v40/v41 retain all three). Mitigation:
  username is a stable, immutable key (D5a); admin fills name at activation.
  Independently corroborated — the DHIS2 sysadmin hit the same `/api/*` token
  rejection integrating PayloadCMS.
- **Username recycling** → see D5a (documented precondition + tombstone
  safeguard). Low likelihood per sysadmin confirmation.
- **Case-sensitivity** → DHIS2 usernames are case-sensitive; OB stores `sub`
  verbatim and uses a case-sensitive collation on `dhis2_username` (D5).
- **Email-scope token-mint failure** → never request `email` scope (D2).

## Migration Plan

1. Land code with `profile` defaulting to `v40` and `enabled = false` — zero
   behavior change for existing deployments. Run the additive migration (nullable
   `dhis2_uid`, unique case-sensitive `dhis2_username`, `deactivated_at`).
2. v42 deployment: sysadmin enables the SAS, registers the OB client, and applies
   the three PATCHes (scopes `openid,username`; `clientAuthenticationMethods`;
   bcrypt `clientSecret`).
3. Set OB config: `profile=v42`, `/oauth2/*` authorize/token URLs, `scopes=openid
   username`, `clientAuth=basic` (or `post`). No `userUrl` needed for v42.
4. Run one end-to-end login; confirm the OB user is created/linked by username and
   lands on the pending-access page.
5. Rollback: set `profile=v40` or `enabled=false`. The migration is additive
   (nullable column + new unique index) and need not be reverted.

## Validation

**[source]** verified against the local DHIS2 checkout; **[live]** verified
against 2.42.4.1; **[unit]** verifiable in this repo.

- [x] **[live]** SAS token mint succeeds; `/userinfo` returns `{"sub":"admin"}`.
- [x] **[live]** `/api/me` (and `/api/*`) with the SAS bearer returns **401**
  (302→login for browser requests); the UID is in no token/userinfo response.
- [x] **[live]** Decoded `id_token` carries `sub` = username and no UID/name/email;
  the user's real UID (`M5zQapPyTZI`, via basic-auth `/api/me`) differs from `sub`.
- [x] **[source]** Only `username`/`email` token claims exist; `sub` = username —
  `AuthorizationServerConfig.java:107-108,263-289`,
  `Dhis2OAuth2ClientServiceImpl.java:124-126`.
- [x] **[source]** `/api/*` resolver maps by `username`/`email` claim (not `sub`),
  via `getUserByUsername`/`getUserByOpenId` —
  `Dhis2JwtAuthenticationManagerResolver` token converter; `/api/me` 200 requires
  per-user `openId` mapping — `OAuth2Test.testBearerTokenAuthWithMatchingUser`.
- [x] **[source]** Username is immutable through API/UI/import — `E4056` guard at
  `UserObjectBundleHook.java:107-114`; `ErrorCode.java:297`.
- [x] **[source]** Username unique + not-null — `User.hbm.xml:122`.
- [x] **[source]** `email` scope throws at token mint for emailless users —
  `AuthorizationServerConfig.java:263-273`.
- [ ] **[unit]** `Dhis2OAuthServiceSpec`: v42 identity method extracts `sub` from a
  sample `id_token` and returns `Dhis2User(uid: null, username: sub, ...)`.
- [ ] **[unit]** `Dhis2OAuthServiceSpec`: v42 `buildAuthorizeUrl` contains
  `code_challenge` + `code_challenge_method=S256`; v40 unchanged.
- [ ] **[unit]** `Dhis2RegistrationService`: `uid == null` registers/links by
  username with placeholder names; tombstoned-username first-login is not silently
  reused. `uid != null` (v40/v41) path unchanged.
- [ ] **[unit]** `./gradlew test` for `org.pih.warehouse.custom.dhis2auth` green.
- [ ] **[live]** End-to-end v42 login: login→consent→callback→token→OB user
  linked by username→pending-access page.

## Confidence: 9/10

The two load-bearing runtime facts are now settled by **live testing against the
actual 2.42.4.1 target**, not inference: (1) `/api/me` rejects the SAS bearer
(401), and (2) the token carries only `sub` (= username), never the UID. The
identity-key safety rests on a verified immutability guard (E4056) plus a sysadmin
-confirmed deletion policy, with a tombstone safeguard for the residual recycling
edge. v40/v41 are untouched and the v42 surface is additive and config-gated. Not
10/10 only because the final end-to-end browser login (callback→session) hasn't
been captured yet.
