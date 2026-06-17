## Context

Embedding OB inside a DHIS2 dashboard requires a CSP `frame-ancestors`
directive that names specific third-party origins, plus a session cookie that
survives a cross-site request.

Whether OB also emits a conflicting `X-Frame-Options` header today is
**determined by the spike** (`dhis2-oauth-spike` task 1.7 →
`validation/xframeoptions-source.txt`). A pre-audit grep of the repo found no
in-source `X-Frame-Options`, so the most likely outcome is:

- **No in-repo source.** The header may come from a deploy-level proxy (nginx
  in the dev stack, customer-side reverse proxy in production), or simply not
  be set at all. Either way, the iframe filter just ADDS CSP — no upstream
  code to neutralize.
- **Less likely**: a Grails plugin or Tomcat default is emitting it. The
  spike artifact pins this down. If it's emitted, our filter strips it on
  the way out (still no upstream-code edit needed — same filter, just an
  extra response-header removal).

The embedded Tomcat in Grails 3.3.16 / Spring Boot 1.5 is Tomcat 8.5.88
(confirmed by `dhis2-oauth-spike` validation/tomcat-version.txt).
`Rfc6265CookieProcessor.sameSiteCookies` was backported to Tomcat 8.5 in
8.5.47 — it IS technically available at 8.5.88. However, the cookie path
remains a `Set-Cookie`-rewriting Servlet `Filter` (D2) because the filter
approach requires no Tomcat XML config changes and is easier to toggle at
runtime per the `iframe.frameAncestors` flag. The Rfc6265CookieProcessor
path is an available fallback if the filter proves problematic.

Constraints: upstream-isolation (new code under `org.pih.warehouse.custom.*`,
surgical edits to upstream files), JDK 8 / Groovy 2.4 / Grails 3.3 floors.

## Goals / Non-Goals

**Goals:**
- OB pages embeddable in iframes whose top-level origin matches a configured
  DHIS2 allow-list; safely default-deny when the list is empty.
- Session cookie `SameSite=None; Secure` when embedding is enabled.
- `Secure` cookies set correctly behind a TLS-terminating proxy
  (`X-Forwarded-Proto: https`).
- Local TLS dev stack so developers can exercise the iframe flow against a
  real DHIS2 instance without owning a public domain.

**Goals (added for silent SSO):**
- A DHIS2-authenticated user opening an embedded OB screen lands authenticated
  with no second login, on the `v42` profile (silent `prompt=none` flow).
- `v40` embedded users degrade gracefully to a one-time in-frame login.

**Non-Goals:**
- The base OAuth/SSO flow itself (owned by `dhis2-oauth-core` / v42; this change
  only adds the *embedded* silent variant on top of it).
- Deep-linking from DHIS2 dashboard items into OB screens.
- Seamless re-entry into the originating DHIS2 dashboard after a break-out
  interactive login — the user returns via the DHIS2 dashboard, at which point
  the frame loads silently. (No automatic frame re-entry in v1.)
- Iframe-aware re-auth handling when DHIS2 access tokens expire (out of v1
  scope across all three changes; mitigation: configure long-lived DHIS2
  tokens).

## Decisions

### D1: CSP `frame-ancestors`, not `X-Frame-Options`

`X-Frame-Options: SAMEORIGIN` cannot allow specific third-party origins.
`Content-Security-Policy: frame-ancestors` can, and is the modern
replacement. We emit it from a custom filter that runs on all HTML responses,
configured via `iframe.frameAncestors` in `application.yml`. We also remove
any upstream-set `X-Frame-Options` header to avoid the two contradicting
each other.

**Gated to preserve upstream default (revised).** Earlier this decision emitted
`frame-ancestors 'self'` on every response by default. To keep non-embedding
deployments byte-for-byte identical to upstream (OB ships no framing header
today), the filter now **no-ops entirely when `openboxes.custom.iframe.frameAncestors`
is empty** and only emits the directive (and the X-Frame-Options suppression)
when the allow-list is non-empty. Config lives in `openboxes.yml` external
config under `openboxes.custom.iframe.frameAncestors`, not upstream
`application.yml`.

### D2: `SameSite=None` via Rfc6265CookieProcessor on OB's embedded Tomcat (revised)

**Revised from the original `Set-Cookie`-rewriting filter.** Verifying against
the actual code path showed a header-rewriting filter cannot reliably stamp the
one cookie that matters: Tomcat serialises `JSESSIONID` through its internal
`CookieProcessor` at response commit, not via `addHeader`/`setHeader`, so a
filter never sees it. The filter would amend app-set cookies but silently miss
the session cookie.

Instead, an `EmbeddedServletContainerCustomizer` (`IframeCookieCustomizer`,
custom code) installs Tomcat's `Rfc6265CookieProcessor` with
`sameSiteCookies=None` on **OB's own embedded Tomcat 8.5.88** when embedding is
enabled. This is the only Tomcat 8.5 processor that can emit `SameSite`, it is
already bundled (no Tomcat upgrade, no DHIS2 change, no XML), and it reliably
covers `JSESSIONID`.

- Scoped: no-op when `frameAncestors` is empty — OB keeps its default cookie
  processor, so non-embedding deployments are unchanged.
- `Secure` (required alongside `SameSite=None`) comes from D3 forwarded-proto:
  once OB knows it is behind TLS, Tomcat stamps `JSESSIONID` Secure.
- Footgun guard: the customizer logs a loud WARN that embedding requires TLS /
  `server.use-forward-headers`, since `SameSite=None` without `Secure` is
  dropped by browsers and would break login.
- Trade-offs (accepted): switching to `Rfc6265CookieProcessor` applies stricter
  RFC6265 cookie parsing for that deployment, and `SameSite=None` applies to all
  OB cookies (cross-site embedding inherently requires this; OB's own CSRF
  tokens remain the defence). WAR-on-external-Tomcat deployments must set the
  equivalent in that Tomcat's `context.xml`.

### D3: Forwarded-proto via Spring Boot's existing setting

Spring Boot 1.5 supports `server.use-forward-headers=true` (verified in
`dhis2-oauth-spike` validation/spring-boot-1.5-forward-headers.txt), which
configures Tomcat's `RemoteIpValve`. Setting it makes `request.isSecure()`
reflect the proxy's `X-Forwarded-Proto` header. We enable it via a profile
or conditional only when iframe embedding is enabled, to avoid changing
behavior for deployments without a TLS-terminating proxy.

### D4: Local dev stack as a separate `docker/dhis2-sso/` directory

Avoid touching `docker/docker-compose.yml` (upstream file). New directory
contains:
- `docker-compose.yml` — services: `nginx`, `dhis2`, `dhis2-db` (postgres),
  `openboxes`, `openboxes-db` (mysql).
- `nginx.conf` — two `server` blocks (443/TLS) proxying to internal services,
  WebSocket upgrade headers, `X-Forwarded-Proto`, `X-Forwarded-For`, `Host`.
- `certs/.gitignore` — committed empty dir; certs generated by the developer.
- `README.md` — mkcert install, cert generation command, OAuth client
  registration walkthrough in DHIS2 (referencing the spike's setup notes),
  troubleshooting.

Hostnames: `dhis2.localtest.me` and `openboxes.localtest.me`. `*.localtest.me`
is reserved by RFC 6761 / IANA and resolves to `127.0.0.1` automatically —
no `/etc/hosts` edits required for most resolvers (confirmed by
`dhis2-oauth-spike` validation/localtest-resolution.txt; corporate-DNS
workaround documented in the README).

The compose file in this change supersedes the spike's
`docker-compose.spike.yml` (which only had DHIS2 + postgres). Once this
change lands, the spike's file can be deleted as part of `dhis2-oauth-spike`
archival.

### D5: In-frame silent SSO (v42) with break-out on interactive login

Grounded in `validation/prompt-none.md`: DHIS2 v42 (Spring Authorization Server
1.5.x) honors OIDC `prompt=none`; legacy v40/UAA does not.

**v42 silent flow.** When embedding is enabled and an unauthenticated request
arrives, the custom login redirect (the existing `dhis2auth` interceptor /
controller path, not upstream) sends the user to the DHIS2 authorize endpoint
with `prompt=none` added to the normal PKCE + `scope=openid username` request.
`Dhis2OAuthService.buildAuthorizeUrl` gains an optional `prompt` argument; no
other OAuth code changes. On a live DHIS2 session with consent already granted,
DHIS2 returns a `code` and the existing callback establishes the session — zero
UI.

**Error handling = break out of the frame.** DHIS2 returns `prompt=none`
failures as `error=login_required` / `consent_required` / `interaction_required`
on the redirect back to OB's callback. OB must NOT respond by redirecting the
*frame* to DHIS2's interactive login — DHIS2 serves its own `X-Frame-Options`,
so that would render blank. Instead the callback returns a minimal break-out
page whose script sets `window.top.location` to the interactive authorize URL
(same request, `prompt` omitted). DHIS2's login then renders top-level; after
login the OB session cookie (`SameSite=None; Secure`) is set, and the user
returns to the DHIS2 dashboard, where the frame now loads silently.

**Loop guard.** A persistent `login_required` must not bounce authorize↔callback
forever. A one-shot marker (a short-lived session flag or a `silent=tried`
query param echoed through `state`) ensures at most one `prompt=none` attempt
per navigation before falling through to the break-out.

**Detecting "embedded".** OB cannot see framing server-side. We treat embedding
as active when `iframe.frameAncestors` is non-empty; that config is the single
switch. (The break-out page additionally guards with `window.top === window.self`
on the client so a top-level visit never breaks itself out.)

**Consent prerequisite (v42).** For true zero-click, the OB OAuth client in
DHIS2 must be registered with `requireAuthorizationConsent=false` (DHIS2
defaults it to `true` — `validation/prompt-none.md`). Otherwise the first
embedded visit per user breaks out once for consent, then is silent thereafter.

**v40.** No `prompt=none` path. The interceptor makes no silent attempt for
profile `v40`; the user logs in once inside the frame and the `SameSite=None`
cookie carries the session forward.

## Risks / Trade-offs

- **`SameSite=None` cookie rewriting** depends on the `Set-Cookie` header
  being unset before our filter runs. If upstream code commits the cookie
  via a pre-baked `Set-Cookie` string mid-response (rare in Grails), the
  filter would not be able to amend it. Mitigation: filter runs late in the
  chain; if a real case shows up, document the operational workaround
  ("front OB with nginx that rewrites `Set-Cookie`").
- **Origin allow-list misconfiguration** silently breaks the iframe. The
  browser shows the CSP violation in DevTools console. Mitigation: prominent
  troubleshooting section in the dev stack README.
- **DHIS2 OAuth2 client setup varies by DHIS2 version**: the README pins the
  DHIS2 image tag (same tag used in `dhis2-oauth-spike`).
- **Refresh tokens out of scope across all three changes**: in an iframe,
  the re-auth bounce may break out of the iframe when tokens expire.
  Mitigation: configure long-lived DHIS2 access tokens, or accept the
  re-auth bounce in v1. Document in the README.
- **Third-party cookies block silent SSO (D5).** The OB session cookie is
  third-party relative to the DHIS2 host page. `SameSite=None; Secure` (D2)
  covers browsers that still permit third-party cookies, but Chrome's
  third-party-cookie phase-out can block the in-frame session entirely —
  silent `prompt=none` would succeed yet the resulting cookie would not be
  sent on the next embedded request. Mitigations, in order of preference:
  (a) deploy OB and DHIS2 under a shared parent domain so the cookie is
  first-party; (b) `Partitioned` cookies (CHIPS) once viable on the target
  Tomcat/browser matrix; (c) accept that fully-blocked browsers fall back to
  the in-frame login. This is environment-dependent — confirm against the
  customer's actual browser + domain topology during the live test.
- **`prompt=none` behavior is library-confirmed but not yet live-verified.**
  `validation/prompt-none.md` flags spring-security #18647 (a 7.x regression
  that should not affect the 6.5.x line DHIS2 2.42 ships). The three-case
  manual test must pass on the target DHIS2 before relying on D5.

## Migration Plan

1. Land code with `iframe.frameAncestors = []` defaults — zero behavior
   change for existing deployments.
2. No schema changes — no Liquibase work.
3. Per-deployment opt-in: set the allow-list of DHIS2 origins in
   env-specific config and enable `server.use-forward-headers` if behind a
   TLS-terminating proxy.
4. Rollback: clear `frameAncestors`. The CSP filter becomes a no-op and emits
   no framing header, and the cookie customizer leaves OB's default cookie
   processor in place — reverting fully to OB's upstream default.

## Upstream touch points

The entire feature touches **one** upstream file (better than originally
predicted — no `application.yml` edit, no X-Frame-Options neutralization):

- `grails-app/conf/spring/resources.groovy` — registers the CSP filter and the
  cookie customizer beans (an import + a few lines in the `beans {}` block). The
  bean classes themselves are custom (`org.pih.warehouse.custom.iframe`).

Dropped vs the original plan:
- `application.yml` — NOT edited. `frameAncestors` and
  `server.use-forward-headers` live in `openboxes.yml` external config; the CSP
  filter defaults to a no-op when unset.
- X-Frame-Options neutralization — NOT needed. A repo grep confirmed OB emits no
  in-repo `X-Frame-Options`; the response wrapper suppresses any that a proxy
  adds, with no upstream-code edit.

The silent-SSO work (D5) touches **only this fork's own custom files** — no
new upstream touch points:
- `org.pih.warehouse.custom.dhis2auth.Dhis2OAuthService` — optional `prompt`
  on the authorize URL.
- The `dhis2auth` login interceptor / `Dhis2OAuthController` callback —
  embedded silent redirect, one-shot loop guard, and the break-out-on-error
  response.

Everything else lives under the new custom package and `docker/dhis2-sso/`.

## Confidence: 9/10

Re-scored after `dhis2-oauth-spike` validation artifacts landed.

All assumptions confirmed:
- Tomcat 8.5.88 — filter-based cookie rewrite path confirmed (D2) ✓
- No OB in-repo source of `X-Frame-Options` — conditional D1 touch-point
  drops to zero for in-repo code ✓
- `server.use-forward-headers=true` property name confirmed (D3) ✓
- `*.localtest.me` resolves to 127.0.0.1 on this machine (D4) ✓
- Config belongs in `docker/openboxes.yml`, not `application.yml` ✓

Note: DHIS2 itself already emits a `frame-ancestors` CSP on its own
responses (for the DHIS2 UI). This is DHIS2-side and does not affect OB's
filter, which controls OB's own response headers.

Residual risk: the `Set-Cookie` rewrite filter cannot amend cookies committed
mid-response by upstream code (rare in Grails). Mitigation documented: front
OB with nginx that rewrites `Set-Cookie` as a last resort.
