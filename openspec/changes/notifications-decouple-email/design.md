## Context

The in-app notifications feature (see the `in-app-notifications` change) was deliberately built as a side-effect of email: a hook at `MailService.doSendMail` (`grails-app/services/org/pih/warehouse/core/MailService.groovy:205`) fires `customNotificationService.recordSendAsNotifications(to, subject, body)` immediately after `email.send()` succeeds. That was the minimal-upstream-footprint choice for v1, and it works — but it bakes in three structural limits:

1. **Mail-config coupling.** `doSendMail` returns early at line 155 (`if (!isMailEnabled && !override) { ... return false }`) *before* the hook. Turn email off → notifications stop.
2. **Send-success coupling.** The hook is inside the `try { email.send(); ...; return true }` block. If SMTP is down or the send throws, no notification is recorded even though the business event happened.
3. **Email-address coupling.** The hook only sees `to` — a list of email *strings*. Recipients are built upstream as `users.collect { it.email }`, so a `User` with no email (`Person.email` is `nullable: true`, `Person.groovy:39`) is dropped from `to` and is invisible at the hook. There is no token representing that user to attach a notification to.

An agent scan of all 23 `MailService` call sites found that **11 of the 18 "user objects in scope" sites funnel through one file** — `grails-app/services/org/pih/warehouse/report/NotificationService.groovy` — at the points where `User`/`Person` recipients are resolved (shipment shipped/received, requisition pending-approval/status, fulfillment, stock/expiry alerts, user-account create/confirm, application errors). That file is the natural choke point for an event-driven, user-keyed notification trigger.

This change moves the trigger from "an email was sent" to "the app decided to notify person X," recorded by `User`, independent of email.

## Goals / Non-Goals

**Goals:**

- Record in-app notifications from the originating business event, keyed by `User`, regardless of whether an email is also sent, whether the send succeeds, or whether the mail-enabled config is on.
- Reach users who have no email address.
- Expose a first-class `notifyUsers(Collection<User>, String title, String body, NotificationType type)` on `CustomNotificationService`.
- Add an independent enable flag `openboxes.notifications.inApp.enabled` (default `true`), configured **only** in `docker/openboxes.yml` and `docker/openboxes.client-template.yml`.
- Keep all decision/logic in our custom service so the upstream touch is just call insertions — leaving a clean future upstream-contribution path ("promote `notifyUsers` into `NotificationService`, drop the `custom` namespacing").

**Non-Goals:**

- **DHIS2 sync.** This change only makes notifications user-keyed and email-independent (which *enables* a later sync). The sync itself is a separate change.
- **The ~7 controller-level mail sends** outside `NotificationService` (`UserController` ×3, `ProductController`, `ShipmentController`, `CreateShipmentWorkflowController`, `ErrorsController`). Phase 2.
- **Per-user notification preferences, typing/filtering, WebSocket delivery.** Unchanged from the v1 non-goals.
- **Changing what emails are sent.** Every email sent today is still sent; only notification *recording* moves.
- **Frontend changes.** The bell/dropdown/modal/API are untouched.

## Decisions

### D1. New `notifyUsers(Collection<User>, title, body, type)` on `CustomNotificationService`

**Choice:** Add a method that takes resolved `User` objects (not email strings) and writes one `CustomNotification` per user, reusing the existing `withNewSession { withTransaction { ... } }` wrapper (background-thread safe — stock/expiry alerts run on GPars workers, see `recordSendAsNotifications`).

**Rationale:** The recipients are already `User`/`Person` objects at the `NotificationService` call sites; passing them directly removes the email→user re-resolution entirely (no `findAllByEmail`, no non-uniqueness handling, no `username` fallback). The notification is keyed by the authoritative `User`, which is exactly what a future DHIS2-UID mapping needs.

**Alternatives considered:**
- **Keep `recordSendAsNotifications(emails, ...)` and also pass users.** Rejected — two code paths, double-recording risk, and the email-string path is the thing we're trying to eliminate.
- **Spring event (`notify('user:notify', ...)`) + listener.** Rejected for now — adds a listener service and indirection for no benefit over a direct method call at a single choke-point file. Could be retrofitted if the call sites multiply.

### D2. Remove the `MailService.doSendMail` hook

**Choice:** Delete the `try { customNotificationService?.recordSendAsNotifications(...) } catch (Throwable t) { ... }` block (currently `MailService.groovy:204-208`) and the `def customNotificationService` injection if it's otherwise unused.

**Rationale:** Once `NotificationService` records notifications by user, leaving the mail hook in place would double-record for every emailed user. Removing it also *reverts* an upstream touch point — a net reduction in fork surface on `MailService` — and breaks the email coupling at its root. `recordSendAsNotifications` is removed (or kept only if Phase 2 controller sites still need an email-based fallback; current plan: remove).

### D3. Enable flag owned by the custom service, configured in docker only

**Choice:** `CustomNotificationService.notifyUsers` checks `grailsApplication.config.openboxes.notifications.inApp.enabled` (default `true` when unset) and no-ops when disabled. The key is declared **only** in `docker/openboxes.yml` and `docker/openboxes.client-template.yml`.

**Rationale:** Keeping the flag-read in *our* service means the upstream `NotificationService` calls stay unconditional one-liners — no flag logic leaks into upstream files. Per project config rules and the user's explicit instruction, custom config goes in the docker config + client template, never in `application.yml`/`application.groovy`. Default-on preserves current behavior unless a deployment opts out. Email (`grails.mail.*` / `isMailEnabled`) and in-app notifications are now independently switchable.

### D4. Hook placement inside `NotificationService` — capture users before `.collect { it.email }`

**Choice:** At each of the ~11 recipient-resolution points, insert one `customNotificationService.notifyUsers(<resolvedUsers>, subject, body, <type>)` call on the `User`/`Person` collection that already exists in scope, *before* it is mapped to emails. Group under a `// in-app-notifications (custom)` comment for merge visibility.

**Rationale:** These are the semantic events. The `users`/`recipients`/`subscribers`/`recipient` variables are already in scope (the agent scan captured the variable per site). Recording there is email-independent and covers the high-value notifications in one file. Per-site `NotificationType` lets v2 typing land without re-touching these lines.

**Per-site inventory (verified by agent scan; confirm exact lines at implementation time):** `NotificationService.groovy` ~lines 128, 131 (stock/expiry alerts, `subscribers`), 166 (shipment-items shipped, `recipient`), 175 (shipment notifications, `users`), 199–206 (receipt notifications, `recipient`), 221 (application error, `subscribers`), 237 (user-account creation, `recipients`), 252 (user-account confirmation, `userInstance`), 290 (requisition pending-approval, `recipient`), 302 (requisition status update, `recipient`), 312 (fulfillment, `requestor`).

### D5. No schema, no frontend, no API change

**Choice:** Reuse the existing `custom_notification` table, `NotificationType` enum (add values only if a site needs a distinct type), controller, and React/GSP bells unchanged.

**Rationale:** The DTO contract (`{ id, type, title, body, linkUrl, read, createdAt }` + `unreadCount`) and the bell behavior are independent of what writes the rows. Only the write trigger moves.

## Upstream Touch Points

| File | Edit | Reason |
|---|---|---|
| `grails-app/services/org/pih/warehouse/report/NotificationService.groovy` | ~11 one-line `customNotificationService.notifyUsers(...)` calls at recipient-resolution points, under a `// in-app-notifications (custom)` comment; one `def customNotificationService` injection | The event-level choke point. See D1, D4. **New touch point.** |
| `grails-app/services/org/pih/warehouse/core/MailService.groovy` | **Remove** the post-send hook block and (if now unused) the `def customNotificationService` injection | Eliminates double-recording and the email coupling. See D2. **Reverts a prior touch point.** |
| `docker/openboxes.yml` | Add `openboxes.notifications.inApp.enabled: true` | Deployment default for the new flag. See D3. |
| `docker/openboxes.client-template.yml` | Add the same key (documented, default `true`) | Per-client override surface. See D3. |

Net effect on `MailService` is a *reduction* in fork surface (one touch point removed). `application.yml` / `application.groovy` are intentionally **not** touched.

## Risks / Trade-offs

- **[New touch point in `NotificationService` = merge surface.]** ~11 insertions in an upstream file that changes across upstream releases. → Mitigation: one line each under a single comment delimiter; documented above; `git rerere` (see `.claude/docs/FORK_MAINTENANCE.md`) replays resolutions; and the planned upstream contribution would retire the surface entirely.
- **[Behavior change: record on event, not on successful send.]** Notifications now appear even if the email later fails or is disabled. → This is the *intended* semantics (independent channel), but it is a change from v1. Called out in the proposal; verify with a smoke test that disabling mail still yields notifications.
- **[Coverage gap vs. the old funnel.]** The old `MailService` hook caught *all* 23 sites; `NotificationService` covers ~11. The ~7 controller-level sites (and any non-`NotificationService` mail) will no longer create notifications until Phase 2. → Mitigation: the dropped sites are low-value (test email, photo-changed, product-created, error reports, admin free-text, stocklist free-text); enumerate them in tasks and schedule Phase 2. Confirm with PM that none are must-haves for the first cut.
- **[Double-recording during transition.]** If the `NotificationService` calls land before the `MailService` hook is removed, emailed users get two rows. → Mitigation: remove the hook in the same change/commit as adding the calls; a test asserts a single email-triggering event yields exactly one notification per user.
- **[Flag default drift.]** A deployment whose `openboxes.yml` predates this change has no key set. → Mitigation: default-to-`true` in the service when the config is absent, so missing config preserves current behavior.

## Migration Plan

1. **Service:** Add `notifyUsers(Collection<User>, String title, String body, NotificationType type)` to `CustomNotificationService` (reuse `withNewSession { withTransaction { } }`); add the `openboxes.notifications.inApp.enabled` config read with default-true.
2. **Hook in:** Add the ~11 `notifyUsers(...)` calls in `NotificationService.groovy` at the resolution points (D4), plus the `def customNotificationService` injection.
3. **Hook out:** Remove the `MailService.doSendMail` post-send hook and unused injection (D2). Remove `recordSendAsNotifications` (and its email-resolution tests) unless retained for a documented Phase 2 reason.
4. **Config:** Add the flag to `docker/openboxes.yml` and `docker/openboxes.client-template.yml`.
5. **Tests:** Unit/integration for `notifyUsers` (records one row per user incl. an email-less user; no-ops when flag disabled); a test that a representative `NotificationService` event records exactly one notification per recipient user; a smoke check that mail-disabled still notifies.
6. **Verify:** `./gradlew compileGroovy test --tests "*.custom.notifications.*"`; manual check that a stock alert with `isMailEnabled=false` still creates rows.

**Rollback:** Re-add the `MailService` hook, remove the `NotificationService` calls and the config key, restore `recordSendAsNotifications`. No schema change, so no DB rollback. The `custom_notification` table and frontend are untouched.

## Open Questions

- **OQ1 (RESOLVED — yes).** Email-less-user coverage IS a requirement for this cut. That is the core motivation; `notifyUsers` keys by `User` so an absent email never excludes a recipient. An empty recipient set is a no-op (nothing to record), not an error.
- **OQ2.** Do any of the ~7 Phase-2 controller-level sends need to be in the first cut? Default assumption: no.
- **OQ3 (RESOLVED — domain-level types).** Type each notification by domain at the source (cheap now, costly to back-fill later since it means re-touching the upstream hooks). Add to the custom `NotificationType` enum: `SHIPMENT` (shipped/received), `REQUISITION` (pending-approval/status), `FULFILLMENT`, `STOCK_ALERT` (stock/expiry), `USER_ACCOUNT` (creation/confirmation), `SYSTEM` (application errors); keep `EMAIL_TRIGGER` as the fallback for anything not cleanly classifiable. Granularity is domain-level, not per-action (finer than any UI/DHIS2 mapping will use). The bell does not filter by type yet — the value is future filtering/icons and DHIS2 categorization without re-touching `NotificationService`.

## Validation

- [ ] **V1. `notifyUsers` records by `User`, not email.** Verifiable by reading `CustomNotificationService` post-implementation: signature takes `Collection<User>`; body constructs `new CustomNotification(user: user, ...)` with no `findAllByEmail`/`findAllByUsername` call.
- [ ] **V2. Email-less user is reachable.** Integration test: a `User` with `email == null` passed to `notifyUsers` gets a `custom_notification` row. (Contrast with the old path, where they never appeared.)
- [ ] **V3. The mail coupling is gone.** Verifiable by grepping `MailService.groovy` post-implementation: no reference to `customNotificationService` / `recordSendAsNotifications` remains inside `doSendMail`.
- [ ] **V4. Notifications independent of mail-enabled.** Test/smoke: with `isMailEnabled=false`, invoking a `NotificationService` event still creates notification rows (records before, and regardless of, the `doSendMail` early return).
- [ ] **V5. Flag disables recording.** Unit test: with `openboxes.notifications.inApp.enabled=false`, `notifyUsers` writes zero rows; with the key absent, it writes rows (default-true).
- [ ] **V6. No double-recording.** Test: a single `NotificationService` event whose recipients are all emailed yields exactly one notification per user (not two).
- [ ] **V7. Flag is only in docker config.** Verifiable: `grep -rn "notifications.inApp.enabled"` matches `docker/openboxes.yml` and `docker/openboxes.client-template.yml` and **not** `grails-app/conf/application.yml` or `application.groovy`.
- [ ] **V8. Upstream touch is surgical and reversible.** Verifiable by diff: `NotificationService.groovy` changes are only `notifyUsers(...)` call lines + one injection under the comment delimiter; `MailService.groovy` change is a pure deletion of the prior hook; no reformatting elsewhere.
- [ ] **V9. No frontend/schema/API change.** Verifiable: diff touches no file under `src/js/`, no Liquibase changeset, no controller/UrlMappings change.
- [ ] **V10. Phase-2 sites are enumerated.** The tasks list names the ~7 deferred controller-level sites so the coverage gap is explicit, not silent.
