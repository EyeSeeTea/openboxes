## Why

Users only learn about supply-chain events (shipments, requisitions, stock alerts, account changes) through email, which forces them out of the app and is easy to miss in a busy inbox. Surfacing the same events as in-app notifications keeps users in context, gives them a persistent list they can act on, and unblocks a recurring TJK ask without changing how triggers work today.

## What Changes

- New `custom_notification` table holding per-user notification records (title, body, link, read state, type).
- New backend service `CustomNotificationService` (in `org.pih.warehouse.custom.notifications`) that creates and queries notifications.
- New REST controller exposing `GET /api/custom/notifications`, `PUT /api/custom/notifications/:id/read`, `PUT /api/custom/notifications/read-all`.
- Single surgical hook in upstream `MailService.doSendMail` that, after a successful send, resolves each recipient email to a `User` and creates a matching notification. Recipients that don't map to a `User` are skipped.
- React `NotificationBell` component (under `src/js/custom/notifications/`) rendered in the top nav: unread-count badge, dropdown with "Unread" (default) / "All" tabs, per-row mark-as-read with same-origin `link_url` navigation, "mark all read" action.
- Polling-based delivery: frontend polls `GET /api/custom/notifications` every 30s with the active tab's `unreadOnly` flag (no WebSocket). Response envelope `{ data, unreadCount }` lets the badge stay accurate without a second request.
- Liquibase changeset under `grails-app/migrations/custom/` adding the table and indexes.

No existing behavior changes. No existing emails are removed or rewritten — notifications are an additive side-effect of the existing mail send.

## Capabilities

### New Capabilities

- `in-app-notifications`: Per-user persistent notification records, REST API to list/mark-read, and an automatic creation hook that fires whenever the app sends an email to a recipient that matches a known user account.

### Modified Capabilities

_None._ This change does not alter any existing requirements — it's purely additive.

## Impact

- **Backend (new files only):** domain class, service, controller, URL mapping entry, Liquibase changeset under `custom/`.
- **Backend (upstream touch, surgical):** `grails-app/services/org/pih/warehouse/core/MailService.groovy` — a single post-send hook call after `doSendMail` returns `true`. Documented in `design.md` touch points.
- **Frontend (new files only):** `src/js/custom/notifications/` — bell component, dropdown with tabs, hook owning fetch + poll + tab state, API client.
- **Frontend (upstream touch, surgical):** the header/navbar component to slot in `<NotificationBell />`. One import + one tag.
- **Database:** new `custom_notification` table; no schema changes to existing tables.
- **External systems:** none. No new dependencies, no SMTP changes, no auth changes.
- **Rollout scope:** TJK branch first. Designed for clean cherry-pick to EST once stable.
