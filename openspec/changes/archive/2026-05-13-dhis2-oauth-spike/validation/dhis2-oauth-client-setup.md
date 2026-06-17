## DHIS2 OAuth2 Client Registration — <dhis2-host> (2.x)

### Via REST API (preferred for automation/scripting)

DHIS2 exposes a full CRUD API for OAuth2 clients at `/api/oAuth2Clients`.

```bash
# Create a client
curl -s -X POST "https://<dhis2>/api/oAuth2Clients" \
  -u "<admin-user>:<password>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "openboxes",
    "cid": "openboxes",
    "secret": "<uuid-36-chars>",
    "redirectUris": ["https://openboxes.example.com/oauth/dhis2/callback"],
    "grantTypes": ["authorization_code", "refresh_token"]
  }'
```

**Constraints discovered during spike:**
- `secret` MUST be exactly 36 characters (UUID format). Shorter values return
  HTTP 409 with error code E4002.
- `grantTypes` valid values: `authorization_code`, `refresh_token`, `password`,
  `client_credentials`, `implicit`.
- On success: HTTP 201, body `{"httpStatus":"Created","response":{"uid":"<uid>"}}`

Verified on <dhis2-host> — client `openboxes-spike` (uid
`<client-uid>`) was created via API and verified functional.

### Via Admin UI

Navigate to:
1. **Settings** (⚙ gear icon in top-right header menu)
2. **OAuth2 Clients** tab in the Settings app

Direct URL: `https://<dhis2>/dhis-web-settings/#/oauth2clients`

Fields in the form match the API fields: Name, Client ID (`cid`), Secret,
Redirect URIs, Grant Types.

**Version note**: path confirmed for DHIS2 2.x. The Settings app UI may
vary between minor releases; the API path `/api/oAuth2Clients` is stable.

### Parameters for the OpenBoxes integration

| Field | Value |
|---|---|
| Name | openboxes (or client-specific name) |
| Client ID (`cid`) | openboxes (configurable via `openboxes.dhis2.oauth.clientId`) |
| Secret | 36-char UUID (inject via env var, never commit) |
| Redirect URI | `https://<openboxes-host>/oauth/dhis2/callback` |
| Grant Types | `authorization_code`, `refresh_token` |
