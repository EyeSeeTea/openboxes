## ADDED Requirements

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
