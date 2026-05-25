# Implementation Tasks — notifications-decouple-email

Implementation order follows the `Migration Plan` in `design.md`. Each task is sized for one focused commit. The custom service changes (group 1) land before the upstream hook-in/hook-out (groups 2–3) so notifications keep working throughout; the `NotificationService` calls and the `MailService` hook removal MUST ship in the same change to avoid double-recording (design.md Risks).

## 0. Pre-implementation verification

- [ ] **0.1** Confirm the exact recipient-resolution lines in `grails-app/services/org/pih/warehouse/report/NotificationService.groovy` and the in-scope `User`/`Person` variable at each (design.md D4 lists the agent-scanned estimates ~128, 131, 166, 175, 199–206, 221, 237, 252, 290, 302, 312). Capture the real line numbers + variable names as receipts before editing.
- [ ] **0.2** Confirm `Person.email` is `nullable: true` (`grails-app/domain/org/pih/warehouse/core/Person.groovy:39`) so the email-less scenario is real.
- [ ] **0.3** Confirm the current `MailService.doSendMail` hook block location (`grails-app/services/org/pih/warehouse/core/MailService.groovy` ~204–208) and the `def customNotificationService` injection, to scope the removal.
- [ ] **0.4** Confirm the config-read idiom used elsewhere (`grailsApplication.config.openboxes...`) and how `docker/openboxes.yml` keys map into `grailsApplication.config`, so the flag read is correct.

## 1. Custom service — `notifyUsers` + enable flag

- [ ] **1.1** Add `notifyUsers(Collection<User> users, String title, String body, NotificationType type)` to `CustomNotificationService`:
  - No-op (return) when the resolved enable flag is false.
  - Reuse `CustomNotification.withNewSession { CustomNotification.withTransaction { ... } }` (background-thread safe; stock/expiry alerts run on GPars workers).
  - De-duplicate the user collection; for each `User`, create a `CustomNotification` with `user: user`, `notificationType: type?.name() ?: NotificationType.EMAIL_TRIGGER.name()`, the `title`/`body`, `isRead: false`.
  - Per-user `try/catch` logging; never rethrow into the caller.
  - Apply the same blank-title fallback already used by the service (`'(no subject)'`).
- [ ] **1.2** Add the enable-flag read: a private helper reading `grailsApplication.config.openboxes.custom.notifications.inApp.enabled`, defaulting to `true` when unset/null. Inject `def grailsApplication` if not already present.
- [ ] **1.3** Decide the fate of `recordSendAsNotifications`: remove it (and its email-resolution unit/integration tests) unless a documented Phase-2 reason keeps it. Default: remove.

## 2. Hook in — `NotificationService`

- [ ] **2.1** Add `def customNotificationService` injection to `NotificationService`.
- [ ] **2.2** Extend the custom `NotificationType` enum with domain values: `SHIPMENT`, `REQUISITION`, `FULFILLMENT`, `STOCK_ALERT`, `USER_ACCOUNT`, `SYSTEM` (keep `EMAIL_TRIGGER` as fallback). See design.md OQ3.
- [ ] **2.3** At each recipient-resolution point confirmed in 0.1, insert one `customNotificationService.notifyUsers(<users>, subject, body, <type>)` call on the in-scope `User`/`Person` collection, BEFORE any `.collect { it.email }`. Group/annotate with a `// in-app-notifications (custom)` comment for merge visibility. Pass the matching domain `NotificationType` per site (shipment→`SHIPMENT`, requisition→`REQUISITION`, etc.); fall back to `EMAIL_TRIGGER` only where the domain isn't clear.
- [ ] **2.4** For single-`Person` sites (e.g. `recipient`, `requestor`), wrap as a single-element collection (`[recipient]`) — `notifyUsers` takes a `Collection`.

## 3. Hook out — `MailService`

- [ ] **3.1** Remove the post-send hook block in `doSendMail` (the `try { customNotificationService?.recordSendAsNotifications(...) } catch (Throwable t) { ... }`).
- [ ] **3.2** Remove the now-unused `def customNotificationService` injection from `MailService` (verify no other reference first).

## 4. Config

- [ ] **4.1** Add `openboxes.custom.notifications.inApp.enabled: true` to `docker/openboxes.yml`.
- [ ] **4.2** Add the same key (with a brief comment, default `true`) to `docker/openboxes.client-template.yml`.
- [ ] **4.3** Confirm the key is NOT added to `grails-app/conf/application.yml` or `application.groovy`.

## 5. Tests

- [ ] **5.1** Integration: `notifyUsers` creates one row per user, including a `User` whose `email` is null (the email-less scenario). Assert concrete `user_id`s and `title`.
- [ ] **5.2** Integration/unit: with the enable flag `false`, `notifyUsers` writes zero rows; with the key absent, it writes rows (default-true).
- [ ] **5.3** Integration: a representative `NotificationService` event records exactly one notification per recipient user (no double-recording).
- [ ] **5.4** Smoke/behavioral note: with `isMailEnabled=false`, the same event still records notifications (records independent of the `doSendMail` early-return).
- [ ] **5.5** Update/remove the controller and integration specs that asserted the old email-string path (`recordSendAsNotifications`) per task 1.3.

## 6. Verify

- [ ] **6.1** `./gradlew compileGroovy test --tests "*.custom.notifications.*"` is green.
- [ ] **6.2** Rebuild + redeploy the docker image; trigger a stock/expiry alert and confirm a `custom_notification` row appears for a recipient (including one with no email, if available), and that the bell shows it.
- [ ] **6.3** Diff review against design.md § Validation V1–V10 (user-keyed, email-independent, flag-gated, surgical upstream touch, docker-only config, no frontend/schema change).

## 7. Documentation

- [ ] **7.1** Update this change's `design.md` "Upstream Touch Points" if the confirmed line numbers/variables differ from the estimates.
- [ ] **7.2** On archive, update the `in-app-notifications` archived `design.md`/spec to reflect that the `MailService` hook was removed and the trigger moved to `NotificationService` (the archive is the patch manifest).
- [ ] **7.3** Note the Phase-2 follow-up (the ~7 controller-level mail sends: `UserController`, `ProductController`, `ShipmentController`, `CreateShipmentWorkflowController`, `ErrorsController`) so the coverage gap is tracked, not silent.
- [ ] **7.4** Record the open PM question (email-less coverage requirement) and its resolution.
