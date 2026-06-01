## Why

The notification bell dropdown is a good *glance* surface but a poor *management* surface: it shows only the latest items, can't filter by kind of event, and has no way to revisit history. The PM has asked for a dedicated inbox page so users can browse, filter, and triage their notifications — and re-flag ones they want to revisit.

## What Changes

- New **notification inbox page** at `**/notification/inbox`, rendered as a React route (the existing React `Router.jsx` pattern), using a **master-detail layout**: notification list on the left, reading pane on the right that renders the selected notification's body.
- **Filter by notification type** (the existing `NotificationType` enum values) and by a **date range** on `dateCreated`.
- **Mark-as-unread**: a detail-pane action that flips a read notification back to unread. New endpoint `PUT /api/custom/notifications/{id}/unread` — symmetric to the existing `/read`.
- **Read-on-select** in the inbox mirrors the existing dropdown behavior: selecting an unread notification marks it read and shows its body.
- Backend list query gains two optional filters: `type` (exact `notificationType`) and `before` (upper bound on `dateCreated`, pairing with the existing `since` lower bound to form a range).
- A **"View all" link** in the existing bell dropdown navigates to the inbox page.
- The popup dropdown's own mark-as-unread is **explicitly out of scope** (deferred) — the inbox is the management surface; the popup stays a lean glance view.

No existing behavior changes: the dropdown, polling, badge, and recording pipeline are untouched. All additions are additive query params and new endpoints/files.

## Capabilities

### New Capabilities

_None._ This extends the existing `in-app-notifications` capability rather than introducing a new one.

### Modified Capabilities

- `in-app-notifications`:
  - **Listing notifications** — add optional `type` and `before` (date-range upper bound) filters to `GET /api/custom/notifications`.
  - **Marking notifications read/unread** — add the ability to mark a single owned notification back to unread (`PUT /api/custom/notifications/{id}/unread`).
  - **In-app notification UI** — add a dedicated inbox page (master-detail, type + date-range filters, pagination, read-on-select, mark-unread) alongside the existing bell/dropdown, plus a "View all" link from the dropdown to the page.

## Impact

- **Backend (custom files):** extend `CustomNotificationService.listForUser` (add `type`, `before`), add `markUnread` to the service, add `unread` action to `CustomNotificationController`. All under `org.pih.warehouse.custom.notifications`.
- **Backend (upstream touch, surgical):** one new route line in `grails-app/controllers/org/pih/warehouse/UrlMappings.groovy` (`/api/custom/notifications/$id/unread`).
- **Frontend (custom files):** new `src/js/custom/notifications/pages/NotificationInbox.jsx` and supporting components; extend `useNotifications` hook + `notificationsApi` for the new params/endpoint.
- **Frontend (upstream touch, surgical):** two lines in `src/js/components/Router.jsx` — one `Loadable` import and one `<MainLayoutRoute>` registration (no custom-route slot exists). One "View all" link added to the existing bell dropdown surfaces (`NotificationDropdown.jsx` and `_bell.gsp`).
- **Database:** no schema change. The existing `custom_notification` table and indexes are sufficient; date-range + type filtering is a per-user scan within the existing `user_id` index partition.
- **i18n:** add inbox page strings and the eight `NotificationType` labels to `grails-app/i18n/messages.properties` (+ `_ru`, `_tg`).
- **Rollout scope:** TJK branch first, cherry-pickable to EST.
