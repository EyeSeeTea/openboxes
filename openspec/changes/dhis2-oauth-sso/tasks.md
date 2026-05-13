# Implementation Tasks — dhis2-oauth-sso

Tasks are grouped by phase. Each phase ends in a verifiable deliverable. The
spike (Phase 1) gates everything else — do not start Phase 2 until the spike
artifacts are captured under `validation/`.

## Phase 1 — Spike (gate)

Goal: convert every item in `design.md` § Unverified Assumptions into a
captured artifact under `openspec/changes/dhis2-oauth-sso/validation/`.

- [ ] **1.1** Stand up minimal local stack: `docker/dhis2-sso/docker-compose.yml`
      with `dhis2/core` + postgres only (skip OB and nginx for the spike).
      Pin DHIS2 image tag.
- [ ] **1.2** Capture `/api/me` shape: `curl -u admin:district http://localhost:8080/api/me`
      → `validation/dhis2-me.json`. Confirm UID, username, displayName, email.
- [ ] **1.3** Register an OAuth2 client in DHIS2 admin UI with redirect URI
      `http://localhost:9090/oauth/dhis2/callback` (placeholder for OB).
      Document the UI path and any version-specific quirks in
      `validation/dhis2-oauth-client-setup.md`.
- [ ] **1.4** Drive the Authorization Code flow manually via curl/browser,
      capture authorize redirect (`validation/dhis2-authorize.txt`) and
      token-exchange response (`validation/dhis2-token.json`).
- [ ] **1.5** `./gradlew dependencies | grep -i tomcat` → `validation/tomcat-version.txt`.
      Determine whether `Rfc6265CookieProcessor.sameSiteCookies` is reachable.
- [ ] **1.6** Identify upstream login GSP, security filter chain, and
      `X-Frame-Options` source. Capture findings in
      `validation/upstream-touchpoints.md`.
- [ ] **1.7** Confirm Spring Security flow for `User.active = false`: read
      `AuthService` / `UserDetailsService` implementations in OB; capture
      relevant excerpt in `validation/active-flag-behavior.md`.
- [ ] **1.8** Confirm `groovyx.net.http.HTTPBuilder` (or equivalent HTTP client)
      is on the classpath. If not, pick a JDK-only alternative and note it in
      `validation/http-client.md`.
- [ ] **1.9** Re-score `design.md` Confidence section based on findings.
      Update Decisions if any spike result invalidates an assumption.
- [ ] **1.10** Gate check: PM/dev review the validation/ artifacts and updated
      design before starting Phase 2.

## Phase 2 — Domain & schema

- [ ] **2.1** Create `org.pih.warehouse.custom.dhis2auth.Dhis2UserLink` domain
      class under `grails-app/domain/org/pih/warehouse/custom/dhis2auth/`.
      Fields per design D2; constraints: `dhis2Uid` unique + 11 chars,
      `user` unique. `static mapping` to set table name `dhis2_user_link`.
- [ ] **2.2** Create Liquibase changeset
      `grails-app/migrations/custom/0001-dhis2-user-link.groovy` matching
      the design's schema. Include in the custom aggregator
      (`grails-app/migrations/custom/changelog.groovy`) — verify the
      aggregator is included from the master once.
- [ ] **2.3** Run `./gradlew bootRun` locally; confirm the table is created
      against a fresh DB, and against an existing dev DB the migration
      applies cleanly.
- [ ] **2.4** Add a Spock unit test for the domain class
      (`src/test/groovy/.../Dhis2UserLinkSpec.groovy`) covering the unique
      constraints and FK validation.

## Phase 3 — OAuth2 client

All under `src/main/groovy/org/pih/warehouse/custom/dhis2auth/` and
`grails-app/services/org/pih/warehouse/custom/dhis2auth/` and
`grails-app/controllers/org/pih/warehouse/custom/dhis2auth/`.

- [ ] **3.1** Config keys in `application.yml`:
      `dhis2.oauth.enabled`, `dhis2.oauth.clientId`, `dhis2.oauth.clientSecret`,
      `dhis2.oauth.authorizeUrl`, `dhis2.oauth.tokenUrl`, `dhis2.oauth.userUrl`,
      `dhis2.oauth.redirectUri`, `dhis2.oauth.scopes`. All defaults safe
      (feature disabled).
- [ ] **3.2** `Dhis2OAuthClient` Groovy service: `buildAuthorizeUrl(state)`,
      `exchangeCode(code) → AccessToken`, `fetchMe(accessToken) → Dhis2User`.
      Use the HTTP client confirmed in spike task 1.8.
- [ ] **3.3** `Dhis2OAuthController`:
      `initiate()` — generate state nonce, store in session, redirect to authorize URL.
      `callback(code, state)` — verify state, exchange code, fetch user,
      delegate to `Dhis2RegistrationService`, establish session, redirect to
      landing page (or pending-access page).
      Reject malformed callbacks with HTTP 400.
- [ ] **3.4** `Dhis2RegistrationService.findOrRegister(dhis2User) → User`:
      lookup by `Dhis2UserLink.dhis2Uid` → if found, refresh non-auth fields,
      return; if not, create OB `User(active=false)`, create `Dhis2UserLink`,
      handle username collision per spec (suffix + warning log).
- [ ] **3.5** Spock unit tests for service methods:
      `Dhis2OAuthClientSpec` (mock the HTTP layer),
      `Dhis2RegistrationServiceSpec` (data-driven table covering: new user,
      returning user, returning user with renamed email, username collision).
- [ ] **3.6** Wire the controller URL mappings under `/oauth/dhis2/initiate`
      and `/oauth/dhis2/callback`. Place mappings under
      `grails-app/controllers/org/pih/warehouse/custom/dhis2auth/UrlMappings.groovy`
      if Grails supports a per-package URL mappings file; otherwise add the
      smallest possible block to the upstream `UrlMappings.groovy` and
      record it in the design's upstream touch points.

## Phase 4 — Security integration

- [ ] **4.1** Custom Spring Security filter or
      `AuthenticationProvider` that consumes the token + `/api/me` payload
      and produces a `UserDetails` for the OB user. Wire into the filter
      chain via `spring-security-core` config (smallest possible upstream
      edit; document in design touch points).
- [ ] **4.2** Pending-access GSP at `grails-app/views/dhis2auth/pending.gsp`
      — explains the user is awaiting access from an admin and provides a
      logout link.
- [ ] **4.3** Filter/interceptor: when authenticated and `User.active = false`,
      redirect to pending-access page (whitelist: pending page itself,
      logout, static assets).
- [ ] **4.4** Login GSP edit: conditional "Sign in with DHIS2" button block.
      Smallest possible diff. Record in design touch points.
- [ ] **4.5** Spock integration tests for the OAuth callback end-to-end —
      mock DHIS2 with WireMock or an embedded HTTP server; assert session
      established and `User`/`Dhis2UserLink` rows created.

## Phase 5 — Iframe embedding

- [ ] **5.1** Config keys: `iframe.frameAncestors` (list of origins, default `[]`).
- [ ] **5.2** `Dhis2IframeFilter` Groovy filter that, on every HTML response,
      sets `Content-Security-Policy: frame-ancestors 'self' <configured>` and
      removes any `X-Frame-Options` header. No-op when allow-list empty.
- [ ] **5.3** Cookie `SameSite=None; Secure` handling per design D5:
      try `Rfc6265CookieProcessor.sameSiteCookies` first; if unavailable,
      implement a `Set-Cookie`-rewriting filter. Only active when
      `iframe.frameAncestors` non-empty.
- [ ] **5.4** Add `server.use-forward-headers: true` (Spring Boot 1.5
      property name confirmed via spike task 1.6) under a profile or
      conditional, only when iframe enabled.
- [ ] **5.5** Spock filter tests asserting headers and cookie attributes.

## Phase 6 — Admin UX

- [ ] **6.1** Add a "Pending DHIS2 access" filter option to the existing user
      admin list controller/view. Surgical edit; record in touch points.
- [ ] **6.2** Manual smoke test: register a fresh DHIS2 user, log in via OB,
      see them in pending list, grant role + location, confirm next login
      reaches a normal OB page.

## Phase 7 — Local dev stack

- [ ] **7.1** Flesh out `docker/dhis2-sso/docker-compose.yml` to include OB
      and nginx services on the shared network.
- [ ] **7.2** `docker/dhis2-sso/nginx.conf` — two `server` blocks (TLS),
      proxy headers, WebSocket upgrade, certs mounted from `./certs/`.
- [ ] **7.3** `docker/dhis2-sso/README.md` — mkcert install, cert
      generation command, OAuth client setup walkthrough (drawn from spike
      artifact 1.3), troubleshooting (cookie issues, CSP errors, hostname
      resolution).
- [ ] **7.4** `docker/dhis2-sso/certs/.gitignore` — ignore everything except
      `.gitignore` itself.
- [ ] **7.5** End-to-end manual run-through following the README from a
      clean checkout. Capture any gotchas back into the README.

## Phase 8 — Tests, docs, archive

- [ ] **8.1** All Spock tests green: `./gradlew test`.
- [ ] **8.2** Frontend untouched (no `src/js/` edits expected). Run
      `npm test` to confirm no regressions.
- [ ] **8.3** Update top-level `README.md` only if user-facing behavior is
      enabled by default (it isn't — skip unless we decide to enable it on
      a specific customer branch).
- [ ] **8.4** Update PR description on the open PR (if any) to reflect final
      shape.
- [ ] **8.5** OpenSpec archive: ensure `design.md` "Upstream touch points"
      lists every modified upstream file; add "Deploy status" line; run
      `/opsx:archive`.
