# Implementation Tasks — dhis2-iframe-embedding

**Prerequisite:** `dhis2-oauth-spike` is archived. Validation artifacts are
referenced below. The silent-SSO phase builds on `dhis2-oauth-core` / v42 (this
change branches from `feature/dhis2-oauth`).

## Phase 1 — CSP and forwarded-proto

- [x] **1.1** Config key `openboxes.custom.iframe.frameAncestors` (list, default
      `[]`) read from `openboxes.yml` external config — no upstream
      `application.yml` edit. `server.use-forward-headers` documented in the
      client template (Phase 3).
- [x] **1.2** `CspFrameAncestorsFilter` (`org.pih.warehouse.custom.iframe`) emits
      `Content-Security-Policy: frame-ancestors 'self' <configured>` and wraps the
      response to suppress `X-Frame-Options`. **Gated:** no-op when the allow-list
      is empty, so non-embedding deployments are byte-for-byte upstream.
- [x] **1.3** No in-repo `X-Frame-Options` source exists (repo grep), so no
      upstream neutralization needed; the response wrapper strips any added later.
- [x] **1.4** Spock filter tests: empty allow-list → untouched response;
      populated → directive includes each origin + wrapper applied; wrapper drops
      `X-Frame-Options` on setHeader/addHeader.

## Phase 2 — Cookie path (SameSite=None) via Rfc6265CookieProcessor

> Revised from the original `Set-Cookie`-rewriting filter — see design D2. A
> header filter cannot reach Tomcat-serialised `JSESSIONID`; the cookie processor
> can.

- [x] **2.1** `IframeCookieCustomizer` (`EmbeddedServletContainerCustomizer`)
      installs `Rfc6265CookieProcessor(sameSiteCookies=None)` on OB's embedded
      Tomcat 8.5.88 when embedding is enabled. No Tomcat upgrade, no DHIS2 change.
- [x] **2.2** Scoped + guarded: no-op when `frameAncestors` empty (keeps OB's
      default cookie processor); logs a loud WARN that embedding requires TLS /
      forwarded-proto (SameSite=None needs Secure or login breaks).
- [x] **2.3** Spock tests: registers one context customizer when enabled; none
      when disabled; no-op for a non-Tomcat container.

## Phase 3 — Forwarded-proto

- [x] **3.1** `server.use-forward-headers=true` documented in the client template
      (set via `openboxes.yml`, only for TLS-proxied embedding deployments). No
      upstream `application.yml` edit. Provides the `Secure` flag that
      `SameSite=None` requires.
- [ ] **3.2** Live check (folded into 5.7): confirm `request.isSecure()` and a
      `Secure` session cookie behind the dev-stack nginx. (Spring Boot built-in;
      not unit-testable without the container.)

## Phase 4 — Local dev stack

- [x] **4.1** `docker/dhis2-sso/docker-compose.yml` — nginx (TLS) + openboxes +
      mysql; DHIS2 treated as external (`DHIS2_UPSTREAM`).
- [x] **4.2** `docker/dhis2-sso/nginx.conf` — TLS server blocks for
      `openboxes.localtest.me` / `dhis2.localtest.me`, forwarded-proto headers,
      WebSocket upgrade, mounted certs.
- [x] **4.3** `docker/dhis2-sso/README.md` — mkcert, certs, OB config, DHIS2
      OAuth client setup (incl. `require-authorization-consent=false`),
      troubleshooting.
- [x] **4.4** `docker/dhis2-sso/certs/.gitignore` — ignore all but `.gitignore`.
- [ ] **4.5** End-to-end manual run-through from a clean checkout; capture gotchas
      into the README. (Scaffolding is untested.)
- [x] **4.6** No spike compose file exists to delete (n/a).

## Phase 5 — Embedded silent SSO (v42) + v40 fallback

- [x] **5.1** Optional `silent` arg on `Dhis2OAuthService.prepareAuthorize` /
      `buildAuthorizeUrl` → appends `&prompt=none` for v42 only;
      `isSilentAuthSupported()` exposes v42-only support.
- [x] **5.2** `Dhis2OAuthController.initiate`: embedded entry
      (`?embedded=true`) + embedding enabled + v42 → silent `prompt=none`;
      stores a one-shot `session.dhis2OAuthSilent` guard.
- [x] **5.3** `callback`: on `login_required`/`consent_required`/`interaction_required`
      from a silent attempt → renders `breakout.gsp` which navigates
      `window.top` to interactive login (no `prompt=none`, so no loop).
- [x] **5.4** v40: no silent attempt (profile guard); in-frame login works under
      the CSP + SameSite=None cookie.
- [x] **5.5** Spock tests: embedded v42 → `prompt=none`; embedding off → no
      silent; callback breakout vs login redirect; loop-guard cleared; v40 →
      no `prompt=none`.
- [x] **5.6** Consent prerequisite documented in `docker/dhis2-sso/README.md`,
      citing `validation/prompt-none.md`.
- [ ] **5.7** **LIVE (manual):** prompt=none three-case test — DONE for the raw
      flow (`validation/prompt-none.md`, confirmed on DHIS2 2.42.4.1). Still TODO:
      the full embedded end-to-end in a browser (frame render + cookie survival +
      break-out), via the Phase 4 dev stack.

## Phase 6 — Tests, docs, archive

- [ ] **6.1** Full suite green: `./gradlew test` (custom specs green; full run
      pending).
- [ ] **6.2** Frontend untouched. `npm test` to confirm no regressions.
- [ ] **6.3** Update top-level `README.md` only if enabled by default (it isn't).
- [ ] **6.4** Update PR description to reflect final shape.
- [ ] **6.5** OpenSpec archive: `design.md` upstream touch points
      (`resources.groovy` only) + Deploy status; run `/opsx:archive`.
