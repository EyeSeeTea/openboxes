# Stock Transfer Document Upload — Technical Design

## Upstream Compatibility

This fork must stay mergeable with upstream OpenBoxes. Per `CLAUDE.md`
(*Upstream Compatibility*) and `.claude/rules/custom-package-isolation.md`, every
change below is designed to be as isolated and minimally invasive as possible.

**Rules-alignment pass (2026-04-15):** this design was re-reviewed against the
updated `custom-package-isolation.md` rule. Changes from the original design:
1. Frontend files now live under the rule's prescribed sub-folders
   (`components/`, `utils/`, `__tests__/`) instead of a flat folder.
2. Backend i18n keys appended directly to upstream `messages.properties` instead
   of a custom-folder bundle (Grails 3.3 doesn't auto-load `i18n/` sub-folders;
   appending is a smaller net upstream touch than registering a `messageSource`
   override).
3. The `custom/*` webpack alias is now a **definite** upstream touch (verified
   missing in `webpack.config.js`) rather than conditional.
4. Liquibase wiring notes updated to match the rule's
   `custom/changelog.groovy` aggregator pattern.

**Post-initial-commit revisions (2026-04-15):** five design changes after manual
UI smoke tests surfaced bugs:
1. **Activity code split.** Single `REQUIRE_TRANSFER_DOCUMENT` was too coarse —
   replaced with `REQUIRE_TRANSFER_OUT_DOCUMENT` (outbound) and
   `REQUIRE_TRANSFER_IN_DOCUMENT` (inbound). Either or both can be enabled.
   See revision 5 below for the bin-aware check that came after.
2. **i18n rendering bug.** Panel imported `Translate` from `react-localize-redux`
   directly, which does **not** fall back to `defaultMessage` when a key is
   missing — labels rendered as raw key strings. Switched to the project wrapper
   `utils/Translate` which sets `onMissingTranslation`.
3. **Frontend gate is now fail-closed, not fail-open.** Parent's
   `customCanComplete` defaults to `false` so the Complete button is disabled
   until the panel confirms (or load fails — fail-closed in either case). The
   `apiClient` global response interceptor (`handleError` in
   `src/js/utils/apiClient.jsx`) already pops a notification toast for every
   non-2xx, including the backend `ValidationException` we throw from
   `validateForCompletion`, so the component does NOT need a custom
   `Alert.error` in its `.catch` — that would be a duplicate popup.
4. **Post-completion redirect** stays at `STOCK_TRANSFER_URL.show(id)` (the
   dedicated stock transfer show page). A read-only Documents tab was added to
   `stockTransfer/show.gsp` via a custom template
   (`grails-app/views/custom/stockTransferDocuments/_documentsList.gsp`) so
   users can see uploaded documents without landing on the generic `order/show`
   page (which confusingly displays purchase-order UI like dollar amounts).
5. **`isDocumentRequired` is bin-aware.** The first revision only checked the
   order header (`order.origin` / `order.destination`). A second smoke test
   surfaced that admins want to enable the activity code on a **specific bin**
   (e.g., a Quarantine bin), not the whole parent depot. The check now walks
   four sources: order header origin, order header destination, every
   `orderItem.originBinLocation`, and every `orderItem.destinationBinLocation`.
   Any one returning `supports(<flag>)` blocks completion. This is the
   load-bearing change for the quarantine-release workflow — it's the entire
   reason the feature was requested.

**Post-PR-review hardening (2026-04-28):** PR #1 review (xurxodev) flagged the
upload path was missing the file-upload validation required by
`.claude/rules/web/security.md` (MIME allowlist, size cap, filename
sanitization, both client and server side). Added in custom code with one
small upstream touch (6 new i18n keys appended to `messages.properties`):

- New helper `org.pih.warehouse.custom.stockTransferDocuments.UploadConstraints`
  — content-type + extension allowlists (PDF, image, Word, Excel, CSV, ZIP),
  `DEFAULT_MAX_BYTES = 10 MB` (matches upstream `Document.fileContents` GORM cap),
  `sanitizeFilename()` (strips path components and ISO control chars, replaces
  Windows-unsafe chars `<>:"|?*` with `_`, truncates to 255 chars).
- New typed exception `UploadValidationException(messageCode, args)` so the
  controller can resolve a localized error string.
- `CustomStockTransferDocumentService.uploadDocument` now validates size, MIME,
  extension, and filename and persists the sanitized name — and reads the cap
  from `openboxes.custom.stockTransferDocuments.maxUploadSizeBytes` (10 MB
  default) so operators can tune via environment config without touching
  upstream files.
- `CustomStockTransferDocumentController.upload` wraps the service call,
  catches `UploadValidationException`, and renders `400 { errorMessage }` with
  the resolved i18n string. Logs a `warn` audit line (no document bytes).
- Frontend `<Dropzone>` gets `accept` + `maxSize: 10 MB` props and an
  `onDropRejected` handler that surfaces `'file-too-large'` /
  `'file-invalid-type'` codes as a localized inline `<Warning>`.

**Tunable cap rationale:** the 10 MB default matches upstream
`Document.fileContents`'s `maxSize: 10485760` constraint — anything larger
fails GORM validation on save, and lifting that cap requires modifying
upstream `Document.groovy` (forbidden by isolation rules). Operators wanting
smaller caps set `openboxes.custom.stockTransferDocuments.maxUploadSizeBytes`
in environment override config. To use the full 10 MB, operators must also
raise the Spring multipart limit `grails.controllers.upload.maxFileSize`
(default 2 MB in upstream `application.yml`) in env override config — the
framework rejects oversize requests before our service runs.

**Strategy:**
- All new backend code lives under a dedicated custom package:
  `org.pih.warehouse.custom.stocktransferdocuments.*`
  (`grails-app/controllers/org/pih/warehouse/custom/stocktransferdocuments/`,
  `grails-app/services/org/pih/warehouse/custom/stocktransferdocuments/`).
- All new frontend code lives under `src/js/custom/stockTransferDocuments/`,
  split into `components/`, `utils/`, and `__tests__/` sub-folders per the
  custom-package-isolation rule.
- The upload, list, and `documentRequired` flag are served by **new custom endpoints**
  under `/api/custom/stockTransfers/{id}/documents` rather than extending the existing
  `StockTransferApiController` response shape.
- The React UI change is a **single-line mount point** in the existing page, rendering
  a custom component imported from `src/js/custom/stockTransferDocuments/`.

**Unavoidable upstream touch points (kept as small as possible):**

| File | Why it must be touched | Size of edit |
|------|------------------------|--------------|
| `src/main/groovy/org/pih/warehouse/core/ActivityCode.groovy` | Enum values + `list()` cannot be extended from outside the file. We add two new entries (`REQUIRE_TRANSFER_OUT_DOCUMENT`, `REQUIRE_TRANSFER_IN_DOCUMENT`). | 2 enum entries + 2 list() entries (~6 lines) |
| `grails-app/controllers/org/pih/warehouse/UrlMappings.groovy` | URL mappings live in a single file | 1 block (~6 lines) |
| `grails-app/services/.../StockTransferService.groovy` — `completeStockTransfer()` | Completion gating must run inside the existing transactional flow; delegates to a custom validator to keep the edit to a single `if` block | 2 lines (1 service field + 1 delegate call) |
| `src/js/components/stock-transfer/StockTransferCheckPage.jsx` | React page has no extension slot; the render tree must be touched once to mount the custom documents panel, and the parent state needs a `customCanComplete` field driven by the panel's callback. Redirects use `STOCK_TRANSFER_URL.show(id)` (the dedicated transfer show page). Backend errors are not handled in the component — the existing `apiClient` global response interceptor already pops a toast. | ~10 lines (1 import + 1 default-state field + 1 bound method + 1 panel mount + 1 disabled-prop edit) |
| `grails-app/views/stockTransfer/show.gsp` | Stock transfer show page needs a Documents tab to display uploaded documents after completion. Content lives in a custom template; the GSP edit is 2 lines (1 `<li>` + 1 `<div>` rendering the custom template). | 2 lines |
| `grails-app/views/inventoryItem/_actionsCurrentStock.gsp` | Hide "Transfer Stock" action on stock card when the source bin has `REQUIRE_TRANSFER_OUT_DOCUMENT` — prevents bypassing the document gate via the stock card's direct transfer dialog. | 2 lines (`g:if` wrapper) |
| `grails-app/views/inventoryItem/_transferStock.gsp` | Point destination bin dropdown to custom filtered endpoint that excludes bins with `REQUIRE_TRANSFER_IN_DOCUMENT`. | 1 line (data-url change) |
| `webpack.config.js` | `custom/*` alias does not exist yet (verified: only `components`, `hooks`, `utils`, etc. are aliased). First custom feature must register it. | 1 line (`custom: path.resolve(SRC, 'custom')`) |
| `grails-app/i18n/messages.properties` | Backend i18n keys for the two new activity codes (used by both the GSP location-edit page and the React location-config screen), the custom-feature React keys, and (post-PR-review) upload-validation error keys (MIME/size/filename). Grails 3.3 only auto-loads `messages*.properties` from the `i18n/` root, and adding a `messageSource` override would itself be an upstream touch — appending ~20 lines is the smallest net change. | ~20 lines (4 enum/code keys + 1 custom backend key + 9 React keys + 3 upload-validation backend keys + 3 upload-validation React keys, all appended at the end of the file under `# Custom:` comments) |

**Explicitly NOT modified (vs. the earlier plan):**
- ~~`StockTransfer.groovy` (API model) `toJson()` reshape and new `documentRequired` field~~
  → replaced by a custom endpoint that returns `{ documentRequired, documents[] }`.
- ~~`StockTransferApiController.read()` picklist guard and `documentRequired` population~~
  → the custom endpoint is called separately by the frontend.
- ~~`StockTransferService.getDocuments()` rewrite~~ → a new `CustomStockTransferDocumentService`
  reads `Order.documents` directly; the picklist behavior in upstream `getDocuments()` is untouched.

**Liquibase:** no schema change is required (the `order_document` join table already exists).
If that changes, follow the aggregator pattern from `custom-package-isolation.md`:
- new changeset file: `grails-app/migrations/custom/2026-04-15-stock-transfer-documents.groovy`
- aggregator: `grails-app/migrations/custom/changelog.groovy` adds `include file: '2026-04-15-stock-transfer-documents.groovy'`
- master `grails-app/migrations/changelog.groovy` gets **one** new line: `include file: 'custom/changelog.groovy'` (this line is shared infrastructure — the first custom migration to land will own it, subsequent features just extend the aggregator).

**Boy Scout Rule suspended** for the six upstream files above: do not reformat, re-order
imports, or clean up unrelated code in them. The rule is only enforced inside
`org.pih.warehouse.custom.*`, `src/js/custom/*`, and `grails-app/migrations/custom/*`.

## Architecture

The change follows the existing layered architecture and reuses established patterns,
but confines all new code to a dedicated custom package to protect upstream merges:

```
Domain (Order.documents, Document, ActivityCode — upstream, unchanged except +1 enum value)
    ^
    | used by
    |
Custom Service (CustomStockTransferDocumentService — list docs, validate completion)
    ^
    | called by
    |
Custom API Controller (CustomStockTransferDocumentController
                       — GET/POST /api/custom/stockTransfers/{id}/documents)
    ^
    | called by
    |
React Frontend — custom panel component (src/js/custom/stockTransferDocuments/)
    mounted once into StockTransferCheckPage via a single JSX line
```

No new domain classes or tables. The existing `order_document` join table is used.

## Component: ActivityCode Extension (upstream touch point)

**File**: `src/main/groovy/org/pih/warehouse/core/ActivityCode.groovy` *(MODIFY — 6 lines)*

Add **two** new enum entries directly after `REQUIRE_ACCOUNTING`:

```groovy
// custom
REQUIRE_TRANSFER_OUT_DOCUMENT('REQUIRE_TRANSFER_OUT_DOCUMENT'),
REQUIRE_TRANSFER_IN_DOCUMENT('REQUIRE_TRANSFER_IN_DOCUMENT'),
```

…and the matching pair in the `static list()` method (so they appear in the location-edit
admin checklist):

```groovy
REQUIRE_ACCOUNTING,
REQUIRE_TRANSFER_OUT_DOCUMENT,
REQUIRE_TRANSFER_IN_DOCUMENT,
ENABLE_CENTRAL_PURCHASING,
```

Admins assign them to location types or individual locations via the existing
location admin UI — no configuration UI changes needed. The pattern mirrors
existing enforcement codes like `REQUIRE_ACCOUNTING` and `HOLD_STOCK`.

Enum values and the `list()` array cannot be extended from outside the file, so these are
unavoidable upstream edits. Keep the diff to two enum entries + two list entries; do not
reorder existing entries.

## Component: Custom Document API Controller (NEW — isolated)

**File**: `grails-app/controllers/org/pih/warehouse/custom/stocktransferdocuments/CustomStockTransferDocumentController.groovy` *(NEW)*

A new controller serving a custom URL namespace, leaving the upstream
`StockTransferApiController` untouched.

```
GET  /api/custom/stockTransfers/{id}/documents
     → { documentRequired: boolean,
         documents: [ { id, name, documentType, contentType, uri } ] }

POST /api/custom/stockTransfers/{id}/documents
     Content-Type: multipart/form-data
     Body: fileContents (MultipartFile)
     → { "data": "Document was uploaded successfully" }
```

Implementation delegates to `CustomStockTransferDocumentService`. The upload action follows
the `StockMovementController.uploadDocument` pattern (read-only reference — not modified):
look up `Order`, build `Document` from the multipart file, `order.addToDocuments(doc).save(flush: true)`.

**URL Mapping** — single new block in `UrlMappings.groovy` *(MODIFY — 1 block)*:
```groovy
"/api/custom/stockTransfers/$id/documents"(parseRequest: true) {
    controller = "customStockTransferDocument"
    action = [GET: "list", POST: "upload"]
}
```

## Component: Custom Document Service (NEW — isolated)

**File**: `grails-app/services/org/pih/warehouse/custom/stocktransferdocuments/CustomStockTransferDocumentService.groovy` *(NEW)*

Business logic lives here rather than extending the upstream `StockTransferService`:

```
listDocuments(Order order):
    return order.documents.collect { doc ->
        [ id: doc.id,
          name: doc.name ?: doc.filename,
          documentType: doc.documentType?.name,
          contentType: doc.contentType,
          uri: doc.link ]                            // upstream Document.getLink()
    }

originSideRequiresOutDocument(Order order):
    // First check the parent depot
    if (order?.origin?.supports(REQUIRE_TRANSFER_OUT_DOCUMENT)) return true
    // Then check every order item's source bin (e.g., Quarantine bin)
    return order?.orderItems?.any { item ->
        item.originBinLocation?.supports(REQUIRE_TRANSFER_OUT_DOCUMENT)
    }

destinationSideRequiresInDocument(Order order):
    if (order?.destination?.supports(REQUIRE_TRANSFER_IN_DOCUMENT)) return true
    return order?.orderItems?.any { item ->
        item.destinationBinLocation?.supports(REQUIRE_TRANSFER_IN_DOCUMENT)
    }

isDocumentRequired(Order order):
    return originSideRequiresOutDocument(order) || destinationSideRequiresInDocument(order)

validateForCompletion(Order order):
    if (!order) throw new IllegalArgumentException(NULL_ORDER_ERROR)
    if (isDocumentRequired(order) && !order.documents) {
        order.errors.reject(DOCUMENT_REQUIRED_CODE, DOCUMENT_REQUIRED_ERROR)
        log.warn "custom_stock_transfer_completion_blocked orderId=... originLocationId=... destinationLocationId=..."
        throw new ValidationException(DOCUMENT_REQUIRED_ERROR, order.errors)
    }
```

The validator throws `ValidationException` with a `BeanPropertyBindingResult` carrying the
reject code `customStockTransferDocument.required.error`, which resolves via Grails'
`messageSource` to the upstream `messages.properties` entry. The `log.warn` is audit-grade.

The upstream `StockTransferService.getDocuments()` is **not** modified. The custom frontend
panel fetches documents from the custom endpoint, independently of whatever the upstream
read endpoint returns.

## Component: Completion Enforcement (upstream touch point)

**File**: `grails-app/services/org/pih/warehouse/stockTransfer/StockTransferService.groovy` *(MODIFY — ≤ 5 lines)*

In `completeStockTransfer()`, before proceeding, add a single delegation call:

```groovy
// Custom: require document attachment when enforced for the origin location.
customStockTransferDocumentService.validateForCompletion(Order.get(stockTransfer.id))
```

All the logic lives in the custom service — the upstream edit is one injection line plus
the delegating call. Do not alter any surrounding code, comments, or imports beyond
adding the service field.

The origin location is checked (inside the custom service) because the enforcement is
about releasing stock from a controlled location (e.g., quarantine bin's parent depot).

## Component: StockTransfer API Model (NOT modified)

The earlier plan modified `src/main/groovy/org/pih/warehouse/api/StockTransfer.groovy` to
fix the `toJson()` documents default and add a `documentRequired` field. **That change is
dropped** — the custom endpoint returns `documentRequired` and `documents[]` directly, so
the upstream API model stays untouched.

## Component: Frontend — Custom Panel (NEW — isolated)

**File**: `src/js/custom/stockTransferDocuments/components/StockTransferDocumentsPanel.jsx` *(NEW)*

A self-contained React component that encapsulates everything about the documents feature:

- Fetches `GET /api/custom/stockTransfers/{id}/documents` on mount
- Renders existing documents as download links
- Dropzone (via `react-dropzone`, already a project dependency) for file selection
- Pending files list with remove buttons
- Upload button → POST to `/api/custom/stockTransfers/{id}/documents`, then re-fetch
- Enforcement warning when `documentRequired` is true and no documents exist
- Exposes a `canComplete` boolean to the parent via an `onCanCompleteChange(canComplete)`
  callback so the parent page can disable its Complete button without the panel reaching
  into parent state

#### `canComplete` initial-state contract (authoritative)

`canComplete` is emitted by the panel after each load attempt. Parent's
`customCanComplete` defaults to **`false`** — the Complete button is disabled until the
panel has confirmed the user can proceed. **Fail-closed.**

| Panel state | `canComplete` value emitted |
|-------------|----------------------------|
| Mounted, initial load in flight | (parent default `false` — panel emits nothing yet) |
| Load succeeded, `documentRequired=false` | `true` |
| Load succeeded, `documentRequired=true`, 0 documents | `false` |
| Load succeeded, `documentRequired=true`, ≥1 document | `true` |
| Load failed (network error) | `false` (fail-closed) — panel also shows a fetch error alert |

**Fail-closed rationale (post-initial-commit revision):** the original contract was
fail-open, on the theory that the backend was the authoritative gate and the frontend
was UX sugar. In practice the Complete button was clickable for the brief window
between mount and the panel's first fetch resolving — fast clicks bypassed the gate.

Fail-closed eliminates the race. The parent's `customCanComplete` default is `false`,
so the button is disabled until the panel explicitly enables it. Combined with the
backend gate (which still runs and throws `ValidationException`), this is defence in
depth.

If the user does manage to bypass the disabled button (e.g. via devtools), the
`apiClient` global response interceptor in `src/js/utils/apiClient.jsx` (`handleError`)
already pops a notification toast for every non-2xx — including our backend
`ValidationException` — so the component itself does **not** need a custom
`Alert.error(...)` in its `.catch`. Adding one would produce a duplicate popup.

Supporting files (all NEW, all under `src/js/custom/stockTransferDocuments/`,
layout per `custom-package-isolation.md`):
- `utils/api.js` — URL builders and axios calls for the custom endpoint
- `utils/messages.js` — react-intl message key definitions
- `components/StockTransferDocumentsPanel.scss` — isolated styles
- `__tests__/StockTransferDocumentsPanel.test.jsx` — Jest/RTL tests

### Upload flow
```
Panel mounts → GET /api/custom/stockTransfers/{id}/documents
User drops file(s) on Dropzone
    → files stored in panel state
    → "Upload" button becomes visible
    → User clicks Upload
    → POST /api/custom/stockTransfers/{id}/documents
    → On success: clear files, re-fetch documents
    → On error: show Alert
```

## Component: Frontend — Mount Point (upstream touch point)

**File**: `src/js/components/stock-transfer/StockTransferCheckPage.jsx` *(MODIFY — ~14 lines)*

Edits to this upstream file (each in its own hunk; no incidental changes elsewhere):

1. Add one import (uses the new `custom/*` webpack alias):
   ```javascript
   import StockTransferDocumentsPanel from 'custom/stockTransferDocuments/components/StockTransferDocumentsPanel';
   ```
2. Initial state — add `customCanComplete: false` (fail-closed default):
   ```jsx
   this.state = {
     stockTransfer: stockTransfer || {},
     columns,
     customCanComplete: false,
   };
   ```
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
5. **Post-completion redirect** — keep `STOCK_TRANSFER_URL.show(id)` (both
   redirect sites, lines ~194 and ~282). A read-only Documents tab was added
   to `stockTransfer/show.gsp` so users see their uploads on the transfer page
   rather than the confusing `order/show` page.
6. **Backend errors** — `save().catch(...)` stays as `() => this.props.hideSpinner()`.
   The existing `apiClient` global response interceptor (`handleError` in
   `src/js/utils/apiClient.jsx`) already pops a notification toast for every
   non-2xx response, including our backend `ValidationException`, so adding a
   custom `Alert.error(...)` in this `.catch` would be a duplicate popup.

No other edits to this file. Do not refactor, reformat, or rename anything else.

### Completion button logic
```
disabled = (existing upstream disabled expression)
        || (customCanComplete === false)
```
`customCanComplete` defaults to **`false`**. The panel's `onCanCompleteChange` callback
flips it to `true` only after a successful load that reports the user is allowed to
proceed (see the *`canComplete` initial-state contract* above). On fetch failure the
panel emits `false` and the button stays disabled — fail-closed, with the backend gate
as a second line of defence.

## Cross-cutting concerns

### Security & permissions

`CustomStockTransferDocumentController` must gate both actions with the same
`@Secured` / role checks as the upstream `StockTransferApiController` so the custom
namespace does not become a permission bypass:

- `list` (GET): same roles required to read a stock transfer upstream
- `upload` (POST): same roles required to edit a stock transfer upstream (typically
  `ROLE_INVENTORY_MANAGER` or equivalent — confirm against upstream controller)

The custom service reads the roles from Grails config (not hardcoded) so upstream
changes to the role names propagate automatically. Unit tests for the controller must
assert that an unauthorized user receives a 403.

### Grails service injection into custom package

Services under `grails-app/services/org/pih/warehouse/custom/...` are picked up by
Grails 3.3 component scanning just like any other service. The upstream
`StockTransferService` declares the dependency via **by-convention injection**:

```groovy
def customStockTransferDocumentService   // injected by name, no annotations
```

**Do not** add `@Autowired` or constructor injection — Grails convention is the
project standard and mixing styles creates noise in the upstream diff.

### `Order.get(stockTransfer.id)` — PK mapping assumption

In the upstream domain model, a `StockTransfer` is an API-layer wrapper around an
`Order` and `stockTransfer.id` is the `Order` primary key. Task 5 must verify this
before coding: if the ID is actually a different identifier (e.g., a UUID or a
`StockTransfer` wrapper ID), the validator will silently pass against `null` and the
enforcement will be broken. Add a guard in `validateForCompletion` that throws a clear
error if the `Order` lookup returns `null`.

### i18n

All user-visible strings (panel labels, warning messages, upload success/error toasts,
the backend `ValidationException` reject code, and the two new activity-code labels)
live as message keys under two namespaces:

- `enum.ActivityCode.REQUIRE_TRANSFER_OUT_DOCUMENT` /
  `enum.ActivityCode.REQUIRE_TRANSFER_IN_DOCUMENT` — used by the GSP location-edit
  page via `format:metadata`.
- `react.locationsConfiguration.ActivityCode.REQUIRE_TRANSFER_OUT_DOCUMENT` /
  `react.locationsConfiguration.ActivityCode.REQUIRE_TRANSFER_IN_DOCUMENT` — used by
  the React location-config screen.
- `customStockTransferDocument.required.error` — the reject code thrown by
  `validateForCompletion`; resolved by Grails' `messageSource` for the
  `ValidationException` message.
- `react.custom.stockTransferDocuments.*` — all React-side panel strings (panel title,
  empty state, dropzone prompt, upload/remove buttons, fetch/upload errors, required
  warning).
- `customStockTransferDocument.upload.invalidType.error` /
  `customStockTransferDocument.upload.tooLarge.error` /
  `customStockTransferDocument.upload.invalidFilename.error` — backend reject codes
  resolved by `messageSource.getMessage(...)` in the controller's
  `UploadValidationException` handler. The `tooLarge` key takes the configured
  byte cap as `{0}`.
- `react.custom.stockTransferDocuments.upload.invalidType.error` /
  `react.custom.stockTransferDocuments.upload.tooLarge.error` /
  `react.custom.stockTransferDocuments.upload.invalidFilename.error` — React-side
  inline warnings shown by `onDropRejected` when `react-dropzone` filters a
  drop client-side.

**Storage:** all of the above are appended to the **upstream**
`grails-app/i18n/messages.properties` file in a single hunk at the end (and to
the matching upstream lines for the `REQUIRE_ACCOUNTING` / `ENABLE_CENTRAL_PURCHASING`
neighbours). Grails 3.3 only auto-loads `messages*.properties` from the `i18n/` root,
so an `i18n/custom/` sub-folder bundle would require either (a) a `messageSource`
override in `resources.groovy`, or (b) a `BeanPostProcessor` to add basenames — both of
which are larger upstream touches than just appending the keys directly. The keys are
namespaced and grouped under a `# Custom: stock-transfer-document-upload` comment so
future merges can identify them.

**Frontend rendering:** the React panel imports `Translate` from
**`utils/Translate`** (the project wrapper) — **not** from `react-localize-redux`
directly. The raw `react-localize-redux` `<Translate>` does not fall back to
`defaultMessage` when a key is missing; the project wrapper supplies an
`onMissingTranslation: () => defaultMessage` option. The first commit of this feature
incorrectly used the raw component and rendered raw key strings until the Grails-side
keys were added — the wrapper plus the `messages.properties` keys together close the
loop, and the `defaultMessage` prop on each `<Translate>` is the in-source fallback if
a key ever goes missing from the bundle.

The panel's English defaults are mirrored in
`src/js/custom/stockTransferDocuments/utils/messages.js` (a default-exported constant
object), so a developer touching the panel only needs to look at one file to find both
the key and the default text.

### Logging

Follow upstream logging conventions (`log.info` / `log.warn` via Grails-injected log):

- `CustomStockTransferDocumentService.validateForCompletion`: `log.warn` when blocking
  a completion (`stockTransferId`, `originLocationId`) — these are audit-worthy events
- `CustomStockTransferDocumentController.upload`: `log.info` on success (document id,
  size, user); `log.warn` on failure
- No sensitive content in log messages (don't log document bytes or full file contents)

### Webpack alias / module resolution

**Verified 2026-04-15**: the `custom/*` path alias does **not** exist in
`webpack.config.js` (only `components`, `hooks`, `utils`, `reducers`, etc. are
aliased). Task 7 must register a new alias as a 1-line upstream touch:

```js
// webpack.config.js — inside resolve.alias
custom: path.resolve(SRC, 'custom'),
```

If `jsconfig.json` also needs updating for IDE path resolution, that counts as a
second 1-line upstream touch (budget separately).

## Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| Large file uploads cause DB bloat | Document domain already enforces 10MB max via constraints; no change needed |
| Users forget to upload before completing | `REQUIRE_TRANSFER_DOCUMENT` activity provides enforcement; backend validation is the hard gate |
| Frontend-only enforcement bypassed via API | Backend `completeStockTransfer()` checks independently of frontend |
| Picklist documents mixed with uploaded docs | Each document in the response includes `documentType` for frontend differentiation if needed |
| Custom namespace bypasses upstream auth | Custom controller reuses the same `@Secured` roles as upstream `StockTransferApiController`; unit tests assert 403 for unauthorized |
| Panel GET fails; user blocked from completing | Fail-open initial-state contract (see *`canComplete` initial-state contract* above); backend is authoritative |
| `stockTransfer.id` is not an `Order` PK | Task 5 verification step + null-guard in custom service |
| `custom/*` webpack alias missing | Budgeted ≤ 1-line upstream touch registered in Task 7 |

## Validation

- [ ] `POST /api/custom/stockTransfers/{id}/documents` persists the file in the `order_document` join table
- [ ] `GET /api/custom/stockTransfers/{id}/documents` returns uploaded documents with download URIs and the `documentRequired` flag
- [ ] Clicking a document link downloads the correct file
- [ ] Upstream `GET /api/stockTransfers/{id}` behavior is unchanged (picklist PDF still returned as before)
- [ ] Documents uploaded via our custom endpoint appear in the existing upstream `order/show` Documents tab (same `Order.documents` relation)
- [ ] Documents can be uploaded before transfer completion
- [ ] Documents can be uploaded after transfer completion
- [ ] With `REQUIRE_TRANSFER_OUT_DOCUMENT` on **the origin parent depot**: completing an outbound transfer without documents throws `ValidationException`
- [ ] With `REQUIRE_TRANSFER_IN_DOCUMENT` on **the destination parent depot**: completing an inbound transfer without documents throws `ValidationException`
- [ ] With `REQUIRE_TRANSFER_OUT_DOCUMENT` on **a specific source bin** (parent depot does NOT enforce): completing a transfer that picks an item from that bin without documents throws `ValidationException` — this is the quarantine-release workflow
- [ ] With `REQUIRE_TRANSFER_IN_DOCUMENT` on **a specific destination bin** (parent depot does NOT enforce): completing a transfer that lands an item in that bin without documents throws `ValidationException`
- [ ] With any flag set and ≥1 document attached: completion succeeds
- [ ] Without any flag set: completing without documents still succeeds (no regression)
- [ ] Frontend Complete button is **disabled by default on mount** (fail-closed) and only becomes enabled after the panel reports the user can proceed
- [ ] Frontend shows warning message when documents are required
- [ ] Bypassing the disabled Complete button (e.g. via devtools) and POSTing a completion to the backend produces a notification toast surfaced by the existing `apiClient.handleError` interceptor (no duplicate `Alert.error` from the component)
- [ ] Post-completion redirect lands on `order/show/<id>` (where the upstream Documents tab is visible)
- [ ] Activity-code labels (`enum.ActivityCode.REQUIRE_TRANSFER_OUT_DOCUMENT` /  `..._IN_DOCUMENT`) render as human strings on the location-edit page (GSP `format:metadata` lookup)
- [ ] Panel labels render as human strings (verifies the `Translate` wrapper from `utils/Translate` is being used, not raw `react-localize-redux`)
- [ ] Upstream-file diffs stay within the budgets listed in *Upstream Compatibility* (ActivityCode: ~6 lines; UrlMappings: 1 block; StockTransferService: 2 lines; StockTransferCheckPage.jsx: ~14 lines; `webpack.config.js`: 1 alias line; `messages.properties`: ~12 keys)
- [ ] Every new backend file lives under `org.pih.warehouse.custom.stocktransferdocuments.*`
- [ ] Every new frontend file lives under `src/js/custom/stockTransferDocuments/{components,utils,__tests__}/`
- [ ] Every new backend test lives under `src/test/groovy/org/pih/warehouse/custom/stocktransferdocuments/` or `src/integration-test/groovy/org/pih/warehouse/custom/stocktransferdocuments/`
- [ ] No reformatting, import reordering, or Boy-Scout cleanups inside upstream files

## Confidence: 9/10

High confidence because:
- The `Order.documents` relationship and `order_document` table already exist — no schema migration
- The upload pattern is well-established (`StockMovementController.uploadDocument` as a read-only template)
- The `ActivityCode` + `Location.supports()` enforcement pattern is used in 10+ places
- `react-dropzone` is already a project dependency with existing usage in `SendMovementPage`
- The vast majority of new code lives in isolated custom packages/folders — upstream touch points are minimal and bounded

Minor risk: the `StockTransferCheckPage` class is named `StockTransferSecondPage` internally
(despite the filename), which may indicate the component was copied from `StockTransferSecondPage.jsx`.
This is surfaced in Task 7's acceptance criteria — the implementer must verify the
correct class is being edited and update the task's path if a different file turns out
to be the real mount point.
