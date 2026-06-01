# In-App Notifications

## Purpose

Deliver user-facing, in-app notifications keyed by `User` and independent of email transport: records are created from the originating business event (in `NotificationService` and analogous controller call sites) regardless of whether an email is sent, whether the send succeeds, or whether the user has an email address. Users review notifications via a header bell, dropdown, a dedicated inbox page, and REST API. All custom code stays isolated under `custom/` packages for upstream-merge safety.

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

The system SHALL expose `GET /api/custom/notifications` returning the authenticated user's notifications, newest first, with optional filters for unread-only, read/unread status, notification type, a creation-date range, and a result limit. The response SHALL be a JSON object of the shape `{ "data": [...], "unreadCount": N, "totalCount": M }` where `data` is the (filtered) list, `unreadCount` is the total unread count for the user — independent of the filters, so clients can drive a badge in one round trip — and `totalCount` is the number of rows matching the active filters, so clients can drive pagination. The supported query parameters are: `unreadOnly` (boolean), `read` (boolean; `read=false` returns only unread, `read=true` returns only read, and `read` takes precedence over `unreadOnly`), `type` (a single `NotificationType` name; exact match on `notification_type`), `since` (inclusive lower bound on `date_created`), `before` (inclusive upper bound on `date_created`; pairs with `since` to form a range), `limit`, and `offset`. All client requests to the notification API SHALL carry `X-Requested-With: XMLHttpRequest` so that an expired-session request yields a 401 rather than being saved by the security layer as the post-login redirect target.

#### Scenario: Default list returns latest 20

- **WHEN** an authenticated user requests `GET /api/custom/notifications` with no parameters
- **THEN** the response returns up to 20 of that user's notifications, ordered by `date_created` descending, including read and unread, and the response body has the shape `{ "data": [...], "unreadCount": <int>, "totalCount": <int> }`

#### Scenario: Unread-only filter

- **WHEN** the user requests `GET /api/custom/notifications?unreadOnly=true`
- **THEN** the response contains only notifications where `is_read = false` for the authenticated user

#### Scenario: Read/unread status filter

- **WHEN** the user requests `GET /api/custom/notifications?read=false` (or `read=true`)
- **THEN** the response contains only the user's unread (respectively read) notifications, and when both `read` and `unreadOnly` are supplied, `read` wins

#### Scenario: Type filter

- **WHEN** the user requests `GET /api/custom/notifications?type=STOCK_ALERT`
- **THEN** the response contains only notifications whose `notification_type` equals `STOCK_ALERT` for the authenticated user

#### Scenario: Date-range filter

- **WHEN** the user requests `GET /api/custom/notifications?since=2026-05-01T00:00:00.000Z&before=2026-05-31T23:59:59.999Z`
- **THEN** the response contains only notifications whose `date_created` is on or after `since` and on or before `before`

#### Scenario: Total count reflects the active filters

- **WHEN** the user requests a filtered list (any combination of `read`, `type`, `since`, `before`)
- **THEN** `totalCount` equals the number of the user's rows matching those filters (not the unfiltered total), while `unreadCount` remains the user's overall unread total

#### Scenario: Invalid type is ignored or rejected without leaking other users' data

- **WHEN** the user requests `GET /api/custom/notifications?type=NOT_A_TYPE`
- **THEN** the response either returns an empty `data` list or a 400, and never returns notifications of a different type or another user's rows

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

### Requirement: Marking a notification unread

The system SHALL allow the authenticated user to flip one of their own previously-read notifications back to unread, via `PUT /api/custom/notifications/{id}/unread`. The operation SHALL clear `read_at`, set `is_read = false`, and SHALL be scoped to notifications owned by the requesting user. This is symmetric to the single mark-as-read endpoint.

#### Scenario: Mark a read notification unread

- **WHEN** the user calls `PUT /api/custom/notifications/{id}/unread` for a notification they own whose `is_read` is `true`
- **THEN** the row's `is_read` becomes `false`, `read_at` becomes null, and the response indicates success
- **AND** the user's `unreadCount` increases by one

#### Scenario: Marking an already-unread notification is idempotent

- **WHEN** the user calls `PUT /api/custom/notifications/{id}/unread` for a notification that is already unread
- **THEN** the row remains `is_read = false` and the response indicates success (no error)

#### Scenario: Cannot mark another user's notification unread

- **WHEN** user A calls `PUT /api/custom/notifications/{id}/unread` where the notification belongs to user B
- **THEN** the response is 403/404 and the row is unchanged

### Requirement: Notification inbox page

The system SHALL provide a dedicated notification inbox page, reachable at the client route `**/notification/inbox` within the React application, for authenticated users. The page SHALL present a master-detail layout: a paginated list of the user's notifications (newest first) on one side, and a reading pane that renders the selected notification's title and body on the other. The page SHALL let the user filter the list by read/unread status, by notification type, and by a creation-date range; SHALL let the user mark the selected notification read (on selection) or unread (from the detail pane); and SHALL provide a "mark all as read" action when unread notifications exist. The existing bell dropdown SHALL link to this page.

#### Scenario: Inbox lists the user's notifications, newest first

- **WHEN** an authenticated user opens `**/notification/inbox`
- **THEN** the page fetches `GET /api/custom/notifications` and renders the user's notifications in a list ordered by `createdAt` descending, including both read and unread

#### Scenario: Inbox paginates using server-provided total count

- **WHEN** the user has more notifications than the current page size
- **THEN** the page renders pagination controls (page navigation and a rows-per-page selector) driven by the response's `totalCount`, and changing page or page size re-fetches with the corresponding `offset`/`limit`

#### Scenario: Selecting a notification renders its body in the detail pane

- **WHEN** the user selects a notification row in the list
- **THEN** the reading pane renders that notification's `title` and `body`, and the selected row is visually marked as active
- **AND** before any selection, the reading pane shows an empty/placeholder state

#### Scenario: Selecting an unread notification marks it read

- **WHEN** the user selects a notification whose `read` is `false`
- **THEN** the client calls `PUT /api/custom/notifications/{id}/read`, the row's unread indicator clears, and the badge/unread count decreases accordingly

#### Scenario: Selecting an already-read notification does not re-mark it

- **WHEN** the user selects a notification that is already read
- **THEN** the client does NOT call `PUT /api/custom/notifications/{id}/read`; the body still renders in the detail pane

#### Scenario: Mark the selected notification unread from the detail pane

- **WHEN** the user activates the "Mark as unread" control in the detail pane for a read notification
- **THEN** the client calls `PUT /api/custom/notifications/{id}/unread`, the row regains its unread indicator, and the badge/unread count increases accordingly

#### Scenario: Mark all as read from the inbox

- **WHEN** unread notifications exist and the user activates the inbox "mark all as read" action
- **THEN** the client calls `PUT /api/custom/notifications/read-all` and the list re-fetches so it reflects the active filter (e.g. an "unread" filter then shows an empty list)

#### Scenario: Filter by read/unread status

- **WHEN** the user selects "Unread" (or "Read") in the inbox status filter and submits
- **THEN** the client re-fetches `GET /api/custom/notifications?read=false` (respectively `read=true`) and the list shows only matching notifications

#### Scenario: Filter by notification type

- **WHEN** the user selects a specific type in the inbox type filter and submits
- **THEN** the client re-fetches `GET /api/custom/notifications?type=<TYPE>` and the list shows only notifications of that type

#### Scenario: Filter by date range

- **WHEN** the user picks a start date and an end date in the inbox date-range filter and submits
- **THEN** the client re-fetches `GET /api/custom/notifications?since=<start>&before=<end>` with the dates converted to UTC day boundaries (start-of-day for `since`, end-of-day for `before`), and the list shows only notifications whose `createdAt` falls within the range

#### Scenario: Filters combine

- **WHEN** the user has a status, a type, and a date range selected
- **THEN** the client sends `read`, `type`, `since`, and `before` together and the list shows only notifications matching all active filters

#### Scenario: Clearing filters re-fetches the full list

- **WHEN** the user activates the filter bar's "Clear" control
- **THEN** the filter fields reset and the client re-fetches `GET /api/custom/notifications` with no filter parameters

#### Scenario: Bell dropdown links to the inbox

- **WHEN** the bell dropdown is open
- **THEN** it shows a "View all" control that navigates to `**/notification/inbox`

#### Scenario: Inbox is scoped to the current user

- **WHEN** user A opens the inbox
- **THEN** every notification shown belongs to user A; user B's rows are never included (the API enforces ownership)

### Requirement: In-app notification UI

The system SHALL render a notification bell in the main application header for authenticated users, showing an unread count and an interactive dropdown. The dropdown SHALL expose two tabs — "Unread" (default) and "All" — so users can review history without losing the unread-first scanning model. Clicking a notification opens a detail modal showing its title and body; notifications do not carry a navigation link. The dropdown SHALL also provide a "mark all as read" control (when unread exist) and a "View all" control that navigates to the notification inbox page. React-rendered notification strings SHALL resolve from `react.notification.*` message keys (the only namespace the React localize store loads); the server-rendered GSP bell SHALL resolve the same keys server-side, so both surfaces share one key and one translation per string.

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

- **WHEN** the user clicks the "mark all as read" control in the dropdown
- **THEN** the client calls `PUT /api/custom/notifications/read-all` and the badge clears

#### Scenario: View-all navigates to the inbox

- **WHEN** the user clicks the "View all" control in the dropdown
- **THEN** the browser navigates to the notification inbox page (`**/notification/inbox`)

#### Scenario: Polling refresh

- **WHEN** the bell component is mounted and the user is authenticated
- **THEN** the client polls `GET /api/custom/notifications` every 30 seconds with the parameters of the currently-active tab (`unreadOnly=true&limit=20` for "Unread", `unreadOnly=false&limit=20` for "All") and updates the badge and the visible list when the response changes
- **AND** the badge value is always derived from the response's `unreadCount` field, so it is accurate regardless of which tab is active

### Requirement: Upstream isolation

The system SHALL keep all custom notification code under `org.pih.warehouse.custom.notifications` (backend) and `src/js/custom/notifications/` (frontend), and SHALL place the database migration under `grails-app/migrations/custom/`. Edits to upstream files SHALL be limited to surgical hooks: `notificationDispatcherService.notify(...)` call sites in `NotificationService` (and analogous controller call sites) at points where recipient `User` objects are already resolved, removal of the prior `MailService.doSendMail` notification hook, a single `<NotificationBell />` insertion in the header component, a single inbox route registration in `Router.jsx`, the unread-endpoint line in `UrlMappings.groovy`, and the `react.notification.*` / `notifications.*` i18n keys appended to the root `messages.properties` (and locale bundles). The recording and dispatch logic SHALL live in the custom services so the upstream call sites remain unconditional one-liners.

#### Scenario: Backend new files placement

- **WHEN** a reviewer lists new backend files in the change
- **THEN** every new `.groovy` file resides under `grails-app/**/org/pih/warehouse/custom/notifications/` or `src/main/groovy/org/pih/warehouse/custom/notifications/`, except the single route line in `UrlMappings.groovy`

#### Scenario: Frontend new files placement

- **WHEN** a reviewer lists new frontend files in the change
- **THEN** every new `.js`/`.jsx`/`.scss` file resides under `src/js/custom/notifications/`, except the single route registration in `Router.jsx`

#### Scenario: Migration placement

- **WHEN** a reviewer lists new Liquibase changesets
- **THEN** every new `.groovy` changeset resides under `grails-app/migrations/custom/`

#### Scenario: NotificationService hook is surgical

- **WHEN** a reviewer diffs `grails-app/services/org/pih/warehouse/report/NotificationService.groovy`
- **THEN** the change shows only added `notificationDispatcherService.notify(...)` call lines (and one service injection) grouped under a `// in-app-notifications (custom)` comment — no reformatting, import reordering, or unrelated edits

#### Scenario: MailService hook removed

- **WHEN** a reviewer diffs `grails-app/services/org/pih/warehouse/core/MailService.groovy`
- **THEN** the prior post-send notification hook block (and its now-unused service injection) is removed, and no other lines are reformatted
