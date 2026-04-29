# Stock Transfer Document Upload — Implementation Tasks

> **Upstream compatibility note**: per `CLAUDE.md` (*Upstream Compatibility*) and
> `.claude/rules/custom-package-isolation.md`, all new code lives in custom
> packages/folders. Tasks tagged `[UPSTREAM-TOUCH]` modify upstream files — keep the
> diff minimal, do not reformat, do not apply the Boy Scout Rule to those files. See
> `design.md` → *Upstream Compatibility* for the full rationale.
>
> **Rules-alignment pass (2026-04-15)**: updated frontend file paths to use
> `components/`, `utils/`, `__tests__/` sub-folders; moved backend i18n bundle to
> `grails-app/i18n/custom/`; promoted the `custom/*` webpack alias to a definite
> upstream touch (verified missing in `webpack.config.js`).
>
> **Post-initial-commit revisions (2026-04-15)**: after a manual UI smoke test we:
> 1. Split `REQUIRE_TRANSFER_DOCUMENT` into `REQUIRE_TRANSFER_OUT_DOCUMENT` (origin) +
>    `REQUIRE_TRANSFER_IN_DOCUMENT` (destination). Service checks both.
> 2. Switched the panel to `utils/Translate` (the project wrapper that falls back to
>    `defaultMessage`); raw `react-localize-redux` was rendering raw key strings.
> 3. Made the frontend gate **fail-closed**: `customCanComplete` defaults to `false`,
>    panel must explicitly enable it. No new error-surfacing code in the component —
>    `apiClient` already has a global response interceptor that pops a notification
>    toast for every non-2xx, so a custom `Alert.error` in `.catch` would duplicate it.
> 4. Changed post-completion redirect to `order/show/<id>` (where the upstream Documents
>    tab already shows uploads — same `Order` entity).
> 5. Abandoned the `grails-app/i18n/custom/` bundle approach and appended keys directly
>    to upstream `messages.properties` (Grails 3.3 doesn't auto-load i18n sub-folders;
>    appending is a smaller net upstream touch than registering a `messageSource`
>    override).

---

## Task 1: Add `REQUIRE_TRANSFER_OUT_DOCUMENT` and `REQUIRE_TRANSFER_IN_DOCUMENT` ActivityCodes [BE] [UPSTREAM-TOUCH] ✅

**File**: `src/main/groovy/org/pih/warehouse/core/ActivityCode.groovy` *(MODIFY — 6 lines)*

Add **two** enum entries directly after `REQUIRE_ACCOUNTING`, prefixed with a `// custom`
comment so future merges can identify them, and add the matching pair to the `static list()`
method (so they appear in the location-edit admin checklist):

- `REQUIRE_TRANSFER_OUT_DOCUMENT` — checked against the **origin** location of an outbound
  stock transfer.
- `REQUIRE_TRANSFER_IN_DOCUMENT` — checked against the **destination** location of an
  inbound stock transfer.

Do not reorder existing entries or touch anything else in the file.

**Acceptance criteria:**
- Both enum entries compile and are available for `Location.supports()` checks
- Both appear in `ActivityCode.list()` so the location-edit admin UI renders checkboxes
- Diff is exactly two enum entries + two list entries + one `// custom` marker
  (no formatting or import changes)

---

## Task 2: Custom document service [BE] ✅

**File**: `grails-app/services/org/pih/warehouse/custom/stocktransferdocuments/CustomStockTransferDocumentService.groovy` *(NEW)*

Implement on a brand-new service (see design.md → *Custom Document Service*):

- `listDocuments(Order order)` → list of document DTOs with download URIs (uses
  `Document.getLink()` from the upstream domain class).
- `originSideRequiresOutDocument(Order order)` → returns true if
  `order.origin.supports(REQUIRE_TRANSFER_OUT_DOCUMENT)` **OR** any
  `orderItems[].originBinLocation.supports(REQUIRE_TRANSFER_OUT_DOCUMENT)`.
  This is the bin-aware path: enable the activity code on a specific source
  bin (e.g., a Quarantine bin) and any transfer item picking from that bin
  triggers the rule, even if the parent depot does not enforce.
- `destinationSideRequiresInDocument(Order order)` → mirror of the above,
  checks `order.destination` and every `orderItems[].destinationBinLocation`.
- `isDocumentRequired(Order order)` → returns
  `originSideRequiresOutDocument(order) || destinationSideRequiresInDocument(order)`.
- `uploadDocument(String orderId, MultipartFile fileContents)` → constructs a `Document`
  from the multipart file and calls `order.addToDocuments(doc).save(failOnError: true)`.
  No `flush: true` — the service is `@Transactional` and the commit handles flushing.
- `validateForCompletion(Order order)` → throws `IllegalArgumentException` when order is
  null; throws `ValidationException` (carrying the `customStockTransferDocument.required.error`
  reject code) when `isDocumentRequired(order)` is true and no documents are attached.

Static constants `NULL_ORDER_ERROR`, `DOCUMENT_REQUIRED_ERROR`, `DOCUMENT_REQUIRED_CODE`
hold the message strings so unit tests can assert against them by reference.

No changes to upstream `StockTransferService`.

**Acceptance criteria:**
- Service compiles and is auto-wired via Grails convention (no `@Autowired` / constructor injection)
- Unit tests cover all four enforcement sources (parent depot OUT, parent depot IN, bin OUT, bin IN)
  via a data-driven `@Unroll` table; both-flags-set case; neither-flag case; multi-item orders
  where only one item's bin enforces; no-documents-vs-documents case; null-order case
- Unit tests use `DataTest` + `mockDomains(Order, Document)` because `@Transactional` requires
  a GORM datastore even in unit tests
- `mockOrder` helper accepts a `List<OrderItem>` so tests can wire bin locations onto items

**Depends on**: Task 1

---

## Task 3: Custom document API controller [BE] ✅

**File**: `grails-app/controllers/org/pih/warehouse/custom/stocktransferdocuments/CustomStockTransferDocumentController.groovy` *(NEW)*

Two actions, both delegating to `CustomStockTransferDocumentService`:

- `list` → `GET /api/custom/stockTransfers/{id}/documents` → `{ documentRequired, documents[] }`
- `upload` → `POST /api/custom/stockTransfers/{id}/documents` (multipart) → `{ data: "..." }`

Follow the `StockMovementController.uploadDocument` pattern as a read-only reference.
Thin controller — parse params, delegate, return JSON.

**Security**: both actions must be gated by the same roles the upstream
`StockTransferApiController` uses (use `@Secured` or the project's auth mechanism —
read the upstream controller to find the convention). Do not hardcode role names; read
them from config so upstream role-rename changes propagate automatically.

**Logging**: `log.info` on successful upload (document id, size, username);
`log.warn` on upload failure or validation error. Never log document bytes.

**Acceptance criteria:**
- Controller is thin (no business logic)
- Upload persists the file on the underlying `Order` via the custom service
- List returns the shape documented in design.md
- **Unauthorized users (no role) receive HTTP 403** on both GET and POST — covered by
  a controller unit test
- Successful upload emits an `INFO` log line with document id, size, and username

**Depends on**: Task 2

---

## Task 4: URL mapping for custom endpoint [BE] [UPSTREAM-TOUCH] ✅

**File**: `grails-app/controllers/org/pih/warehouse/UrlMappings.groovy` *(MODIFY — 1 block)*

Add exactly one mapping block:
```groovy
"/api/custom/stockTransfers/$id/documents"(parseRequest: true) {
    controller = "customStockTransferDocument"
    action = [GET: "list", POST: "upload"]
}
```

Place it near existing stock transfer mappings. Do not reformat the file.

**Acceptance criteria:**
- GET and POST to `/api/custom/stockTransfers/{id}/documents` route correctly
- No other edits to the file

**Depends on**: Task 3

---

## Task 5: Completion enforcement hook [BE] [UPSTREAM-TOUCH] ✅

**File**: `grails-app/services/org/pih/warehouse/stockTransfer/StockTransferService.groovy` *(MODIFY — ≤ 5 lines)*

**Pre-work (verification)**: before editing, confirm that `stockTransfer.id` is the
`Order` primary key in this codebase (StockTransfer is an API-layer wrapper). If it is
not, the `Order.get(...)` call below will return `null` and the enforcement will
silently pass. If the mapping is different, update Task 2's `validateForCompletion`
signature to accept whatever identifier is correct, and add a null-guard that throws a
clear error if the `Order` lookup fails.

Edit:
1. Inject the custom service (Grails by-convention, **no `@Autowired` or constructor
   injection**): `def customStockTransferDocumentService` (one line)
2. In `completeStockTransfer()`, add a single delegating call before existing logic:
   ```groovy
   customStockTransferDocumentService.validateForCompletion(Order.get(stockTransfer.id))
   ```

All logic lives in the custom service. Do not modify any surrounding code, comments,
imports, or method signatures.

**Acceptance criteria:**
- Upstream diff is ≤ 5 lines
- `stockTransfer.id` → `Order` PK mapping is verified (or the call signature is
  adjusted accordingly)
- `validateForCompletion` throws a clear error when the `Order` lookup returns `null`
- Completion is blocked when the activity code is enabled and no documents attached
- Completion succeeds when the activity code is disabled (no regression)
- Completion succeeds when documents are attached and activity code is enabled
- A `log.warn` audit line is emitted when a completion is blocked (includes
  `stockTransferId` and `originLocationId`)

**Depends on**: Task 2

---

## Task 6: Custom frontend panel [FE] ✅

**Files** (all NEW, under `src/js/custom/stockTransferDocuments/`, layout per
`custom-package-isolation.md`):
- `components/StockTransferDocumentsPanel.jsx`
- `components/StockTransferDocumentsPanel.scss`
- `utils/api.js`
- `utils/messages.js` — i18n message key definitions
- `__tests__/StockTransferDocumentsPanel.test.jsx`

Implement a self-contained panel component (see design.md → *Frontend — Custom Panel*
and *`canComplete` initial-state contract*):

- Fetch `GET /api/custom/stockTransfers/{id}/documents` on mount
- `react-dropzone` for file selection (already a project dependency — reference
  `SendMovementPage.jsx` for existing integration)
- Render existing documents as download links
- Pending files list with remove buttons; Upload button POSTs FormData to the custom endpoint
- Warning message when `documentRequired` is true and no documents are present
- `onCanCompleteChange(canComplete)` prop that emits per the contract in design.md →
  *`canComplete` initial-state contract* (fail-open during initial load and on network
  error — backend is the hard gate)
- All user-visible strings via `react-intl` message keys under
  `react.custom.stockTransferDocuments.*` (no hardcoded English)

**Acceptance criteria:**
- All new files live under `src/js/custom/stockTransferDocuments/`
- No imports into or edits of any file under `src/js/components/stock-transfer/` (except the mount point in Task 7)
- Jest + RTL tests cover: empty state, loaded documents, drag-and-drop, upload success, upload error, document-required warning, **network-error (canComplete=false, fail-closed)**
- Asserts use concrete values (no `toBeTruthy` / `toBeDefined` for knowable results)
- All rendered text comes from `react-intl` — no hardcoded English strings

**Depends on**: Task 6a (i18n wiring)

---

## Task 6a: i18n wiring [FE/BE] [UPSTREAM-TOUCH] ✅

**Files**:
- `src/js/custom/stockTransferDocuments/utils/messages.js` *(NEW)* — default-exported
  constant object holding `{ id, defaultMessage }` pairs for every panel string. The
  panel imports it as `import M from '...'` and consumes via `<Translate id={M.X.id}
  defaultMessage={M.X.defaultMessage}/>`. The `defaultMessage` is the in-source
  fallback; the `id` is the lookup key in `messages.properties`.
- `grails-app/i18n/messages.properties` *(MODIFY — ~12 lines, all appended)*:
  - `enum.ActivityCode.REQUIRE_TRANSFER_OUT_DOCUMENT` /
    `enum.ActivityCode.REQUIRE_TRANSFER_IN_DOCUMENT` — used by the GSP location-edit
    page via `format:metadata`.
  - `react.locationsConfiguration.ActivityCode.REQUIRE_TRANSFER_OUT_DOCUMENT` /
    `react.locationsConfiguration.ActivityCode.REQUIRE_TRANSFER_IN_DOCUMENT` — used by
    the React location-config screen.
  - `customStockTransferDocument.required.error` — the reject code thrown by the
    backend validator; resolved by Grails' `messageSource` for the `ValidationException`.
  - `react.custom.stockTransferDocuments.*` — all React-side panel strings (panel
    title, empty state, dropzone prompt, upload/remove buttons, fetch/upload errors,
    required warning).

**Why we abandoned the `grails-app/i18n/custom/` sub-folder approach:** Grails 3.3
`messageSource` only auto-loads `messages*.properties` from the `i18n/` root. A
`custom/` sub-folder bundle would require either a `messageSource` override in
`resources.groovy` or a `BeanPostProcessor` adding basenames at runtime — both larger
upstream touches than appending the keys directly. The keys are namespaced and grouped
under a `# Custom: stock-transfer-document-upload` comment so future merges can spot them.

**Frontend rendering:** the panel imports `Translate` from **`utils/Translate`** (the
project wrapper that supplies `onMissingTranslation: () => defaultMessage`). The raw
`react-localize-redux` `<Translate>` does NOT fall back to `defaultMessage` and renders
raw key strings — that was the first-pass bug. The panel test mock now also targets
`utils/Translate` instead of the raw library.

**Acceptance criteria:**
- All panel strings render as human text (not raw keys) in the browser
- Both new activity-code labels render as human text on the GSP location-edit page
  AND the React location-config screen
- The backend `ValidationException` resolves the reject code to the correct English
  string via Grails' `messageSource`
- The `Translate` wrapper is the one from `utils/Translate`, not raw `react-localize-redux`
- The append in `messages.properties` is grouped under a `# Custom:` comment so future
  upstream merges can identify it

**Depends on**: Task 2 (backend reject code), Task 6 planning

---

## Task 7: Mount the panel + post-completion redirect + error surfacing [FE] [UPSTREAM-TOUCH] ✅

**File**: `src/js/components/stock-transfer/StockTransferCheckPage.jsx` *(MODIFY — ~14 lines)*

**Pre-work (verification)**:
1. Confirm the file at this path is the actual Check Page wizard step rendered in the
   UI — the class inside the file is named `StockTransferSecondPage` (despite the
   filename), which suggests it may have been copied. If the real mount point is a
   different file, update this task's path before editing.
2. **`custom/*` webpack alias is confirmed missing** (verified 2026-04-15 against
   `webpack.config.js`). This task must register it as a definite 1-line upstream
   touch — see Task 7a below.

Edits to `StockTransferCheckPage.jsx`:

1. Add one import (uses the new `custom/*` alias added in Task 7a):
   ```javascript
   import StockTransferDocumentsPanel from 'custom/stockTransferDocuments/components/StockTransferDocumentsPanel';
   ```
2. Add `customCanComplete: false` to the initial state object (fail-closed default —
   see design.md → *`canComplete` initial-state contract*).
3. Add one JSX mount between the items table and the submit buttons:
   ```jsx
   <StockTransferDocumentsPanel
       stockTransferId={this.state.stockTransfer.id || this.props.match?.params?.stockTransferId}
       disabled={this.state.stockTransfer.status === 'COMPLETED'}
       onCanCompleteChange={(canComplete) => this.setState({ customCanComplete: canComplete })}
   />
   ```
4. Extend the existing Complete button's `disabled` prop:
   ```jsx
   disabled={this.state.stockTransfer.status === 'COMPLETED' || this.state.customCanComplete === false}
   ```
5. **Post-completion redirect** — swap the import from `STOCK_TRANSFER_URL` to
   `ORDER_URL` (both live in `consts/applicationUrls.js`), then change the two
   `window.location = STOCK_TRANSFER_URL.show(...)` sites (lines ~194 and ~282) to
   `window.location = ORDER_URL.show(this.state.stockTransfer.id)`. Stock transfers
   and orders share the same `Order` PK, so this lands users on the upstream
   `order/show` page where the existing Documents tab already shows their uploads.
   Use the `ORDER_URL.show` helper rather than inlining a string literal — it
   survives any future `CONTEXT_PATH` change.
6. **Backend errors** — `save().catch(...)` stays as `() => this.props.hideSpinner()`.
   The existing `apiClient` global response interceptor (`handleError` in
   `src/js/utils/apiClient.jsx`) already pops a notification toast for every non-2xx,
   including our backend `ValidationException`, so adding a custom `Alert.error`
   in the component would be a duplicate popup.

Do not reformat, reorder, or refactor anything else in this file. Boy Scout Rule is
suspended for this file.

**Acceptance criteria:**
- Correct file confirmed (class name verified to match the mounted page)
- `custom/*` alias verified or registered as a budgeted upstream touch
- Diff matches the five edits above (~10 lines net)
- Complete button is disabled by default on mount (fail-closed)
- Complete button is enabled only after the panel reports `canComplete=true` after a
  successful load
- After a successful completion, the browser navigates to the URL produced by
  `ORDER_URL.show(<id>)` (the upstream `order/show` page, where the Documents tab
  is visible)
- Attempting completion without an attached document (e.g. by bypassing the disabled
  button via devtools) shows the existing `apiClient.handleError` notification toast
  with the backend's error message — exactly one popup, not zero and not two

**Depends on**: Task 6, Task 7a

---

## Task 7a: Register `custom/*` webpack alias [FE] [UPSTREAM-TOUCH] ✅

**File**: `webpack.config.js` *(MODIFY — 1 line)*

Inside `resolve.alias`, add exactly one entry next to the existing aliases
(`components`, `hooks`, `utils`, etc.):

```js
custom: path.resolve(SRC, 'custom'),
```

Do **not** reformat the alias object or reorder existing entries. This is the one-
line infrastructure that every future custom frontend feature will reuse — subsequent
features should not need to touch `webpack.config.js` again.

**Acceptance criteria:**
- Diff is exactly one line added to `resolve.alias`
- `import X from 'custom/stockTransferDocuments/components/X'` resolves at build time
- Jest resolves the alias via `moduleNameMapper` if it already reads from
  `webpack.config.js`; otherwise add a matching 1-line entry to the Jest config
  (budget it as a separate `[UPSTREAM-TOUCH]` in the summary below if needed)
- No other aliases are touched or reordered

**Depends on**: (none — can run in parallel with backend work)

---

## Task 9: Upload validation hardening (PR review #1) [BE/FE] ✅

PR review on the initial implementation flagged the upload path had no MIME
allowlist, no size cap, and no filename sanitization (`web/security.md` requires
both client and server-side validation). All work is in custom files; the only
upstream touch is appending 6 new i18n keys to `messages.properties`.

**Files (NEW — custom):**
- `src/main/groovy/org/pih/warehouse/custom/stockTransferDocuments/UploadConstraints.groovy`
  — content-type allowlist (PDF, image, Word, Excel, CSV, ZIP), extension allowlist,
  `DEFAULT_MAX_BYTES = 10 MB` (matches the upstream `Document.fileContents` GORM cap),
  `sanitizeFilename()` (strips path components + ISO control chars, replaces
  Windows-unsafe chars `<>:"|?*` with `_`, truncates to 255 chars).
- `src/main/groovy/org/pih/warehouse/custom/stockTransferDocuments/UploadValidationException.groovy`
  — typed exception carrying `messageCode` + `messageArgs` so the controller can
  resolve a localized error string via `messageSource`.

**Files (MODIFY — custom):**
- `CustomStockTransferDocumentService.uploadDocument` — injects `grailsApplication`,
  reads `openboxes.custom.stockTransferDocuments.maxUploadSizeBytes` (default 10 MB),
  validates size + MIME + extension + filename, sanitizes the filename before
  persisting on `Document.name` / `Document.filename`.
- `CustomStockTransferDocumentController.upload` — wraps the service call in
  `try/catch (UploadValidationException)`; on rejection, resolves the i18n message
  via injected `messageSource` and returns `400 { errorMessage }`. Logs a `warn`
  audit line with order id, code, content-type and size.
- `src/js/custom/stockTransferDocuments/components/StockTransferDocumentsPanel.jsx`
  — adds `accept` (matching backend allowlist) and `maxSize: 10 MB` props on
  `<Dropzone>`, plus `onDropRejected` that surfaces `'file-too-large'` /
  `'file-invalid-type'` codes as a localized inline `<Warning>`.
- `src/js/custom/stockTransferDocuments/utils/messages.js` — adds `invalidTypeError`
  and `tooLargeError` message keys.

**Files (MODIFY — upstream, +6 lines):**
- `grails-app/i18n/messages.properties` — appends 6 new keys (3 backend + 3
  React-side) alongside the existing custom-feature block:
  - `customStockTransferDocument.upload.invalidType.error`
  - `customStockTransferDocument.upload.tooLarge.error`
  - `customStockTransferDocument.upload.invalidFilename.error`
  - `react.custom.stockTransferDocuments.upload.invalidType.error`
  - `react.custom.stockTransferDocuments.upload.tooLarge.error`
  - `react.custom.stockTransferDocuments.upload.invalidFilename.error`

**Tunable cap:** the 10 MB default is the upstream `Document.fileContents` GORM
constraint ceiling — raising it requires modifying upstream `Document.groovy`,
which is forbidden by the isolation rule. Operators wanting smaller caps can set
`openboxes.custom.stockTransferDocuments.maxUploadSizeBytes` in their environment
override config (default `openboxes-config.properties` or `external config`),
which leaves all upstream files untouched. The Spring multipart limit
(`grails.controllers.upload.maxFileSize`, default 2 MB in upstream
`application.yml`) caps the request size **before** our service runs and must
also be raised in env config for the full 10 MB to be reachable.

**Acceptance criteria:**
- Server rejects oversize uploads with `400 { errorMessage: <localized> }`
- Server rejects disallowed MIME or extension with `400 { errorMessage: <localized> }`
- Server sanitizes path traversal, control chars, and Windows-unsafe chars from
  the persisted `Document.name` / `Document.filename`
- Client `<Dropzone>` filters the OS file picker via `accept` and rejects
  oversize / wrong-type drops via `onDropRejected` + inline localized warning
- Both client and server use the same allowlist (defense in depth — browsers send
  inconsistent MIME types for `.csv`, `.zip`, and `.docx`)

**Depends on**: Tasks 2, 3, 6a (existing implementation)

---

## Task 10: Idempotent upload retry (PR review #2) [FE] ✅

PR review on the initial implementation flagged that when one file in a batch
upload fails, the *full* `pendingFiles` array stayed in state, so clicking
Upload again re-sent every successful file and created duplicate `Document`
rows.

Changed `uploadPendingFiles` in
`src/js/custom/stockTransferDocuments/components/StockTransferDocumentsPanel.jsx`
to a per-file try/catch over a serial promise chain that accumulates the failed
files and writes only those back into `pendingFiles`. Successful files
disappear from the pending list on resolve, so a retry only sends the still-failed
ones. `loadDocuments()` runs whenever at least one upload succeeded so the
documents list reflects what got persisted.

The existing `uploadError` boolean became a `uploadErrorMessage` slot holding
either `null`, the generic `M.uploadError` (when every file failed), or the new
`M.partialUploadError` (when some succeeded and some didn't). The panel renders
either message via the existing `<Warning>` component.

**Files (MODIFY — custom):**
- `src/js/custom/stockTransferDocuments/components/StockTransferDocumentsPanel.jsx`
  — per-file try/catch reduce, `uploadErrorMessage` state, conditional refresh.
- `src/js/custom/stockTransferDocuments/utils/messages.js` — adds
  `partialUploadError` key.
- `src/js/custom/stockTransferDocuments/__tests__/StockTransferDocumentsPanel.test.jsx`
  — new tests for partial-failure pending state, retry idempotence, and the
  multi-file drop helper `dropFiles`.

**Files (MODIFY — upstream, +1 line):**
- `grails-app/i18n/messages.properties` — appends
  `react.custom.stockTransferDocuments.upload.partialError` next to the
  existing custom-feature keys.

**Acceptance criteria:**
- After A succeeds and B fails in a batch `[A, B, C]`, only `B` remains in
  `pendingFiles`; A and C are uploaded.
- Retrying the upload sends only the files still in `pendingFiles` (no
  duplicates of already-persisted documents).
- Partial failures show `M.partialUploadError`; full-batch failures show the
  generic `M.uploadError`.
- Documents list refreshes when ≥1 upload succeeded; skips refresh when nothing
  changed server-side.

**Backend dedup (added after manual testing):**
Manual testing with DevTools Offline mode surfaced that the frontend fix
alone is not enough — a network drop can cancel the request browser-side
after the server has already persisted the file, and the client treats it as
a failure. The retry then inserts a duplicate. Hardened
`CustomStockTransferDocumentService.uploadDocument` to skip insertion when
`order.documents` already contains a `Document` with the same sanitized
filename and byte size, returning 200 without creating a new record. The
retry becomes a server-side no-op, so duplicates are prevented regardless
of network flakiness.

- Spock test added in `CustomStockTransferDocumentServiceSpec` using a
  metaClass-stubbed `Order.get` to simulate an order with a pre-existing
  same-name-same-size document. Verifies no `addToDocuments` / `save` calls
  on dedup hit.

**Depends on**: Tasks 6, 6a, 9 (existing implementation + #1 hardening)

---

## Task 8: Integration spec for upstream+custom completion flow [BE] ✅

**File**: `src/integration-test/groovy/org/pih/warehouse/custom/stocktransferdocuments/CustomStockTransferDocumentServiceIntegrationSpec.groovy` *(NEW)*

`@Integration @Rollback` Spock spec that exercises the full upstream+custom wiring against
a real testcontainer MySQL with full Liquibase migrations + seed data:

1. Verifies the custom service bean is present in the application context.
2. Verifies `StockTransferService.customStockTransferDocumentService` is wired (this is
   the assertion that protects the Task-5 upstream touch — any future merge that drops
   the injection field fails CI).
3. `validateForCompletion(null)` → throws `IllegalArgumentException`.
4. Neither parent depot enforces → no exception.
5. Parent depot enforces `REQUIRE_TRANSFER_OUT_DOCUMENT` (origin) and no documents → `ValidationException`.
6. Parent depot enforces `REQUIRE_TRANSFER_IN_DOCUMENT` (destination) and no documents → `ValidationException`.
7. **Origin BIN enforces `REQUIRE_TRANSFER_OUT_DOCUMENT`** (parent depot does not), order item
   carries a transient child Location with the activity code → `ValidationException`. Verifies
   the bin-aware path through real GORM.
8. **Destination BIN enforces `REQUIRE_TRANSFER_IN_DOCUMENT`** — mirror of the above.

The fixture toggles `supportedActivities` on the seeded `Main Warehouse` (id=1) and uses
it as both parent origin and parent destination (depending on the test). For the bin-level
tests, a transient child Location is constructed (never saved — `validateForCompletion`
only reads `supportedActivities`, so persistence is not required) and assigned to a transient
`OrderItem`. All mutations on the parent location are reverted by the surrounding `@Rollback`
transaction so neighbouring specs are unaffected. We chose to reuse the seeded parent location
instead of building a fresh one because `Location` has a custom `organization` validator
(`DEPOT`/`SUPPLIER` types require an org) that makes ad-hoc construction fragile.

**Acceptance criteria:**
- All eight scenarios pass via `./gradlew integrationTest --tests "...custom.stocktransferdocuments.*"`
- Spec is in a custom package (not mixed into upstream integration test suites)
- Spec uses `@Rollback` so each feature method opens its own Hibernate session
- Spec uses the seeded `Main Warehouse` instead of constructing a fresh parent location
- The bin-level tests use a transient child `Location` (no DB save) — the validator's
  reads are purely in-memory

**Depends on**: Task 5

---

## Implementation Order

```
Task 1 (ActivityCode) ──→ Task 2 (custom service) ──┬──→ Task 3 (custom controller) ──→ Task 4 (URL mapping)
                                                     │
                                                     └──→ Task 5 (completion hook) ──→ Task 8 (integration test)

Task 7a (webpack alias) ─┐
Task 6a (i18n wiring) ───┴──→ Task 6 (custom panel) ──→ Task 7 (mount in upstream page)
```

Backend (1–5, 8) and frontend (6a, 7a, 6, 7) can proceed in parallel after Task 2.

## Upstream Touch Point Summary

| Task | File | Actual edit |
|------|------|-------------|
| 1 | `ActivityCode.groovy` | 6 lines (2 enum entries + 2 list() entries + 1 `// custom` marker + 1 blank line) |
| 4 | `UrlMappings.groovy` | 6 lines (1 mapping block + comment + spacing) |
| 5 | `StockTransferService.groovy` | 2 lines (1 service field + 1 delegate call) |
| 7 | `StockTransferCheckPage.jsx` | ~10 lines (1 import swap + 1 default-state field + 1 panel mount + 1 disabled-prop edit + 2 redirect URL swaps to use `ORDER_URL.show`) |
| 7a | `webpack.config.js` (`custom` alias) | 1 line |
| 6a / 9 / 10 | `grails-app/i18n/messages.properties` | ~19 lines (4 enum/code keys + 8 base React keys + 3 backend upload-validation keys + 3 React upload-validation keys + 1 partial-upload-error key, all appended under `# Custom:` comments) |

**Total: ~48 lines across 6 upstream files.** All edits documented above; no
incidental cleanups, no reordering, no reformatting. The Boy Scout Rule is
suspended for every file in this list.

**Conditional rows that did NOT end up needed:**
- `jest.config.js` / `package.json` `moduleNameMapper` — Jest already resolves
  via `moduleDirectories: ["src/js"]`, so `import 'custom/...'` works automatically.
- `IntlProvider` setup — switching the panel to `utils/Translate` (which already
  provides `onMissingTranslation`) made this unnecessary.
- `application.yml` — the i18n bundle move into `messages.properties` directly
  removed the need to register a custom `messageSource` basename.

If any task's diff grows beyond what's documented here, pause and revisit the
design before committing.
