## ADDED Requirements

### Requirement: DHIS2 OAuth login option
The system SHALL allow users to authenticate via a DHIS2 OAuth2 Authorization
Code flow when DHIS2 OAuth is configured. The existing username/password login
SHALL continue to function unchanged for users with local credentials.

#### Scenario: DHIS2 button shown when configured
- **WHEN** an unauthenticated user opens the login page and `dhis2.oauth.enabled = true`
- **THEN** the page displays a "Sign in with DHIS2" button alongside the username/password form

#### Scenario: DHIS2 button hidden when not configured
- **WHEN** `dhis2.oauth.enabled = false` (default)
- **THEN** the login page renders unchanged from upstream — no DHIS2 button

#### Scenario: Authorization Code flow completes
- **WHEN** the user clicks "Sign in with DHIS2", consents in DHIS2, and is redirected back to the OB callback URL with a valid code
- **THEN** OB exchanges the code for an access token, fetches the user from DHIS2 `/api/me`, establishes an authenticated OB session, and redirects to the user's landing page (or pending-access page if not yet authorized)

#### Scenario: State parameter mismatch is rejected
- **WHEN** the callback request's `state` parameter does not match the value stored in the user's session
- **THEN** OB rejects the callback with HTTP 400 and does not establish a session

### Requirement: First-login auto-registration
On the first successful DHIS2 OAuth login for a DHIS2 UID not previously seen,
the system SHALL create a new local OpenBoxes `User` with `active = false`, no
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
On every successful DHIS2 OAuth login for a DHIS2 UID that already has a
`Dhis2UserLink`, the system SHALL match the existing OB `User` by UID and
refresh non-authorization fields. Roles, location assignments, and the `active`
flag SHALL NOT be modified by login.

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
