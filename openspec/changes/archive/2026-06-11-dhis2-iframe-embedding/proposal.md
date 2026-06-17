## Why

Operators deploying OpenBoxes alongside DHIS2 want to embed OB screens inside
DHIS2 dashboards/apps via iframe so users do not have to context-switch tabs.
Today OB serves `X-Frame-Options: SAMEORIGIN`, which blocks iframe embedding
from any other origin, and the session cookie is set without `SameSite=None`,
which would prevent it being sent on cross-site requests inside an iframe
even if the framing were allowed.

This change owns the response-header, cookie, and forwarded-proto changes
needed to make iframe embedding work, a local TLS dev stack to test the flow
end-to-end without a public domain, AND the in-frame SSO behavior that makes
the embedded experience seamless: a DHIS2-authenticated user opening an
embedded OB screen should land authenticated without a second login.

The header/cookie/proxy plumbing is independent of how users log in. The
seamless in-frame SSO, however, builds on the DHIS2 OAuth flow
(`dhis2-oauth-core` / v42) — it is the OAuth flow run silently inside the
frame. This change therefore branches from and depends on the DHIS2 OAuth
work.

**This change also depends on `dhis2-oauth-spike` being archived** —
`design.md` Decisions reference its `validation/` artifacts (embedded-Tomcat
version, `Rfc6265CookieProcessor.sameSiteCookies` availability). The silent-SSO
design is grounded in `validation/prompt-none.md` (source-level confirmation
that DHIS2 v42 / Spring Authorization Server honors OIDC `prompt=none`, and
that legacy v40 / UAA does not).

## What Changes

- Replace `X-Frame-Options` with `Content-Security-Policy: frame-ancestors`
  scoped to a configured allow-list of DHIS2 origins (default empty → behaves
  equivalently to `frame-ancestors 'self'`).
- Mark the session cookie `SameSite=None; Secure` when iframe embedding is
  enabled (allow-list non-empty). Unchanged otherwise.
- Honor `X-Forwarded-Proto` so OB recognizes it is behind a TLS-terminating
  proxy and sets `Secure` cookies correctly.
- Provide a local Docker dev stack under `docker/dhis2-sso/`: nginx
  terminating TLS in front of `dhis2` and `openboxes` containers, mkcert-issued
  certs, `*.localtest.me` hostnames so the OAuth + iframe flow can be tested
  faithfully without a public domain.
- **Seamless in-frame SSO (v42):** when embedding is enabled and an
  unauthenticated request arrives, OB silently runs the OAuth Authorization
  Code flow with OIDC `prompt=none`. If the user has a live DHIS2 session (and
  the OB client skips consent), OB establishes a session with zero UI. If DHIS2
  returns `login_required` / `consent_required`, OB breaks out of the frame for
  a one-time interactive login rather than rendering DHIS2's login page inside
  the frame (which DHIS2's own `X-Frame-Options` would block).
- **v40 fallback (login-once-in-frame):** legacy UAA does not support
  `prompt=none` (`validation/prompt-none.md`), so v40 embedded users log in
  once inside the frame (DHIS2 SSO button or local credentials); the
  `SameSite=None` session cookie then keeps them authenticated on later loads.

Out of scope: deep-linking from DHIS2 dashboard items into specific OB screens;
seamless re-entry into the *same* dashboard after a break-out interactive login
(the user returns via the DHIS2 dashboard, at which point the frame loads
silently); iframe-aware re-auth handling when access tokens expire.

## Capabilities

### New Capabilities
- `iframe-embedding`: CSP `frame-ancestors`, `SameSite=None; Secure` cookies,
  forwarded-proto handling, configurable DHIS2 origin allow-list.
- `local-dev-tls`: docker-compose stack with nginx + mkcert + DHIS2 + OB for
  faithful local OAuth/iframe testing.

### Modified Capabilities
- `dhis2-auth`: adds embedded silent authentication — when OB is framed and
  embedding is enabled, the v42 OAuth flow runs with `prompt=none` and breaks
  out of the frame on `login_required`/`consent_required`; v40 falls back to
  in-frame login. The non-embedded (top-level) login flow is unchanged.

## Impact

- **New custom backend code** under `org.pih.warehouse.custom.dhis2auth.iframe`
  (or a sibling package — same custom isolation rules): CSP filter, cookie
  rewrite filter (if needed per spike), forwarded-headers config.
- **Custom backend edits (this fork's own files, not upstream)** under
  `org.pih.warehouse.custom.dhis2auth`: `Dhis2OAuthService` gains an optional
  `prompt` on the authorize URL; the controller/interceptor gains the
  embedded-silent-redirect + loop-guard + break-out-on-error handling.
- **Upstream touch points**:
  - `grails-app/conf/application.yml` — config keys for
    `iframe.frameAncestors` and forwarded-headers, with defaults that preserve
    upstream behavior.
  - The filter or interceptor that emits `X-Frame-Options` today (location
    confirmed by `dhis2-oauth-spike` task 1.7) — replace with the
    CSP-emitting filter, behind the allow-list flag.
- **New dev-environment files** under `docker/dhis2-sso/`: `docker-compose.yml`,
  `nginx.conf`, `README.md`, `certs/.gitignore`. Does not modify the existing
  `docker/docker-compose.yml`.
- **No new Grails plugins**.
- **No Liquibase changesets**.
- **Operational**: deployments embedding OB in DHIS2 must terminate TLS on a
  domain reachable by both DHIS2 and the user's browser, and configure the
  DHIS2 origin allow-list. Documented in the change's `design.md`.
