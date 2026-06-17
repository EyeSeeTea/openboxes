## 1. DHIS2 verification (done — receipts in design.md)

- [x] 1.1 **Live:** SAS token mint + `/userinfo` succeed (`{"sub":"admin"}`); the token carries only `sub` (= username), no UID/name/email (decoded `id_token`/access-token claims).
- [x] 1.2 **Live:** `/api/me` and all `/api/*` reject the SAS bearer (401; 302→login for browsers). The UID (`M5zQapPyTZI`) is in no token/userinfo response.
- [x] 1.3 **Source:** `sub` = username, only `username`/`email` claims exist (`AuthorizationServerConfig.java:107-108,263-289`, `Dhis2OAuth2ClientServiceImpl.java:124-126`); `/api/*` resolver maps by `username`/`email` claim, needs per-user `openId` for `/api/me` (`OAuth2Test.testBearerTokenAuthWithMatchingUser`).
- [x] 1.4 **Source:** username immutable via API/UI/import — E4056 guard (`UserObjectBundleHook.java:107-114`); unique + not-null (`User.hbm.xml:122`). Sysadmin confirmed real users are not deleted (recycling precondition holds).
- [x] 1.5 **Source:** `email` scope throws at token mint for emailless users (`AuthorizationServerConfig.java:263-273`) — do not request it.

## 2. Config surface

- [x] 2.1 `profile` (default `v40`) and `clientAuth` (default `basic`) under `openboxes.custom.dhis2.oauth.*`. No `userinfoUrl`/`userUrl` for v42 (identity is the `id_token` `sub`, not an HTTP fetch).
- [x] 2.2 Default v42 scopes `openid username` (`openid` mandatory for the `id_token`). v40 default `ALL`. Never `email`.
- [x] 2.3 Document the v42 block in `docker/openboxes.client-template.yml`: `profile`, `/oauth2/*` authorize/token URLs, `scopes: openid username`, `clientAuth`, **no** `userUrl`. Note the three DHIS2-side PATCHes (scopes, `clientAuthenticationMethods`, bcrypt `clientSecret`) and that v42 identifies users by username only.

## 3. PKCE + handshake (controller/service — done)

- [x] 3.1 `prepareAuthorize`/`initiate`: v42 generates an RFC 7636 verifier, stored in session beside `state`; null for v40.
- [x] 3.2 `buildAuthorizeUrl`: v42 appends `code_challenge` + `code_challenge_method=S256`; v40 unchanged.
- [x] 3.3 `exchangeCode`: v42 sends `code_verifier`; `clientAuth=post` sends `client_id`/`client_secret` in the body, `basic` keeps the header. v40 unchanged.

## 4. v42 identity = id_token `sub` (`Dhis2OAuthService`)

- [x] 4.1 `exchangeCode`/`AccessToken`: capture `id_token` from the token response (additive field; v40 ignores it).
- [x] 4.2 New v42 identity method: base64url-decode the `id_token` claims segment (no signature verification — back-channel/TLS), read `sub`, return `Dhis2User(uid: null, username: sub, displayName: null, email: null)`. Throw a clear error if `sub` is blank.
- [x] 4.3 `fetchMe` (`/api/me` + `json.id`) stays **v40/v41 only** — untouched. `Dhis2OAuthController.callback` selects by profile: v40 → `fetchMe`; v42 → the `sub` method.

## 5. Registration + migration (username keying)

- [x] 5.1 Migration under `grails-app/migrations/custom/` (new id, additive): `dhis2_uid` → nullable; `dhis2_username` → unique, not-null, `utf8mb4_bin` (case-sensitive); add `deactivated_at TIMESTAMP NULL`. Reversible (drop column / loosen index).
- [x] 5.2 `Dhis2UserLink`: add `deactivatedAt` (nullable); relax `dhis2Uid` to nullable; `dhis2Username` unique + not-null. Keep v40/v41 rows valid.
- [x] 5.3 `Dhis2RegistrationService.findOrRegister`: branch on `uid == null` → `findByDhis2Username`; else existing `findByDhis2Uid` (v40/v41 unchanged).
- [x] 5.4 v42 register: create inactive OB user with placeholder `firstName = username`, `lastName` marker; link by `dhis2Username`. v42 first-login on a **tombstoned** username → do NOT silently relink; route to fresh admin approval. Log link create/resolve.

## 6. Tests (unit)

- [x] 6.1 `Dhis2OAuthServiceSpec`: v42 identity method extracts `sub` from a sample `id_token` → `Dhis2User(uid: null, username: sub, ...)`; blank `sub` errors.
- [x] 6.2 `Dhis2OAuthServiceSpec`: v42 `buildAuthorizeUrl` has `code_challenge`/`S256` (RFC 7636 appendix-B vector); v40 unchanged (no challenge, `scope=ALL`).
- [x] 6.3 `Dhis2OAuthControllerSpec`: v42 callback resolves via `sub` (no `/api/me` call); v40 callback still uses `fetchMe`.
- [x] 6.4 `Dhis2RegistrationService` spec: `uid == null` registers/links by username with placeholder names; tombstoned-username first-login not silently reused; `uid != null` (v40/v41) path unchanged.
- [x] 6.5 `./gradlew test` for `org.pih.warehouse.custom.dhis2auth` green.

## 7. Live validation (sysadmin-gated)

- [x] 7.1 Confirmed target DHIS2 2.42.4.1, SAS enabled; client `openboxes-dev` registered; handshake (authorize→token→`/userinfo`) works; `/api/me` rejection reproduced.
- [ ] 7.2 End-to-end v42 login with `scopes=openid username`: login→consent→callback→token→OB user created/linked by username→pending-access page. Capture redacted responses into `validation/`.
- [ ] 7.3 Tick the `[live]` end-to-end box in design.md; note result in the archive Deploy status.

## 8. Wrap-up

- [ ] 8.1 `openspec validate dhis2-oauth-v42`; then `/opsx:apply` verification.
- [ ] 8.2 `/opsx:archive` once `[unit]` + `[source]`/`[live]` criteria pass; record Deploy status (branches replayed onto) and the username-recycling precondition.
