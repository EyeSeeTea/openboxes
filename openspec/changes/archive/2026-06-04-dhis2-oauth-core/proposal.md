## Why

OpenBoxes is being deployed alongside DHIS2 in environments where DHIS2 is the
authoritative identity system for health workers. Operators want a single
login experience via DHIS2 without disturbing the existing local
username/password flow. Today OB has its own user store with no SSO support.

This change adds the SSO flow itself (OAuth2 Authorization Code, pending-access
on first login, admin-managed authorization) without touching response headers,
cookie attributes, or iframe embedding — those live in the sibling change
`dhis2-iframe-embedding` and are an opt-in deployment concern. Splitting them
keeps each change reviewable in isolation and avoids coupling auth landing on
the cookie/CSP risk.

**This change depends on `dhis2-oauth-spike` being archived first** —
`design.md` Decisions reference its `validation/` artifacts.

## What Changes

- Add DHIS2 as an OAuth2 identity provider (Authorization Code flow). Existing
  username/password login keeps working unchanged; DHIS2 login is an
  additional option on the login page.
- On first DHIS2 login for an unknown user, auto-register a local OpenBoxes
  `User` with `active = false` and **no roles, no locations**. The user lands
  on a "pending access" page until an admin grants them roles/locations the
  normal way. On subsequent logins, refresh name/email from DHIS2, do not
  touch roles.
- Match users by DHIS2 UID (stable, never reused). Store the link in a new
  side-table `Dhis2UserLink` (per `upstream-entity-extension` rule) — never
  add columns to `User`.
- Admin UX: a "pending users" filter on the existing user admin screen so
  admins can find newly-registered DHIS2 users awaiting role assignment. No
  notification system in v1 (admins poll).

Out of scope for this change: refresh-token handling (re-prompt on expiry
instead), group → role auto-mapping, org-unit → location auto-mapping,
deactivation propagation beyond "next login fails." Iframe embedding,
`SameSite=None` cookies, CSP `frame-ancestors`, forwarded-proto handling, and
the local TLS dev stack are out of scope here — see `dhis2-iframe-embedding`.

## Capabilities

### New Capabilities
- `dhis2-auth`: DHIS2 OAuth2 client, first-login registration, user linking,
  pending-access flow, admin filter for pending users.

### Modified Capabilities
<!-- None — existing user/auth flows are not modified at the spec level;
     password login keeps its current behavior. -->

## Impact

- **New custom backend code** under `org.pih.warehouse.custom.dhis2auth`:
  OAuth controller (callback + initiation), token-exchange service, DHIS2
  client, registration service, `Dhis2UserLink` domain class, security filter
  integration.
- **Liquibase**: one new changeset under `grails-app/migrations/custom/` for
  the `dhis2_user_link` table.
- **Upstream touch points** (kept minimal, surgical, documented in design.md):
  - `grails-app/conf/application.yml` — config keys for the DHIS2 OAuth client;
    defaults disable the feature.
  - `grails-app/controllers/org/pih/warehouse/SecurityInterceptor.groovy` —
    add the pending-access redirect branch (OB has no Spring Security
    plugin; the hand-rolled `SecurityInterceptor` is the gate).
  - `grails-app/controllers/org/pih/warehouse/user/AuthController.groovy` —
    only if needed (see design D1.1): extract the smallest possible reusable
    session-setup method so the OAuth callback can call it.
  - Login GSP — add a single "Sign in with DHIS2" button, conditionally
    rendered when `custom.dhis2.oauth.enabled = true`.
  - User-admin list controller/view — add a "Pending DHIS2 access" filter
    option.
- **Dependencies**: no new Grails plugins. OAuth client is hand-rolled
  (~200 lines) on top of the HTTP client confirmed by `dhis2-oauth-spike`
  task 1.9.
- **Operational**: deployments enabling SSO configure DHIS2 client
  id/secret/URLs in env-specific config. The feature is opt-in.
