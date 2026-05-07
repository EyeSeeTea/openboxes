## Context

The prior change `block-expired-items-on-outbound` introduced two enforcement layers for expired stock:
1. A server interceptor that rejects `updatePicklist` payloads referencing expired lots (HTTP 400).
2. A bean override of `stockMovementService` that strips expired `AvailableItem`s from autopick suggestions before allocation.

Together those guarantee that **no expired lot ends up on a picklist for an outbound `STOCK_MOVEMENT`**.

What that change deliberately did *not* touch is the **Edit step** of the outbound (and request) wizard, which sits one step earlier in the workflow. At Edit, the user enters a `quantityRequested` for each product and can revise it. The displayed "Available" column, sourced from `quantityAvailable` in the response of `GET /api/stockMovements/{id}/stockMovementItems?stepNumber=3`, **still includes expired stock**. The existing validator at `outbound/EditPage.jsx:415-416` and `request/EditPage.jsx:1098` (key `react.stockMovement.errors.lowerQty.label`, message *"Revise quantity! Quantity available is lower than requested"*) blocks "Next" when `quantityRequested > quantityAvailable` — but with the inflated number, it doesn't fire when expired stock would otherwise close the gap. Users hit the wall at Pick instead, with no easy revision path.

This change adds a parallel `quantityPickable` field to the Edit-step API response (sum of non-expired `availableItems[].quantityAvailable`) and switches the validator + visual cues to use it. `quantityAvailable` retains its existing global meaning so other endpoints, screens, and reports are unaffected.

## Goals / Non-Goals

**Goals:**
- The `lowerQty` validator fires correctly when a request cannot be fulfilled with non-expired stock.
- The Edit-step "Available" column shows the breakdown (e.g. `83 (50 expired)`) so users can self-diagnose without re-running the picker.
- Both the outbound wizard's Edit step and the request wizard's Edit step are aligned (same backend, same validator, same UX).
- `quantityAvailable` keeps its existing semantics across all endpoints, screens, reports, and integrations.
- Reuse of the `ExpiryRule.isExpired` helper from the prior change — single source of truth for the "expired" predicate.

**Non-Goals:**
- Modifying any other availability number anywhere else (no global redefinition of `quantityAvailable`).
- Touching outbound returns (`/api/stockTransfers/...`), inbound receiving, putaway, replenishment, or stock-transfer flows.
- Adding `quantityPickable` to the substitution-row map (line 1078-1090). Substitutions have their own picker; out of scope here.
- Adding a config flag to disable the new behaviour. The picking guard from the prior change is universal; this change keeps the same posture.
- Modifying the `EditPageItem` Groovy class in `StockMovementItem.groovy:589`. That class is dead code in this code path (`buildEditPageItems` returns `Map`s, not class instances).

## Decisions

### D1 — Add `quantityPickable` field; do not change `quantityAvailable` semantics

Compute `quantityPickable` on the same loop iteration where `quantityAvailable` and `quantityOnHand` are already computed (`StockMovementService.groovy:1050-1051`). Insert as a new map entry (`StockMovementService.groovy:1054-1092`) so the JSON response gains a single new key.

**Why over alternatives:**
- *Alt: redefine `quantityAvailable` to exclude expired globally.* Rejected — breaks every other consumer (other wizards, reports, API integrations) and creates a semantic mismatch between this codebase and its upstream/customer forks.
- *Alt: filter on the frontend.* Rejected — the frontend doesn't have per-lot expiry data on the Edit step; the response already aggregates totals.
- *Alt: separate `/api/products/{id}/expiryAwareAvailability` endpoint.* Rejected — adds a per-product fetch fan-out (N+1 by row) and a parallel endpoint to maintain. Adding one field to an existing response is cheaper.
- *Alt: change `EditPageItem.toJson()` in `StockMovementItem.groovy`.* Rejected — that class is constructed nowhere in this flow; `buildEditPageItems` returns `Map`s directly. Changing the dead class would be confusing.

### D2 — Compute `quantityPickable` from the same `availableItems` already loaded

`buildEditPageItems` already pulls `List<AvailableItem>` per product via `productAvailabilityService.getAllAvailableBinLocations(...)` (`StockMovementService.groovy:1030-1032`). Each `AvailableItem` carries `inventoryItem.expirationDate`. So the new computation is a one-line filter on data already in memory:

```groovy
def quantityPickable = availableItems
        ?.findAll { it.quantityAvailable > 0 && !ExpiryRule.isExpired(it.inventoryItem?.expirationDate) }
        ?.sum { it.quantityAvailable }
```

No extra database round trip. No N+1 risk.

### D3 — Reuse `ExpiryRule.isExpired` from the prior change

The same Groovy helper at `src/main/groovy/org/pih/warehouse/custom/outboundExpiryRestrictions/support/ExpiryRule.groovy` is the single source of truth for "expired" semantics in this fork. Importing it into `StockMovementService.groovy` keeps the strict-`<` comparison consistent with the picking guard and the autopick filter.

### D4 — Frontend updates the validator AND the visual cues, in both wizards

The "Revise quantity!" UX has three visible parts:
1. The validator (returns the i18n error → blocks "Next" until revised).
2. The row-level styling (`font-weight-bold` when `quantityAvailable < quantityRequested`) to draw the user's eye.
3. The cell-level styling (`text-danger` red on the Available cell).

All three should now key off `quantityPickable`. Otherwise the validator could fire on a row that visually looks fine, or vice versa — the user is left guessing.

The request wizard (`src/js/components/stock-movement-wizard/request/EditPage.jsx`) uses three field-config blocks (`AD_HOCK_FIELDS`, `STOCKLIST_FIELDS_PUSH_TYPE`, `STOCKLIST_FIELDS_PULL_TYPE`), each with its own row + cell condition. All three field configs and the shared validator are updated.

### D5 — Inline display: `83` followed by red `(50 expired)`

When `quantityAvailable > quantityPickable`, render the column with the pickable count in the existing numeric format, followed by the expired count in red — using Bootstrap's `text-danger` class on a `<span>` wrapping just the parenthesised tail. The pickable number itself adopts the row's existing colour (red when the row triggers the validator, default otherwise), and the red expired tail draws the eye to *why* the available number is not what the user expected.

Example DOM (when `quantityAvailable=83`, `quantityPickable=33`):
```jsx
<>
  33 <span className="text-danger">(50 expired)</span>
</>
```

When `quantityAvailable === quantityPickable`, render the single number with no tail (existing behaviour). The "(N expired)" tail uses an i18n key (`outboundExpiryRestrictions.edit.expiredHint`, default `({0} expired)`) so localisations can adapt the wording.

### D6 — No changes to substitution-row availability

The substitution flow at `StockMovementService.groovy:1078-1086` computes its own `qtyAvailable` per substitution candidate and is rendered through a separate modal. Substitutions have their own UX problem (a substitution lot might also be expired), but solving it here would expand scope. Tracked as a follow-up; not in v1.

## Risks / Trade-offs

| Risk | Mitigation |
|---|---|
| Bundle and backend deploy out of sync — old bundle reads `quantityAvailable`, new backend supplies both | Old bundle ignores the new `quantityPickable` field (extra JSON keys are silent in JS). UX degrades to upstream behaviour, not regression. |
| New backend, old bundle — Edit step still uses inflated number | Same as above — UX same as today. Once bundle ships, the validator activates. |
| `quantityPickable` is `null` on rows with no `availableItems` | Guard with `?:` and treat `null` as `0` in the validator (consistent with how upstream treats `null` in `quantityAvailable`). |
| Performance — extra `findAll` + `sum` over `availableItems` per row | Same `availableItems` list is already iterated twice on the same row (lines 1050, 1051). One more pass is negligible — pages cap at ~50 products and `availableItems` rarely exceeds 10 lots per product. |
| Other consumers of the response start relying on `quantityPickable` and create coupling | Documented in the spec as "for use by Edit-step pre-flight checks." If a future change wants this number elsewhere, that is its own change with its own validation. |
| Substitution rows still inflate availability | Out of scope (D6). Surfaced as an open question. |
| Future upstream merge changes `buildEditPageItems` | The added lines are surgical (1 import, 1 expression, 1 map entry) and clearly self-contained — typical 3-way merge resolves cleanly. Rule of thumb: a future upstream rename of `quantityAvailable` would conflict; an addition of unrelated fields would not. |

## Migration Plan

1. Implement behind no flag — like the prior change, the rule is universally desired.
2. Deploy backend + frontend together. If backend lands first, the bundle still works (extra JSON key is ignored). If frontend lands first, the new column shows the same number for both sides of the breakdown (`quantityPickable` is missing → fallback to `quantityAvailable`).
3. **Rollback:** revert the commit. There are no schema changes, no data mutations, no migration files.
4. **Data note:** in-flight outbound stock movements are unaffected — `quantityPickable` is computed at read time from current `inventory_item.expiration_date`, not persisted.

## Upstream Touch Points

This section is the merge-conflict hitlist for future upstream pulls (per `.claude/rules/custom-package-isolation.md` § "When You MUST Touch an Upstream File").

| File | Reason | Diff size |
|---|---|---|
| `grails-app/services/org/pih/warehouse/inventory/StockMovementService.groovy` | Add `import` for `ExpiryRule` (custom support helper), one-line `quantityPickable = ExpiryRule.sumPickableQuantity(availableItems)` alongside the existing `quantityAvailable`/`quantityOnHand` (around line 1050), one entry in the row map literal (around line 1054). | 3 added lines (1 import + 1 expression + 1 map entry). |
| `src/js/components/stock-movement-wizard/outbound/EditPage.jsx` | Switch the row className condition (line 53), the cell className condition (line 137), and the validator (line 415-416) from `quantityAvailable` to `quantityPickable`. Use the extracted `renderAvailableCell` helper for the Available cell `formatValue` (line 147). | ~5 modified lines. |
| `src/js/components/stock-movement-wizard/request/EditPage.jsx` | Same swaps in three field-config blocks (rows at 55/367/667, cells at 202/502/802) plus the shared validator at 1098, plus the extracted `renderAvailableCell` helper for each Available cell. | ~10 modified lines. |
| `src/js/custom/outboundExpiryRestrictions/utils/expiryHelpers.jsx` | Add new export `renderAvailableCell(translate)` that returns a `formatValue`-style function rendering `<pickable><span className="text-danger ml-1">(N expired)</span>` (or just the single number when nothing is expired). Custom file from the prior change (renamed from `.js` since it now contains JSX); not an upstream touch. | 1 added export. |
| `src/main/groovy/org/pih/warehouse/custom/outboundExpiryRestrictions/support/ExpiryRule.groovy` | Add static helper `sumPickableQuantity(List<AvailableItem> availableItems, Date today)` that takes `today` from the caller and reuses the in-class `isExpired` predicate. `today` is injected (rather than computed inside the helper) so unit tests can pin it deterministically and so the upstream `StockMovementService` caller can hoist `today` once across the row loop. Custom file from the prior change. | 1 added method (~7 lines). |
| `src/js/custom/outboundExpiryRestrictions/__tests__/expiryHelpers.test.js` | Add Jest tests for `renderAvailableCell` (all-fresh, mixed, all-expired). Custom file from the prior change. | ~30 added lines. |
| `grails-app/i18n/messages.properties` | Add new key `outboundExpiryRestrictions.edit.expiredHint=({0} expired)` (and the two sibling `outboundExpiryRestrictions.expired.*` keys for the block-expired feature) at the bottom of the file. Merged into the upstream root bundle rather than a custom basename so the default `PluginAwareResourceBundleMessageSource` (which only globs `grails-app/i18n/messages*.properties` at the root) actually picks them up at runtime. Documented as an upstream touch point. | 4 added lines. |

No upstream React component is replaced or wrapped — these are surgical edits to existing wizard files. The new `quantityPickable` JSON field is the sole new contract.

## Open Questions

1. **Substitution rows.** `quantityAvailable` for substitution candidates (line 1078-1086) is also inflated. Out of scope per D6 — but worth a follow-up. Possible follow-up change: `expiry-aware-substitution-availability`.
2. **Inline hint format.** `83 (50 expired)` vs `33 / 83` vs a tooltip on hover — design choice. Default to the parenthesised form since it composes with the existing right-aligned numeric formatter and degrades gracefully under narrow columns.
3. **Strict-`<` vs. strict-`<=` for the Edit step.** Use strict-`<` to match the prior change's `ExpiryRule` semantics (a lot expiring today is still pickable today). Reconfirmed; no override.

## Validation

Each criterion below is verifiable from a concrete artifact (file:line, captured curl, grep output, fixture). "Tests pass" alone is not a valid criterion.

### Source-receipt criteria (verified during this design phase)

- [x] **R1 — Validator location and predicate (outbound).** `src/js/components/stock-movement-wizard/outbound/EditPage.jsx:411-417` checks `_.isNil(item.quantityRevised) && (item.quantityRequested > item.quantityAvailable) && (item.statusCode !== 'SUBSTITUTED')` and writes `react.stockMovement.errors.lowerQty.label` to `errors.editPageItems[key].quantityRevised`. Verified by direct read.
- [x] **R2 — Validator location and predicate (request).** `src/js/components/stock-movement-wizard/request/EditPage.jsx:1094-1106`, same predicate and same error key. Verified by direct read.
- [x] **R3 — i18n key for the validator message.** `grails-app/i18n/messages.properties:3636` defines `react.stockMovement.errors.lowerQty.label=Revise quantity! Quantity available is lower than requested`. Verified.
- [x] **R4 — Backend code path that produces `quantityAvailable` for the Edit step.** `StockMovementService.groovy:964-987` (`getEditPageItems`) → `buildEditPageItems` (line 1013-1095). The `quantityAvailable` value is computed at line 1050 and written to the map at line 1065. Verified by direct read.
- [x] **R5 — `EditPageItem` class is dead code in this flow.** `StockMovementItem.groovy:589` defines the class with `getQuantityAvailable()` and `toJson()` accessors, but `buildEditPageItems` returns `Map`s directly (line 1054-1092 → `editPageItems = data.collect { ... [...] }`). The class is never `new`'d in `getEditPageItems` / `getEditPageItem`. Verified by direct read.
- [x] **R6 — `availableItems` carry per-lot expirationDate.** `availableItems` at line 1041-1046 is built from `productAvailabilityService.getAllAvailableBinLocations(...)` → each entry has `inventoryItem` with full domain access including `expirationDate` (verified via `EditPageItem.getMinExpirationDate()` at `StockMovementItem.groovy:622-628` traversing the same path).
- [x] **R7 — `ExpiryRule.isExpired(Date)` exists in this branch.** Committed in `12531d258` at `src/main/groovy/org/pih/warehouse/custom/outboundExpiryRestrictions/support/ExpiryRule.groovy:11-17`. Strict-`<` against `today.clearTime()`.
- [x] **R8 — Outbound returns are unaffected (do not call this endpoint).** `src/js/components/returns/outbound/SendOutboundReturn.jsx:246`, `AddItemsPage.jsx:257`, `CreateOutboundReturn.jsx:138,182`, `PickPage.jsx:138` — all hit `/api/stockTransfers/...`. None hit `/api/stockMovements/.../stockMovementItems?stepNumber=3`. Verified by repository-wide grep.
- [x] **R9 — Inbound receiving is unaffected.** Repository-wide grep for `stockMovementItems?stepNumber=3` returns only the two files (`outbound/EditPage.jsx` and `request/EditPage.jsx`). Receiving wizards (`src/js/components/receiving/`) do not use this endpoint.
- [x] **R10 — request/EditPage has three field configs with the same condition.** `request/EditPage.jsx` rows at 55, 367, 667 and cells at 202, 502, 802 — verified by direct grep + read of two examples (lines 55 and 667). All match the pattern `quantityAvailable < quantityRequested`.
- [x] **R11 — `AvailableItem.inventoryItem` is hydrated by `getAllAvailableBinLocations`.** `ProductAvailabilityService.groovy:838-851` constructs each `AvailableItem` with `inventoryItem` directly from the HQL projection's `it[0]` (which is a full `InventoryItem` domain instance via the `ProductAvailability.inventoryItem` association). `expirationDate` is therefore accessible without lazy-loading concerns. Verified by direct read.
- [x] **R12 — Both wizards share the backend code path.** Both `outbound/EditPage.jsx` and `request/EditPage.jsx` hit `?stepNumber=3`, which dispatches in `StockMovementService.getStockMovementItems:782` to `getEditPageItems:788` (because `OutboundWorkflowState.fromStepNumber(3) == REVISE_ITEMS`) → `buildEditPageItems:1013`. Adding `quantityPickable` once benefits both. Verified by direct read.
- [x] **R13 — Frontend consumers do not reject extra JSON fields.** Both wizard files access response fields by name (`item.quantityRequested`, `item.quantityAvailable`, `item.statusCode`, etc.) with no schema validation, no `Object.keys` strictness, no `JSON.parse(..., reviver)` filtering. Standard JS access for unknown keys returns `undefined` without error; old bundle reading the new response simply ignores `quantityPickable`. Verified by reading `outbound/EditPage.jsx:285-330` and `request/EditPage.jsx:955-1010`.

### Code-after-implementation criteria

- [ ] **C1 — `quantityPickable` is on the wire.** `curl -s -b "$COOKIE" "http://localhost:8081/openboxes/api/stockMovements/<sm-id>/stockMovementItems?stepNumber=3" | jq '.data[0].quantityPickable'` returns an integer (not `null` for a product that has any non-expired stock).
- [ ] **C2 — `quantityPickable` ≤ `quantityAvailable`.** For every row in the same response, `.quantityPickable <= .quantityAvailable` holds. Captured `jq` script in test plan.
- [ ] **C3 — Validator fires on insufficient pickable stock.** Manual test required. Setup: a product with 10 fresh + 50 expired lots; request quantity 30. Expected: Edit step shows `10 (50 expired)`, the row is bold red, "Next" is blocked with the existing `lowerQty.label` message.
- [ ] **C4 — Validator does not fire when pickable is sufficient.** Same product but request quantity 5. Row renders normally, "Next" proceeds.
- [ ] **C5 — `quantityAvailable` semantics unchanged elsewhere.** `grep -rnE 'quantityAvailable' grails-app/services/ src/js/ | wc -l` should be unchanged in lines that don't reference `quantityPickable`. (Sanity check that no other consumer was modified.)
- [ ] **C6 — i18n key resolves in two locales.** With `Accept-Language: en` and another available locale, the inline hint renders the localised string from `outboundExpiryRestrictions.edit.expiredHint` (or the default message if the locale lacks it).
- [ ] **C7 — Outbound returns regression-free.** `git diff develop..HEAD -- src/js/components/returns/` is empty after this change.
- [ ] **C8 — Inbound receiving regression-free.** `git diff develop..HEAD -- src/js/components/receiving/` is empty after this change.

### Conformance criteria (verifiable against rules in this repo)

- [x] **N1 — All new files (if any) under custom paths.** This change adds no new backend/frontend files (only edits). The i18n properties addition lives under `grails-app/i18n/custom/` which is already a custom path.
- [x] **N2 — `ExpiryRule` reuse, no duplication.** No new helper for "is expired" — direct import of the existing class.
- [x] **N3 — Upstream touches surgical and documented.** Three upstream files modified, ~18 lines net change combined, listed under "Upstream Touch Points." No incidental cleanup.
- [x] **N4 — No `metaClass` mutation, no schema change.** No domain-class extension; no migration.
- [x] **N5 — No `addColumn` against any upstream table.** Working-tree diff against `grails-app/migrations/` will be empty for this change.

## Unverified Assumptions

(none — the three items previously listed have been verified against current source and promoted to receipts R11, R12, R13 in the Validation section.)

## Confidence: 9/10

Every factual claim about external systems has a `file:line` receipt. The Unverified Assumptions list is empty. The previously-flagged framework-semantic assumptions (lazy-load behaviour, shared code path between wizards, backward-compatibility of extra JSON fields) are now backed by direct code reads.

**Why not 10:**
- Three upstream files are modified (one backend, two frontend), and the request wizard's three field-config blocks (ad-hoc, PULL stocklist, PUSH stocklist) need to stay aligned. The unit-test surface (Spock) covers the backend computation; the JSX field configs require manual exercise across all three variants — there's no automated regression net for the styling and validator threading.

## Deploy status

- **PR:** [EyeSeeTea/openboxes#3](https://github.com/EyeSeeTea/openboxes/pull/3) — bundled with `block-expired-items-on-outbound` since both ship from `feature/strict-expired-handling`. Open against `release/est/tjk/0.9.7`.
- **Replayed onto customer branches:** none yet — pending merge of PR #3 onto `release/est/tjk/0.9.7`. TJK-only for now; promotion to `release/est/0.9.7` is a follow-up if/when SP wants the same Edit-step pickable behaviour.
- **Submitted upstream:** no.
