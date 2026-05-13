# Implementation Tasks — dhis2-iframe-embedding

**Prerequisite:** `dhis2-oauth-spike` is archived. Validation artifacts are
referenced below. `dhis2-oauth-core` is not a hard prerequisite — but pairing
the dev stack with SSO is what end-to-end testing looks like.

## Phase 1 — CSP and forwarded-proto

- [ ] **1.1** Config keys in `application.yml`:
      `iframe.frameAncestors` (list of origins, default `[]`),
      `server.use-forward-headers` (default unset / upstream behavior).
- [ ] **1.2** `Dhis2IframeFilter` (or named per design — same package
      isolation rules) Groovy filter that, on every HTML response, sets
      `Content-Security-Policy: frame-ancestors 'self' <configured>` and
      removes any `X-Frame-Options` header. When the allow-list is empty,
      emit `frame-ancestors 'self'` so we remain the single source of truth
      for framing.
- [ ] **1.3** **Conditional on spike outcome**: if
      `dhis2-oauth-spike` validation/xframeoptions-source.txt identified an
      in-repo source emitting `X-Frame-Options`, neutralize it (smallest
      possible upstream edit). If the spike found no in-repo source, skip
      this task and note in the PR description that the iframe filter is the
      sole source. The filter itself already strips `X-Frame-Options` on the
      way out as a belt-and-braces guard against deploy-time proxies adding
      it back.
- [ ] **1.4** Spock filter tests asserting:
      - allow-list empty → `frame-ancestors 'self'`, no `X-Frame-Options`
      - allow-list populated → directive includes each configured origin
      - upstream-set `X-Frame-Options` is removed in both cases

## Phase 2 — Cookie path (SameSite=None; Secure)

- [ ] **2.1** Per `dhis2-oauth-spike` validation/tomcat-version.txt, the
      embedded Tomcat is 8.5 → use the `Set-Cookie`-rewriting filter (Plan B
      from the original combined design). If the spike re-scored and found a
      different Tomcat version, update Decision D2 and adjust this task.
- [ ] **2.2** `Set-Cookie` rewriter filter:
      - active only when `iframe.frameAncestors` is non-empty,
      - on the way out, appends `; SameSite=None; Secure` to cookies that do
        not already declare `SameSite`,
      - leaves `HttpOnly` and existing attributes intact.
- [ ] **2.3** Spock filter tests asserting:
      - allow-list empty → cookie attributes unchanged
      - allow-list non-empty + HTTPS → cookie has `SameSite=None; Secure`
      - cookie that already declares `SameSite=Lax` is left untouched

## Phase 3 — Forwarded-proto

- [ ] **3.1** Enable `server.use-forward-headers=true` (Spring Boot 1.5
      property name confirmed via `dhis2-oauth-spike` task 1.10) under a
      profile or conditional, only when iframe embedding is enabled. Do not
      change behavior for deployments without a TLS-terminating proxy.
- [ ] **3.2** Spock integration test: simulate request with
      `X-Forwarded-Proto: https` and confirm `request.isSecure()` returns
      true and a `Secure` cookie is written.

## Phase 4 — Local dev stack

- [ ] **4.1** Flesh out `docker/dhis2-sso/docker-compose.yml` to include OB,
      OB's MySQL, DHIS2, DHIS2's postgres, and nginx on the shared network.
      Pin the DHIS2 image tag to match `dhis2-oauth-spike`.
- [ ] **4.2** `docker/dhis2-sso/nginx.conf` — two `server` blocks (TLS),
      proxy headers (`X-Forwarded-Proto`, `X-Forwarded-For`, `Host`),
      WebSocket upgrade, certs mounted from `./certs/`.
- [ ] **4.3** `docker/dhis2-sso/README.md` — mkcert install, cert
      generation command, OAuth client setup walkthrough (link to
      `dhis2-oauth-spike` validation/dhis2-oauth-client-setup.md),
      troubleshooting (cookie issues, CSP errors, hostname resolution).
- [ ] **4.4** `docker/dhis2-sso/certs/.gitignore` — ignore everything except
      `.gitignore` itself.
- [ ] **4.5** End-to-end manual run-through following the README from a
      clean checkout. Capture any gotchas back into the README.
- [ ] **4.6** Delete `docker/dhis2-sso/docker-compose.spike.yml` (left over
      from `dhis2-oauth-spike`) now that the full compose file supersedes it.

## Phase 5 — Tests, docs, archive

- [ ] **5.1** All Spock tests green: `./gradlew test`.
- [ ] **5.2** Frontend untouched. Run `npm test` to confirm no regressions.
- [ ] **5.3** Update top-level `README.md` only if user-facing behavior is
      enabled by default (it isn't — skip unless we decide to enable it on a
      specific customer branch).
- [ ] **5.4** Update PR description on the open PR (if any) to reflect final
      shape.
- [ ] **5.5** OpenSpec archive: ensure `design.md` "Upstream touch points"
      lists every modified upstream file; add "Deploy status" line; run
      `/opsx:archive`.
