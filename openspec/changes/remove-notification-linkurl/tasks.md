# Tasks — Remove notification linkUrl

## 1. Backend domain + migration

- [x] **1.1** Remove `String linkUrl` field, the `linkUrl nullable: true, maxSize: 2048` constraint, and the `linkUrl column: 'link_url'` mapping from `CustomNotification.groovy`.
- [x] **1.2** Remove the `column(name: 'link_url', ...)` block from `2026-05-22-create-custom-notification.groovy` (edit the create changeset; table is undeployed).
- [x] **1.3** Remove the `linkUrl : notification.linkUrl,` line from the `data.collect` map in `CustomNotificationController.groovy`.

## 2. Frontend (React)

- [x] **2.1** `NotificationModal.jsx`: remove the `isSameOriginPath` helper, `handleOpenLink`, the open-link footer JSX block, and the `onOpenLink` prop (signature + PropTypes). Modal becomes title/body only.
- [x] **2.2** `NotificationDropdown.jsx`: remove the `isSameOriginPath` helper, `handleOpenLink`, the `onOpenLink` prop passed to the modal, and the `linkUrl` PropTypes entry.

## 3. Frontend (GSP)

- [x] **3.1** `_bell.gsp`: remove the modal-footer / open-link markup, the `modalFooter`/`modalOpenLink` lookups, the `isSameOriginPath` JS, and the link branch in `openModal`.

## 4. i18n

- [x] **4.1** Remove `notifications.modal.openLink` from `messages.properties`, `messages_ru.properties`, and `messages_tg.properties`.

## 5. Tests

- [x] **5.1** `CustomNotificationControllerSpec.groovy`: drop the `linkUrl` fixture and assertion.
- [x] **5.2** `NotificationModal.test.jsx`: drop the `linkUrl` describe block and `onOpenLink` wiring.
- [x] **5.3** `NotificationDropdown.test.jsx`: drop the `linkUrl — modal shows Open link` describe block.

## 6. Verify (design.md § Validation)

- [x] **6.1** Grep checks: no `linkUrl`/`link_url` in source (excluding webpack bundle), no `notifications.modal.openLink`, no `onOpenLink`/`isSameOriginPath`.
- [ ] **6.2** `npm test` (notifications suite) green. (Blocked: pre-existing Babel 7.21.8 vs ^7.22.0 mismatch fails all Jest suites — unrelated to this change.)
- [x] **6.3** `./gradlew -Dtest.single=CustomNotificationControllerSpec test` green.
- [ ] **6.4** Fresh DB: `custom_notification` created without `link_url` (`SHOW COLUMNS`).
- [ ] **6.5** `GET /api/custom/notifications` payload has no `linkUrl` key.

## 7. Docs / spec

- [ ] **7.1** On archive, sync the `in-app-notifications` capability spec (MODIFIED "In-app notification UI") and run `openspec validate --specs`.
