## Why

The in-app notification `linkUrl` (DB column `custom_notification.link_url`) is dead weight. No production code path ever writes it: the only creation entry points are `NotificationDispatcherService.notify(users, title, body, type, sendEmail)` and `CustomNotificationService.notifyUsers(users, title, body, type)` — neither accepts a link. The value is therefore always `NULL`, the column is nullable, and the only places that set a real `linkUrl` are test fixtures.

Because the value is always null, the "Open link" button never renders for a real user: both the React modal (`isSameOriginPath(notification.linkUrl)`) and the GSP bell (`isSameOriginPath(n.linkUrl)`) guard on a non-null same-origin path, and `isSameOriginPath(null)` is false. So the feature is a NULL-only column plus unreachable UI duplicated across two render paths.

Wiring it up properly is a real feature (per-domain deep-link construction at ~11 dispatch sites, ~3 of which have no linkable entity, plus route verification for both the GSP full-page and React SPA navigation contexts). That is out of scope and not currently needed. The right move is to remove the dead surface; if deep links are wanted later, they return as their own scoped change with the URL design done deliberately.

## What Changes

- **Remove** the `linkUrl` field, its constraint, and its mapping from the `CustomNotification` domain class.
- **Remove** the `link_url` column from the create-table migration. The table is **not deployed anywhere** (local/test only), so editing the original changeset is correct and cleaner than adding a drop-column changeset — there is no applied checksum to preserve.
- **Remove** the `linkUrl` field from the controller's JSON serialization.
- **Remove** the open-link UI from both render paths:
  - React: the `isSameOriginPath` helper + open-link footer button + `onOpenLink`/`handleOpenLink` wiring in `NotificationModal.jsx` and `NotificationDropdown.jsx`.
  - GSP: the open-link footer button, the `isSameOriginPath` JS, and the `modalOpenLink` onclick in `_bell.gsp`.
- **Remove** the `notifications.modal.openLink` i18n key from `messages.properties`, `messages_ru.properties`, and `messages_tg.properties`.
- **Remove** the `linkUrl` test fixtures/cases in the affected Spock and Jest specs.
- **Update** the `in-app-notifications` capability spec: drop the `link_url` navigation clauses from the "In-app notification UI" requirement scenarios.

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `in-app-notifications`: narrows the "In-app notification UI" requirement — clicking a notification opens its detail modal and (for unread) marks it read, but there is no link-navigation behavior. The notification payload no longer carries a `linkUrl`/`link_url`.

## Impact

- **Backend (custom files):** `CustomNotification.groovy`, `CustomNotificationController.groovy`, `2026-05-22-create-custom-notification.groovy`.
- **Frontend (custom files):** `NotificationModal.jsx`, `NotificationDropdown.jsx`, `_bell.gsp`, and their tests.
- **i18n (upstream root bundle):** removes one key from `messages.properties` (+ `_ru`, `_tg`). This is an upstream-file touch — documented in `design.md`.
- **Database:** none beyond the (undeployed) migration edit. No data migration needed — column was always NULL.
- **External systems:** none.
- **Rollout scope:** TJK/EST feature branch, alongside the rest of the notifications work.
