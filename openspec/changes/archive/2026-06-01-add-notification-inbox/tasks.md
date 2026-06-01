# Implementation Tasks — add-notification-inbox

Order follows `design.md` § Migration Plan. Each task is sized for one focused commit. Backend first (it unblocks the frontend), then the page, then wiring, then i18n and tests.

## 0. Apply-time verification

- [x] **0.1** `MainLayoutRoute` prop contract confirmed: `({ path, component })`, both required (`MainLayoutRoute.jsx`). Resolved during proposal.
- [x] **0.2** Filter stack confirmed: list pages use `components/Filter/FilterForm` + a `filterFields` config (`DateFilter` + `FilterSelectField`) + a `use<X>Filters` hook (`invoice/list/InvoiceListFilters.jsx:6-8`, `FilterFields.jsx:1-2`). Resolved during proposal.
- [x] **0.3** (UA1) After wiring, smoke-test that `/openboxes/notification/inbox` resolves on a cold full-page load — see task 5.4.

## 1. Backend — list filters (`type`, `before`)

- [x] **1.1** Extend `CustomNotificationService.listForUser` to accept a `type` (String `NotificationType` name) and `before` (Date) filter:
  - add `eq('notificationType', type)` when `type` is non-null,
  - add `le('dateCreated', before)` when `before` is non-null,
  - keep `order('dateCreated', 'desc')` and existing clauses.
  - Prefer refactoring to an immutable filter/params object over a longer positional signature; keep defaults so existing callers are unaffected.
- [x] **1.2** In `CustomNotificationController.list()`, parse `type` and `before`:
  - `type`: accept only values in `NotificationType.values()*.name()`; otherwise treat as no-match (empty result) — never an unfiltered list.
  - `before`: parse via the existing `parseIsoDate` helper.
- [x] **1.3** Confirm `since` + `before` together produce a closed range and ordering is served by `idx_custom_notification_user_created`.

## 2. Backend — mark unread

- [x] **2.1** Add `markUnread(String notificationId, User user)` to `CustomNotificationService`, mirroring `markRead`: `get` → ownership check (`notification.user?.id != user.id` → return false) → set `isRead = false`, `readAt = null` → `save(flush: true)`.
- [x] **2.2** Add `markUnread(String id)` action to `CustomNotificationController`, mirroring `markRead` (same auth/ownership handling and success/failure responses).
- [x] **2.3** Add the URL mapping under the existing `// in-app-notifications (custom)` block in `grails-app/controllers/org/pih/warehouse/UrlMappings.groovy`:
  `"/api/custom/notifications/$id/unread"(controller: 'customNotification', action: 'markUnread', method: 'PUT')`.

## 3. Frontend — API + data hook

- [x] **3.1** Extend `src/js/custom/notifications/api/notificationsApi.js`: add optional `type`, `since`, `before` params to the list call (omit when unset), and add a `markUnread(id)` → `PUT /api/custom/notifications/{id}/unread`.
- [x] **3.2** Extend `useNotifications` (or add a dedicated `useNotificationInbox` hook under `src/js/custom/notifications/hooks/`) for paginated, filtered fetching: `type`, date range, `limit`/`offset`, selected-row state, read-on-select, and mark-unread. Do not change the existing bell/dropdown polling behavior.

## 4. Frontend — inbox page

- [x] **4.1** Create `src/js/custom/notifications/pages/NotificationInbox.jsx` with a master-detail layout: paginated list (left) + reading pane (right) rendering the selected notification's `title`/`body`; empty/placeholder state before selection; responsive collapse to list → detail on narrow viewports.
- [x] **4.2** Read-on-select: selecting an unread row calls `markRead`; selecting an already-read row does not. Mark-unread control in the detail pane calls `markUnread`. Keep the badge/unread count in sync.
- [x] **4.3** Filter bar via the established stack: a `filterFields` config (type → `components/form-elements/FilterSelectField` with enum-driven, i18n-labelled options; date range → `components/form-elements/DateFilter/DateFilter`) rendered through `components/Filter/FilterForm`, backed by a `useNotificationInboxFilters` hook under `src/js/custom/notifications/hooks/` (modeled on `hooks/list-pages/<x>/use<X>Filters`). Reflect active filters in the URL query string.
- [x] **4.4** Add the inbox path constant under `src/js/custom/notifications/` (not in upstream `applicationUrls.js`). Add page styles under `src/js/custom/notifications/styles/`.

## 5. Wiring — route + "View all"

- [x] **5.1** Register the route in `src/js/components/Router.jsx`: one `Loadable` import for `AsyncNotificationInbox` (mirror `AsyncInvoiceList` at line 86) and one `<MainLayoutRoute path="**/notification/inbox" component={AsyncNotificationInbox} />` inside the `<Switch>` (mirror line 272). No other edits to this file.
- [x] **5.2** Add a "View all" control to `src/js/custom/notifications/components/NotificationDropdown.jsx` linking to the inbox path.
- [x] **5.3** Add the equivalent "View all" link to `grails-app/views/custom/notifications/_bell.gsp`.
- [x] **5.4** Verify (UA1): hit `/openboxes/notification/inbox` directly (cold full-page load) and via the dropdown link; confirm the route resolves and renders.

## 6. i18n

- [x] **6.1** Add inbox page strings and the eight `NotificationType` labels to `grails-app/i18n/messages.properties` under the existing `# in-app-notifications (custom)` block.
- [x] **6.2** Add the same keys to `messages_ru.properties` and `messages_tg.properties`.

## 7. Tests

- [x] **7.1** Service tests: `listForUser` with `type` only, `before` only, `since`+`before` range, and `type`+range combined return the expected rows; unknown `type` returns none.
- [x] **7.2** Service tests: `markUnread` sets `is_read=false`/`read_at=null` for an owned row; returns false (no mutation) for a row owned by another user.
- [x] **7.3** Controller test: `PUT …/{id}/unread` success path and 403/404 for non-owned; `list()` passes `type`/`before` through and rejects/empties an invalid `type`.
- [x] **7.4** Frontend tests (`__tests__/`): page renders list + empty detail; selecting an unread row triggers exactly one `markRead` and selecting a read row triggers none; mark-unread triggers `markUnread`; combined filters send `type`+`since`+`before` together.

## 8. Post-change checklist (per CLAUDE.md)

- [x] **8.1** Update the live `openspec/specs/in-app-notifications/spec.md` on archive (handled by `/opsx:archive`).
- [x] **8.2** Confirm upstream touch points match `design.md` (only `Router.jsx` + `UrlMappings.groovy` + i18n `messages.properties`/`_ru`/`_tg`) via `git diff --name-only`. Confirmed — no others.
- [x] **8.3** No committed bundles under `grails-app/assets/` or `src/main/webapp/webpack`. Confirmed clean.
- [x] **8.4** PR description / README — no `notification/inbox` README docs exist; behavior captured in the spec + design revisions. No open PR doc to update at archive time.
