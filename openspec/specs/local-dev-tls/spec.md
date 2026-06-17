# local-dev-tls Specification

## Purpose
TBD - created by archiving change dhis2-iframe-embedding. Update Purpose after archive.
## Requirements
### Requirement: Local docker-compose stack with TLS
The repository SHALL provide a docker-compose stack under `docker/dhis2-sso/`
that runs DHIS2, OpenBoxes, and an nginx TLS-terminating reverse proxy on a
shared Docker network, suitable for end-to-end testing of OAuth + iframe flows.

#### Scenario: Single-command bring-up
- **WHEN** a developer runs `docker compose -f docker/dhis2-sso/docker-compose.yml up` from a clean checkout (after generating certs per README)
- **THEN** DHIS2, OpenBoxes, and nginx all start; nginx serves `https://dhis2.localtest.me` and `https://openboxes.localtest.me` with browser-trusted (mkcert-signed) TLS certs

#### Scenario: Internal service-to-service traffic
- **WHEN** OpenBoxes performs the OAuth2 token exchange with DHIS2
- **THEN** it uses the internal Docker network hostname (`http://dhis2:8080`), bypassing nginx

#### Scenario: Browser-facing redirects use public hostnames
- **WHEN** OpenBoxes constructs the OAuth2 authorization URL or redirect URI
- **THEN** the URLs use `https://dhis2.localtest.me` and `https://openboxes.localtest.me` respectively, matching what is registered as the OAuth client in DHIS2

### Requirement: Documented certificate setup
The stack SHALL include a README documenting how to install mkcert, generate
certificates for the two hostnames, and place them where nginx mounts them.

#### Scenario: Following README produces a working stack
- **WHEN** a developer follows the README from a clean machine (mkcert installed, no prior certs)
- **THEN** they end up with a stack where the browser shows a green padlock on both hostnames and `SameSite=None; Secure` cookies are honored

### Requirement: No interference with existing dev stack
The new stack SHALL live in its own subdirectory and SHALL NOT modify the
existing `docker/docker-compose.yml` or its supporting files.

#### Scenario: Existing dev stack untouched
- **WHEN** a developer not using DHIS2 SSO runs the existing `docker/docker-compose.yml`
- **THEN** it behaves identically to before this change (no new dependencies, no new required env vars)

