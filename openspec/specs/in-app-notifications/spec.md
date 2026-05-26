# In-App Notifications

## Purpose

Deliver user-facing, in-app notifications keyed by `User` and independent of email transport: records are created from the originating business event (in `NotificationService` and analogous controller call sites) regardless of whether an email is sent, whether the send succeeds, or whether the user has an email address. Users review notifications via a header bell, dropdown, and REST API. All custom code stays isolated under `custom/` packages for upstream-merge safety.

## Requirements

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

### Requirement: Listing notifications for the current user

The system SHALL expose `GET /api/custom/notifications` returning the authenticated user's notifications, newest first, with optional filters for unread-only and a result limit. The response SHALL be a JSON object of the shape `{ "data": [...], "unreadCount": N }` where `data` is the (filtered) list and `unreadCount` is the total unread count for the user — independent of the filter — so clients can drive a badge in one round trip.

#### Scenario: Default list returns latest 20

- **WHEN** an authenticated user requests `GET /api/custom/notifications` with no parameters
- **THEN** the response returns up to 20 of that user's notifications, ordered by `created_at` descending, including read and unread, and the response body has the shape `{ "data": [...], "unreadCount": <int> }`

#### Scenario: Unread-only filter

- **WHEN** the user requests `GET /api/custom/notifications?unreadOnly=true`
- **THEN** the response contains only notifications where `is_read = false` for the authenticated user

#### Scenario: Limit parameter respected

- **WHEN** the user requests `GET /api/custom/notifications?limit=5`
- **THEN** the response contains at most 5 notifications

#### Scenario: Unauthenticated request rejected

- **WHEN** an unauthenticated client requests `GET /api/custom/notifications`
- **THEN** the response is a 401 (or the framework's standard auth-failure response) and no notification data is returned

#### Scenario: User cannot see another user's notifications

- **WHEN** user A requests their notification list
- **THEN** the response contains only rows where `user_id` matches user A; user B's rows are never included

### Requirement: Marking notifications as read

The system SHALL allow the authenticated user to mark a single notification or all of their notifications as read.

#### Scenario: Mark a single notification read

- **WHEN** the user calls `PUT /api/custom/notifications/{id}/read` for a notification they own
- **THEN** the row's `is_read` becomes `true` and the response indicates success

#### Scenario: Mark all read

- **WHEN** the user calls `PUT /api/custom/notifications/read-all`
- **THEN** every notification owned by that user has `is_read` set to `true`

#### Scenario: Cannot mark another user's notification

- **WHEN** user A calls `PUT /api/custom/notifications/{id}/read` where the notification belongs to user B
- **THEN** the response is 403/404 and the row is unchanged

### Requirement: In-app notification UI

The system SHALL render a notification bell in the main application header for authenticated users, showing an unread count and an interactive dropdown. The dropdown SHALL expose two tabs — "Unread" (default) and "All" — so users can review history without losing the unread-first scanning model. Clicking a notification opens a detail modal showing its title and body; notifications do not carry a navigation link.

#### Scenario: Unread badge reflects current state

- **WHEN** the authenticated user has 3 unread notifications
- **THEN** the bell icon displays a badge with the value "3", sourced from the server-provided `unreadCount` field (not from filtering the list client-side)

#### Scenario: Dropdown opens on the Unread tab by default

- **WHEN** the user clicks the bell icon
- **THEN** the dropdown opens with the "Unread" tab active and lists the user's most recent unread notifications (newest first), each showing title, short body, relative timestamp, and a visual unread indicator
- **AND** the "Unread" tab label includes the current unread count

#### Scenario: Switching to the All tab shows read + unread

- **WHEN** the user clicks the "All" tab in the open dropdown
- **THEN** the client re-fetches `GET /api/custom/notifications?unreadOnly=false&limit=20` and the list shows both read and unread notifications, newest first
- **AND** read rows are visually distinct from unread (no unread indicator, lower-emphasis background)

#### Scenario: Empty states are tab-specific

- **WHEN** the active tab is "Unread" and the user has no unread notifications
- **THEN** the dropdown shows an "No unread notifications" empty state
- **WHEN** the active tab is "All" and the user has no notifications at all
- **THEN** the dropdown shows a "No notifications yet" empty state

#### Scenario: Clicking an unread notification marks it read and opens its detail

- **WHEN** the user clicks an unread notification row
- **THEN** the client calls `PUT /api/custom/notifications/{id}/read`, the row's unread indicator clears, and the notification's detail modal opens showing its title and body

#### Scenario: Clicking an already-read notification does not re-mark it

- **WHEN** the user clicks a notification that is already read
- **THEN** the client does NOT call `PUT /api/custom/notifications/{id}/read`, and the notification's detail modal still opens

#### Scenario: Notification payload carries no navigation link

- **WHEN** the client fetches `GET /api/custom/notifications`
- **THEN** each notification object in `data` contains `id`, `type`, `title`, `body`, `read`, and `createdAt`, and SHALL NOT contain a `linkUrl` field

#### Scenario: Mark-all-read action

- **WHEN** the user clicks the "Mark all read" control in the dropdown
- **THEN** the client calls `PUT /api/custom/notifications/read-all` and the badge clears

#### Scenario: Polling refresh

- **WHEN** the bell component is mounted and the user is authenticated
- **THEN** the client polls `GET /api/custom/notifications` every 30 seconds with the parameters of the currently-active tab (`unreadOnly=true&limit=20` for "Unread", `unreadOnly=false&limit=20` for "All") and updates the badge and the visible list when the response changes
- **AND** the badge value is always derived from the response's `unreadCount` field, so it is accurate regardless of which tab is active

### Requirement: Upstream isolation

The system SHALL keep all custom notification code under `org.pih.warehouse.custom.notifications` (backend) and `src/js/custom/notifications/` (frontend), and SHALL place the database migration under `grails-app/migrations/custom/`. Edits to upstream files SHALL be limited to surgical hooks: `notificationDispatcherService.notify(...)` call sites in `NotificationService` (and analogous controller call sites) at points where recipient `User` objects are already resolved, removal of the prior `MailService.doSendMail` notification hook, and a single `<NotificationBell />` insertion in the header component. The recording and dispatch logic SHALL live in the custom services so the upstream call sites remain unconditional one-liners.

#### Scenario: Backend new files placement

- **WHEN** a reviewer lists new backend files in the change
- **THEN** every new `.groovy` file resides under `grails-app/**/org/pih/warehouse/custom/notifications/` or `src/main/groovy/org/pih/warehouse/custom/notifications/`

#### Scenario: Frontend new files placement

- **WHEN** a reviewer lists new frontend files in the change
- **THEN** every new `.js`/`.jsx`/`.scss` file resides under `src/js/custom/notifications/`

#### Scenario: Migration placement

- **WHEN** a reviewer lists new Liquibase changesets
- **THEN** every new `.groovy` changeset resides under `grails-app/migrations/custom/`

#### Scenario: NotificationService hook is surgical

- **WHEN** a reviewer diffs `grails-app/services/org/pih/warehouse/report/NotificationService.groovy`
- **THEN** the change shows only added `notificationDispatcherService.notify(...)` call lines (and one service injection) grouped under a `// in-app-notifications (custom)` comment — no reformatting, import reordering, or unrelated edits

#### Scenario: MailService hook removed

- **WHEN** a reviewer diffs `grails-app/services/org/pih/warehouse/core/MailService.groovy`
- **THEN** the prior post-send notification hook block (and its now-unused service injection) is removed, and no other lines are reformatted
