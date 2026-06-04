## Context

OpenBoxes (Grails 3.3.16 / Spring Boot 1.5 / Groovy 2.4 / JDK 8) currently
authenticates via **hand-rolled code** — there is no Spring Security plugin
(`spring-security-core`, `spring-security-oauth2`, SAML, LDAP, Shiro, CAS) in
`build.gradle`. The auth surface is:

- `grails-app/controllers/org/pih/warehouse/user/AuthController.groovy` (~228
  lines) — login form handler, plain username/password check, session setup.
- `grails-app/services/org/pih/warehouse/auth/AuthService.groovy` (~49 lines) —
  thread-locals for the current `User` and `Location`. Not actually an
  authenticator; just a place where the controller stashes the result.
- `grails-app/controllers/org/pih/warehouse/SecurityInterceptor.groovy` (~130
  lines) — per-request gate that checks the session-stored user.

This is the integration surface our OAuth code hooks into. We do NOT need to
register a `UserDetailsService`, an `AuthenticationProvider`, or a filter in
any Spring Security chain — none exists. Our callback action becomes the
hand-rolled equivalent of "login succeeded", calling whatever code path
`AuthController` uses today to mark the user as logged in.

Several deployments now run alongside DHIS2 and want a single sign-on
experience. DHIS2 ships an OAuth2 authorization server at
`/uaa/oauth/authorize` and `/uaa/oauth/token`, with user details available at
`/api/me`. The DHIS2 admin UI exposes OAuth2 client management under
**System Settings → OAuth2 Clients**. This is the integration surface on the
DHIS2 side. (Verified by `dhis2-oauth-spike` — `validation/dhis2-authorize.txt`,
`validation/dhis2-me.json`, `validation/dhis2-oauth-client-setup.md`.)

Constraints:

- **Upstream-isolation rules** (`CLAUDE.md`, `rules/custom-package-isolation.md`,
  `rules/upstream-entity-extension.md`): new code under
  `org.pih.warehouse.custom.dhis2auth`; new schema as a side-table with a
  UNIQUE FK; no columns added to upstream tables; minimal, surgical edits to
  upstream files (login GSP, `application.yml`, the `AuthController` hand-off
  point, user-admin filter).
- **Stack floors**: JDK 8, Groovy 2.4, Grails 3.3, no Spring stereotypes
  (`@Autowired` / `@Service` / `@Repository`) on new beans — use Grails
  service injection.
- **Coexistence with password login**: cannot break the existing
  username/password flow handled by `AuthController`; cannot force migration
  of existing users.

## Goals / Non-Goals

**Goals:**
- DHIS2 OAuth2 Authorization Code login that produces a normal authenticated
  OB session (via the existing hand-rolled `AuthController` /
  `AuthService` / `SecurityInterceptor` path — no new Spring Security plugin
  introduced).
- Self-registration on first login with `active = false` and zero permissions
  until an admin grants access.
- Match returning users by stable DHIS2 UID via a side-table.
- Admin UX to find pending DHIS2 users in the existing user-admin screen.

**Non-Goals:**
- Iframe embedding, CSP `frame-ancestors`, `SameSite=None` cookies,
  forwarded-proto handling, local TLS dev stack — see `dhis2-iframe-embedding`.
- Refresh-token handling (re-prompt on expiry instead).
- DHIS2 user-group → OB role mapping.
- DHIS2 org-unit → OB Location mapping.
- Deactivation propagation beyond "next OAuth login fails."
- Replacing or deprecating local username/password login.

## Decisions

### D1: Hand-roll OAuth2 client; do not adopt a Spring Security plugin

OB has no Spring Security plugin today (see Context). Adopting
`spring-security-oauth2:2.x` would mean *also* adopting `spring-security-core`
first — a load-bearing rewrite of the existing hand-rolled auth path, which
is a much bigger change than the OAuth flow itself. The Authorization Code
flow is small (~200 lines): a controller with two actions (`initiate` →
redirect to DHIS2; `callback` → exchange code, fetch `/api/me`, hand off to
the existing `AuthController` session-setup path), plus a `Dhis2OAuthClient`
Groovy service that talks to DHIS2 over HTTPS using the HTTP client confirmed
in `dhis2-oauth-spike` validation/http-client.md.

Alternatives considered:
- **`spring-security-core` + `spring-security-oauth2:2.x`** → requires
  replacing OB's hand-rolled auth wholesale. Out of proportion to the goal.
  Rejected.
- **Pac4j** → another full auth framework that would replace the hand-rolled
  path. Same problem. Rejected.
- **DHIS2's SAML profile** → DHIS2 supports it but PM specifically asked for
  OAuth2. Park for a future change if needed.

### D1.1: OAuth callback hands off to AuthController, not to a filter chain

Because there is no Spring Security filter chain to integrate with, the
`callback` action does not produce a `UserDetails` — it does whatever
`AuthController.login` does today on success (sets the session-stored
`User`, calls `AuthService.setCurrentUser`, redirects). The spike artifact
`validation/active-flag-behavior.md` captures the exact statements
`AuthController` runs so the OAuth callback can mirror them rather than
re-implement them. If `AuthController` exposes a reusable method, we call
it; otherwise we extract a small `Dhis2SessionService` that both paths use.

### D2: Match by DHIS2 UID, store in `Dhis2UserLink` side-table

DHIS2 UIDs are stable 11-character identifiers and do not change when
usernames or emails change. Matching by username invites bugs when DHIS2
admins rename users; matching by email is fragile when emails are missing or
shared. Storing the link in a side-table (per `upstream-entity-extension`)
means `User` stays untouched, keeping upstream merges painless.

Side-table schema (Liquibase changeset under
`grails-app/migrations/custom/0001-dhis2-user-link.groovy`):

```
custom_dhis2_user_link
├── id              CHAR(38) PK                 -- UUID, matches User.id generator
├── version         BIGINT NOT NULL DEFAULT 0   -- GORM optimistic-lock
├── user_id         CHAR(38) NOT NULL UNIQUE FK → user.id
├── dhis2_uid       VARCHAR(11) NOT NULL UNIQUE
├── dhis2_username  VARCHAR(255)
├── last_login_at   TIMESTAMP
├── created_at      TIMESTAMP NOT NULL
└── updated_at      TIMESTAMP NOT NULL
```

`UNIQUE(user_id)` enforces 1:1 — a local user can be linked to at most one
DHIS2 identity. `UNIQUE(dhis2_uid)` ensures one OB user per DHIS2 user.

### D3: First-login = inactive registration, admin grants access

The `User.active = false` state is the natural "pending access" gate. This
reuses OB's existing user-admin screen for granting access (admin flips
`active`, assigns roles, assigns locations) and avoids inventing a parallel
"pending users" table.

The spike (task 1.8 → `validation/active-flag-behavior.md`) traces exactly
how OB's hand-rolled `AuthController` + `SecurityInterceptor` treat
`active = false` today. Three possible outcomes, all workable:

1. **`AuthController` blocks login for `active = false`.** Then our OAuth
   callback must NOT call the standard login hand-off — instead, it sets a
   session marker ("pending DHIS2 user") that `SecurityInterceptor` reads to
   route everything to the pending-access page. Logout clears the marker.
2. **`AuthController` allows login but `SecurityInterceptor` 403s everything.**
   Then our `SecurityInterceptor` edit (an existing upstream touch point)
   adds the "redirect to pending-access page" branch in front of the 403
   branch when the user has a `Dhis2UserLink`.
3. **`AuthController` allows login and the user can navigate.** We add the
   pending-access redirect to `SecurityInterceptor` as the gate.

The implementation picks the branch the spike reveals. Hand-rolled auth
makes this strictly cheaper than the Spring-Security version of the same
question — no `UserDetailsService` to subclass, no `AuthenticationProvider`
to register.

### D4: Login-page button is a single conditional GSP edit

The "Sign in with DHIS2" button is rendered conditionally based on
`grailsApplication.config.openboxes.dhis2.oauth.enabled`. Single Groovy expression in
the existing login GSP at the path confirmed by spike task 1.6.

## Risks / Trade-offs

- **DHIS2 OAuth2 client setup is admin-driven and varies by DHIS2 version** →
  Pin the DHIS2 image tag in spike + iframe changes; reference the exact UI
  walk-through from the spike artifact.
- **Username collision** between DHIS2 and existing local users → Resolved by
  the spec's deterministic suffix rule (`<username>-dhis2`) plus a warning
  log. Admins can manually merge by linking the existing user via DB if
  intended. Acceptable for v1.
- **Refresh tokens out of scope** → Users are re-prompted when DHIS2 access
  tokens expire. Without iframe embedding (this change's scope), the
  re-prompt is a normal browser navigation, not a disruption. Iframe-aware
  re-auth handling is `dhis2-iframe-embedding`'s problem.

## Migration Plan

1. Land code with `dhis2.oauth.enabled = false` defaults — zero behavior
   change for existing deployments.
2. Run Liquibase changeset (creates empty `custom_dhis2_user_link` table) — no
   impact on existing data.
3. Per-deployment opt-in: set DHIS2 client id/secret/URLs in env-specific
   config.
4. Rollback: set `dhis2.oauth.enabled = false`. Existing local logins keep
   working. Liquibase changeset is additive and can stay (no rollback
   migration needed).

## Upstream touch points

These upstream files will be edited; merge conflicts on future upstream pulls
should be expected here:

- `grails-app/controllers/org/pih/warehouse/UrlMappings.groovy` — three new
  route entries (`/oauth/dhis2/initiate`, `/oauth/dhis2/callback`,
  `/oauth/dhis2/pending`) mapped to the custom `Dhis2OAuthController`. A
  per-package `UrlMappings` is not viable in Grails 3.3, so the upstream
  mapper is touched directly.
- `grails-app/controllers/org/pih/warehouse/SecurityInterceptor.groovy` — add
  the pending-access redirect branch (smallest possible block; whitelist the
  pending page, logout, and static assets). No new Spring Security wiring —
  this is the existing interceptor's gate.
- `grails-app/controllers/org/pih/warehouse/user/UserController.groovy` —
  surgical addition of the pending-users filter mode that delegates to
  `Dhis2AdminService.findPendingDhis2Users`.
- `grails-app/views/auth/login.gsp` — single conditional `<g:if>` block
  rendering the "Sign in with DHIS2" button when
  `openboxes.dhis2.oauth.enabled` is true.
- `grails-app/views/user/list.gsp` — single filter-link addition for the
  pending-users view.
- `grails-app/i18n/messages.properties` — appended `dhis2auth.*` keys (must
  live in the root bundle; Grails 3.3 only globs `messages*.properties` at
  the root).
- `docker/docker-compose.yml` — single `ports: 9090:8080` mapping on the `app`
  service so the OAuth `redirectUri` (`http://localhost:9090/...`) resolves in
  local dev. Dev-compose only; no production impact.
- `docker/openboxes.yml` — per-deployment external config (not a code file).
  Carries the `dhis2.oauth.*` block for the local/test env. Treated as a
  reference template here; deployments override it.

Config: `dhis2.oauth.*` is supplied via the per-deployment external config
file `docker/openboxes.yml` (see `docker/openboxes.client-template.yml` for
the canonical key list). No touch to `grails-app/conf/application.yml` — the
external file is loaded by the existing config loader, so no upstream edit
is required.

Everything else lives under `org.pih.warehouse.custom.dhis2auth` and
`grails-app/migrations/custom/`.

## Confidence: 9/10

Re-scored after `dhis2-oauth-spike` validation artifacts landed.

All major assumptions confirmed:
- OAuth2 endpoints at `/uaa/oauth/authorize` + `/uaa/oauth/token` ✓
- `/api/me` returns `id` (11-char UID), `username`, `displayName`, `email` ✓
- OAuth2 client registration works via API (36-char UUID secret required) ✓
- `User.active = false` → **D3 branch 1** confirmed: `AuthController.handleLogin`
  rejects inactive users outright; OAuth callback must use session marker, not
  the normal session-setup path ✓
- No reusable session-setup method on `AuthController` → extract
  `Dhis2SessionService.establishSession(User, HttpSession)` (per D1.1) ✓
- HTTP client: `fluent-hc` / `httpclient` already on classpath; no new dep ✓
- Login GSP path: `grails-app/views/auth/login.gsp` confirmed ✓

Residual risk: username-collision UX (D2) remains untested against a real
deployment with conflicting usernames. Recoverable inside this change's scope.
