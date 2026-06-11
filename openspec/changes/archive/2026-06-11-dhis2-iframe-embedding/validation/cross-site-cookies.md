# Validation — cross-site cookies, frame-ability, and break-out (live)

Browser-level findings from running the full embedded flow locally: the
EyeSeeTea app skeleton (Vite, `http://localhost:8081`) embedding OB
(`http://localhost:8080`) in an iframe, against real DHIS2 instances. These
findings pin down the **single hardest constraint** of the feature: the
deployment topology, dictated by the DHIS2 session cookie's `SameSite`.

## TL;DR

| | Effect |
|---|---|
| DHIS2 session cookie is `SameSite=Strict` (sp/cpr-test) | The DHIS2 session is **invisible to the OB iframe unless the embedding page is the same site as DHIS2**. No OB/OB-cookie flag can change this — it is DHIS2's cookie. |
| DHIS2 login page sets `frame-ancestors 'none'` + `X-Frame-Options: SAMEORIGIN` | DHIS2's login page **cannot be framed by anyone**, by design. A not-logged-in user hitting it inside the iframe gets "refused to connect". |
| v42 `prompt=none` → `login_required` → `breakout.gsp` | Confirmed working: silent failure escapes the iframe to a **top-level** interactive login, then lands in OB. This is the designed cross-site fallback. |

**Conclusion:** the embedding page, OB, and DHIS2 must all sit under the **same
registrable domain** (e.g. `*.samaritanspurse.org`). Then the `Strict` DHIS2
cookie is sent in-frame, silent SSO succeeds, and OB renders inside the iframe.
Cross-site (e.g. a `localhost` dev skeleton against remote DHIS2) **cannot** see
the DHIS2 session and will always fall back to break-out (v42) or dead-end on the
un-framable login page (v40).

## Evidence

### 1. DHIS2 session cookie is `SameSite=Strict` (sp / cpr-test, v40)

```
$ curl -sSI https://cpr-test.samaritanspurse.org/dhis-web-commons/security/login.action
Set-Cookie: JSESSIONID=...; Path=/; Secure; HttpOnly; SameSite=Strict
```

`SameSite=Strict` ⇒ the browser sends the cookie **only when the top-level page
is the same site as the cookie's host**. With the skeleton at `localhost:8081`
(a different site), the iframe's request to `…/oauth/authorize` carries **no**
DHIS2 session cookie → DHIS2 treats it as anonymous. Being logged into DHIS2 in
another tab does not help: the cookie cannot cross sites.

This is stricter than OB's own cookie: OB uses `SameSite=None` which *can* work
cross-site if `Secure`; `Strict` has no such escape hatch.

### 2. DHIS2 login page forbids framing

```
$ curl -sSI https://cpr-test.samaritanspurse.org/dhis-web-commons/security/login.action
Content-Security-Policy: frame-ancestors 'none';
X-Frame-Options: SAMEORIGIN
```

Other DHIS2 endpoints on the same host *do* allow framing — the instance's CSP
whitelist includes the dev origins (`http://localhost:8081`..`8085`, etc.). Only
the **login page** is hard-locked to `'none'` (clickjacking protection on
credential entry). So an anonymous request reaching the login page inside the
iframe yields the browser's "refused to connect". Not changeable from OB; not to
be changed in DHIS2.

### 3. Break-out confirmed live (v42, `172.16.0.99`)

OB access logs for one cycle (skeleton iframe → OB):

```
/oauth/dhis2/initiate?embedded=true                 [user:null]    silent (prompt=none)
/oauth/dhis2/callback?error=login_required&…        [user:null]    silent failed → breakout.gsp
/oauth/dhis2/initiate                               [user:null]    interactive (top-level, no prompt=none)
/oauth/dhis2/callback?code=…                        → user:admin-dhis2   SUCCESS
/openboxes/  → "requires location, redirecting to chooseLocation"
```

`breakout.gsp` runs `window.top.location.replace(initiateUrl)`, so the **whole
tab** leaves the skeleton and lands on OB at top level (`…/dashboard/chooseLocation`).
That top-level landing is the **correct** fallback, not a bug. The `login_required`
arose because the iframe could not see the DHIS2 session (cross-site, per §1).

## Deployment requirement (both v40 and v42)

- **Embedding app + OB + DHIS2 under one registrable domain** (any subdomains).
  Subdomains are same-site, so the `Strict` DHIS2 cookie is sent in-frame.
- **OB over TLS** with `server.use-forward-headers: true` so its own
  `SameSite=None` cookie is `Secure`. (Localhost-only crutch:
  `server.session.cookie.secure: true` — never ship it.)
- **OB `frameAncestors`** lists the embedder's origin.
- **v42** degrades gracefully when there is no DHIS2 session (break-out to a
  top-level login). **v40** has no `login_required` signal, so a not-logged-in
  user hits the un-framable login page with no recovery — v40 effectively
  requires an existing DHIS2 session.

## Update (2026-06-11): in-frame silent success reproduced — production topology is "embedder is a DHIS2 app"

The earlier sections assumed **topology B**: a standalone embedder app (the
`localhost` skeleton) that frames OB *and* reaches DHIS2. That topology makes
DHIS2's `SameSite=Strict`/`Lax` session cookie cross-site → invisible in-frame →
break-out every time. **Production is topology A**: the embedder is a **DHIS2
app installed into DHIS2** and opened from within DHIS2, so the **top-level page
is the DHIS2 origin itself**. That changes everything:

- DHIS2's own session cookie is now **first-party** to the top-level page →
  sent in-frame regardless of `SameSite` (even `Strict`). The "DHIS2 cookie
  can't cross sites" blocker **does not apply** to the real topology.
- The only cross-site party left is **OB** (the framed content), so only OB's
  cookie needs `SameSite=None; Secure` — which it already sets.

In-frame **silent success was reproduced end-to-end** on a local same-site rig:
DHIS2 `2.42.4.1` (d2-docker) behind a TLS proxy at `dhis2.tjk.test`, the
EyeSeeTea skeleton **built and installed as a real DHIS2 app**, embedding OB at
`https://openboxes.lvh.me/...initiate?embedded=true`. Logged into DHIS2, opening
the app rendered OB inside the iframe with **no second login** — for the admin
and for a fresh non-admin user.

### Production config requirements discovered (DHIS2 side)

- **`oauth2.server.enabled=on` REQUIRES `server.base.url`** set to the absolute
  HTTPS DHIS2 URL (the OIDC issuer). Without it DHIS2 **fails to start**
  (`issuer must be a valid URL`). Normally already set on a real instance.
- **`require-authorization-consent` must be `false`** on the OAuth client.
  `prompt=none` cannot render a consent screen, so a consent-required client
  fails silent SSO with `consent_required` until the user consents once. Set via
  a full `clientSettings` replace (json-patch can't target a key inside the
  serialized map).
- **Redirect URI host must have a real TLD.** DHIS2 rejects `.test`/`.local`
  (`E1004 Invalid redirect URI`). Use `localhost`, an IP, or a real TLD.
- **DHIS2 2.42 needs Tomcat 10 / Jakarta.** The d2-docker `dhis2-core:2.42` tag
  ships Tomcat 9 and silently fails to deploy the Jakarta WAR (blank app, no
  `dhis.log`). Pin a Tomcat-10 core (`-c …/dhis2-core:2.42.4.1`).

### Third-party-cookie limitation (the real-world caveat)

With OB cross-site to DHIS2, OB's `SameSite=None` cookie is a **third-party**
cookie in the frame. When the browser blocks third-party cookies (Incognito,
Safari ITP, Chrome's deprecation), OB's session cookie is dropped on the OAuth
callback → OB can't find its stored `state` → **`Bad request: state mismatch`**
(reproduced in Incognito; the silent SSO itself succeeded — DHIS2 returned a
code — but the OB session was gone). Fixes:

- **Deploy OB same-site with DHIS2** (a subdomain of the DHIS2 parent domain) →
  not third-party → cookie always flows. *(Recommended; no code change.)*
- **or** mark OB's embedded session cookie **`Partitioned` (CHIPS)** — works in
  partitioned third-party contexts. *(Code change in `IframeCookieCustomizer`.)*

### Local-testing artifact (not a deployment concern)

DHIS2's app shell is a PWA and registers a service worker on the DHIS2 origin.
Repointing the DHIS2 hostname to a different instance mid-test leaves a **stale
service worker** that serves cached assets as `text/html` → blank apps. Clear it
(DevTools → Application → Unregister + Clear site data). Production DHIS2 is not
swapped under a stable hostname, so this does not occur there.
