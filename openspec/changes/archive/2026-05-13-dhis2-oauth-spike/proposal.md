## Why

We want to integrate DHIS2 as an OAuth2 identity provider for OpenBoxes and
allow OB pages to be embedded inside DHIS2 dashboards via iframe (see sibling
changes `dhis2-oauth-core` and `dhis2-iframe-embedding`). Both designs rest
on several externally-claimed facts: the DHIS2 OAuth surface, the embedded
Tomcat version in Grails 3.3.16, the shape of OB's hand-rolled login
pipeline (`AuthController` / `AuthService` / `SecurityInterceptor` — OB has
no Spring Security plugin), and how that pipeline treats `User.active = false`.
None of those facts have been verified against a running stack.

Implementing either follow-up change before resolving those assumptions would
risk mid-implementation redesign — most notably around the `SameSite=None`
cookie path and the pending-access flow. This change exists to convert each
assumption into a captured artifact, before any production code is written.

## What Changes

This is a **pure investigation change** — it produces validation artifacts
under `openspec/changes/dhis2-oauth-spike/validation/` and one minimal
`docker-compose.yml` fragment for running DHIS2 locally. It does NOT add
production code, migrations, or specs.

- Stand up a minimal local DHIS2 container (DHIS2 + postgres, no OB, no nginx)
- Capture the DHIS2 `/api/me` shape, OAuth2 endpoint behavior, and the admin
  UI path for registering OAuth2 clients
- Drive the Authorization Code flow manually (curl/browser) and capture the
  authorize redirect and token-exchange response
- Resolve embedded-Tomcat version and `Rfc6265CookieProcessor.sameSiteCookies`
  availability
- Identify the upstream OB login GSP path and trace the hand-rolled auth
  pipeline (`AuthController` + `AuthService` + `SecurityInterceptor`) for
  `User.active = false` behavior — and whether `AuthController` exposes a
  reusable session-setup method the OAuth callback can call
- Determine whether OB emits `X-Frame-Options` from an in-repo source today
  (pre-audit grep found none — confirm)
- Confirm `HTTPBuilder` (or equivalent HTTP client) availability on the
  classpath

On completion, re-score the design confidence in both follow-up changes and
update their Decisions sections if any spike result invalidates an assumption.

## Capabilities

### New Capabilities
<!-- None — this is a research-only change. Capability specs land in the
     follow-up changes (`dhis2-oauth-core`, `dhis2-iframe-embedding`). -->

### Modified Capabilities
<!-- None. -->

## Impact

- **New artifacts only**: files under `openspec/changes/dhis2-oauth-spike/validation/`
- **One throwaway file**: `docker/dhis2-sso/docker-compose.spike.yml` (DHIS2 +
  postgres only). Lives in the same `docker/dhis2-sso/` folder that
  `dhis2-iframe-embedding` will flesh out — kept here so it doesn't bit-rot in
  isolation. Pinned image tag.
- **No upstream files modified.**
- **No Liquibase changesets.**
- **No new dependencies.**
- **Operational impact**: none. Spike runs entirely on the developer's machine.

## Gates the follow-ups

`dhis2-oauth-core` and `dhis2-iframe-embedding` MUST NOT start until this
change's validation artifacts are complete and reviewed.
