## ADDED Requirements

### Requirement: Notification record persistence

The system SHALL persist a notification record for each `User` account matched by an outgoing application email's recipient list. A single recipient email SHALL be matched against `Person.email` first; if no match is found, it SHALL be matched against `User.username` as a fallback. Recipient emails that match no `User` SHALL NOT cause a record to be persisted. A recipient email that matches multiple `User` accounts SHALL produce one record per matched user.

#### Scenario: Email sent to a recipient matching exactly one user

- **WHEN** the application successfully sends an email to `alice@example.com` and exactly one `User` has that email (via `Person.email` or `User.username`)
- **THEN** the system creates exactly one `custom_notification` row owned by that user, with `is_read = false`, a `date_created` timestamp, a non-empty `title`, and the email subject preserved as the notification title source

#### Scenario: Email sent to a recipient matching multiple users

- **WHEN** the application sends an email to `shared@team.example.com` and two `User` accounts share that email value on `Person.email`
- **THEN** the system creates exactly two `custom_notification` rows — one per matched user — each with a distinct `user_id` referencing one of the matches

#### Scenario: Email matches only via username fallback

- **WHEN** the application sends an email to `legacy.user@example.com` and no `Person.email` matches but a `User.username` does
- **THEN** the system creates one `custom_notification` row owned by the user whose `username` matched

#### Scenario: Email sent to an unknown recipient

- **WHEN** the application sends an email to `external-vendor@partner.com` and neither `Person.email` nor `User.username` matches
- **THEN** no `custom_notification` row is created and the email send is not affected

#### Scenario: Email send fails

- **WHEN** `MailService.doSendMail` returns `false` (mail send disabled, exception during construction, or exception during `send()`)
- **THEN** no `custom_notification` row is created for any recipient of that send

#### Scenario: Multi-recipient email with mixed matches

- **WHEN** an email is sent to three recipients — two whose emails each match a single distinct `User`, and one external recipient with no `User` match
- **THEN** exactly two `custom_notification` rows are created, one per matched user; the external recipient is silently skipped

#### Scenario: Notification persistence failure does not break the email

- **WHEN** the email send succeeds but a transient database error occurs while creating the `custom_notification` row
- **THEN** the error is logged and `doSendMail` still returns `true`; no exception propagates back into the mail send path

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

The system SHALL render a notification bell in the main application header for authenticated users, showing an unread count and an interactive dropdown. The dropdown SHALL expose two tabs — "Unread" (default) and "All" — so users can review history without losing the unread-first scanning model.

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

#### Scenario: Clicking an unread notification marks it read and navigates

- **WHEN** the user clicks an unread notification row
- **THEN** the client calls `PUT /api/custom/notifications/{id}/read`, the row's unread indicator clears
- **AND** if the notification has a `link_url` that is a same-origin path (starts with `/`, not `//`), the user is navigated there via the SPA router; external or protocol-relative URLs are ignored for safety

#### Scenario: Clicking an already-read notification does not re-mark it

- **WHEN** the user clicks a notification that is already read
- **THEN** the client does NOT call `PUT /api/custom/notifications/{id}/read`
- **AND** `link_url` navigation still applies if present and same-origin

#### Scenario: Mark-all-read action

- **WHEN** the user clicks the "Mark all read" control in the dropdown
- **THEN** the client calls `PUT /api/custom/notifications/read-all` and the badge clears

#### Scenario: Polling refresh

- **WHEN** the bell component is mounted and the user is authenticated
- **THEN** the client polls `GET /api/custom/notifications` every 30 seconds with the parameters of the currently-active tab (`unreadOnly=true&limit=20` for "Unread", `unreadOnly=false&limit=20` for "All") and updates the badge and the visible list when the response changes
- **AND** the badge value is always derived from the response's `unreadCount` field, so it is accurate regardless of which tab is active

### Requirement: Upstream isolation

The system SHALL keep all custom notification code under `org.pih.warehouse.custom.notifications` (backend) and `src/js/custom/notifications/` (frontend), and SHALL place the database migration under `grails-app/migrations/custom/`. Edits to upstream files SHALL be limited to surgical hooks (one call site in `MailService.doSendMail`, one tag insertion in the header component).

#### Scenario: Backend new files placement

- **WHEN** a reviewer lists new backend files in the change
- **THEN** every new `.groovy` file resides under `grails-app/**/org/pih/warehouse/custom/notifications/` or `src/main/groovy/org/pih/warehouse/custom/notifications/`

#### Scenario: Frontend new files placement

- **WHEN** a reviewer lists new frontend files in the change
- **THEN** every new `.js`/`.jsx`/`.scss` file resides under `src/js/custom/notifications/`

#### Scenario: Migration placement

- **WHEN** a reviewer lists new Liquibase changesets
- **THEN** every new `.groovy` changeset resides under `grails-app/migrations/custom/`

#### Scenario: Upstream edits are surgical

- **WHEN** a reviewer diffs the upstream files touched by the change
- **THEN** `MailService.doSendMail` shows only an added post-send call to the custom notification service, and the header component shows only an import + a single `<NotificationBell />` tag — no reformatting, import reordering, or unrelated edits
