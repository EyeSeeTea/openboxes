## Context

This change is the risk-reduction gate for `dhis2-oauth-core` and
`dhis2-iframe-embedding`. Both follow-ups carry several externally-claimed
facts (DHIS2 OAuth endpoints, `/api/me` shape, embedded Tomcat version,
Spring Boot 1.5 forward-headers config name, login GSP path, whether OB
emits `X-Frame-Options` from in-repo code, how OB's hand-rolled
`AuthController` / `SecurityInterceptor` treat `User.active = false`,
`HTTPBuilder` availability).
This spike converts each into a captured artifact so the follow-ups can be
designed against reality rather than memory.

## Goals / Non-Goals

**Goals:**
- Each assumption listed in §Assumptions below becomes a file under
  `validation/` with the relevant excerpt, response, or grep output.
- Update the follow-up changes' Decisions / Confidence after the spike.

**Non-Goals:**
- No production code.
- No specs (this change adds no capabilities).
- No public-domain or TLS work — pure local HTTP is fine for the spike.
- No iframe wiring — `dhis2-iframe-embedding` owns that.

## Decisions

### D1: DHIS2 image is pinned

The DHIS2 image tag must be pinned (e.g. `dhis2/core:2.40.0`). DHIS2 behavior —
including the OAuth client admin UI path — varies by version. The pinned tag
becomes the DHIS2 version both follow-ups target.

### D2: Spike-only compose file is separate from the production dev stack

`docker/dhis2-sso/docker-compose.spike.yml` runs DHIS2 + postgres only. The
full stack (with OB and nginx and TLS) lives in `dhis2-iframe-embedding` and
adds to the same directory. Keeping the spike file separate means the spike
keeps working even while the follow-up evolves the production compose file.

## Risks / Trade-offs

- **Spike artifacts go stale**: DHIS2 may patch its OAuth surface between
  spike and implementation. Mitigation: pin DHIS2 to the same tag in both
  the spike and `dhis2-iframe-embedding`.
- **Spike produces a "no" answer**: if e.g. `Rfc6265CookieProcessor.sameSiteCookies`
  is unavailable on the embedded Tomcat (likely — Tomcat 8.5 ships with
  Spring Boot 1.5), `dhis2-iframe-embedding` switches to the `Set-Cookie`
  rewrite filter path. That is the expected outcome, not a failure.

## Assumptions to verify

Each becomes a `validation/<name>` artifact:

1. DHIS2 exposes OAuth2 at `/uaa/oauth/authorize` and `/uaa/oauth/token` and
   user details at `/api/me`.
2. DHIS2 UIDs are stable 11-character identifiers.
3. The DHIS2 admin UI lets us register OAuth2 clients without writing config
   files.
4. The Authorization Code flow completes end-to-end against this DHIS2
   version with the parameters our follow-ups assume.
5. The embedded Tomcat version in Grails 3.3.16 / Spring Boot 1.5 — does it
   support `Rfc6265CookieProcessor.sameSiteCookies`?
6. The upstream OB login GSP path (historically `auth.gsp`).
7. The mechanism by which upstream OB sets `X-Frame-Options` today.
8. How OB's hand-rolled auth pipeline (`AuthController` +
   `SecurityInterceptor`) treats `User.active = false`: does
   `AuthController.login` reject outright, does `SecurityInterceptor` 403
   all routes, or does navigation succeed? Also: does `AuthController`
   expose a reusable session-setup method the OAuth callback can call, or
   do we need a small extract?
9. Is `groovyx.net.http.HTTPBuilder` (or an equivalent) on the classpath?

## Validation

- [ ] Every `validation/*` artifact named in tasks.md exists and is non-empty.
- [ ] `validation/findings.md` summarizes what each artifact proved or
      disproved, with a one-line "follow-ups impact" pointing to which
      Decision in `dhis2-oauth-core` or `dhis2-iframe-embedding` it touches.
- [ ] Both follow-up changes' `design.md` Confidence sections are re-scored
      and any invalidated Decisions are rewritten.

## Confidence: 9/10

Low risk — the spike either confirms our assumptions (good) or replaces them
with facts (also good). The only way this change fails is the DHIS2 container
not starting, which is recoverable by trying a different pinned tag.
