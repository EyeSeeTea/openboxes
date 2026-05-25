## Context

Today, OpenBoxes only delivers event signals to users via email — there's no persistent in-app surface listing things that need their attention. The TJK customer has asked for an in-app notification center. The dominant constraint is **upstream isolation**: this is a fork, and the more we touch upstream files, the worse our merges get. So the design's goal is to deliver the feature with the smallest possible upstream-edit footprint.

The fortunate fact that makes the design clean: every email currently flows through one method, `MailService.doSendMail` (`grails-app/services/org/pih/warehouse/core/MailService.groovy:139`), which all 14+ public `sendMail*` overloads on the same class delegate to (lines 62, 66, 70, 74, 78, 82, 86, 90, 94, 101, 105, 109, 113, 117, 121). One intercept point catches everything. The success exit is also unambiguous: `doSendMail` has exactly one `return true` at line 203, immediately after `email.send()` at line 202. The hook goes between those two lines.

Second fortunate fact: `User extends Person` via **joined-table inheritance** (`User.groovy:17`, `Person.groovy:27-31` — `tablePerHierarchy false`), so `user` and `person` are separate tables joined by primary key. `Person.email` is the canonical email field (`Person.groovy:21`, validated with `email: true, nullable: true, maxSize: 255` at line 39) and `User.findAllByEmail(...)` is a sound dynamic finder against the inherited property. **It is NOT unique** — the constraints block has no `unique:` clause for `email`, so two `Person`/`User` rows can share an email. Our hook accounts for this by using `findAllByEmail` (returns 0..N users) rather than `findByEmail` (returns 0..1) and creates one notification per matching user.

There is also a legacy wrinkle: `User.groovy:20` comments that `username` holds "email or username", so some pre-2017-style accounts may have their email *only* in `username`. The hook falls back to `findAllByUsername` when the email lookup misses.

The custom migrations aggregator is already wired (`grails-app/migrations/custom/changelog.groovy`) — adding a new changeset is one file plus one `include file:` line. The URL mapping file is `grails-app/controllers/org/pih/warehouse/UrlMappings.groovy` (not the conventional `grails-app/conf/` location), and it uses **explicit per-route declarations** (sample at lines 45–192: `/api/categories`, `/api/users`, `/api/products`, etc.), with no `/api/**` wildcard. Three new mapping lines will be needed.

## Goals / Non-Goals

**Goals:**

- Persist a per-user notification record every time the app sends an email to a recipient that maps to a known `User`.
- Expose a tiny REST surface (list / mark-read / mark-all-read) scoped to the authenticated user.
- Render a bell-icon notification dropdown in the application header with unread badge and 30s polling.
- Keep the upstream-file footprint to exactly two surgical edits: one call in `MailService.doSendMail`, one `<NotificationBell />` insertion in the layout header.
- Place all custom backend code under `org.pih.warehouse.custom.notifications` and all custom frontend code under `src/js/custom/notifications/`, per `rules/custom-package-isolation.md`.
- Designed so the feature can be cherry-picked from TJK up to EST without refactoring.

**Non-Goals:**

- **Per-user opt-in/out preferences.** Defer to a v2.
- **Per-notification-type categorization or filtering.** v2.
- **WebSocket / push delivery.** 30s polling is sufficient for this use case and avoids new infrastructure.
- **Mobile push, browser push, desktop notifications.** Out of scope.
- **Replacing or rewriting any existing email trigger.** Notifications are purely additive — every email that's sent today is still sent.
- **Notifying users without a `User` account** (external vendors, carriers, etc.). They keep receiving email; they just don't get a `custom_notification` row, because there's nowhere persistent to attach it.
- **Backfilling notifications from historical emails.** New emails only.

## Decisions

### D1. Single intercept point at `MailService.doSendMail`

**Choice:** Hook the notification-creation call into `doSendMail` itself, immediately after the email-send succeeds.

**Rationale:** All 14+ `sendMail*` overloads on `MailService` delegate to `doSendMail` (verified: `MailService.groovy:62,66,70,74,78,82,86,90,94,101,105,109,113,117,121`). The agent scan found ~16 distinct notification call-sites across `NotificationService`, shipment, requisition, and user lifecycle code — every one of them ultimately calls one of the public `sendMail*` overloads, so they all funnel through `doSendMail`. One hook covers all of them.

**Alternatives considered:**

- **Hook at each call site (16+ places).** Rejected: massive upstream-edit footprint, easy to miss, every new upstream email trigger silently bypasses notifications.
- **Spring AOP around the `MailService` bean.** Rejected: works, but on Grails 3.3 it requires extra config in `resources.groovy`, debugging aspect proxies is harder than reading one line of code, and the conditional logic ("only fire on `true` return") is more naturally expressed inline.
- **Event-driven via Grails events (`notify('mail:sent', ...)` + listener).** Rejected as initial approach: requires touching all 16 call sites to fire the event *and* a listener service. Strictly worse than a single hook. Could be retrofitted later if the hook proves too coupled.
- **Cron job that polls a "sent mail" log.** Rejected: there is no persisted log of sends, so we'd have to add one first — at which point we've added a side-effect to `doSendMail` anyway.

**The hook itself** is a single call after the existing `return true` (or after the success branch, depending on the flow inside `doSendMail`): `customNotificationService.recordSendAsNotifications(to, subject)`. The custom service handles email→User lookup, swallows lookup failures (returns silently if no user matches), and never throws back into `MailService` so a notification-persistence bug can never break an email send.

### D2. Side table, no upstream schema edits

**Choice:** New `custom_notification` table with FK to `user(id)`. No changes to upstream tables.

**Rationale:** Per `rules/upstream-entity-extension.md` — never inject columns onto upstream tables. The notification record is its own concept anyway (read state, link URL, type), not an extension of `User`. The FK is plain referential, not a 1-to-1 extension, so this isn't even the "side-table extension" pattern — it's just a related table that happens to point at `user`.

**Schema (Liquibase changeset under `grails-app/migrations/custom/`):**

```
custom_notification
  id              varchar(255)  PK (uuid)
  user_id         varchar(255)  FK -> user(id), NOT NULL
  notification_type varchar(64) NOT NULL     -- e.g. 'EMAIL_PIGGYBACK' for now
  title           varchar(255)  NOT NULL
  body            text          NULL
  link_url        varchar(2048) NULL
  is_read         boolean       NOT NULL DEFAULT false
  read_at         datetime      NULL
  date_created    datetime      NOT NULL
  last_updated    datetime      NOT NULL

  INDEX idx_custom_notification_user_unread (user_id, is_read, date_created DESC)
  INDEX idx_custom_notification_user_created (user_id, date_created DESC)
```

The composite `(user_id, is_read, date_created DESC)` index serves the bell-poll query (`unreadOnly=true` ordered by `date_created` desc).

### D3. Email → User resolution (multi-match aware)

**Choice:** For each recipient email, run `User.findAllByEmail(emailString)`. If empty, fall back to `User.findAllByUsername(emailString)`. For every matching `User` in either result list, create one `CustomNotification` row. If both lookups return empty, silently skip.

**Rationale:** Verified by reading `Person.groovy:35-41` — the `static constraints` block declares `email(nullable: true, email: true, maxSize: 255)` with **no `unique:` constraint**. Duplicates are possible (rare in practice, but the schema permits them). `findByEmail` returns only the first match and would silently drop notifications for the other matched users; `findAllByEmail` returns the full list and the hook iterates.

The `username` fallback handles the legacy case documented at `User.groovy:20` (`String username  // email or username`) — older accounts may hold the email only in `username`. `findAllByUsername` is used for symmetry, even though `username` IS constrained `unique: true` at `User.groovy:48` (so it'll always return 0 or 1).

Silent skip is correct behavior for external recipients (vendors, carriers, partner admins): the email still gets sent; we just don't have a persistent home for an in-app notification.

The lookup runs *after* `doSendMail` succeeds, so a slow lookup can't delay an email; and any exception in the lookup is caught and logged (never rethrown), so a transient DB failure on the lookup path can't break email sends.

### D4. Notification content from email subject + body

**Choice:** For v1, the notification's `title` is the email subject (stripped of the configured `[OpenBoxes]` prefix that `doSendMail` adds at line 181), and `body` is left null (the email body is HTML, not a clean fit for an in-app row). `link_url` is also null in v1.

**Rationale:** Subject lines for the existing 16 trigger types are already human-readable (verified by spot-checking `NotificationService.groovy`'s shipment/requisition/user-lifecycle methods — each builds a subject like `"Shipment ${shipment.name} has been shipped"`). The HTML body is appropriate for an email but not for a 200px-wide dropdown row, and reformatting it for the dropdown is more work than it's worth for v1. Surfaces for type-specific titles, bodies, and `link_url` arrive in v2 when we have user feedback.

### D5. Frontend delivery is 30-second polling, not WebSocket

**Choice:** `NotificationBell` polls `GET /api/custom/notifications?unreadOnly=true&limit=20` every 30 seconds while mounted.

**Rationale:** OpenBoxes has no existing WebSocket / SSE infrastructure. Adding it would 5×+ the project. Notifications are low-frequency, low-latency-tolerant ("you have a new shipment to review" doesn't need sub-second delivery). 30s × N concurrent users × a couple of small JSON responses is comfortably within the Grails server's headroom. A small ETag / `If-Modified-Since` optimization can be added later if load is measurable.

### D6. Bell component is composed into the upstream Header, not a fork of the Header

**Choice:** Add `<NotificationBell />` as a new sibling JSX element inside the existing `<ul className="navbar-nav w-100">` block in `src/js/components/Layout/Header.jsx`, positioned between `<Menu />` and `<NavbarIcons />` (the current children at `Header.jsx:43-44`). The component itself lives at `src/js/custom/notifications/components/NotificationBell.jsx`.

**Rationale:** Per `rules/custom-package-isolation.md`, custom React lives under `src/js/custom/<feature>/`. The Header is upstream; we don't fork it. Adding the bell as a sibling of `<NavbarIcons />` rather than editing `NavbarIcons.jsx` (also upstream) confines the upstream touch to a single component. The `<ul className="navbar-nav w-100">` already wraps the bar's right-hand icon group, so a third sibling sits naturally there visually.

Verified by reading `src/js/components/Layout/Header.jsx` (82 lines, single component):
- Lines 41–46 contain the `<div className="collapse navbar-collapse w-100">` wrapper.
- Lines 42–45 contain `<ul className="navbar-nav w-100"><Menu /><NavbarIcons /></ul>`.
- Insertion is a new `<NotificationBell />` line between `<Menu />` and `<NavbarIcons />`, plus one `import NotificationBell from '…'` line at the top.

Note that an unrelated upstream `notification.jsx` already exists in `src/js/components/Layout/notifications/` (read 2026-05-22: it's a `react-s-alert` toast wrapper, not a persistent notification system). `react-s-alert` manages its own state internally — verified by grepping `src/js/reducers/`, `src/js/store/`, and `src/js/App.jsx` for `s-alert|sAlert|react-s-alert`, which returns zero hits. So a new Redux slice (or local hook state) under `src/js/custom/notifications/` cannot collide with the toast system. No name collision either because our feature lives under `src/js/custom/notifications/`.

### D7. Feature name `notifications` (camelCase) for code paths

**Choice:** Backend package `org.pih.warehouse.custom.notifications`, frontend folder `src/js/custom/notifications/`. The OpenSpec change name stays `in-app-notifications` (kebab-case per OpenSpec convention).

**Rationale:** `rules/custom-package-isolation.md` explicitly requires camelCase for `<feature>` in code paths. The OpenSpec change name uses kebab-case per its own tooling convention. The two conventions live in different namespaces and don't conflict.

### D8. URL mapping registration

**Choice:** Add three mapping lines to `grails-app/controllers/org/pih/warehouse/UrlMappings.groovy`:

```groovy
"/api/custom/notifications"(controller: 'customNotification', action: 'list', method: 'GET')
"/api/custom/notifications/read-all"(controller: 'customNotification', action: 'markAllRead', method: 'PUT')
"/api/custom/notifications/$id/read"(controller: 'customNotification', action: 'markRead', method: 'PUT')
```

**Rationale:** Verified by reading the actual `UrlMappings.groovy` (file lives at `grails-app/controllers/org/pih/warehouse/UrlMappings.groovy`, NOT the conventional `grails-app/conf/` — confirmed by `find`). Sample mappings at lines 45–192 show explicit per-route declarations (`/api/categories`, `/api/users`, `/api/products`, etc.) with no `/api/**` wildcard fallback, so each new endpoint needs its own mapping line. Three lines is the minimal surface for the three actions. Placement: append to the existing block of `/api/*` mappings, with a `// in-app-notifications (custom)` comment delimiter so the touch is easy to spot during upstream merges.

`method:` constraints in the mapping mean a `POST` to `/api/custom/notifications` returns 405 rather than hitting an unrelated action — small but worthwhile.

## Upstream Touch Points

The following upstream files are modified by this change. Each edit is surgical and documented here so future upstream merges can target these locations.

| File | Edit | Reason |
|---|---|---|
| `grails-app/services/org/pih/warehouse/core/MailService.groovy` | One `def customNotificationService` injection property near the existing service injections; one `try { customNotificationService.recordSendAsNotifications(to, subject) } catch (Exception ex) { log.error(...) }` block inserted between `email.send()` (current line 202) and `return true` (current line 203) | The one funnel point all emails flow through. See D1, D3. |
| `src/js/components/Layout/Header.jsx` | One `import NotificationBell from '…'` at the top; one `<NotificationBell />` JSX element inserted between `<Menu />` and `<NavbarIcons />` inside the `<ul className="navbar-nav w-100">` at current lines 42–45 | The component's mount point in the layout. See D6. |
| `grails-app/controllers/org/pih/warehouse/UrlMappings.groovy` | Three mapping lines under a `// in-app-notifications (custom)` comment delimiter, appended to the existing `/api/*` block | Route mapping for the three REST endpoints. See D8. |
| `grails-app/i18n/messages.properties` | Append seven keys under a `# in-app-notifications (custom)` comment block (per `rules/custom-package-isolation.md` § "i18n exception") | Bell tooltip, empty/empty-unread, mark-all-read, two tab labels, unread-count aria-label. The root bundle is the only file Grails 3.3's `messageSource` actually loads. |
| `grails-app/controllers/org/pih/warehouse/SecurityInterceptor.groovy` | Append `'customNotification'` to `controllersWithLocationNotRequired` | The bell polls on every page including `chooseLocation`, so the controller must be reachable before the user has selected a warehouse. Without the exemption, every poll redirects to `chooseLocation` and the frontend gets an HTML body back instead of JSON. |

## Risks / Trade-offs

- **[Hook in upstream file = merge-conflict surface.]** `MailService.doSendMail` is the touch point most likely to drift in upstream releases. → Mitigation: keep the call to one line, document the touch point above, rely on the OpenSpec archive as the merge hitlist, and `git rerere` (documented in `.claude/docs/FORK_MAINTENANCE.md`) replays the same resolution automatically.
- **[Email-to-User mapping is heuristic.]** A user with their email in `username` rather than `email` will only be found via the fallback lookup; if they have *neither* `email` nor `username` set to their actual email, we silently skip — they keep getting emails but no in-app notification. → Mitigation: accept as a known limitation; add a v2 task to backfill `Person.email` for users where `username` looks like an email and `email` is null.
- **[`Person.email` is non-unique.]** Two `Person` rows (and therefore two `User` rows under joined inheritance) can share an email. Using `findByEmail` would silently drop notifications for all but the first match. → Mitigated by D3: use `findAllByEmail` and create one notification per match. Cost: a single email send to a shared mailbox creates N notifications (one per user); acceptable, since each user logs in independently and expects their own copy.
- **[Polling cost at high user counts.]** 30s polling × N logged-in users = N/30 RPS of small JSON requests against the API. At 500 concurrent users that's ~17 RPS, well within Grails headroom. At 5000+ it's worth revisiting. → Mitigation: defer; add ETag support if/when measured.
- **[Notification title quality depends on upstream subject lines.]** If a future upstream email trigger uses a generic subject like "Notification", users will see a generic title in the bell. → Mitigation: acceptable for v1; v2 introduces typed notifications with custom titles for the triggers we care about.
- **[Confusion with existing `notification.jsx`.]** Upstream already has a `notification.jsx` (toast wrapper). A reader looking for "the notification code" might land on the wrong file. → Mitigation: feature folder is `notifications` (not `notifications`); the component is `NotificationBell` (not `Notification`).
- **[Side effect inside `doSendMail` couples mail and notifications.]** A bug in the custom notification service could theoretically affect mail send timing. → Mitigation: the hook is wrapped in a try/catch that logs but never rethrows; the call happens *after* mail send returns true, so a notification-service exception cannot prevent or undo the email.

## Migration Plan

1. **Schema:** Liquibase changeset under `grails-app/migrations/custom/2026-05-22-create-custom-notification.groovy` adds the table and indexes (with `rollback` block dropping the table). One-line include in `grails-app/migrations/custom/changelog.groovy`.
2. **Backend:** Create domain class, service, controller, command-object (if needed for the controller). All under `org.pih.warehouse.custom.notifications`.
3. **Hook:** Edit `MailService.doSendMail` to inject the post-send call.
4. **Frontend:** Create `NotificationBell`, dropdown, API client, Redux slice (or local hook-based state) under `src/js/custom/notifications/`.
5. **Layout integration:** Edit `Header.jsx` to import and render `<NotificationBell />`.
6. **URL mapping:** Add the three mapping lines listed in D8 to `grails-app/controllers/org/pih/warehouse/UrlMappings.groovy` (file location verified; no `/api/**` wildcard exists).
7. **Smoke test:** Trigger a known email (e.g., user creation), confirm a row appears in `custom_notification`, confirm the bell badge increments after the next 30s poll.

**Rollback:** Drop the table (`rollback { dropTable(...) }` block in the changeset). Revert the upstream edits (the `MailService` injection + try/catch hook, the `Header.jsx` import + `<NotificationBell />` line, the three `UrlMappings.groovy` lines, the four `messages.properties` keys). Delete the custom files. No data outside the new table is touched.

## Open Questions

- **OQ1.** Should the bell's "click a notification" action open the related entity if we don't have `link_url` populated yet in v1? → Proposed answer: no, it just marks the row read in v1; v2 introduces typed notifications with deep-links.
- **OQ2.** Should the API be paginated, or is `limit=20` enough for the dropdown? → Proposed answer: `limit` is enough for the dropdown; if/when we add a full notifications page, add cursor pagination then.
- **OQ3.** Where should the `<NotificationBell />` sit in `Header.jsx` — left of the user menu, or right of the localization toggle? → Defer to the implementer reading the current header layout.

## Validation

Each criterion below is verifiable from an artifact outside this design (a file:line in the repo, a captured fixture, or a runnable test).

- [ ] **V1. The `doSendMail` hook fires for every email send funneled through `MailService`.** Verifiable by reading `grails-app/services/org/pih/warehouse/core/MailService.groovy` (post-implementation) and confirming the call to `customNotificationService.recordSendAsNotifications` appears between `email.send()` (current line 202) and `return true` (current line 203) inside `doSendMail` — and NOT in any of the public `sendMail*` overloads above it.
- [ ] **V2. All public `sendMail*` overloads route through `doSendMail`.** Pre-implementation receipt: the 14 `return doSendMail(...)` lines at `MailService.groovy:62,66,70,74,78,82,86,90,94,101,105,109,113,117,121`. Post-implementation: those lines remain unchanged.
- [ ] **V3. `MailService.doSendMail` has exactly one success exit.** Pre-implementation receipt: `MailService.groovy:200-207` shows one `try { … email.send(); return true } catch { … return false }` block plus a dead unreachable `return false` at line 208. Hook placement is unambiguous. Verifiable post-implementation by grepping `return true` inside the `doSendMail` method body.
- [ ] **V4. `User.email` lookup against the inherited `Person.email` property works.** Receipt: `Person.groovy:21` declares `String email`; `Person.groovy:27-31` declares joined inheritance via `tablePerHierarchy false`; `User.groovy:17` declares `class User extends Person`. Therefore `User.findAllByEmail(string)` is a valid dynamic finder per `rules/groovy/patterns.md` "Dynamic finders" decision tree.
- [ ] **V5. `Person.email` is non-unique → `findAllByEmail` is required, not `findByEmail`.** Receipt: `Person.groovy:35-41` declares `static constraints { … email(nullable: true, email: true, maxSize: 255) … }` with no `unique:` clause. Verifiable post-implementation by reading `CustomNotificationService` and confirming it calls `User.findAllByEmail(...)` (returns a list) and iterates, not `User.findByEmail(...)`.
- [ ] **V6. `username` fallback covers the documented legacy case.** Receipt: `User.groovy:20` literally comments `String username  // email or username` and `User.groovy:48` declares `username(blank: false, unique: true, maxSize: 255)` — the uniqueness constraint means the fallback returns 0 or 1 user.
- [ ] **V7. The custom migrations aggregator already exists.** Receipt: `grails-app/migrations/custom/changelog.groovy` exists with `databaseChangeLog = { /* No custom migrations yet — append `include file:` lines here as they ship. */ }`. The new changeset will be the first entry.
- [ ] **V8. The bell mounts in the upstream header at the documented position.** Receipt: `Header.jsx:42-45` currently contains `<ul className="navbar-nav w-100"><Menu /><NavbarIcons /></ul>`. Verifiable by post-implementation diff: exactly one new `import NotificationBell from …;` line near the existing imports (lines 1–15) and exactly one new `<NotificationBell />` JSX element between `<Menu />` and `<NavbarIcons />` — no other edits, no reformatting (per `rules/custom-package-isolation.md` Boy Scout suspension).
- [ ] **V9. `UrlMappings.groovy` actually lives in `grails-app/controllers/org/pih/warehouse/UrlMappings.groovy`.** Receipt: `find grails-app -name "UrlMappings*.groovy"` returns that single path; the conventional `grails-app/conf/` location does not have one. Verifiable by the diff: the three new `/api/custom/notifications*` mapping lines appear in `grails-app/controllers/org/pih/warehouse/UrlMappings.groovy`, not anywhere else.
- [ ] **V10. No `/api/**` wildcard already covers the new endpoints.** Receipt: `grep -n "api" grails-app/controllers/org/pih/warehouse/UrlMappings.groovy` returns ~30 distinct `/api/<resource>` declarations (e.g. lines 45, 49, 55, 61, 67, 73, 79, 84, 89, 94, 99, 104, 109, 114, 119, 123, 128, 133, 138, 143, 148, 153, 158, 163, 168, 173, 178, 183, 188, 192…), none of which is a wildcard. Adding three explicit mappings is therefore necessary, not redundant.
- [ ] **V11. All backend custom files live under the camelCase feature package.** Verifiable with `find grails-app src/main -path '*custom/notifications/*' -o -path '*custom.notifications*'` matching every new backend file in the diff.
- [ ] **V12. All frontend custom files live under `src/js/custom/notifications/`.** Verifiable with `find src/js/custom/notifications -type f` matching every new frontend file in the diff.
- [ ] **V13. The new table has the read-state composite index.** Verifiable by reading the Liquibase changeset post-implementation: a `createIndex` (or `addIndex`) entry on `(user_id, is_read, date_created)` for `custom_notification`.
- [ ] **V14. The hook is exception-safe.** Verifiable by reading the post-implementation `doSendMail`: the call to `customNotificationService.recordSendAsNotifications(...)` is wrapped in a `try { ... } catch (Exception ex) { log.error(...) }` block so it never throws into the mail-send path.
- [ ] **V15. A scenario test exercises the multi-recipient case (3 emails, 2 known, 1 external → 2 notifications).** Verifiable: a Spock test under `src/test/groovy/org/pih/warehouse/custom/notifications/` that mocks `User.findAllByEmail` accordingly and asserts the exact row count.
- [ ] **V16. A scenario test exercises the non-unique-email case (1 email → 2 users → 2 notifications).** Verifiable: a Spock test that mocks `User.findAllByEmail(email)` to return a 2-element list and asserts both notifications are created with distinct `user_id` values.
- [ ] **V17. Polling interval is 30s.** Verifiable: grep `src/js/custom/notifications/` for `30000` or `30 * 1000` in the `setInterval`/`useInterval` call inside the polling hook.
- [ ] **V18. The custom notification feature has no Redux name collisions with the existing toast system.** Receipt: `grep -rn "s-alert\|sAlert\|react-s-alert" src/js/reducers/ src/js/store/ src/js/App.jsx` returns zero hits — the toast library manages its own state outside Redux. Verifiable post-implementation by confirming our Redux slice (if added) registers under a unique key (e.g. `customNotifications`) in the root reducer.
- [ ] **V19. Email subject lines used by all upstream triggers are user-readable.** Pre-implementation receipt: `grep -n "subject\|setSubject" grails-app/services/org/pih/warehouse/report/NotificationService.groovy` shows 10 subjects — five Groovy string-interpolated descriptive forms (e.g. `"Shipment ${shipmentInstance?.shipmentNumber} has been shipped"`, `"Expiry Alerts - ${location.name}"`, `"Application Error: ${exception?.message}"`) and five i18n-localized via `messageSource.getMessage(...)` / `messageLocalizer.localize(...)`. None are placeholder strings. Verifiable as a one-time visual check; titles render as their subjects in v1 without per-trigger title customization.
- [ ] **V20. The `[OpenBoxes]` prefix added by `doSendMail:181` is NOT propagated to notification titles.** Receipt: the `email.setSubject("${prefix} ${subject}")` call at line 181 mutates only the outgoing `Email` object's subject. The local `subject` parameter remains the raw input. The hook receives the raw `subject` argument, so notifications get the clean title with no `[OpenBoxes]` prefix.
- [ ] **V21. No upstream files outside the touch-point list are modified.** Verifiable: `git diff --name-only <base>..HEAD` against the merge base shows that every changed path is either under `custom/` (backend or frontend), under `grails-app/migrations/custom/`, or one of the four documented upstream files above (`MailService.groovy`, `Header.jsx`, `UrlMappings.groovy`, `messages.properties`). Anything else is a rule violation.

## Verified Assumptions

All assumptions that were flagged as unverified in the initial draft have been resolved against direct source reads or captured grep output. The receipts are inline in the Validation section above; the summary:

- **UA1 → V4, V5** ✅ `Person.email` is non-unique. Design corrected to use `findAllByEmail` in D3. Service iterates over the result list.
- **UA2 → V3** ✅ `doSendMail` has exactly one success exit at line 203 (after `email.send()` at line 202). Hook placement is unambiguous.
- **UA3 → V9, V10** ✅ `UrlMappings.groovy` lives at `grails-app/controllers/org/pih/warehouse/UrlMappings.groovy`, has no `/api/**` wildcard, requires three explicit mapping lines. D8 updated.
- **UA4 → V18** ✅ `react-s-alert` manages state internally; zero Redux collision risk.
- **UA5 → V8** ✅ Exact insertion point in `Header.jsx` confirmed (between `<Menu />` and `<NavbarIcons />` inside the `<ul>` at lines 42–45). D6 updated.
- **UA6 → V19, V20** ✅ Subjects across the 10 spotted call-sites in `NotificationService.groovy` are descriptive or i18n-localized. The `[OpenBoxes]` prefix is added inside the outgoing email object only; the hook sees the clean subject.

## Confidence: 9/10

**Rationale:** Every external claim in the design now has a direct receipt — a file:line, a grep result, or a captured pattern from the actual source. The architecture decisions (single funnel point at `MailService:200-207`, joined inheritance enabling `User.findAllByEmail`, no wildcard in `UrlMappings`, clean header insertion point at `Header.jsx:42-45`, zero Redux collision risk) are each backed by a verification step that can be re-run by any reader. The remaining 1-point uncertainty is honest implementation-time risk that no amount of pre-reads can eliminate:

- Adding the Spring bean injection (`def customNotificationService` in `MailService`) on a Grails 3.3 service that already has a non-trivial set of bean dependencies — should "just work" via convention, but Grails 3.3 occasionally surprises with proxy/init ordering issues that only surface at boot.
- Polling-hook cancellation correctness across unmount + route changes — straightforward but a frequent source of `act()` warnings in Jest, so the test will be more iterative than the spec implies.

Neither is a design issue; both are routine implementation friction. The plan is implementation-ready.
