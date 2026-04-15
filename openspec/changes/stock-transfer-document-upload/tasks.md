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

---

## Task 1: Add `REQUIRE_TRANSFER_DOCUMENT` ActivityCode [BE] [UPSTREAM-TOUCH] ✅

**File**: `src/main/groovy/org/pih/warehouse/core/ActivityCode.groovy` *(MODIFY — 1 line)*

Add `REQUIRE_TRANSFER_DOCUMENT` to the enum. Place it next to existing `REQUIRE_*` codes.
Do not reorder existing entries or touch anything else in the file.

**Acceptance criteria:**
- Enum compiles and is available for `Location.supports()` checks
- Diff is a single enum entry; no formatting or import changes

---

## Task 2: Custom document service [BE] ✅

**File**: `grails-app/services/org/pih/warehouse/custom/stocktransferdocuments/CustomStockTransferDocumentService.groovy` *(NEW)*

Implement three methods on a brand-new service (see design.md → *Custom Document Service*):

- `listDocuments(Order order)` → list of document DTOs with download URIs
- `isDocumentRequired(Order order)` → boolean based on `order.origin?.supports(ActivityCode.REQUIRE_TRANSFER_DOCUMENT)`
- `validateForCompletion(Order order)` → throws `ValidationException` when enforced and no documents attached

No changes to upstream `StockTransferService`.

**Acceptance criteria:**
- Service compiles and is auto-wired via Grails convention (no `@Autowired` / constructor injection)
- Unit tests cover all three methods including the no-enforcement path (no regression)

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
- Jest + RTL tests cover: empty state, loaded documents, drag-and-drop, upload success, upload error, document-required warning, **initial-load-in-flight (canComplete=true)**, **network-error (canComplete=true + alert shown)**
- Asserts use concrete values (no `toBeTruthy` / `toBeDefined` for knowable results)
- All rendered text comes from `react-intl` — no hardcoded English strings

**Depends on**: Task 6a (i18n wiring)

---

## Task 6a: Custom i18n wiring [FE] ✅

**Files**:
- `src/js/custom/stockTransferDocuments/utils/messages.js` *(NEW)* — English default
  strings for the keys defined in design.md → *i18n*
  (`react.custom.stockTransferDocuments.*`)
- `grails-app/i18n/custom/stock-transfer-documents-messages.properties` *(NEW)* —
  backend message for the `ValidationException` thrown in Task 2 (path matches the
  `grails-app/i18n/custom/<feature>-messages_<locale>.properties` layout from
  `custom-package-isolation.md`)

**Pre-work**: check whether the fork already has a custom-messages merge mechanism in
the frontend `IntlProvider` and whether Grails picks up additional `i18n` bundles
automatically. Report the findings:
- If both mechanisms exist: this task is pure-new-file work (no `[UPSTREAM-TOUCH]`).
- If either is missing: add the necessary registration as a sub-task tagged
  `[UPSTREAM-TOUCH]` with a ≤ 2-line budget, and add it to the Upstream Touch Point
  Summary at the bottom of this file.

The backend `CustomStockTransferDocumentService.validateForCompletion` must throw the
`ValidationException` using a message key (not a hardcoded English string), so that
localization works without further edits.

**Acceptance criteria:**
- All four frontend keys render correctly in English
- The backend validation error message comes from the new properties file
- No upstream i18n files are modified (or, if the merge/pickup mechanism is missing,
  the registration is a budgeted `[UPSTREAM-TOUCH]` ≤ 2 lines)

**Depends on**: Task 2 (backend message key), Task 6 planning

---

## Task 7: Mount the panel in StockTransferCheckPage [FE] [UPSTREAM-TOUCH] ✅

**File**: `src/js/components/stock-transfer/StockTransferCheckPage.jsx` *(MODIFY — 1 import + 1 JSX mount + 1 disabled-prop edit)*

**Pre-work (verification)**:
1. Confirm the file at this path is the actual Check Page wizard step rendered in the
   UI — the class inside the file is named `StockTransferSecondPage` (despite the
   filename), which suggests it may have been copied. If the real mount point is a
   different file, update this task's path before editing.
2. **`custom/*` webpack alias is confirmed missing** (verified 2026-04-15 against
   `webpack.config.js`). This task must register it as a definite 1-line upstream
   touch — see Task 7a below.

Exactly three edits to `StockTransferCheckPage.jsx`:

1. Add one import (uses the new `custom/*` alias added in Task 7a):
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
3. Combine `customCanComplete` into the existing Complete button's `disabled` prop:
   ```jsx
   disabled={existingDisabledExpr || this.state.customCanComplete === false}
   ```

Do not reformat, reorder, or refactor anything else in this file. Boy Scout Rule is
suspended for this file.

**Acceptance criteria:**
- Correct file confirmed (class name verified to match the mounted page)
- `custom/*` alias verified or registered as a budgeted upstream touch
- Diff is exactly the three edits above
- Complete button is disabled when documents are required but none uploaded
- Complete button is unchanged when the activity code is disabled
- `customCanComplete` defaults to `true` so initial render matches upstream behavior

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

## Task 8: Integration test for upstream+custom completion flow [BE] ✅

**File**: `src/integration-test/groovy/org/pih/warehouse/custom/stocktransferdocuments/StockTransferCompletionIntegrationSpec.groovy` *(NEW)*

End-to-end integration test that exercises the full upstream+custom flow:

1. Create a stock transfer with a location that has `REQUIRE_TRANSFER_DOCUMENT` enabled
2. Call `StockTransferService.completeStockTransfer()` → assert `ValidationException`
3. Upload a document via `CustomStockTransferDocumentService`
4. Retry completion → assert success
5. Create a second stock transfer with a location that does **not** have the activity
   code → assert completion succeeds without documents (no regression)

This test protects Task 5's upstream touch point: any future upstream change that
breaks the delegating call into the custom service will fail this test in CI.

**Acceptance criteria:**
- All four scenarios pass
- Test runs via `./gradlew integrationTest`
- Test is in a custom package (not mixed into upstream integration test suites)

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

| Task | File | Budget |
|------|------|--------|
| 1 | `ActivityCode.groovy` | 1 line |
| 4 | `UrlMappings.groovy` | 1 block (3–4 lines) |
| 5 | `StockTransferService.groovy` | ≤ 5 lines |
| 7 | `StockTransferCheckPage.jsx` | 1 import + 1 JSX mount + 1 disabled-prop edit |
| 7a | `webpack.config.js` (`custom` alias) | 1 line (definite — verified missing) |
| 7a — if Jest doesn't share the webpack alias | `jest.config.js` / `package.json` `moduleNameMapper` | ≤ 2 lines |
| 6a — if frontend i18n merge hook missing | `IntlProvider` setup | ≤ 2 lines |
| 6a — if Grails doesn't auto-pick-up extra bundles | `application.yml` | ≤ 2 lines |

**Total upstream budget: ≤ 20 lines across ≤ 8 files.** Rows 1–5 are definite;
the last three rows are conditional on verification outcomes in Tasks 6a and 7a —
they may not be needed at all.

If any task's diff grows beyond its budget, pause and revisit the design before committing.
