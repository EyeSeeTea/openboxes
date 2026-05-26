## MODIFIED Requirements

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
