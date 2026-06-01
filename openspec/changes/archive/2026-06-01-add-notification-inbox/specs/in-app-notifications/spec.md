## ADDED Requirements

### Requirement: Marking a notification unread

The system SHALL allow the authenticated user to flip one of their own previously-read notifications back to unread, via `PUT /api/custom/notifications/{id}/unread`. The operation SHALL clear `read_at`, set `is_read = false`, and SHALL be scoped to notifications owned by the requesting user. This is symmetric to the existing single mark-as-read endpoint.

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

The system SHALL provide a dedicated notification inbox page, reachable at the client route `**/notification/inbox` within the React application, for authenticated users. The page SHALL present a master-detail layout: a paginated list of the user's notifications (newest first) on one side, and a reading pane that renders the selected notification's title and body on the other. The page SHALL let the user filter the list by notification type and by a date range, and SHALL let the user mark the selected notification as read or unread. The existing bell dropdown SHALL link to this page.

#### Scenario: Inbox lists the user's notifications, newest first

- **WHEN** an authenticated user opens `**/notification/inbox`
- **THEN** the page fetches `GET /api/custom/notifications` and renders the user's notifications in a list ordered by `createdAt` descending, including both read and unread, with paging controls when there are more than one page

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

#### Scenario: Filter by notification type

- **WHEN** the user selects a specific type in the inbox type filter
- **THEN** the client re-fetches `GET /api/custom/notifications?type=<TYPE>` and the list shows only notifications of that type
- **AND** choosing "All types" clears the filter and re-fetches without the `type` parameter

#### Scenario: Filter by date range

- **WHEN** the user picks a start date and an end date in the inbox date-range filter
- **THEN** the client re-fetches `GET /api/custom/notifications?since=<start>&before=<end>` and the list shows only notifications whose `createdAt` falls within the range

#### Scenario: Type and date-range filters combine

- **WHEN** the user has both a type and a date range selected
- **THEN** the client sends `type`, `since`, and `before` together and the list shows only notifications matching all active filters

#### Scenario: Bell dropdown links to the inbox

- **WHEN** the bell dropdown is open
- **THEN** it shows a "View all" control that navigates to `**/notification/inbox`

#### Scenario: Inbox is scoped to the current user

- **WHEN** user A opens the inbox
- **THEN** every notification shown belongs to user A; user B's rows are never included (the API enforces ownership)

## MODIFIED Requirements

### Requirement: Listing notifications for the current user

The system SHALL expose `GET /api/custom/notifications` returning the authenticated user's notifications, newest first, with optional filters for unread-only, notification type, a creation-date range, and a result limit. The response SHALL be a JSON object of the shape `{ "data": [...], "unreadCount": N }` where `data` is the (filtered) list and `unreadCount` is the total unread count for the user — independent of the filters — so clients can drive a badge in one round trip. The supported query parameters are: `unreadOnly` (boolean), `type` (a single `NotificationType` name; exact match on `notification_type`), `since` (inclusive lower bound on `date_created`), `before` (inclusive upper bound on `date_created`; pairs with `since` to form a range), `limit`, and `offset`.

#### Scenario: Default list returns latest 20

- **WHEN** an authenticated user requests `GET /api/custom/notifications` with no parameters
- **THEN** the response returns up to 20 of that user's notifications, ordered by `created_at` descending, including read and unread, and the response body has the shape `{ "data": [...], "unreadCount": <int> }`

#### Scenario: Unread-only filter

- **WHEN** the user requests `GET /api/custom/notifications?unreadOnly=true`
- **THEN** the response contains only notifications where `is_read = false` for the authenticated user

#### Scenario: Type filter

- **WHEN** the user requests `GET /api/custom/notifications?type=STOCK_ALERT`
- **THEN** the response contains only notifications whose `notification_type` equals `STOCK_ALERT` for the authenticated user

#### Scenario: Date-range filter

- **WHEN** the user requests `GET /api/custom/notifications?since=2026-05-01T00:00:00Z&before=2026-05-31T23:59:59Z`
- **THEN** the response contains only notifications whose `date_created` is on or after `since` and on or before `before`

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

### Requirement: In-app notification UI

The system SHALL render a notification bell in the main application header for authenticated users, showing an unread count and an interactive dropdown. The dropdown SHALL expose two tabs — "Unread" (default) and "All" — so users can review history without losing the unread-first scanning model. Clicking a notification opens a detail modal showing its title and body; notifications do not carry a navigation link. The dropdown SHALL also provide a "View all" control that navigates to the notification inbox page.

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

#### Scenario: View-all navigates to the inbox

- **WHEN** the user clicks the "View all" control in the dropdown
- **THEN** the browser navigates to the notification inbox page (`**/notification/inbox`)

#### Scenario: Polling refresh

- **WHEN** the bell component is mounted and the user is authenticated
- **THEN** the client polls `GET /api/custom/notifications` every 30 seconds with the parameters of the currently-active tab (`unreadOnly=true&limit=20` for "Unread", `unreadOnly=false&limit=20` for "All") and updates the badge and the visible list when the response changes
- **AND** the badge value is always derived from the response's `unreadCount` field, so it is accurate regardless of which tab is active
