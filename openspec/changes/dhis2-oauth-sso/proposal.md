## Why

OpenBoxes is being deployed alongside DHIS2 in environments where DHIS2 is the
authoritative identity system for health workers. Operators want a single login
(via DHIS2) and want to embed OpenBoxes screens inside DHIS2 dashboards/apps via
iframe. Today OB has its own username/password store, no SSO, and serves
`X-Frame-Options: SAMEORIGIN` so it cannot be embedded.

## What Changes

- Add DHIS2 as an OAuth2 identity provider (Authorization Code flow). Existing
  username/password login keeps working unchanged; DHIS2 login is an additional
  option on the login page.
- On first DHIS2 login for an unknown user, auto-register a local OpenBoxes
  `User` with `active = false` and **no roles, no locations**. The user lands on
  a "pending access" page until an admin grants them roles/locations the normal
  way. On subsequent logins, refresh name/email from DHIS2, do not touch roles.
- Match users by DHIS2 UID (stable, never reused). Store the link in a new
  side-table `Dhis2UserLink` (per `upstream-entity-extension` rule) — never add
  columns to `User`.
- Replace `X-Frame-Options` with `Content-Security-Policy: frame-ancestors`
  scoped to a configured allow-list of DHIS2 origins. Mark the session cookie
  `SameSite=None; Secure`. Honor `X-Forwarded-Proto` so OB knows it's behind
  TLS-terminating nginx.
- Provide a local Docker dev stack: nginx terminating TLS in front of `dhis2`
  and `openboxes` containers, mkcert-issued certs, `*.localtest.me` hostnames
  so the OAuth + iframe flow can be tested faithfully without a public domain.
- Admin UX: a "pending users" filter on the existing user admin screen so
  admins can find newly-registered DHIS2 users awaiting role assignment. No
  notification system in v1 (admins poll).

Out of scope for v1: refresh token handling (re-prompt on expiry instead),
group → role auto-mapping, deep-linking from DHIS2 dashboard items into
specific OB screens, deactivation propagation beyond "next login fails."

## Capabilities

### New Capabilities
- `dhis2-auth`: DHIS2 OAuth2 client, first-login registration, user linking,
  pending-access flow.
- `iframe-embedding`: CSP `frame-ancestors`, `SameSite=None; Secure` cookies,
  forwarded-proto handling, configurable DHIS2 origin allow-list.
- `local-dev-tls`: docker-compose stack with nginx + mkcert + DHIS2 + OB for
  faithful local OAuth/iframe testing.

### Modified Capabilities
<!-- None — existing user/auth flows are not modified at the spec level; password
     login keeps its current behavior. -->

## Impact

- **New custom backend code** under `org.pih.warehouse.custom.dhis2auth`:
  OAuth controller (callback + initiation), token-exchange service,
  DHIS2 client, registration service, `Dhis2UserLink` domain class, security
  filter integration.
- **New custom frontend** under `src/js/custom/dhis2auth` *only if* a React
  pending-access page is preferred over a GSP page. Default plan is GSP to
  minimize bundle changes.
- **Liquibase**: one new changeset under `grails-app/migrations/custom/` for
  the `dhis2_user_link` table.
- **Upstream touch points** (kept minimal, surgical, documented in design.md):
  - `grails-app/conf/application.yml` — config keys for DHIS2 OAuth client,
    iframe allow-list, forwarded-headers; default values keep current behavior.
  - `grails-app/conf/application.groovy` or `spring/resources.groovy` — wire the
    custom security filter into the `spring-security-core` chain.
  - Login GSP — add a "Sign in with DHIS2" button (one element, conditionally
    rendered when DHIS2 OAuth is configured).
  - One filter or interceptor that emits `CSP: frame-ancestors` and removes
    `X-Frame-Options` for matching responses.
- **New dev-environment files** under `docker/dhis2-sso/`: `docker-compose.yml`,
  `nginx.conf`, cert generation README. Does not modify the existing
  `docker/docker-compose.yml`.
- **Dependencies**: no new Grails plugins if we hand-roll the OAuth flow
  (preferred — avoids Grails-3.3-compatibility risk). If we adopt
  `spring-security-oauth2:2.x`, that's a new managed dependency.
- **Operational**: deployments embedding OB in DHIS2 must terminate TLS on a
  domain reachable by both DHIS2 and the user's browser, and configure the
  DHIS2 origin allow-list. Documented in the change's `design.md`.
