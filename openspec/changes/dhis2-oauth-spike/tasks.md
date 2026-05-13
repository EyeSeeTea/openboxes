# Implementation Tasks — dhis2-oauth-spike

All artifacts land under `openspec/changes/dhis2-oauth-spike/validation/`.

- [x] **1.1** Stand up the minimal local stack:
      `docker/dhis2-sso/docker-compose.spike.yml` with `dhis2/core` + postgres
      only. Pin the DHIS2 image tag in the file and record the chosen version
      in `validation/dhis2-image-tag.txt`.
- [x] **1.2** Capture `/api/me` shape:
      `curl -u admin:district http://localhost:8080/api/me` → `validation/dhis2-me.json`.
      Confirm UID (11 chars), username, displayName, email fields are present.
- [x] **1.3** Register an OAuth2 client in the DHIS2 admin UI with redirect URI
      `http://localhost:9090/oauth/dhis2/callback`. Document the exact UI
      navigation path and any version-specific quirks in
      `validation/dhis2-oauth-client-setup.md`.
- [x] **1.4** Drive the Authorization Code flow manually via curl/browser.
      Capture the authorize redirect into `validation/dhis2-authorize.txt`
      (first 200 bytes is enough) and the token-exchange response into
      `validation/dhis2-token.json`.
- [x] **1.5** Embedded Tomcat version:
      `./gradlew dependencies | grep -i tomcat` → `validation/tomcat-version.txt`.
      Add a one-line conclusion (top of file) on whether
      `Rfc6265CookieProcessor.sameSiteCookies` is reachable.
- [x] **1.6** Identify the upstream OB login GSP path:
      `find grails-app/views -name '*.gsp' -path '*login*'` →
      `validation/login-gsp-path.txt`.
- [x] **1.7** Determine whether OB emits `X-Frame-Options` today:
      `grep -rn -i "X-Frame-Options\|frameOptions" grails-app/ src/main/` →
      `validation/xframeoptions-source.txt`. (Pre-audit suggests no in-repo
      source — if grep is empty, document that and note that the iframe
      filter just ADDS CSP without needing to neutralize anything in OB
      code. If the header originates from a deploy-level config — nginx,
      embedded Tomcat default — note where so the iframe change's README can
      flag it.)
- [x] **1.8** Trace the hand-rolled auth path for `User.active = false`. OB
      has NO Spring Security plugin — auth lives in
      `grails-app/controllers/org/pih/warehouse/user/AuthController.groovy`,
      `grails-app/services/org/pih/warehouse/auth/AuthService.groovy`, and
      `grails-app/controllers/org/pih/warehouse/SecurityInterceptor.groovy`.
      Read the login action and the interceptor; capture the relevant
      excerpts in `validation/active-flag-behavior.md`. Conclude with one
      of: (a) `AuthController.login` rejects `active=false` users outright,
      (b) login succeeds but `SecurityInterceptor` 403s all routes, or
      (c) login succeeds and the user can navigate. Note any reusable
      method on `AuthController` the OAuth callback could hand off to (per
      `dhis2-oauth-core` design D1.1) — if there is none, recommend the
      smallest extract.
- [x] **1.9** HTTP client availability:
      `find . -path ./node_modules -prune -o -name 'HTTPBuilder*' -print` and
      `./gradlew dependencies | grep -i http-builder` →
      `validation/http-client.md`. If not on the classpath, recommend a
      JDK-only alternative (`HttpURLConnection`) in the same file.
- [x] **1.10** Spring Boot 1.5 `server.use-forward-headers` confirmation:
      paste the relevant paragraph from the Spring Boot 1.5.22 reference docs
      anchor `howto-use-tomcat-behind-a-proxy-server` into
      `validation/spring-boot-1.5-forward-headers.txt`.
- [ ] **1.11** `*.localtest.me` resolution:
      `dig +short dhis2.localtest.me` and `dig +short openboxes.localtest.me`
      → both should print `127.0.0.1`. Capture in
      `validation/localtest-resolution.txt`. If the developer's resolver
      strips it, document the `/etc/hosts` workaround in the same file.
- [ ] **1.12** Synthesize findings:
      `validation/findings.md` — one bullet per assumption, format:
      `- <assumption> → <verdict>. Impact: <which Decision in which follow-up>`.
- [ ] **1.13** Re-score the Confidence section in
      `openspec/changes/dhis2-oauth-core/design.md` and
      `openspec/changes/dhis2-iframe-embedding/design.md`. Rewrite any
      Decision a spike result invalidates. Mark this gate as cleared in a
      short PR description.
