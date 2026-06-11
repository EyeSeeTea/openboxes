## MODIFIED Requirements

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
