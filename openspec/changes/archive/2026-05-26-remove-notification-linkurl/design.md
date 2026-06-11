# Design — Remove notification linkUrl

## Context

`linkUrl` was added with the original notification UI as a forward-looking deep-link slot, but the decouple-from-email rework never gave any caller a way to populate it. This change removes the dead surface.

## Evidence: linkUrl is never written in production

The two creation entry points take no link argument:

- `grails-app/services/org/pih/warehouse/custom/notifications/CustomNotificationService.groovy:13` — `void notifyUsers(Collection<User> users, String title, String body, NotificationType type = NotificationType.EMAIL_TRIGGER)`
- `grails-app/services/org/pih/warehouse/custom/notifications/NotificationDispatcherService.groovy:21` — `void notify(Collection<User> users, String title, String body, NotificationType type, boolean sendEmail = true)`, which delegates at `:23` to `customNotificationService.notifyUsers(users, title, body, type)`.

A repo-wide grep for `linkUrl` assignment found writes **only** in test fixtures (`CustomNotificationControllerSpec.groovy:40`, `NotificationModal.test.jsx`, `NotificationDropdown.test.jsx`). Every non-test reference only *reads* the value.

Both UI guards therefore never fire, because the value is always null:

- React: `NotificationModal.jsx:7` / `NotificationDropdown.jsx:10` — `const isSameOriginPath = (url) => typeof url === 'string' && url.startsWith('/') && !url.startsWith('//');`
- GSP: `_bell.gsp` `isSameOriginPath` — `typeof url === 'string' && url.indexOf('/') === 0 && url.indexOf('//') !== 0`.

`isSameOriginPath(null)` → `typeof null === 'string'` is `false` → button hidden.

## What gets removed (with receipts)

### Backend

- `grails-app/domain/org/pih/warehouse/custom/notifications/CustomNotification.groovy`
  - field `String linkUrl` (line 12)
  - constraint `linkUrl nullable: true, maxSize: 2048` (line 22)
  - mapping `linkUrl column: 'link_url'` (line 37)
- `grails-app/controllers/org/pih/warehouse/custom/notifications/CustomNotificationController.groovy:49` — the `linkUrl : notification.linkUrl,` line in the `data.collect { ... }` map.
- `grails-app/migrations/custom/2026-05-22-create-custom-notification.groovy:22-24` — the `column(name: 'link_url', type: 'varchar(2048)') { constraints(nullable: true) }` block.

### Frontend (React)

- `NotificationModal.jsx`: the `isSameOriginPath` helper (line 7), the `handleOpenLink` handler (lines 41-44), the open-link footer block (lines 93-103, the `{isSameOriginPath(notification.linkUrl) && (...)}` JSX), the `onOpenLink` prop in the signature and PropTypes (`:116`).
- `NotificationDropdown.jsx`: the `isSameOriginPath` helper (line 10), `handleOpenLink` (lines 46-50), the `onOpenLink={handleOpenLink}` prop passed to the modal (`:137`), and the `linkUrl` PropTypes entry (`:150`).

### Frontend (GSP)

- `_bell.gsp`: the `custom-notification-bell__modal-footer` / `__modal-open-link` markup (lines ~67-71), the `modalFooter`/`modalOpenLink` element lookups (lines ~166-167), the `isSameOriginPath` JS function, and the `if (isSameOriginPath(n.linkUrl)) { ... } else { ... }` block in `openModal` (lines ~207-213).

### i18n (upstream root bundle — touch point)

- `grails-app/i18n/messages.properties:4977` — `notifications.modal.openLink=Open link`
- `grails-app/i18n/messages_ru.properties:4935` and `grails-app/i18n/messages_tg.properties:4935` — same key.

### Tests

- `CustomNotificationControllerSpec.groovy` — drop the `linkUrl` fixture (`:40`) and assertion (`:61`).
- `NotificationModal.test.jsx` — drop the `linkUrl` describe block and `onOpenLink` mock wiring (lines ~15, 99-123).
- `NotificationDropdown.test.jsx` — drop the `linkUrl — modal shows Open link` describe block (lines ~161-172).

## Approach decisions

- **Edit the create migration, do not add a drop-column changeset.** The user confirmed `custom_notification` is deployed only on local/test, never to a shared/production DB. With no applied checksum to preserve, editing the original changeset keeps the migration history clean. (If this assumption were wrong, the correct move would be an additive drop-column changeset under `migrations/custom/` to avoid a Liquibase checksum mismatch on already-migrated databases.)
- **Remove `onOpenLink` from the modal contract entirely** rather than leaving a dead prop. The modal becomes a pure title/body viewer.
- **The detail modal stays.** Only the *link* affordance is removed; clicking a row still opens the modal and (for unread) marks it read.

## Upstream touch points

| File | Reason |
|---|---|
| `grails-app/i18n/messages.properties` (+ `_ru`, `_tg`) | Remove the `notifications.modal.openLink` key. Custom i18n keys must live in the root bundle (Grails 3.3 `messageSource` globs `messages*.properties` at root only), so removing them is an upstream-file edit. |

All other edited files are under `org.pih.warehouse.custom.notifications` / `src/js/custom/notifications` / `grails-app/views/custom/notifications` / `grails-app/migrations/custom` — custom-owned, not upstream.

## Validation

- [ ] `grep -rn "linkUrl\|link_url" grails-app/ src/js/custom/notifications/ src/test/groovy/org/pih/warehouse/custom/notifications/` returns **no matches** (excluding the committed `src/main/webapp/webpack/bundle.*` artifact, which is regenerated on build and is a separate gitignore issue).
- [ ] `grep -rn "notifications.modal.openLink" grails-app/i18n/` returns no matches.
- [ ] `grep -rn "onOpenLink\|isSameOriginPath" src/js/custom/notifications/` returns no matches.
- [ ] The create migration `2026-05-22-create-custom-notification.groovy` has no `link_url` column; a fresh `./gradlew bootRun` against an empty DB creates `custom_notification` without that column (verify with `SHOW COLUMNS FROM custom_notification;` — `link_url` absent).
- [ ] `npm test` passes for the notifications suite (`src/js/custom/notifications/__tests__/`).
- [ ] `./gradlew -Dtest.single=CustomNotificationControllerSpec test` passes.
- [ ] The controller JSON response for `GET /api/custom/notifications` contains keys `{id, type, title, body, read, createdAt}` and **no** `linkUrl` — confirm against `CustomNotificationController.groovy` `data.collect` map after edit.
- [ ] `openspec validate --specs` reports `in-app-notifications` valid after archive/sync.

## Unverified Assumptions

- **`custom_notification` is not deployed beyond local/test.** Stated by the user this session. If false, the migration edit must instead become an additive drop-column changeset. (Project knowledge, not independently verifiable from the repo.)
- The committed `src/main/webapp/webpack/bundle.*.js` still references `linkUrl`; it is a build artifact and out of scope — it regenerates on the next `npm run bundle`. (Observed in grep output this session.)

## Confidence: 8/10

All code/line references were read from current source this session, the "never written" claim is backed by both method signatures and a repo-wide grep, and the removal is subtractive within already-isolated custom files. The one external dependency — that the table is undeployed — is a user-stated project fact rather than a repo-verifiable artifact, but it only changes *how* the migration is edited, not *whether* the change is safe. No third-party API or framework-semantic claims are involved.
