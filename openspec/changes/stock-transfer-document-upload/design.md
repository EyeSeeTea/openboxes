# Stock Transfer Document Upload — Technical Design

## Upstream Compatibility

This fork must stay mergeable with upstream OpenBoxes. Per `CLAUDE.md`
(*Upstream Compatibility*) and `.claude/rules/custom-package-isolation.md`, every
change below is designed to be as isolated and minimally invasive as possible.

**Rules-alignment pass (2026-04-15):** this design was re-reviewed against the
updated `custom-package-isolation.md` rule. Changes from the original design:
1. Frontend files now live under the rule's prescribed sub-folders
   (`components/`, `utils/`, `__tests__/`) instead of a flat folder.
2. Backend i18n bundle moved to `grails-app/i18n/custom/` per the rule's layout.
3. The `custom/*` webpack alias is now a **definite** upstream touch (verified
   missing in `webpack.config.js`) rather than conditional.
4. Liquibase wiring notes updated to match the rule's
   `custom/changelog.groovy` aggregator pattern.

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
| `src/main/groovy/org/pih/warehouse/core/ActivityCode.groovy` | Enum values cannot be added from outside the file | 1 line |
| `grails-app/controllers/org/pih/warehouse/UrlMappings.groovy` | URL mappings live in a single file | 1 block (3-4 lines) |
| `grails-app/services/.../StockTransferService.groovy` — `completeStockTransfer()` | Completion gating must run inside the existing transactional flow; delegates to a custom validator to keep the edit to a single `if` block | ≤ 5 lines, delegating to `CustomStockTransferDocumentService` |
| `src/js/components/stock-transfer/StockTransferCheckPage.jsx` | React page has no extension slot; the render tree must be touched once to mount the custom documents panel | 1 import + 1 JSX line + 1 disabled-prop edit |
| `webpack.config.js` | `custom/*` alias does not exist yet (verified: only `components`, `hooks`, `utils`, etc. are aliased). First custom feature must register it. | 1 line (`custom: path.resolve(SRC, 'custom')`) |

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

**Boy Scout Rule suspended** for the five upstream files above: do not reformat, re-order
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

**File**: `src/main/groovy/org/pih/warehouse/core/ActivityCode.groovy` *(MODIFY — 1 line)*

Add `REQUIRE_TRANSFER_DOCUMENT` to the enum. This follows the pattern of existing enforcement
codes like `REQUIRE_ACCOUNTING` and `HOLD_STOCK`. Admins assign it to location types or
individual locations via the existing admin UI — no configuration UI changes needed.

Enum values cannot be added from outside the file, so this is an unavoidable upstream edit.
Keep the diff to a single enum entry; do not reorder existing entries.

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
          uri: "/document/download/${doc.id}" ]
    }

isDocumentRequired(Order order):
    return order.origin?.supports(ActivityCode.REQUIRE_TRANSFER_DOCUMENT)

validateForCompletion(Order order):
    if (isDocumentRequired(order) && !order.documents) {
        throw new ValidationException(
            "A document is required to complete stock transfers from this location"
        )
    }
```

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

`canComplete` is emitted by the panel every time its internal state changes, per this
contract:

| Panel state | `canComplete` value |
|-------------|---------------------|
| Mounted, initial load in flight | `true` (fail-open — see note below) |
| Load succeeded, `documentRequired=false` | `true` |
| Load succeeded, `documentRequired=true`, 0 documents | `false` |
| Load succeeded, `documentRequired=true`, ≥1 document | `true` |
| Load failed (network error) | `true` (fail-open) — panel also shows an Alert; backend is the hard gate |

**Fail-open rationale**: the backend `completeStockTransfer()` check in Task 5 is the
authoritative enforcement. The frontend gate is only UX sugar — if the panel hasn't
finished loading or the GET fails, we do not block the user; if enforcement is actually
required, the backend will reject the completion with a `ValidationException` which the
existing error-handling path already surfaces. This keeps the frontend edit minimal and
avoids blocking users when the custom endpoint is temporarily unavailable.

The parent reads the emitted value into `customCanComplete` state (default `true`) and
disables the Complete button only when `customCanComplete === false` — so the upstream
default behavior is preserved for every state except the explicit "required-and-missing"
case.

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

**File**: `src/js/components/stock-transfer/StockTransferCheckPage.jsx` *(MODIFY — minimal)*

The only edits to this upstream file:

1. Add one import (uses the new `custom/*` webpack alias — see Task 7's
   upstream-touch entry for alias registration):
   ```javascript
   import StockTransferDocumentsPanel from 'custom/stockTransferDocuments/components/StockTransferDocumentsPanel';
   ```
2. Add one JSX mount between the items table and the submit buttons:
   ```jsx
   <StockTransferDocumentsPanel
       stockTransferId={this.state.values.stockTransferId}
       onCanCompleteChange={(canComplete) => this.setState({ customCanComplete: canComplete })}
   />
   ```
3. AND combine into the existing Complete button's `disabled` prop:
   ```jsx
   disabled={existingDisabledExpr || this.state.customCanComplete === false}
   ```

No other edits to this file. Do not refactor, reformat, or rename anything else.

### Completion button logic
```
disabled = (existing upstream disabled expression)
        || (customCanComplete === false)
```
`customCanComplete` defaults to `true` and only becomes `false` when the panel reports
the explicit "required-and-missing" state — see the `canComplete` initial-state contract
above for the full table. This keeps the edit backwards-compatible with upstream logic
and avoids blocking users when the custom endpoint is in flight or failing.

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

All user-visible strings emitted from custom code (panel labels, warning message,
upload success/error toasts, validation error message) must use `react-intl` /
`messages.properties` keys under a dedicated `react.custom.stockTransferDocuments.*`
namespace. Keep them in isolated bundles:

- Frontend: `src/js/custom/stockTransferDocuments/utils/messages.js` (new file,
  merged into the existing `react-intl` provider via a custom hook — if the merge
  hook does not already exist, add a ≤ 2-line `[UPSTREAM-TOUCH]` task to register it)
- Backend: `grails-app/i18n/custom/stock-transfer-documents-messages.properties`
  (new file, under the rule's prescribed `grails-app/i18n/custom/` sub-folder; if
  Grails does not auto-pick-up additional bundles, a 1-line `[UPSTREAM-TOUCH]` to
  register it in `application.yml` — budget it separately)

The backend `ValidationException` message ("A document is required to complete stock
transfers from this location") must be a message key, not a hardcoded string.

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
- [ ] Documents can be uploaded before transfer completion
- [ ] Documents can be uploaded after transfer completion
- [ ] With `REQUIRE_TRANSFER_DOCUMENT` on origin location: completing without documents throws ValidationException
- [ ] With `REQUIRE_TRANSFER_DOCUMENT` on origin location: completing with documents succeeds
- [ ] Without `REQUIRE_TRANSFER_DOCUMENT`: completing without documents still succeeds (no regression)
- [ ] Frontend Complete button is disabled when documents are required but none uploaded
- [ ] Frontend shows warning message when documents are required
- [ ] Upstream-file diffs stay within the budgets listed in *Upstream Compatibility* (ActivityCode: 1 line; UrlMappings: 1 block; StockTransferService: ≤ 5 lines; StockTransferCheckPage.jsx: 1 import + 1 JSX mount + 1 disabled-prop edit; `webpack.config.js`: 1 alias line)
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
