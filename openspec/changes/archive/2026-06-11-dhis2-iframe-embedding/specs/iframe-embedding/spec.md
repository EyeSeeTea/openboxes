## ADDED Requirements

### Requirement: Configurable frame-ancestors allow-list
The system SHALL allow embedding OpenBoxes pages in an iframe whose top-level
origin matches a configured allow-list of DHIS2 origins, and SHALL deny
embedding from any other origin.

#### Scenario: Allowed DHIS2 origin
- **WHEN** OB is configured with `iframe.frameAncestors = ["https://dhis2.example.org"]` and serves any HTML response
- **THEN** the response includes `Content-Security-Policy: frame-ancestors 'self' https://dhis2.example.org` and does NOT include an `X-Frame-Options` header

#### Scenario: No allow-list configured (default)
- **WHEN** `iframe.frameAncestors` is empty or unset
- **THEN** OB serves `Content-Security-Policy: frame-ancestors 'self'` (no third-party embedding) — preserving upstream-equivalent protection

#### Scenario: Multiple DHIS2 origins
- **WHEN** the allow-list contains multiple origins
- **THEN** all configured origins appear in the `frame-ancestors` directive, each as a complete origin (scheme + host + optional port), space-separated

### Requirement: Cross-site session cookie
The system SHALL set the OpenBoxes session cookie with `SameSite=None; Secure`
when iframe embedding is enabled, so the cookie is sent with requests made from
within a DHIS2-embedded iframe.

#### Scenario: Iframe embedding enabled
- **WHEN** `iframe.frameAncestors` is non-empty and a session is established over HTTPS
- **THEN** the `Set-Cookie` header for the session cookie includes `SameSite=None; Secure` and `HttpOnly`

#### Scenario: Iframe embedding disabled (upstream behavior)
- **WHEN** `iframe.frameAncestors` is empty
- **THEN** the session cookie is set with the upstream default `SameSite` policy, unmodified

### Requirement: Forwarded-proto handling
The system SHALL treat requests as HTTPS when a TLS-terminating proxy sets
`X-Forwarded-Proto: https`, so that the `Secure` cookie attribute is applied
correctly behind a reverse proxy.

#### Scenario: Behind TLS-terminating proxy
- **WHEN** OB receives a request with `X-Forwarded-Proto: https` from a configured trusted proxy
- **THEN** `request.isSecure()` returns true and `Secure` cookies are set

#### Scenario: Direct HTTP request (untrusted)
- **WHEN** OB receives an HTTP request with no forwarded headers (or from a non-trusted source)
- **THEN** `request.isSecure()` returns false and `Secure` cookies are NOT set
