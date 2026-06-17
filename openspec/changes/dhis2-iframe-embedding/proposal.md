## Why

Operators deploying OpenBoxes alongside DHIS2 want to embed OB screens inside
DHIS2 dashboards/apps via iframe so users do not have to context-switch tabs.
Today OB serves `X-Frame-Options: SAMEORIGIN`, which blocks iframe embedding
from any other origin, and the session cookie is set without `SameSite=None`,
which would prevent it being sent on cross-site requests inside an iframe
even if the framing were allowed.

This change owns the response-header, cookie, and forwarded-proto changes
needed to make iframe embedding work, plus a local TLS dev stack to test the
flow end-to-end without a public domain. It is independent of how users log
in — it makes embedding possible whether the user authenticates via DHIS2
SSO (see `dhis2-oauth-core`) or local username/password.

**This change depends on `dhis2-oauth-spike` being archived first** —
`design.md` Decisions reference its `validation/` artifacts, especially the
embedded-Tomcat version and `Rfc6265CookieProcessor.sameSiteCookies`
availability.

`dhis2-oauth-core` is NOT a hard prerequisite for the code in this change.
The dev stack here pairs naturally with SSO for E2E testing, but the
header/cookie work stands on its own.

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

Out of scope: OAuth/SSO flow itself (see `dhis2-oauth-core`), deep-linking from
DHIS2 dashboard items into specific OB screens, iframe-aware re-auth handling
when access tokens expire.

## Capabilities

### New Capabilities
- `iframe-embedding`: CSP `frame-ancestors`, `SameSite=None; Secure` cookies,
  forwarded-proto handling, configurable DHIS2 origin allow-list.
- `local-dev-tls`: docker-compose stack with nginx + mkcert + DHIS2 + OB for
  faithful local OAuth/iframe testing.

### Modified Capabilities
<!-- None — upstream login/session flow is preserved when the feature is off. -->

## Impact

- **New custom backend code** under `org.pih.warehouse.custom.dhis2auth.iframe`
  (or a sibling package — same custom isolation rules): CSP filter, cookie
  rewrite filter (if needed per spike), forwarded-headers config.
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
