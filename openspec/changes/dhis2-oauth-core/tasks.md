# Implementation Tasks — dhis2-oauth-core

**Prerequisite:** `dhis2-oauth-spike` is archived. Validation artifacts are
referenced below.

## Phase 1 — Domain & schema

- [x] **1.1** Create `org.pih.warehouse.custom.dhis2auth.Dhis2UserLink` domain
      class under `grails-app/domain/org/pih/warehouse/custom/dhis2auth/`.
      Fields per design D2; constraints: `dhis2Uid` unique + 11 chars,
      `user` unique. `static mapping` to set table name `dhis2_user_link`.
- [x] **1.2** Create Liquibase changeset
      `grails-app/migrations/custom/0001-dhis2-user-link.groovy` matching
      the design's schema. Include in the custom aggregator
      (`grails-app/migrations/custom/changelog.groovy`) — verify the
      aggregator is included from the master once.
- [ ] **1.3** Run `./gradlew bootRun` locally; confirm the table is created
      against a fresh DB, and against an existing dev DB the migration
      applies cleanly.
- [x] **1.4** Add a Spock unit test for the domain class
      (`src/test/groovy/.../Dhis2UserLinkSpec.groovy`) covering the unique
      constraints and FK validation.

## Phase 2 — OAuth2 client

All under `src/main/groovy/org/pih/warehouse/custom/dhis2auth/`,
`grails-app/services/org/pih/warehouse/custom/dhis2auth/`, and
`grails-app/controllers/org/pih/warehouse/custom/dhis2auth/`.

- [x] **2.1** Config keys documented in `docker/openboxes.client-template.yml`
      under `openboxes.dhis2.oauth.*` (`enabled`, `baseUrl`, `clientId`,
      `clientSecret`, `redirectUri`). No touch to `application.yml` — feature
      is disabled by default (missing config evaluates to falsy in Groovy).
- [x] **2.2** `Dhis2OAuthClient` Groovy service: `buildAuthorizeUrl(state)`,
      `exchangeCode(code) → AccessToken`, `fetchMe(accessToken) → Dhis2User`.
      Use the HTTP client confirmed in `dhis2-oauth-spike` task 1.9.
- [x] **2.3** `Dhis2OAuthController`:
      `initiate()` — generate state nonce, store in session, redirect to authorize URL.
      `callback(code, state)` — verify state, exchange code, fetch user,
      delegate to `Dhis2RegistrationService`, then hand off to the
      session-setup path (mirror what `AuthController.login` does on success
      per spike validation/active-flag-behavior.md — see design D1.1; either
      call an extracted `AuthController` method or a new
      `Dhis2SessionService`). Redirect to landing page or pending-access page.
      Reject malformed callbacks with HTTP 400.
- [x] **2.4** `Dhis2RegistrationService.findOrRegister(dhis2User) → User`:
      lookup by `Dhis2UserLink.dhis2Uid` → if found, refresh non-auth fields,
      return; if not, create OB `User(active=false)`, create `Dhis2UserLink`,
      handle username collision per spec (suffix + warning log).
- [x] **2.5** Spock unit tests:
      `Dhis2OAuthClientSpec` (mock the HTTP layer),
      `Dhis2RegistrationServiceSpec` (data-driven table: new user, returning
      user, returning user with renamed email, username collision).
- [x] **2.6** Wire the controller URL mappings under `/oauth/dhis2/initiate`
      and `/oauth/dhis2/callback`. Place mappings under
      `grails-app/controllers/org/pih/warehouse/custom/dhis2auth/UrlMappings.groovy`
      if Grails supports a per-package URL mappings file; otherwise add the
      smallest possible block to the upstream `UrlMappings.groovy` and
      record it in the design's upstream touch points.

## Phase 3 — Session integration

OB has no Spring Security plugin; integration is into the hand-rolled
`AuthController` / `AuthService` / `SecurityInterceptor` path. See design
Context and D1.1.

- [x] **3.1** Hand off to OB's existing session-setup logic. Per the spike
      `validation/active-flag-behavior.md`, either: (a) call an extracted
      `AuthController` method (one-line `AuthController` edit to make it
      callable), or (b) create `Dhis2SessionService` that sets the session
      `User` and calls `AuthService.setCurrentUser` directly. Pick the
      smaller of the two upstream edits and record the choice in design
      touch points.
- [x] **3.2** Pending-access GSP at `grails-app/views/dhis2auth/pending.gsp`
      — explains the user is awaiting access from an admin and provides a
      logout link.
- [x] **3.3** Edit `SecurityInterceptor` to redirect to the pending-access
      page when the session-stored `User` has `active = false` AND a
      `Dhis2UserLink` (whitelist: pending page itself, logout, static
      assets). Smallest possible block. Per spike's branch decision in
      design D3, this may need to happen earlier (in the OAuth callback) if
      `AuthController` blocks login outright for inactive users.
- [x] **3.4** Login GSP edit: conditional "Sign in with DHIS2" button block.
      Smallest possible diff at the path confirmed by spike task 1.6. Record
      in design touch points.
- [ ] **3.5** Spock integration tests for the OAuth callback end-to-end —
      mock DHIS2 with WireMock or an embedded HTTP server; assert session
      established and `User`/`Dhis2UserLink` rows created, and that
      `SecurityInterceptor` redirects an inactive linked user to the
      pending-access page.

## Phase 4 — Admin UX

- [x] **4.1** Add a "Pending DHIS2 access" filter option to the existing user
      admin list controller/view. Surgical edit; record in touch points.
- [ ] **4.2** Manual smoke test against a real (non-iframed) DHIS2: register
      a fresh DHIS2 user, log in via OB, see them in pending list, grant
      role + location, confirm next login reaches a normal OB page.

## Phase 5 — Tests, docs, archive

- [ ] **5.1** All Spock tests green: `./gradlew test`.
- [ ] **5.2** Frontend untouched. Run `npm test` to confirm no regressions.
- [ ] **5.3** Update top-level `README.md` only if user-facing behavior is
      enabled by default (it isn't — skip unless we decide to enable it on
      a specific customer branch).
- [ ] **5.4** Update PR description on the open PR (if any) to reflect final
      shape.
- [ ] **5.5** OpenSpec archive: ensure `design.md` "Upstream touch points"
      lists every modified upstream file; add "Deploy status" line; run
      `/opsx:archive`.
