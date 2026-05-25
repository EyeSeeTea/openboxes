## Why

In-app notifications currently exist only as a side-effect of sending email: a hook inside `MailService.doSendMail` fires *after* a successful send. That couples a user-facing channel to an unrelated transport, with three consequences — notifications never record when the mail-enabled config is off, they never record when an SMTP send fails, and they can never reach users who have no email address (those users are dropped from the recipient list before `doSendMail` is ever called). A notification channel should stand on its own: triggered by the business event, delivered to a `User`, regardless of whether an email also goes out.

## What Changes

- **BREAKING (internal):** Remove the post-send hook in `MailService.doSendMail`. Notifications are no longer a byproduct of email delivery.
- Add a first-class `notifyUsers(Collection<User> users, String title, String body, NotificationType type)` method to `CustomNotificationService` that records one notification per user — keyed by `User`, never by email string.
- Hook `notifyUsers` into `NotificationService` (upstream `org.pih.warehouse.report.NotificationService`) at the ~11 points where `User`/`Person` recipients are already resolved, *before* they are reduced to email strings via `.collect { it.email }`. This is the single choke point for the high-value events (shipment shipped/received, requisition pending-approval and status, fulfillment, stock/expiry alerts, user-account events, application errors).
- Add an independent enable flag `openboxes.notifications.inApp.enabled` (default `true`), read by `CustomNotificationService` so the on/off logic lives in our code, not in the upstream mail path. Email and in-app notifications become independently switchable.
- Configure the flag **only** in `docker/openboxes.yml` and the docker client template (`docker/openboxes.client-template.yml`) — **not** in `application.yml`/`application.groovy`.
- Email-less users are now reachable (their `User` is captured before the email filter), which lays the groundwork for a future DHIS2 sync (notifications keyed by `User` → mappable to a DHIS2 user UID). The DHIS2 sync itself is **out of scope** for this change.

Out of scope / Phase 2: the ~7 scattered controller-level mail sends (`UserController`, `ProductController`, `ShipmentController`, `CreateShipmentWorkflowController`, `ErrorsController`) that build recipients outside `NotificationService`.

Resolved: email-less-user coverage IS required (it is the core motivation). Notification types are domain-level (`SHIPMENT`, `REQUISITION`, `FULFILLMENT`, `STOCK_ALERT`, `USER_ACCOUNT`, `SYSTEM`, with `EMAIL_TRIGGER` as fallback) — see design.md OQ3.

## Capabilities

### New Capabilities

_None._ This refines an existing capability rather than introducing a new one.

### Modified Capabilities

- `in-app-notifications`: The notification-creation requirement changes from "created as a side-effect of a successful email send" to "created from the originating business event, by `User`, independent of email transport and of the mail-enabled config." Adds the `openboxes.notifications.inApp.enabled` flag requirement. Removes the `MailService` recipient-email→user resolution requirement.

## Impact

- **Backend (our custom files):** `CustomNotificationService` gains `notifyUsers(...)` + the enable-flag read; existing `recordSendAsNotifications` (email-string based) is removed or superseded.
- **Backend (upstream touch, surgical):**
  - `grails-app/services/org/pih/warehouse/core/MailService.groovy` — **remove** the existing post-send hook (reverts a touch point we previously added).
  - `grails-app/services/org/pih/warehouse/report/NotificationService.groovy` — add ~11 one-line `notifyUsers(...)` calls at the recipient-resolution points. New upstream touch point; documented in `design.md`.
- **Config:** new key `openboxes.notifications.inApp.enabled` in `docker/openboxes.yml` and `docker/openboxes.client-template.yml` only.
- **Database:** none. Reuses the existing `custom_notification` table.
- **Frontend:** none. The bell, dropdown, modal, and REST API are unchanged — only *what triggers a notification row* changes server-side.
- **External systems:** none in this change. (Enables a later, separate DHIS2 sync.)
- **Upstream-contribution note:** keeping `notifyUsers` in `CustomNotificationService` and the calls surgical means a future upstream PR is mostly "promote `notifyUsers` into `NotificationService` natively and drop the `custom` namespacing." DHIS2 stays in the fork.
- **Rollout scope:** TJK/EST branch. Clean cherry-pick target once stable.
