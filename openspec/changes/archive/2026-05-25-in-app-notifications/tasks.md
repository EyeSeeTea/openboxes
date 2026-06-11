# Implementation Tasks — in-app-notifications

Implementation order follows the `Migration Plan` in `design.md`. Each task is sized for one focused commit.

## 0. Pre-implementation verification (resolved 2026-05-22)

All Unverified Assumptions from the initial draft were resolved against direct source reads before implementation began. Receipts captured in `design.md` § Validation and § Verified Assumptions. Summary of the relevant findings (so implementation can move directly to step 1):

- [x] **0.1** `Person.email` is **not unique** (`Person.groovy:35-41` — no `unique:` constraint). Service uses `User.findAllByEmail(...)` and iterates; one notification per matching `User`.
- [x] **0.2** `doSendMail` has a single success exit: `email.send()` at `MailService.groovy:202` followed by `return true` at line 203. Hook is inserted between these two lines.
- [x] **0.3** `UrlMappings.groovy` lives at `grails-app/controllers/org/pih/warehouse/UrlMappings.groovy` (NOT `grails-app/conf/`). No `/api/**` wildcard exists — three explicit mapping lines are required.
- [x] **0.4** `Header.jsx:42-45` contains `<ul className="navbar-nav w-100"><Menu /><NavbarIcons /></ul>`. The bell inserts between `<Menu />` and `<NavbarIcons />` as a third sibling inside the `<ul>`.
- [x] **0.5** `react-s-alert` (the existing `notification.jsx`) does not register a Redux slice (grep of `src/js/reducers/`, `src/js/store/`, `src/js/App.jsx` returned 0 hits). Our Redux slice / hook state cannot collide.

## 1. Schema

- [x] **1.1** Create `grails-app/migrations/custom/2026-05-22-create-custom-notification.groovy`:
  - `createTable(tableName: 'custom_notification', ...)` with columns per `design.md` D2.
  - `addForeignKeyConstraint` from `user_id` → `user(id)`.
  - `createIndex` on `(user_id, is_read, date_created)`.
  - `createIndex` on `(user_id, date_created)`.
  - `rollback { dropTable(tableName: 'custom_notification') }`.
  - `author: 'eyeseetea'`, id `2026-05-22-01-create-custom-notification`.
- [x] **1.2** Append `include file: '2026-05-22-create-custom-notification.groovy'` to `grails-app/migrations/custom/changelog.groovy`.
- [ ] **1.3** Run `./gradlew bootRun` (or apply migrations via standard mechanism) against a dev DB; confirm the table and indexes exist.

## 2. Backend domain + service

- [x] **2.1** Create `grails-app/domain/org/pih/warehouse/custom/notifications/CustomNotification.groovy`:
  - Properties matching the table (`user`, `notificationType`, `title`, `body`, `linkUrl`, `isRead`, `readAt`, `dateCreated`, `lastUpdated`).
  - `belongsTo = [user: User]`.
  - `static constraints` with appropriate nullable/maxSize.
  - `static mapping { table 'custom_notification'; id generator: 'uuid' }`.
- [x] **2.2** Create `grails-app/services/org/pih/warehouse/custom/notifications/CustomNotificationService.groovy`:
  - `recordSendAsNotifications(Collection<String> recipientEmails, String subject)`:
    - For each email in the (de-duplicated) recipient list:
      - `List<User> matches = User.findAllByEmail(email)`.
      - If `matches.empty`: `matches = User.findAllByUsername(email)` (legacy fallback per `User.groovy:20`).
      - For each `User` in `matches`: create a `CustomNotification` row with `user`, `notificationType = 'EMAIL_PIGGYBACK'`, `title = subject`, `body = null`, `linkUrl = null`, `isRead = false`.
    - Catch all exceptions and log via `log.error`; never rethrow (the hook in `MailService` is wrapped in a try/catch too, this is belt-and-suspenders).
  - `listForUser(User user, Boolean unreadOnly, Integer limit)` — returns at most `limit` (default 20) rows owned by `user`, newest first. Uses the `(user_id, is_read, date_created)` index when `unreadOnly = true`.
  - `markRead(String notificationId, User user)` — sets `isRead = true, readAt = now()` only if the row is owned by `user`. Returns `true` on success, `false` if the row doesn't exist or isn't owned by `user`. Does NOT throw — the controller decides the HTTP status from the return value.
  - `markAllRead(User user)` — bulk update via HQL `executeUpdate("update CustomNotification n set n.isRead = true, n.readAt = :now where n.user = :user and n.isRead = false", [now: new Date(), user: user])`. Returns the number of rows updated.
  - `countUnread(User user)` — `CustomNotification.countByUserAndIsRead(user, false)`.
- [x] **2.3** Unit tests (Spock) under `src/test/groovy/org/pih/warehouse/custom/notifications/CustomNotificationServiceSpec.groovy`:
  - V15 — multi-recipient with mixed matches: 3 recipients, 2 known (each matched by `Person.email`), 1 external → exactly 2 rows created with distinct `user_id`.
  - V16 — non-unique email: 1 recipient whose email matches 2 `User` rows → 2 notifications created, both referencing distinct users.
  - Username-fallback case: `Person.email` returns empty, `User.username` returns 1 → 1 notification created.
  - Cross-user mark-read attempt → returns `false`; row unchanged.
  - `countUnread` after `markAllRead` → returns 0.

## 3. Backend controller + URL mapping

- [x] **3.1** Create `grails-app/controllers/org/pih/warehouse/custom/notifications/CustomNotificationController.groovy`:
  - `def list()` → GET `/api/custom/notifications?unreadOnly=&limit=` returning JSON list.
  - `def markRead(String id)` → PUT `/api/custom/notifications/:id/read`.
  - `def markAllRead()` → PUT `/api/custom/notifications/read-all`.
  - Use `springSecurityService.currentUser` for the actor; 401 if absent.
  - Delegate all logic to `customNotificationService`; controller stays thin.
- [x] **3.2** Edit `grails-app/controllers/org/pih/warehouse/UrlMappings.groovy` (file location verified — NOT in `grails-app/conf/`). Append, after the existing `/api/*` block, under a `// in-app-notifications (custom)` comment delimiter:
  ```groovy
  // in-app-notifications (custom)
  "/api/custom/notifications"(controller: 'customNotification', action: 'list', method: 'GET')
  "/api/custom/notifications/read-all"(controller: 'customNotification', action: 'markAllRead', method: 'PUT')
  "/api/custom/notifications/$id/read"(controller: 'customNotification', action: 'markRead', method: 'PUT')
  ```
  No other edits to the file. No reformatting.
- [x] **3.3** Integration test under `src/integration-test/groovy/org/pih/warehouse/custom/notifications/CustomNotificationControllerIntegrationSpec.groovy` covering:
  - List with default params, unread-only, and custom limit.
  - Mark read on owned row.
  - Mark read on someone else's row → 403/404.
  - Unauthenticated request → 401.

## 4. MailService hook (the one upstream backend edit)

- [x] **4.1** Edit `grails-app/services/org/pih/warehouse/core/MailService.groovy`. Exactly two surgical changes:

  **(a)** Add `def customNotificationService` near the existing top-of-class service-injection properties (matches the convention from `rules/groovy/patterns.md` § "Dependency injection is property-based and convention-driven"). No other edits in the property block.

  **(b)** Inside `doSendMail`, between the existing `email.send()` and `return true` (currently lines 202 and 203 — exact line numbers may have shifted by a constant offset due to (a), but the structural location is unambiguous: it's between the only `email.send()` call and its immediately-following `return true` inside the `try` at lines 200–203), insert:
  ```groovy
  try {
      customNotificationService.recordSendAsNotifications(to, subject)
  } catch (Exception ex) {
      log.error("custom_notification_record_failed subject='${subject}'", ex)
  }
  ```
  No reformatting of the surrounding `try { … } catch (Exception e) { … }` block. No reordering of imports. Boy Scout rule is suspended for this file.
- [ ] **4.2** Confirm via Spock test on `MailService` (or by running the user-create flow on a dev instance) that a real send produces a row.
- [ ] **4.3** Confirm an early-return (e.g., `!isMailEnabled` branch at line 154) does NOT create a notification — V3 / scenario "Email send fails".

## 5. Frontend — bell component + API client

- [x] **5.1** Create `src/js/custom/notifications/api/notificationsApi.js` — `getNotifications({unreadOnly, limit})`, `markRead(id)`, `markAllRead()` using the standard `apiClient`.
- [x] **5.2** Create `src/js/custom/notifications/hooks/useNotifications.js` — manages list state, polling (30s interval), mark-read/mark-all-read mutations. Cleanly cancels on unmount.
- [x] **5.3** Create `src/js/custom/notifications/components/NotificationBell.jsx`:
  - Bell icon (use the existing `react-icons/ri` family for visual consistency — see existing `Layout/notifications/notification.jsx` imports).
  - Unread badge with count (hidden when zero).
  - Click toggles dropdown.
- [x] **5.4** Create `src/js/custom/notifications/components/NotificationDropdown.jsx`:
  - Renders the list (newest first).
  - Each row: title, relative timestamp, unread dot.
  - Row click → `markRead(id)`.
  - "Mark all read" action at the top.
  - Empty state when no notifications.
- [x] **5.5** Add SCSS at `src/js/custom/notifications/styles/_bell.scss` (or `.scss` co-located). Bootstrap 4.6 base.

## 6. Layout integration (the one upstream frontend edit)

- [x] **6.1** Edit `src/js/components/Layout/Header.jsx` (82 lines; small, simple file). Exactly two surgical changes:

  **(a)** Add `import NotificationBell from 'custom/notifications/components/NotificationBell';` to the absolute-imports group (existing absolute imports are at lines 7–13). Place it alphabetically — between `Logo` and `Menu`. Match the existing `eslint-plugin-simple-import-sort` order from `rules/web/coding-style.md`.

  **(b)** Inside the `<ul className="navbar-nav w-100">` element (currently at lines 42–45), insert `<NotificationBell />` as a new sibling element between `<Menu />` and `<NavbarIcons />`. The resulting `<ul>` block reads:
  ```jsx
  <ul className="navbar-nav w-100">
    <Menu />
    <NotificationBell />
    <NavbarIcons />
  </ul>
  ```
  **No other edits.** No reformatting, no PropTypes additions, no `mapStateToProps` changes. Boy Scout rule is suspended for this file.

## 7. i18n

- [x] **7.1** Append the feature's keys to the root `grails-app/i18n/messages.properties` under a `# in-app-notifications (custom)` comment block. Keys at minimum:
  - `notifications.bell.title=Notifications`
  - `notifications.bell.empty=No notifications yet`
  - `notifications.bell.markAllRead=Mark all as read`
  - `notifications.bell.unreadCount={0} unread`
- [x] **7.2** Document the touch in `design.md` touch points (already covered in the rules; `messages.properties` is the one upstream file that always gets appended).

## 8. Smoke test + verification gate

- [ ] **8.1** Run `./gradlew test`.
- [ ] **8.2** Run `npm test`.
- [ ] **8.3** Manual smoke: trigger one of the known email flows (account creation is the easiest), confirm a row appears in `custom_notification`, confirm the bell badge increments within 30s, confirm clicking the row clears the badge.
- [ ] **8.4** Walk through `design.md` § Validation V1–V21 and check each off.
- [ ] **8.5** Run `git diff --name-only` against the merge base; confirm only the four upstream files in the touch-points list are changed (`MailService.groovy`, `Header.jsx`, `UrlMappings.groovy`, `messages.properties`), everything else is under `custom/`.

## 9. Documentation + close-out

- [ ] **9.1** Update PR description to link to OpenSpec change + summarize.
- [ ] **9.2** Confirm `design.md` § Upstream Touch Points lists every upstream file actually modified (including the conditional `UrlMappings.groovy` if it was edited).
- [ ] **9.3** Open the PR onto `release/est/tjk/0.9.7`. Note in the description that promotion to EST is a follow-up cherry-pick once stabilized.
