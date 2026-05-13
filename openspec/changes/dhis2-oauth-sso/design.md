## Context

OpenBoxes (Grails 3.3.16 / Spring Boot 1.5 / Groovy 2.4 / JDK 8) currently
authenticates via the upstream `spring-security-core` plugin against local
credentials. Several deployments now run alongside DHIS2 and want a single
sign-on experience plus the ability to embed OB screens inside DHIS2 apps via
iframe.

DHIS2 ships an OAuth2 authorization server at `/uaa/oauth/authorize` and
`/uaa/oauth/token`, with user details available at `/api/me`. The DHIS2 admin
UI exposes OAuth2 client management under **System Settings → OAuth2 Clients**.
This is the integration surface.

Constraints we must respect:

- **Upstream-isolation rules** (`CLAUDE.md`, `rules/custom-package-isolation.md`,
  `rules/upstream-entity-extension.md`): new code under
  `org.pih.warehouse.custom.dhis2auth`; new schema as a side-table with a
  UNIQUE FK; no columns added to upstream tables; minimal, surgical edits to
  upstream files (login GSP, `application.yml`, security wiring).
- **Stack floors**: JDK 8, Groovy 2.4, Grails 3.3, no Spring stereotypes
  (`@Autowired` / `@Service` / `@Repository`) on new beans — use Grails
  service injection.
- **Coexistence with password login**: cannot break existing username/password
  flow; cannot force migration of existing users.

## Goals / Non-Goals

**Goals:**
- DHIS2 OAuth2 Authorization Code login that produces a normal Grails
  Spring-Security session.
- Self-registration on first login with `active = false` and zero permissions
  until an admin grants access.
- Match returning users by stable DHIS2 UID via a side-table.
- Iframe-embeddable OB pages from a configured DHIS2 origin allow-list.
- Local docker-compose stack with real TLS so OAuth + iframe can be tested
  end-to-end without a public domain.

**Non-Goals:**
- Refresh-token handling (re-prompt on expiry instead).
- DHIS2 user-group → OB role mapping (manual admin assignment in v1).
- DHIS2 org-unit → OB Location mapping (manual admin assignment in v1).
- Deactivation propagation beyond "next OAuth login fails."
- Replacing or deprecating local username/password login.
- Deep-linking from DHIS2 dashboard items into specific OB screens.

## Decisions

### D1: Hand-roll OAuth2 client; do not adopt `spring-security-oauth2`

The legacy `spring-security-oauth2:2.x` library exists, but Grails 3.3 plugin
support is uneven and adding it brings transitive dependency risk on a frozen
stack. The Authorization Code flow is small (~200 lines): a controller with
two actions (`initiate` → redirect to DHIS2; `callback` → exchange code,
fetch `/api/me`, establish session), a `Dhis2OAuthClient` Groovy service that
talks to DHIS2 over HTTPS using `groovyx.net.http.HTTPBuilder` (already on the
classpath) or plain `URLConnection`, and a small filter that recognizes the
authenticated principal.

Alternatives considered:
- `spring-security-oauth2:2.x` → adds a managed dependency on a frozen
  Spring Boot 1.5 line; integration with `spring-security-core` (the plugin OB
  uses) is non-trivial. Rejected.
- Pac4j → another full framework; same integration cost. Rejected.
- DHIS2's own SAML profile → DHIS2 supports SAML, but our PM specifically
  asked for OAuth2. Park for a future change if needed.

### D2: Match by DHIS2 UID, store in `Dhis2UserLink` side-table

DHIS2 UIDs are stable 11-character identifiers and do not change when
usernames or emails change. Matching by username invites bugs when DHIS2
admins rename users; matching by email is fragile when emails are missing or
shared. Storing the link on a side-table (per `upstream-entity-extension`)
means `User` stays untouched, which keeps upstream merges painless.

Side-table schema (Liquibase changeset under
`grails-app/migrations/custom/0001-dhis2-user-link.groovy`):

```
dhis2_user_link
├── id              BIGINT PK
├── version         BIGINT NOT NULL DEFAULT 0   -- GORM optimistic-lock
├── user_id         BIGINT NOT NULL UNIQUE FK → user.id
├── dhis2_uid       VARCHAR(11) NOT NULL UNIQUE
├── dhis2_username  VARCHAR(255)
├── last_login_at   TIMESTAMP
├── created_at      TIMESTAMP NOT NULL
└── updated_at      TIMESTAMP NOT NULL
```

`UNIQUE(user_id)` enforces 1:1 — a local user can be linked to at most one
DHIS2 identity. `UNIQUE(dhis2_uid)` ensures one OB user per DHIS2 user.

### D3: First-login = inactive registration, admin grants access

The `User.active = false` state is the natural "pending access" gate: the
session authenticates fine, but every authorization check fails. The
pending-access page is reachable; everything else redirects to it. This avoids
inventing a parallel "pending users" table and reuses OB's existing user-admin
screen for granting access (admin flips `active`, assigns roles, assigns
locations).

We must verify that OB's login pipeline actually allows `active = false`
users to reach a session — some Spring Security configs reject inactive
accounts at the `UserDetailsService` layer with `DisabledException`. If so,
we use a custom `UserDetailsService` for DHIS2-linked users that returns the
user as enabled but with no granted authorities, and rely on the existing
authorization checks failing closed. **This is an unverified assumption — see
Unverified Assumptions below.**

### D4: CSP `frame-ancestors`, not `X-Frame-Options`

`X-Frame-Options: SAMEORIGIN` (the upstream default) cannot allow specific
third-party origins. `Content-Security-Policy: frame-ancestors` can, and is
the modern replacement. We emit it from a `dhis2auth` filter that runs on all
HTML responses, configured via `iframe.frameAncestors` in `application.yml`.
We also remove any upstream-set `X-Frame-Options` header to avoid the two
contradicting each other.

### D5: `SameSite=None; Secure` cookies, conditional on iframe being enabled

When `iframe.frameAncestors` is non-empty, set the session cookie with
`SameSite=None; Secure`. When empty (default), leave upstream behavior
unchanged. This keeps the change opt-in: deployments that don't embed OB
in DHIS2 see no cookie behavior change.

The Tomcat default `SameSite` handling in Grails 3.3 / Spring Boot 1.5 does
NOT support `SameSite=None` natively (the attribute was added later). We will
need to either:
- (a) override the cookie via a `Filter` that rewrites `Set-Cookie` on the
  way out (works on Servlet 3.1 / Tomcat 8.5), OR
- (b) configure the embedded Tomcat `Rfc6265CookieProcessor` with
  `sameSiteCookies` (Tomcat 9 only — **may not be available** in the embedded
  Tomcat shipped with Spring Boot 1.5).

Plan: try (b) first; fall back to (a). **This is an unverified assumption —
need to confirm which Tomcat version Grails 3.3 / Spring Boot 1.5 embeds.**

### D6: Honor `X-Forwarded-Proto` via Spring Boot's existing setting

Spring Boot 1.5 supports `server.use-forward-headers=true` in
`application.yml`, which configures Tomcat's `RemoteIpValve`. Setting it makes
`request.isSecure()` reflect the proxy's `X-Forwarded-Proto` header. We add
this only when iframe embedding is enabled to avoid changing behavior for
deployments that don't use a TLS-terminating proxy. **Need to verify the
exact property name for Spring Boot 1.5 — Spring Boot 2.x renamed it.**

### D7: Local dev stack as a separate `docker/dhis2-sso/` directory

Avoid touching `docker/docker-compose.yml` (upstream file). New directory
contains:
- `docker-compose.yml` — services: `nginx`, `dhis2`, `dhis2-db` (postgres),
  `openboxes`, `openboxes-db` (mysql).
- `nginx.conf` — two `server` blocks (443/TLS) proxying to internal services,
  WebSocket upgrade headers, `X-Forwarded-Proto`, `X-Forwarded-For`, `Host`.
- `certs/.gitignore` — committed empty dir; certs generated by the developer.
- `README.md` — mkcert install, cert generation command, OAuth client
  registration walkthrough in DHIS2, troubleshooting.

Hostnames: `dhis2.localtest.me` and `openboxes.localtest.me`. `*.localtest.me`
is reserved by RFC 6761 / IANA and resolves to `127.0.0.1` automatically — no
`/etc/hosts` edits required. **Need to confirm RFC 6761 / current resolver
behavior for `localtest.me`.**

### D8: Login-page button is a single conditional GSP edit

The "Sign in with DHIS2" button is rendered conditionally based on
`grailsApplication.config.dhis2.oauth.enabled`. This is a single Groovy
expression in the existing login GSP — minimal upstream surface. Documented
in this design's "Upstream touch points" section.

## Risks / Trade-offs

- **`SameSite=None` cookie handling on Spring Boot 1.5 / Tomcat embedded** →
  May require fallback (a) above. Mitigation: spike this early; if neither
  approach works on the embedded Tomcat, document that the iframe deployment
  requires running OB behind nginx that rewrites the cookie attribute.
- **DHIS2 OAuth2 client setup is admin-driven and varies by DHIS2 version** →
  Document the exact UI path for DHIS2 2.39+ in the dev README; pin the
  DHIS2 image version in docker-compose.
- **Inactive user reaching pending page** (D3) → If Spring Security rejects
  inactive users at authentication time, we adapt with a custom
  `UserDetailsService`. Spike during week 1.
- **Username collision** between DHIS2 and existing local users → Resolved by
  the spec's deterministic suffix rule (`<username>-dhis2`) plus a warning
  log. Admins can manually merge by linking the existing user via DB if
  intended. Acceptable for v1; revisit if it shows up in real deployments.
- **Refresh tokens are out of scope** → Users get re-prompted when DHIS2
  access tokens expire. For an iframe-embedded app this means a redirect
  bounce that may break out of the iframe. Mitigation: configure long-lived
  access tokens in DHIS2 (or accept the re-auth bounce in v1).
- **Origin allow-list misconfiguration** → A wrong/missing entry silently
  breaks the iframe. Mitigation: `frame-ancestors` errors are visible in
  browser DevTools console; document this prominently in the README.

## Migration Plan

1. Land code with `dhis2.oauth.enabled = false` and `iframe.frameAncestors = []`
   defaults — zero behavior change for existing deployments.
2. Run Liquibase changeset (creates empty `dhis2_user_link` table) — no
   impact on existing data.
3. Per-deployment opt-in: deployment sets DHIS2 client id/secret/URLs and
   iframe origin allow-list in env-specific config.
4. Rollback: set `dhis2.oauth.enabled = false`. Existing local logins keep
   working. Liquibase changeset is additive and can stay (no rollback
   migration needed).

## Upstream touch points

These upstream files will be edited; merge conflicts on future upstream pulls
should be expected here:

- `grails-app/conf/application.yml` — add `dhis2.oauth.*` and `iframe.*` keys
  with safe defaults (feature disabled).
- `grails-app/conf/application.groovy` (or `spring/resources.groovy`) — wire
  the custom security filter into the `spring-security-core` filter chain.
- `grails-app/views/login/auth.gsp` (or current login GSP — verify path) —
  conditional `<g:if>` block rendering the DHIS2 button.
- The `Filters.groovy` (or interceptor) that sets `X-Frame-Options` (verify
  upstream location) — replace with our CSP-emitting filter, kept behind an
  enabled-flag.

Everything else lives under `org.pih.warehouse.custom.dhis2auth`,
`grails-app/migrations/custom/`, and `docker/dhis2-sso/`.

## Open Questions

- Which login GSP is current upstream? `auth.gsp` historically; verify path.
- Where does upstream set `X-Frame-Options` today — a `Filters.groovy`, a
  config property, or relying on `spring-security-core` defaults?
- Does the embedded Tomcat in Grails 3.3.16 support
  `Rfc6265CookieProcessor.sameSiteCookies`?
- Does Spring Boot 1.5's `server.use-forward-headers` (or
  `server.tomcat.internal-proxies`) cover what we need, or do we need to
  configure the `RemoteIpValve` directly in `Application.groovy`?
- Will Spring Security let an `active = false` user reach an authenticated
  session, or is `DisabledException` thrown at authentication time?

These are intentionally listed as open and will be answered by a 2-day spike
(see tasks.md task 1).

## Validation

- [ ] **DHIS2 OAuth2 endpoints exist as documented.** Run against the local
      DHIS2 container after bring-up:
      `curl -s https://dhis2.localtest.me/uaa/oauth/authorize?...` returns a
      consent/redirect (not 404). Capture the first 200 bytes into the change
      directory as `validation/dhis2-authorize.txt`.
- [ ] **DHIS2 `/api/me` returns a UID, username, displayName, email.**
      `curl -u admin:district https://dhis2.localtest.me/api/me` against the
      local stack returns JSON containing `id` (11 chars), `username`,
      `displayName`, optional `email`. Capture as `validation/dhis2-me.json`.
- [ ] **Embedded Tomcat version known.** `./gradlew dependencies | grep -i tomcat`
      output captured as `validation/tomcat-version.txt`. Confirms whether
      `Rfc6265CookieProcessor.sameSiteCookies` is available.
- [ ] **`server.use-forward-headers` accepted by Spring Boot 1.5.** Reference:
      https://docs.spring.io/spring-boot/docs/1.5.22.RELEASE/reference/html/howto-embedded-servlet-containers.html#howto-use-tomcat-behind-a-proxy-server
      — anchor `howto-use-tomcat-behind-a-proxy-server`. Capture the relevant
      paragraph as `validation/spring-boot-1.5-forward-headers.txt`.
- [ ] **`*.localtest.me` resolves to 127.0.0.1.** `dig +short dhis2.localtest.me`
      and `dig +short openboxes.localtest.me` both return `127.0.0.1`.
      Capture as `validation/localtest-resolution.txt`.
- [ ] **Existing OB login GSP path identified.** `find grails-app/views -name '*.gsp' -path '*login*'`
      output captured as `validation/login-gsp-path.txt` so the upstream
      touch-point is concrete.
- [ ] **Existing `X-Frame-Options` source identified.** `grep -rn -i "X-Frame-Options\|frameOptions" grails-app/ src/main/`
      output captured as `validation/xframeoptions-source.txt`.
- [ ] **Spec compliance — schema isolation.** After running the new Liquibase
      changeset locally, `mysqldump --no-data openboxes user dhis2_user_link`
      shows `user` schema unchanged versus pre-change baseline; only
      `dhis2_user_link` is new.
- [ ] **End-to-end OAuth flow.** Manual test against the local stack: log in
      as a fresh DHIS2 user, observe (a) auto-registered OB user with
      `active=false`, (b) pending-access page renders, (c) admin grants
      access, (d) next request reaches a normal OB page.
- [ ] **End-to-end iframe.** Manual test: load `https://dhis2.localtest.me`
      with a custom HTML page that iframes `https://openboxes.localtest.me`,
      log in via the iframe, confirm session cookie is sent on subsequent
      iframe requests (DevTools → Application → Cookies shows
      `SameSite=None; Secure`).

## Unverified Assumptions

These claims are based on prior knowledge / docs / pattern-matching, NOT on
running the thing or reading current source. They are the spike targets
(task 1 in tasks.md):

- DHIS2 exposes OAuth2 at `/uaa/oauth/authorize` and `/uaa/oauth/token` and
  user details at `/api/me`. (DHIS2 docs claim this; not verified against a
  running 2.40+ instance.)
- DHIS2 UIDs are stable, 11 chars, never reused.
- DHIS2 admin UI lets us register OAuth2 clients without writing config files.
- Grails 3.3.16 embeds a Tomcat version recent enough to support
  `Rfc6265CookieProcessor.sameSiteCookies` — **likely false** (Spring Boot
  1.5 ships Tomcat 8.5; `sameSiteCookies` lands in Tomcat 9). Plan B (a
  Servlet `Filter` rewriting `Set-Cookie`) is therefore the realistic path.
- Spring Boot 1.5's `server.use-forward-headers=true` configures Tomcat's
  `RemoteIpValve` correctly for our proxy. (Documented in Spring Boot 1.5
  reference; not verified in this codebase.)
- Spring Security in OB allows `User.active = false` accounts to reach an
  authenticated session (we assume `active` gates authorization, not
  authentication). If wrong, we adapt via a custom `UserDetailsService`.
- `*.localtest.me` resolves to `127.0.0.1` everywhere modern resolvers run.
  (Reserved per RFC 6761 → AS112; widely supported but not universal — some
  corporate DNS strips it.)
- `groovyx.net.http.HTTPBuilder` is already on OB's classpath. (Common in
  Grails apps; not verified.)
- Upstream login GSP is `grails-app/views/login/auth.gsp`. (Historical
  spring-security-core convention; need to confirm.)
- Upstream emits `X-Frame-Options: SAMEORIGIN` somewhere we can replace
  cleanly. (Plausible — most Grails apps inherit it from
  `spring-security-core` defaults — but the exact mechanism in OB is
  unverified.)

## Confidence: 6/10

Cap is binding: every external claim above (DHIS2 OAuth endpoints, DHIS2
`/api/me` shape, Tomcat embedded version, Spring Boot 1.5 forward-headers
config name, login GSP path, upstream `X-Frame-Options` source, Spring
Security `active`-flag behavior) is based on docs / prior knowledge, not
captured artifacts or current source reads. The design is internally
coherent and the architecture is sound, but several decisions could change
shape after the spike — most importantly the `SameSite=None` cookie path,
which I expect will need the filter-rewrite fallback rather than the
`Rfc6265CookieProcessor` approach.

The spike (tasks.md task 1) is structured to convert each unverified
assumption into a captured artifact under `validation/`. After the spike,
re-score this design — most items should clear and the score should land
in the 8–9 range or trigger a small redesign of the cookie path.

Risks worth flagging up front:
- If the embedded Tomcat doesn't support `SameSite=None` and the filter
  rewrite is also blocked (e.g. by upstream code setting cookies via a
  pre-baked `Set-Cookie` string), iframe embedding regresses to "must run
  OB behind a proxy that rewrites cookies" — operationally workable but
  worth flagging to your PM now.
- If DHIS2's OAuth2 implementation differs from the spec (e.g. requires
  PKCE, has a non-standard `/api/me` shape, or has version-specific bugs),
  the spike will surface it before the build phase.
