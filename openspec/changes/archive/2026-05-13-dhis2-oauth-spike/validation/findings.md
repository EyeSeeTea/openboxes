# Spike Findings

Source: <dhis2-host> (DHIS2 2.x), OpenBoxes codebase, Gradle dependency tree.

---

- **DHIS2 OAuth2 endpoints** → Confirmed at `/uaa/oauth/authorize` and `/uaa/oauth/token`. Token response shape: `{access_token, token_type:"bearer", refresh_token, expires_in, scope:"ALL"}`. Impact: dhis2-oauth-core D1 — hand-rolled OAuth client targets these exact endpoints; no surprises.

- **DHIS2 `/api/me` shape** → `id` (11-char UID), `username`, `displayName`, `firstName`, `surname`, `email` all present. Impact: dhis2-oauth-core D2 — UID match confirmed; `dhis2_user_link.dhis2_uid VARCHAR(11)` is correct.

- **DHIS2 UIDs are stable 11-char identifiers** → Confirmed: `xEhRFt7bYkA`. Impact: dhis2-oauth-core D2 — match-by-UID strategy is sound.

- **DHIS2 OAuth2 client registration** → Admin UI at Settings → OAuth2 Clients (`/dhis-web-settings/#/oauth2clients`). Also available via REST API at `/api/oAuth2Clients` (POST with `name`, `cid`, `secret` [exactly 36 chars / UUID], `redirectUris`, `grantTypes`). Impact: dhis2-oauth-core D1 — deployment guide should document both paths; the 36-char secret constraint means secrets must be UUIDs.

- **Embedded Tomcat version** → 8.5.88. `Rfc6265CookieProcessor.sameSiteCookies` was backported to Tomcat 8.5 in 8.5.47 — it IS available at 8.5.88. However, dhis2-iframe-embedding D2 (Set-Cookie-rewriting filter) is still the recommended approach because it doesn't require Tomcat XML config changes. Impact: dhis2-iframe-embedding D2 — design note updated; filter approach stands but the Rfc6265CookieProcessor path is an available fallback.

- **Upstream OB login GSP path** → `grails-app/views/auth/login.gsp`. Impact: dhis2-oauth-core D4 — "Sign in with DHIS2" button goes in this file (confirmed).

- **X-Frame-Options in OB source** → Not found. No in-repo source. Impact: dhis2-iframe-embedding D1 conditional touch-point drops to zero; the CSP filter only needs to ADD `frame-ancestors`, not neutralize an existing OB-set header. Note: DHIS2 itself emits its own `frame-ancestors` CSP (for the DHIS2 UI being framed) — this is a DHIS2-side concern, not OB's.

- **User.active = false behavior** → Outcome (a): `AuthController.handleLogin` rejects inactive users outright (before password check). `SecurityInterceptor` also double-checks on every request. No reusable session-setup method on `AuthController` today. Impact: dhis2-oauth-core D3 branch 1 is the implementation path — OAuth callback must NOT call session-setup for inactive users; uses a session marker instead. Recommend extracting `Dhis2SessionService.establishSession(User, HttpSession)` to avoid duplicating the 5-line session block.

- **HTTP client on classpath** → `org.apache.httpcomponents:httpclient` and `fluent-hc` are explicit `implementation` dependencies. No HTTPBuilder. Impact: dhis2-oauth-core D1 — use `org.apache.http.client.fluent.Request` for token exchange and `/api/me` call; no new dependency needed.

- **Spring Boot 1.5 `server.use-forward-headers`** → Confirmed property name. Configures Tomcat's `RemoteIpValve` to honour `X-Forwarded-Proto`. Impact: dhis2-iframe-embedding D3 — property name confirmed; add to `docker/openboxes.yml` (not `application.yml`) when iframe embedding is enabled.

- **`*.localtest.me` resolution** → Both `dhis2.localtest.me` and `openboxes.localtest.me` resolve to `127.0.0.1` on this machine. Impact: dhis2-iframe-embedding D4 — hostname strategy confirmed; `/etc/hosts` workaround documented for corporate-DNS environments.
