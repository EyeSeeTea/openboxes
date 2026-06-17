# DHIS2 Authentication

## Purpose
DHIS2 OAuth2 SSO for OpenBoxes: a login option, first-login auto-registration,
returning-user identity refresh, side-table user linkage, and pending-access
admin gating. Supports the `v40` (UAA OAuth 2.0) and `v42` (Spring Authorization
Server, OAuth 2.1 / OIDC) profiles.
## Requirements
### Requirement: DHIS2 OAuth login option
The system SHALL allow users to authenticate via a DHIS2 OAuth2 Authorization
Code flow when DHIS2 OAuth is configured. The existing username/password login
SHALL continue to function unchanged for users with local credentials. The flow
SHALL support two configurable profiles selected by
`custom.dhis2.oauth.profile`: `v40` (legacy UAA OAuth 2.0, default) and `v42`
(Spring Authorization Server, OAuth 2.1 / OIDC). The `v40` profile SHALL resolve
the DHIS2 user identity from `/api/me` (UID = `id`). The `v42` profile SHALL
resolve the DHIS2 user identity from the OIDC `id_token` `sub` claim (the DHIS2
username), because the SAS token is not accepted on `/api/*` endpoints. v42
SHALL key the `Dhis2UserLink` on the username; v40 SHALL key it on the UID. The
selected profile SHALL NOT change session establishment, the pending-access
gate, or the admin pending-users filter.

#### Scenario: DHIS2 button shown when configured
- **WHEN** an unauthenticated user opens the login page and `custom.dhis2.oauth.enabled = true`
- **THEN** the page displays a "Sign in with DHIS2" button alongside the username/password form

#### Scenario: DHIS2 button hidden when not configured
- **WHEN** `custom.dhis2.oauth.enabled = false` (default)
- **THEN** the login page renders unchanged from upstream — no DHIS2 button

#### Scenario: v40 profile Authorization Code flow completes
- **WHEN** `custom.dhis2.oauth.profile = v40` (or unset) and the user clicks "Sign in with DHIS2", consents in DHIS2, and is redirected back to the OB callback URL with a valid code
- **THEN** OB exchanges the code at the UAA token endpoint using HTTP Basic client authentication, fetches the user from DHIS2 `/api/me`, establishes an authenticated OB session, and redirects to the user's landing page (or pending-access page if not yet authorized)

#### Scenario: v42 profile authorize request carries PKCE challenge
- **WHEN** `custom.dhis2.oauth.profile = v42` and the user clicks "Sign in with DHIS2"
- **THEN** OB generates a PKCE code verifier, stores it in the session, and the redirect to `custom.dhis2.oauth.authorizeUrl` includes `response_type=code`, the configured scopes (default `openid username`; `openid` is required so the token response includes an `id_token`), `code_challenge=<S256(verifier)>`, and `code_challenge_method=S256`

#### Scenario: v42 profile token exchange sends code_verifier with configured client auth
- **WHEN** `custom.dhis2.oauth.profile = v42` and OB receives a valid code at the callback URL with a matching state
- **THEN** OB POSTs to `custom.dhis2.oauth.tokenUrl` with `grant_type=authorization_code`, `code`, `redirect_uri`, and `code_verifier`; when `custom.dhis2.oauth.clientAuth = post` it additionally sends `client_id`/`client_secret` in the body and no Basic header, and when `clientAuth = basic` (default) it sends the HTTP Basic `Authorization` header

#### Scenario: v42 profile resolves identity from the id_token sub claim
- **WHEN** `custom.dhis2.oauth.profile = v42` and OB has obtained a token response containing an `id_token`
- **THEN** OB reads the `sub` claim (the DHIS2 username) from the `id_token`, builds a `Dhis2User` with `uid = null`, `username = sub`, and no name/email, and proceeds through the registration/session/pending path; OB does NOT call `/api/me` or any `/api/*` endpoint for v42

#### Scenario: v42 first login registers an inactive user keyed by username
- **WHEN** a v42 user with username `<u>` completes login and no `Dhis2UserLink` exists for `<u>`
- **THEN** OB creates an inactive OB user with placeholder first/last name, links it via `Dhis2UserLink.dhis2Username = <u>` (UID null), and routes the user to the pending-access page for admin approval

#### Scenario: v42 returning user is linked by username
- **WHEN** a v42 user with username `<u>` completes login and an active `Dhis2UserLink` exists for `<u>`
- **THEN** OB establishes the authenticated session for the linked OB user without creating a duplicate

#### Scenario: v42 reused username after tombstone is not silently relinked
- **WHEN** a v42 user logs in with username `<u>` whose `Dhis2UserLink` was previously tombstoned (`deactivated_at` set)
- **THEN** OB does NOT auto-establish a session for the old account; it routes through a fresh admin approval

#### Scenario: State parameter mismatch is rejected
- **WHEN** the callback request's `state` parameter does not match the value stored in the user's session
- **THEN** OB rejects the callback with HTTP 400 and does not establish a session

### Requirement: First-login auto-registration
The system SHALL, on the first successful DHIS2 OAuth login for a DHIS2 UID not
previously seen, create a new local OpenBoxes `User` with `active = false`, no
roles, and no location assignments, linked to the DHIS2 UID via a
`Dhis2UserLink` record.

#### Scenario: Unknown DHIS2 user logs in for the first time
- **WHEN** OB receives a valid token + `/api/me` response for a DHIS2 UID with no existing `Dhis2UserLink`
- **THEN** OB creates a `User` (username derived from DHIS2 username, name and email copied from `/api/me`, `active = false`, no roles, no `LocationRole`s) and a `Dhis2UserLink` row pointing at it

#### Scenario: Username collision with existing local user
- **WHEN** the DHIS2 username matches an existing OB `User.username` that has no `Dhis2UserLink`
- **THEN** OB does NOT silently link them; it creates the new user with a deterministic suffix (e.g. `<dhis2-username>-dhis2`) and logs a warning so an admin can manually merge if intended

#### Scenario: Pending user lands on access-denied page
- **WHEN** an authenticated session belongs to a `User` with `active = false`
- **THEN** all requests except logout and the pending-access page redirect to the pending-access page, which explains the user is awaiting access from an admin

### Requirement: Returning-user identity refresh
The system SHALL, on every successful DHIS2 OAuth login for a DHIS2 UID that
already has a `Dhis2UserLink`, match the existing OB `User` by UID and refresh
non-authorization fields. Roles, location assignments, and the `active` flag
SHALL NOT be modified by login.

#### Scenario: Returning DHIS2 user with updated email
- **WHEN** a returning DHIS2 user logs in and `/api/me` returns a different email or display name than stored
- **THEN** OB updates the `User.email` / `User.firstName` / `User.lastName` fields and leaves roles, locations, and `active` untouched

#### Scenario: DHIS2 username changed
- **WHEN** the DHIS2 username changed since last login but the UID is the same
- **THEN** OB matches by UID, updates the linked `User.username` if and only if the new value does not collide with another local user, otherwise keeps the old username and logs a warning

### Requirement: User linkage via side-table
DHIS2 identity SHALL be stored on a dedicated `Dhis2UserLink` domain class with
a unique foreign key to `User`. The system SHALL NOT add columns to the `User`
table or modify upstream `User` mappings.

#### Scenario: Schema isolation
- **WHEN** the `dhis2_user_link` Liquibase changeset runs
- **THEN** it creates a new table with columns including `user_id` (UNIQUE, FK to `user.id`), `dhis2_uid` (UNIQUE, NOT NULL), `dhis2_username`, `last_login_at`, and standard audit columns; it does NOT alter the `user` table

### Requirement: Pending-access admin visibility
The system SHALL allow administrators to filter the user admin screen by
"pending DHIS2 users" (users with `active = false` and a `Dhis2UserLink`).

#### Scenario: Admin filters for pending users
- **WHEN** an administrator selects the "Pending DHIS2 access" filter on the user admin list
- **THEN** the list shows only users with `active = false` AND a non-null `Dhis2UserLink`, ordered by most recent `Dhis2UserLink.createdAt` first

### Requirement: Embedded silent authentication
The system SHALL establish an OB session without user interaction when
OpenBoxes is served inside a DHIS2 iframe (embedding enabled via a non-empty
`iframe.frameAncestors`), an unauthenticated request arrives, and the
configured DHIS2 profile supports silent authentication (`v42`). Where the
profile does not support it (`v40`), the system SHALL fall back to in-frame
interactive login. The non-embedded (top-level) login flow SHALL remain
unchanged from the base DHIS2 OAuth behavior.

#### Scenario: v42 silent success with a live DHIS2 session
- **WHEN** embedding is enabled, `custom.dhis2.oauth.profile = v42`, the request has no OB session, the user has a live DHIS2 session, and the OB OAuth client skips consent (or consent was previously granted)
- **THEN** OB redirects to the DHIS2 authorize endpoint with `prompt=none` (plus the usual PKCE challenge and `scope=openid username`), DHIS2 returns an authorization `code` with no UI, and OB establishes the session and renders the requested screen inside the frame

#### Scenario: v42 no DHIS2 session breaks out for interactive login
- **WHEN** embedding is enabled, profile is `v42`, and the `prompt=none` authorize attempt returns `error=login_required` (or `interaction_required`) to the callback
- **THEN** OB does NOT render DHIS2's login page inside the frame; instead it returns a break-out response that navigates the top-level window (`window.top`) to the interactive authorize URL (no `prompt=none`)

#### Scenario: v42 consent required is treated as a break-out
- **WHEN** the `prompt=none` attempt returns `error=consent_required` because the OB client was registered with `requireAuthorizationConsent=true` and the user has not yet consented
- **THEN** OB breaks out of the frame for the one-time interactive consent, after which subsequent `prompt=none` attempts succeed silently

#### Scenario: silent attempt runs at most once (no redirect loop)
- **WHEN** a `prompt=none` attempt has already been made for the current navigation and returns an error again
- **THEN** OB does NOT issue another silent `prompt=none` redirect for that navigation (a one-shot guard prevents an authorize↔callback loop); it proceeds to the break-out interactive login

#### Scenario: v40 falls back to in-frame login
- **WHEN** embedding is enabled and `custom.dhis2.oauth.profile = v40` (or unset)
- **THEN** OB makes NO silent `prompt=none` attempt; the unauthenticated user is shown the OB login (DHIS2 SSO button or local credentials) inside the frame, and after login the `SameSite=None; Secure` session cookie keeps the session active on later embedded loads

#### Scenario: non-embedded login is unchanged
- **WHEN** `iframe.frameAncestors` is empty (embedding disabled), regardless of profile
- **THEN** the login flow is identical to the base DHIS2 OAuth behavior — no `prompt=none`, no break-out response

