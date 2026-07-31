# Local TLS dev stack — DHIS2 iframe embedding + silent SSO

Exercises the full embedded flow (CSP `frame-ancestors`, `SameSite=None; Secure`
session cookie, v42 `prompt=none` silent SSO) against a real DHIS2 instance,
behind locally-trusted TLS — no public domain required.

> **Status:** scaffolding. Run through it end-to-end once and capture any gotchas
> back here (tasks 4.5 / 5.7). Treat the compose/nginx files as a starting point.

## Why TLS locally

`SameSite=None` cookies are **rejected by browsers unless `Secure`**, and `Secure`
needs HTTPS. nginx terminates TLS and forwards `X-Forwarded-Proto: https`, so OB
(with `server.use-forward-headers=true`) marks the session cookie `Secure`. Both
hosts sit under `*.localtest.me` (which resolves to `127.0.0.1` with no
`/etc/hosts` edits), giving OB and DHIS2 a shared parent domain.

## 1. Trust + generate certs (mkcert)

```bash
# install mkcert (https://github.com/FiloSottile/mkcert), then:
mkcert -install
cd docker/dhis2-sso/certs
mkcert openboxes.localtest.me
mkcert dhis2.localtest.me
```

Certs land in `./certs/` and are git-ignored.

## 2. Point at your DHIS2

```bash
export DHIS2_UPSTREAM=http://172.16.0.99:18081   # your DHIS2 instance
```

The nginx `dhis2.localtest.me` server block proxies to it, so DHIS2 and OB share
the `localtest.me` parent domain.

## 3. Configure OB (`openboxes.yml`)

Place an `openboxes.yml` next to this file (git-ignored — never commit secrets)
enabling embedding, forwarded-proto, and the v42 OAuth client:

```yaml
server:
    use-forward-headers: true
openboxes:
    custom:
        iframe:
            frameAncestors: ['https://dhis2.localtest.me']
        dhis2:
            oauth:
                enabled: true
                profile: v42
                clientId: openboxes-dev
                clientSecret: <inject-via-env>      # never commit
                authorizeUrl: https://dhis2.localtest.me/oauth2/authorize
                tokenUrl: https://dhis2.localtest.me/oauth2/token
                redirectUri: https://openboxes.localtest.me/openboxes/oauth/dhis2/callback
                scopes: openid username
                clientAuth: basic
```

## 4. Register the OB client in DHIS2

In DHIS2, the OAuth2 client (`clientId: openboxes-dev`) needs:

- `redirectUris`: `https://openboxes.localtest.me/openboxes/oauth/dhis2/callback`
- `scopes`: `openid,username`
- `clientAuthenticationMethods`: `client_secret_basic`
- **For zero-click silent SSO:** `require-authorization-consent = false`
  (DHIS2 defaults it to `true`, which makes the first silent attempt return
  `consent_required` until the user consents once). See
  `../../openspec/changes/dhis2-iframe-embedding/validation/prompt-none.md`.

## 5. Run

```bash
docker compose -f docker/dhis2-sso/docker-compose.yml up
```

Then embed `https://openboxes.localtest.me/openboxes/oauth/dhis2/initiate?embedded=true`
in a DHIS2 dashboard iframe and watch a logged-in DHIS2 user land in OB with no
second login.

## Troubleshooting

- **Blank iframe / CSP error in DevTools console** — `frameAncestors` does not
  list the embedding origin (`https://dhis2.localtest.me`).
- **Logged out immediately inside the frame** — the session cookie is being
  blocked. Check it is `SameSite=None; Secure` (DevTools → Application → Cookies)
  and that the browser is not blocking third-party cookies. If blocked, the
  shared-parent-domain setup (both under `localtest.me`) should make it
  first-party; confirm both hosts resolve through nginx.
- **`prompt=none` shows a login page instead of returning silently** — the DHIS2
  user has no live session, or `require-authorization-consent` is still `true`
  and consent has not been granted.
- **Login fails even top-level** — embedding enabled without TLS/forwarded-proto;
  the `SameSite=None` cookie is dropped. Ensure `server.use-forward-headers=true`
  and access via `https://openboxes.localtest.me`.
