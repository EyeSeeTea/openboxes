## MODIFIED Requirements

### Requirement: Notification record persistence

The system SHALL persist a notification record for each `User` resolved at the originating business event, keyed by the `User` object (not by email address). Notification persistence SHALL be independent of email: it SHALL occur regardless of whether an email is also sent for the event, whether any email send succeeds, and whether the mail-enabled configuration is on. A `User` without an email address SHALL still receive a notification record. Persistence failures SHALL be logged and SHALL NOT propagate into the calling business logic.

#### Scenario: Event notifies a single user

- **WHEN** a business event in `NotificationService` resolves a recipient `User` and invokes `notifyUsers([user], title, body, type)`
- **THEN** the system creates exactly one `custom_notification` row owned by that user, with `is_read = false`, a `date_created` timestamp, and a non-empty `title`

#### Scenario: Event notifies a user with no email address

- **WHEN** a business event resolves a `User` whose `Person.email` is null and invokes `notifyUsers([user], ...)`
- **THEN** the system creates one `custom_notification` row owned by that user (the absence of an email does not prevent the notification)

#### Scenario: Notification recorded when email is disabled

- **WHEN** the mail-enabled configuration is off (`isMailEnabled = false`) and a business event invokes `notifyUsers([user], ...)`
- **THEN** the system still creates the `custom_notification` row (no email is sent, but the in-app notification is recorded)

#### Scenario: Notification recorded when email send fails

- **WHEN** a business event invokes `notifyUsers([user], ...)` and the corresponding email send later throws or returns false
- **THEN** the `custom_notification` row is still present (notification recording does not depend on send success)

#### Scenario: Event notifies multiple users

- **WHEN** a business event resolves three recipient users and invokes `notifyUsers(users, ...)`
- **THEN** the system creates exactly three `custom_notification` rows, one per user, each with a distinct `user_id`

#### Scenario: No double recording for emailed users

- **WHEN** a single business event both records notifications via `notifyUsers` and sends an email to the same users
- **THEN** exactly one `custom_notification` row exists per user for that event (the email send does NOT additionally create notifications)

#### Scenario: Persistence failure does not break the business event

- **WHEN** `notifyUsers` is invoked and a transient database error occurs while creating a row
- **THEN** the error is logged and the exception does not propagate back into the calling `NotificationService` method or the email path

## ADDED Requirements

### Requirement: Independent in-app notification toggle

The system SHALL provide a configuration flag `openboxes.custom.notifications.inApp.enabled` that enables or disables in-app notification recording independently of the email configuration. When the flag is absent, the system SHALL default to enabled. The flag SHALL be configured only in the docker configuration (`docker/openboxes.yml`) and the docker client template (`docker/openboxes.client-template.yml`), and SHALL NOT be added to `application.yml` or `application.groovy`.

#### Scenario: Recording disabled by flag

- **WHEN** `openboxes.custom.notifications.inApp.enabled` is `false` and a business event invokes `notifyUsers(...)`
- **THEN** no `custom_notification` row is created

#### Scenario: Recording enabled by flag

- **WHEN** `openboxes.custom.notifications.inApp.enabled` is `true` and a business event invokes `notifyUsers(...)`
- **THEN** notification rows are created as specified by "Notification record persistence"

#### Scenario: Default-on when unconfigured

- **WHEN** the flag is not present in any loaded configuration and a business event invokes `notifyUsers(...)`
- **THEN** the system behaves as if the flag were `true` and creates notification rows

#### Scenario: Independent of email toggle

- **WHEN** email is enabled but `openboxes.custom.notifications.inApp.enabled` is `false`
- **THEN** emails are still sent but no in-app notifications are recorded; and conversely, when email is disabled but the in-app flag is on, notifications are recorded while no email is sent

## MODIFIED Requirements

### Requirement: Upstream isolation

The system SHALL keep all custom notification code under `org.pih.warehouse.custom.notifications` (backend) and `src/js/custom/notifications/` (frontend), and SHALL place any database migration under `grails-app/migrations/custom/`. Edits to upstream files SHALL be limited to surgical hooks: `notifyUsers(...)` call sites in `NotificationService` at points where recipient `User` objects are already resolved, removal of the prior `MailService.doSendMail` notification hook, and the in-app enable flag in the docker configuration files. The on/off and recording logic SHALL live in the custom service so the upstream call sites remain unconditional one-liners.

#### Scenario: Backend new files placement

- **WHEN** a reviewer lists new backend files in the change
- **THEN** every new `.groovy` file resides under `grails-app/**/org/pih/warehouse/custom/notifications/` or `src/main/groovy/org/pih/warehouse/custom/notifications/`

#### Scenario: NotificationService hook is surgical

- **WHEN** a reviewer diffs `grails-app/services/org/pih/warehouse/report/NotificationService.groovy`
- **THEN** the change shows only added `customNotificationService.notifyUsers(...)` call lines (and one service injection) grouped under a `// in-app-notifications (custom)` comment — no reformatting, import reordering, or unrelated edits

#### Scenario: MailService hook removed

- **WHEN** a reviewer diffs `grails-app/services/org/pih/warehouse/core/MailService.groovy`
- **THEN** the prior post-send notification hook block (and its now-unused service injection) is removed, and no other lines are reformatted

#### Scenario: Flag config placement

- **WHEN** a reviewer searches for `notifications.inApp.enabled`
- **THEN** it appears only in `docker/openboxes.yml` and `docker/openboxes.client-template.yml`, and not in `grails-app/conf/application.yml` or `application.groovy`
