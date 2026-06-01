## Context

The `in-app-notifications` capability already ships a bell + dropdown + detail modal backed by `custom_notification` and a small REST surface (list / mark-read / mark-all-read / unread-count). The dropdown is a glance surface; this change adds a **management** surface — a dedicated inbox page — plus the two query filters and the one endpoint it needs. The dominant constraint, as always on this fork, is upstream isolation: keep the upstream-file footprint minimal and documented.

Three facts from the live codebase shape the design (all verified, receipts in Validation):

- **The React app already has a route table.** `src/js/components/Router.jsx` wraps a `<Switch>` of `<MainLayoutRoute path="**/…" component={Async…} />` entries (lines 251–297), each page lazy-loaded via `Loadable` (e.g. `AsyncInvoiceList` at line 86, routed at line 272). `MainLayoutRoute` is imported from `components/Layout/v2/MainLayoutRoute` (line 18). There is **no** custom-route extension slot — adding a route means editing this upstream file.
- **The list query already paginates and filters.** `CustomNotificationService.listForUser(user, unreadOnly, limit, offset, since, updatedSince)` builds a criteria query with `eq('user')`, optional `eq('isRead', false)`, optional `ge('dateCreated', since)`, ordered `dateCreated desc`. Adding `type` and `before` is two more optional clauses. The controller's `list()` already parses `limit`/`offset`/`unreadOnly`/`since` and clamps the limit to 1–100.
- **The supporting index already exists.** `idx_custom_notification_user_created` on `(user_id, date_created)` (migration lines 47–50) backs a date-range filter + `date_created desc` ordering scoped to one user. No schema change is needed.

## Goals / Non-Goals

**Goals:**

- A React inbox page at `**/notification/inbox` with a master-detail layout (list + reading pane).
- Filter the list by `NotificationType` and by a `dateCreated` range.
- Read-on-select (reusing the dropdown's existing semantics) and a mark-unread action in the detail pane.
- One new endpoint (`PUT /api/custom/notifications/{id}/unread`) and two new optional list params (`type`, `before`).
- Keep the upstream-file footprint to two surgical edits: the route registration in `Router.jsx` and one mapping line in `UrlMappings.groovy`.
- All new code under `org.pih.warehouse.custom.notifications` (backend) and `src/js/custom/notifications/` (frontend).

**Non-Goals:**

- **Mark-unread in the bell dropdown.** Deferred — the popup stays a lean glance surface; the inbox is the management surface. The endpoint exists either way, so surfacing it in the popup later is trivial.
- **Full-text search over title/body.** Deferred. With type + date-range + unread filters and pagination over a bounded per-user list, search adds cost (un-indexable `LIKE` on the `body` TEXT column, debounce, empty states) for little gain. Easy to add later as one more clause.
- **Saved filters / per-user inbox preferences.** Out of scope.
- **Schema changes.** None — existing table and indexes suffice.
- **Changing the dropdown, polling, badge, or recording pipeline.** Untouched.

## Decisions

### D1. Inbox is a React route, not a GSP page

**Choice:** Register `**/notification/inbox` in `src/js/components/Router.jsx` as a `Loadable` page following the existing pattern, and put the page itself under `src/js/custom/notifications/pages/NotificationInbox.jsx`.

**Rationale:** ~40 pages already use this pattern (Router.jsx:251–297). React is the team's stated frontend direction, and the inbox reuses existing React notification components and the `useNotifications` hook. The GSP alternative would force reimplementing the master-detail UI in vanilla JS (as `_bell.gsp` did for the bell), duplicating logic and diverging from the React direction. The cost of the React path is a two-line edit to an upstream file (`Router.jsx`), which is smaller and lower-risk than a parallel vanilla-JS page.

**Two-line upstream edit** (mirrors `AsyncInvoiceList` at Router.jsx:86 + its route at line 272):
```jsx
const AsyncNotificationInbox = Loadable({
  loader: () => import('custom/notifications/pages/NotificationInbox'),
  loading: Loading,
});
// …inside <Switch>:
<MainLayoutRoute path="**/notification/inbox" component={AsyncNotificationInbox} />
```

The `**/` prefix is the established convention for every route in the file; it matches under the `/openboxes` context path without a `BrowserRouter` `basename` (the `<BrowserRouter>` at Router.jsx:251 has none). A full-page navigation from a GSP page to `/openboxes/notification/inbox` therefore boots the React app and resolves the route the same way a direct hit on `**/invoice/list` does.

### D2. Backend list gains `type` and `before` filters

**Choice:** Extend `listForUser` to accept `type` (a `NotificationType` name → `eq('notificationType', type)`) and `before` (→ `le('dateCreated', before)`), and parse both in the controller's `list()` alongside the existing params.

**Rationale:** Two more optional criteria clauses on the same query. `before` pairs with the existing `since` (`ge('dateCreated', …)`) to form a closed range. Filtering + ordering on `date_created` within a user is served by `idx_custom_notification_user_created`. `type` is validated against `NotificationType.values()*.name()` before being applied; an unrecognized value yields an empty result (or 400) rather than an unfiltered list — never another type's or user's rows.

To keep the criteria readable and avoid threading a long positional argument list, `listForUser` may take a small immutable filter map/params object; either way the change is additive and the existing call sites keep working with defaults.

### D3. Mark-unread endpoint, symmetric to mark-read

**Choice:** Add `markUnread(String id)` to the controller and `markUnread(notificationId, user)` to the service, mapped at `PUT /api/custom/notifications/$id/unread`. It sets `isRead = false`, clears `readAt`, and is scoped to the owning user — the mirror image of the existing `markRead`.

**Rationale:** `markRead` already establishes the ownership-checked single-row update pattern (`get` → verify `user.id` → mutate → save). `markUnread` is the same code with inverted state. No `SecurityInterceptor` change is needed: the `customNotification` controller is already in `controllersWithLocationNotRequired` (SecurityInterceptor.groovy:21), so all its actions — including the new one — are reachable before a warehouse is chosen.

### D4. Master-detail layout, read-on-select reuses dropdown semantics

**Choice:** The page renders a left list and a right reading pane. Selecting a row sets it active and renders its `title`/`body` on the right. Selecting an **unread** row fires `PUT …/{id}/read` (exactly the dropdown's "click an unread notification" behavior); selecting an already-read row does not re-call read. The detail pane shows a "Mark as unread" control that fires `PUT …/{id}/unread`. On narrow viewports the layout collapses to list → detail → back.

**Rationale:** Reuses the read-on-open contract already specified for the dropdown/modal, so behavior is consistent across surfaces. The unread toggle lives in the detail pane (where there is room and where the user is deciding "deal with later"), not in the list rows.

### D5. Filters reuse the established list-page filter stack (`FilterForm` + `filterFields` + filter hook)

**Choice:** Build the filter bar the way every other list page does: a `filterFields` config object whose entries use the shared `components/form-elements/DateFilter/DateFilter` (date range) and `components/form-elements/FilterSelectField` (type select), rendered through `components/Filter/FilterForm`, with a `useNotificationInboxFilters` hook under `src/js/custom/notifications/hooks/` modeled on `hooks/list-pages/<x>/use<X>Filters`. The type select's options are the `NotificationType` enum values, each labelled by an i18n key.

**Rationale:** This is the verified, repository-wide list-filter pattern — `invoice/list/InvoiceListFilters.jsx:6-8` composes `FilterForm` + `filterFields` (`FilterFields.jsx:1-2` imports `DateFilter` and `FilterSelectField`) + `useInvoiceFilters`. Reusing it keeps the inbox visually and behaviorally consistent with other list pages, sidesteps the project's multiple raw datepicker deps, and removes any "which form library" ambiguity (the choice is `FilterForm`, not a bare `react-final-form`/`react-hook-form` decision). The type options are enum-driven, so a future `NotificationType` only needs a new label key. The `since`/`before` ISO timestamps and `type` name are sent as query params and reflected in the URL so a filtered inbox is shareable/bookmarkable.

### D6. Inbox URL constant lives in the custom folder

**Choice:** Define the inbox route/path constant under `src/js/custom/notifications/` (not in the upstream `src/js/consts/applicationUrls.js`), and have both the React dropdown ("View all") and the GSP `_bell.gsp` link to it.

**Rationale:** Keeps the only upstream JS edit confined to the single route registration in `Router.jsx` (D1). `applicationUrls.js` is upstream; adding a constant there would be an extra, avoidable touch point. The "View all" control is added to the already-custom `NotificationDropdown.jsx` and `_bell.gsp`, so those edits are inside files we own.

## Upstream Touch Points

| File | Edit | Reason |
|---|---|---|
| `src/js/components/Router.jsx` | One `Loadable` import for `AsyncNotificationInbox` (mirroring line 86) and one `<MainLayoutRoute path="**/notification/inbox" component={AsyncNotificationInbox} />` inside the `<Switch>` (mirroring line 272) | No custom-route slot exists; this is the route table. See D1. |
| `grails-app/controllers/org/pih/warehouse/UrlMappings.groovy` | One mapping line `"/api/custom/notifications/$id/unread"(controller: 'customNotification', action: 'markUnread', method: 'PUT')` under the existing `// in-app-notifications (custom)` block (currently lines 1123–1127) | Route for the new unread endpoint. See D3. |
| `grails-app/i18n/messages.properties` (+ `_ru`, `_tg`) | Append inbox page strings and eight `NotificationType` labels under the existing `# in-app-notifications (custom)` block (per `rules/custom-package-isolation.md` § i18n exception) | UI strings; the root bundle is the only one Grails 3.3 loads. |

No other upstream files change. `NotificationDropdown.jsx`, `NotificationModal.jsx`, `useNotifications.js`, `notificationsApi.js`, `_bell.gsp`, the service, controller, and the new page are all under `custom/` (or are the custom controller/service we own).

## Risks / Trade-offs

- **[Route registration in upstream `Router.jsx` = merge-conflict surface.]** → Mitigation: keep it to the two canonical lines, document here, rely on `git rerere` and the OpenSpec archive as the merge hitlist.
- **[`listForUser` positional-arg growth.]** Adding `type`/`before` to a positional signature is brittle. → Mitigation: introduce a small immutable filter object/params map so future filters don't reshuffle positions; keep defaults so existing callers are untouched.
- **[Date-range on `date_created` while the unread-poll index leads with `is_read`.]** The range query uses `idx_custom_notification_user_created` (user_id, date_created), which is the correct index for this access path; the unread-only inbox filter could prefer `idx_custom_notification_user_unread`. → Mitigation: per-user row counts are small; MySQL picks the better of the two existing indexes. No new index needed.
- **[Two `react-datepicker` versions in `package.json`.]** Ambiguity if a new datepicker were introduced. → Mitigation: D5 reuses the shared `DateFilter` element instead of importing a datepicker directly, sidestepping the choice.
- **[Full-page load into the SPA from a GSP page.]** A "View all" link from the GSP bell triggers a full reload into the React app. → Mitigation: this is exactly how every existing `**/…` route is reached from server-rendered pages; the `**/` prefix + no `basename` handles the context path.

## Migration Plan

1. **Backend filters:** extend `listForUser` (`type`, `before`) and `list()` param parsing; validate `type` against the enum.
2. **Backend unread:** add `markUnread` service method + controller action; add the one `UrlMappings.groovy` line.
3. **Frontend API/hook:** extend `notificationsApi` for `type`/`before` params and the unread call; extend `useNotifications` (or add an inbox-specific hook under `custom/notifications/hooks/`) for paginated, filtered fetching.
4. **Frontend page:** build `NotificationInbox.jsx` (master-detail, filter bar using `DateFilter` + type select, pagination, read-on-select, mark-unread).
5. **Route + links:** register the route in `Router.jsx`; add "View all" to `NotificationDropdown.jsx` and `_bell.gsp`.
6. **i18n:** add page strings + eight type labels to `messages.properties` (+ `_ru`, `_tg`).
7. **Tests:** service (type/before/unread), controller (params + unread + ownership), frontend (page render, filter fetch, read-on-select, mark-unread).

**Rollback:** revert the two upstream lines (`Router.jsx`, `UrlMappings.groovy`) and the i18n additions; delete the new custom files; remove the added service/controller methods. No data or schema is touched.

## Validation

Each criterion is verifiable from an artifact outside this design — a file:line in the repo, captured grep output, or a runnable test.

- [ ] **V1. The route-table pattern this design copies exists and has the cited shape.** Receipt: `Router.jsx:18` imports `MainLayoutRoute` from `components/Layout/v2/MainLayoutRoute`; `Router.jsx:86` defines `AsyncInvoiceList` via `Loadable({ loader: () => import(...), loading: Loading })`; `Router.jsx:272` routes it via `<MainLayoutRoute path="**/invoice/list" component={AsyncInvoiceList} />`. Post-implementation: the diff adds exactly one analogous `Loadable` const and one `<MainLayoutRoute path="**/notification/inbox" …>` line, nothing else in this file.
- [ ] **V2. `<BrowserRouter>` has no `basename`, so `**/` routes resolve under the context path.** Receipt: `grep -n "BrowserRouter\|basename" Router.jsx` returns the import (line 7) and `<BrowserRouter>` (line 251) with no `basename` prop; ~40 sibling routes use the `**/` prefix. The new route uses the same prefix.
- [ ] **V3. `listForUser`'s current signature and criteria are as described.** Receipt: `CustomNotificationService.groovy` `listForUser(User, Boolean, Integer, Integer, Date, Date)` builds `createCriteria().list(max, offset){ eq('user', user); if(unreadOnly) eq('isRead', false); if(since) ge('dateCreated', since); if(updatedSince) ge('lastUpdated', updatedSince); order('dateCreated','desc') }`. Post-implementation: the same method adds `eq('notificationType', type)` when a type is given and `le('dateCreated', before)` when `before` is given.
- [ ] **V4. The date-range index exists.** Receipt: migration `2026-05-22-create-custom-notification.groovy:47-50` creates `idx_custom_notification_user_created` on `(user_id, date_created)`. No new `createIndex` is added by this change.
- [ ] **V5. `markRead` establishes the ownership-checked single-row update pattern that `markUnread` mirrors.** Receipt: `CustomNotificationService.markRead` does `get(id)` → `if (!notification || notification.user?.id != user.id) return false` → set `isRead=true`, `readAt=new Date()` → `save`. Post-implementation: `markUnread` is the same with `isRead=false`, `readAt=null`.
- [ ] **V6. No `SecurityInterceptor` change is needed for the new action.** Receipt: `SecurityInterceptor.groovy:21` already contains `'customNotification'` in `controllersWithLocationNotRequired`. Post-implementation diff of that file is empty.
- [ ] **V7. The new endpoint is the only new line in `UrlMappings.groovy`.** Receipt: the existing block at `UrlMappings.groovy:1123-1127` declares the four current notification routes. Post-implementation: exactly one added line `"/api/custom/notifications/$id/unread"(… action: 'markUnread', method: 'PUT')`.
- [ ] **V8. The filter bar reuses the shared list-page filter stack.** Receipt: `invoice/list/InvoiceListFilters.jsx:6-8` composes `FilterForm` (`components/Filter/FilterForm`) + `filterFields` + `useInvoiceFilters`; `invoice/list/FilterFields.jsx:1-2` imports `DateFilter` (`components/form-elements/DateFilter/DateFilter`) and `FilterSelectField` (`components/form-elements/FilterSelectField`). Post-implementation: the inbox filter bar imports the same `FilterForm`/`DateFilter`/`FilterSelectField`, not a raw `react-datepicker` or an ad-hoc form.
- [ ] **V16. `MainLayoutRoute`'s prop contract is exactly `{ path, component }`.** Receipt: `components/Layout/v2/MainLayoutRoute.jsx` destructures `({ path, component: Component })` and declares `propTypes` `path: string.isRequired`, `component: oneOfType([string, func]).isRequired`. The new route passes exactly these two props.
- [ ] **V9. Type options are enum-driven.** Receipt: `NotificationType.groovy` enumerates 8 values (SHIPMENT, REQUISITION, FULFILLMENT, STOCK_ALERT, USER_ACCOUNT, SYSTEM, PRODUCT, EMAIL_TRIGGER). Post-implementation: each type filter option maps to one of these names and the API rejects/empties any other `type` value (Spock test asserts an unknown `type` returns no rows).
- [ ] **V10. All new frontend files live under `src/js/custom/notifications/`.** Verifiable: `git diff --name-only <base>..HEAD | grep '\.jsx\?$\|\.scss$'` shows every new path under `src/js/custom/notifications/`, except the single `Router.jsx` route registration.
- [ ] **V11. All new backend code lives under the custom package.** Verifiable: new/changed `.groovy` (non-test) files are under `org/pih/warehouse/custom/notifications/`, except the single `UrlMappings.groovy` line.
- [ ] **V12. The inbox URL constant is not added to upstream `applicationUrls.js`.** Receipt: `applicationUrls.js` defines URL objects (e.g. `DASHBOARD_URL` line 8). Post-implementation: `git diff applicationUrls.js` is empty; the inbox path constant is under `src/js/custom/notifications/`.
- [ ] **V13. Read-on-select calls read only for unread rows.** Verifiable: a Jest test on `NotificationInbox` asserts selecting an unread row triggers `PUT …/{id}/read` exactly once and selecting an already-read row triggers zero read calls.
- [ ] **V14. Mark-unread round-trips.** Verifiable: a Spock controller/service test asserts `PUT …/{id}/unread` on an owned read row sets `is_read=false`, `read_at=null`, and a 403/404 for a row owned by another user.
- [ ] **V15. Combined filters compose in the query string.** Verifiable: a Jest test asserts that with a type and a date range selected, the API call includes `type`, `since`, and `before` together.

## Unverified Assumptions

- **UA1. A brand-new `**/notification/inbox` route resolves on a fresh full-page load from a GSP page.** Every cross-file *contract* this depends on is now source-confirmed: the route-table shape (Router.jsx:18,86,272), the `basename`-less `<BrowserRouter>` (Router.jsx:251), and `MainLayoutRoute` rendering a plain `<Route path render>` (MainLayoutRoute.jsx). What remains unexecuted is only the runtime boot of the SPA on a cold load at the new path — a low-stakes smoke check (task 5.4), not an unknown API or contract. Risk: low; identical to every existing route.

_(UA2 and UA3 from the prior draft are resolved — see V16 (`MainLayoutRoute` prop contract) and V8 (the `FilterForm`/`DateFilter`/`FilterSelectField` filter stack). Both were closed by direct source reads, not pattern-matching.)_

## Confidence: 8/10

**Rationale:** Every external/cross-file claim now has a file:line receipt from a current source read — the route-table pattern (Router.jsx:18,86,272), `MainLayoutRoute`'s exact `{ path, component }` contract (MainLayoutRoute.jsx, V16), the `basename`-less `<BrowserRouter>` (Router.jsx:251), the query layer and its signature (`CustomNotificationService.listForUser`), the supporting `(user_id, date_created)` index (migration:47-50), the `markRead` pattern `markUnread` mirrors, the already-present security exemption (SecurityInterceptor.groovy:21), the existing mapping block (UrlMappings.groovy:1123-1127), and the established list-filter stack `FilterForm` + `DateFilter` + `FilterSelectField` + filter hook (InvoiceListFilters.jsx:6-8, FilterFields.jsx:1-2, V8). The earlier two pattern-matched assumptions (UA2, UA3) are closed by source reads, so the propose discipline's hard cap no longer applies. Backend work is low-risk (additive query clauses + a mirrored endpoint, no schema change). The sole residual (UA1) is a one-time runtime smoke check of the new route booting on a cold load — a verification step, not a design unknown. Not a 9–10 only because that single end-to-end path hasn't been executed in a running app. Implementation-ready.

## Post-Implementation Revisions (2026-06-01)

Changes made during implementation/review, beyond the original design above:

1. **`listForUser`/`countForUser` signature → `Map params`.** Adopted the immutable params-map mitigation noted under Risks instead of growing positional args. Added a `countForUser` for pagination totals.
2. **Read/unread status filter.** Added a tri-state `read` query param (`read=false`→unread, `read=true`→read) that takes precedence over the legacy `unreadOnly` flag, exposed as a "Status" filter on the inbox.
3. **Server-side pagination instead of "load more".** The inbox list uses the shared `components/DataTable/TablePagination` (page input + rows-per-page select), driven by a new `totalCount` field added to the list response. The dropdown keeps its "load more".
4. **Date filters converted to UTC day boundaries.** The shared `DateFilter` runs the picker in UTC (`utcOffset={0}`); a `custom/notifications/utils/dateFilters.js` helper converts the `MM/DD/YYYY` value to start-of-day (`since`) / end-of-day (`before`) UTC ISO so the range matches the picked calendar day. The backend already parses ISO-8601 (`yyyy-MM-dd'T'HH:mm:ss.SSSX`).
5. **Mark-all-as-read on the inbox.** Added an icon button in the inbox header (shown only when `unreadCount > 0`) that calls `read-all` then re-fetches. The detail-pane "mark as unread" is an icon button (`RiMailLine`); the dropdown/inbox actions use `RiMailOpenLine`/`RiInboxLine`.
6. **Post-login redirect fix.** All notification API calls send `X-Requested-With: XMLHttpRequest` so an expired-session background poll returns 401 instead of being saved by Spring Security as the post-login redirect target (which landed users on the JSON endpoint).
7. **i18n namespacing — `react.notification.*` (single source of truth).** The React localize store only loads `react.*` keys (`fetchTranslations` requests `prefix=react.notification`, and `notification` is already in `MainRouter`'s `TRANSLATION_PREFIXES`). The original `notifications.*` keys never reached the React store, so labels rendered as raw keys. Resolution: all notification strings live under `react.notification.*`; the custom GSP bell (`_bell.gsp`) resolves the same keys server-side via `message(code:)`, so the bell dropdown shares one key + one translation across both surfaces. The plain `notifications.*` block was removed from all three bundles; ru/tg translations were relocated 1:1 (the new `read.read` "Read" value in `_ru`/`_tg` was derived as the parallel of the existing "unread" and is flagged for native-speaker/Crowdin confirmation). Type/status option labels fall back to a `humanize()` of the enum when a key is missing.
8. **No new upstream touch points beyond the design's list** (`Router.jsx`, `UrlMappings.groovy`, `messages.properties` + `_ru`/`_tg`). The `_bell.gsp` key swap is inside a custom file.

**Deploy status:** implemented on `feature/in-app-notifications`. Not yet replayed onto customer branches. Not submitted upstream.
