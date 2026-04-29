## Context

OpenBoxes' outbound stock-movement wizard separates **product selection** from **lot selection** into two distinct steps:

| Wizard step | File | What the user picks | What the API receives |
|---|---|---|---|
| Step 2 — Add Items | `src/js/components/stock-movement-wizard/outbound/AddItemsPage.jsx` | Products + requested quantity | `{product.id, quantityRequested, recipient, sortOrder}` only — **no `inventoryItem.id`** (verified at lines 540–554) |
| Step 4 — Pick | `src/js/components/stock-movement-wizard/outbound/PickPage.jsx` (table) and `src/js/components/stock-movement-wizard/modals/EditPickModal.jsx` (per-line lot picker) | Specific lots and quantities to pick from each bin | `{picklistItems[].inventoryItem.id, binLocation.id, quantityPicked, reasonCode}` (verified at `EditPickModal.jsx:212-220`) → POST to `/api/stockMovementItems/<id>/updatePicklist` (verified at `UrlMappings.groovy:219-221`) → `StockMovementItemApiController.updatePicklist()` (verified at `StockMovementItemApiController.groovy:60-82`) |

**The "expired product" restriction therefore belongs at the Pick step — that is the only step where the user touches inventory items.** Restricting at Add Items would block products in name only; expired-vs-fresh is a per-lot property, not a per-product property.

**Outbound returns live in a separate component tree.** Returns set `type: 'RETURN_ORDER'` from `src/js/components/returns/outbound/CreateOutboundReturn.jsx:179` — they do **not** flow through `stock-movement-wizard/outbound/` at all. No branching on `stockMovementType` is needed in the wizard. (We still branch on it server-side as defence-in-depth.)

**Existing infrastructure we will reuse.** `ProductAvailabilityService.calculateQuantitiesByExpirationFilter` (`grails-app/services/org/pih/warehouse/inventory/ProductAvailabilityService.groovy:540-569`) already encodes a "subtract expired stock" mode through an `ExpirationFilter` enum (`SUBTRACT_EXPIRED_STOCK` / `DO_NOT_SUBTRACT_EXPIRED_STOCK`). The expression at line 555 — `inventoryItem.expirationDate >= today` — is the canonical "is this lot still valid?" check upstream uses today. **We adopt the same predicate** to stay consistent.

**Defines for this design:**
- "Expired" = `inventoryItem.expirationDate != null && expirationDate < today` (server timezone, midnight). Matches upstream `ProductAvailabilityService.groovy:555`.
- "Outbound STOCK_MOVEMENT" = `OutboundStockMovement.stockMovementType == StockMovementType.STOCK_MOVEMENT` (verified at `OutboundStockMovement.groovy:61` and `StockMovementType.groovy:5`). Returns are `RETURN_ORDER` and do not reach the pick endpoint we intercept.

## Goals / Non-Goals

**Goals:**
- Prevent users from picking expired lots in the **Pick step** of the outbound STOCK_MOVEMENT wizard, both in the UI (disabled row in `EditPickModal`) and via the API (server-side guard on `StockMovementItemApiController.updatePicklist`).
- Leave outbound `RETURN_ORDER`, all inbound flows, and every other surface unchanged.
- Add zero columns to upstream tables. Edit at most one upstream frontend file (`EditPickModal.jsx`) with surgical, two-line-equivalent changes.
- Reuse the existing `ExpirationFilter`-style predicate so the "is expired?" rule lives in exactly one canonical helper.

**Non-Goals:**
- Block already-saved expired allocations on movements that pre-date the rollout (no retroactive cleanup; the guard fires on `updatePicklist` only).
- Block expired items in the **packing/shipping** later steps — those reuse the picks already made; if the Pick step refused them, downstream is clean.
- Cover bulk CSV import paths for picks (out of scope; flagged as a follow-up).
- Touch the Add Items / Inbound / Stock Transfer wizards. Add Items only handles products and there are no per-lot decisions there.
- Hide products on the Add Items step whose only available stock is expired. Possible follow-up; not core to "selecting expired products".
- Add a Settings toggle to choose hide-vs-disable. The disable-with-tooltip behaviour is hardcoded (see D1).

## Decisions

### D1 — Disable expired rows in the lot picker (do not hide)

In `EditPickModal.jsx`, render rows whose lot is expired with disabled styling (`text-disabled` class — already in use at `EditPickModal.jsx:43-45` for `quantityAvailable === 0` rows) and a tooltip showing the expiration date and reason "Cannot ship — expired".

- **Why:** the modal already shows the lot's `expirationDate` column (line 75-83). Disabling the `quantityPicked` input on expired rows is consistent with the existing "no-stock" disable pattern (line 129-133: `disabled: !quantityAvailable && !quantityPicked`). Hiding rows would conflict with the modal's role of showing the user *all* lots in the bin.
- **Alternative considered:** hide expired rows server-side. Rejected because the modal exists specifically to show full bin contents — hiding rows hides legitimate state.

### D2 — Custom React file replaces (or extends) EditPickModal; one upstream-line edit

The cleanest minimum-touch approach is:

- Create `src/js/custom/outboundExpiryRestrictions/components/ExpiryAwareEditPickModal.jsx` that re-exports the upstream `EditPickModal` with a small wrapper: in the `quantityPicked` field's `getDynamicAttr`, also disable when the row is expired; in `getDynamicRowAttr`, add the `text-disabled` class when expired; pass an i18n tooltip.
- Edit `src/js/components/stock-movement-wizard/outbound/PickPage.jsx:25` (the import) and the JSX site that uses `EditPickModal` to use our wrapper instead. Two-line change.

The wrapper is feasible because `EditPickModal` is consumed via the field config object (`type: EditPickModal`) — replacing the type substitutes our wrapper.

- **Trade-off:** if upstream changes `EditPickModal`'s prop signature, our wrapper's pass-through breaks loudly. Acceptable; tests in tasks.md will catch the breakage.
- **Alternative considered:** patch `EditPickModal.jsx` directly. Rejected — that file is upstream and frequently updated.

### D3 — Server-side guard via custom Grails interceptor on `updatePicklist`

A custom Grails interceptor `OutboundExpiryGuardInterceptor` matches `controller: 'stockMovementItemApi', action: 'updatePicklist'` (verified at `StockMovementItemApiController.groovy:60` and the URL mapping at `UrlMappings.groovy:219-221`). In `before()`:

1. Parse `picklistItems` from the JSON body (same shape as `updatePicklist` reads at line 67).
2. Load the parent `StockMovementItem` via `stockMovementService.getStockMovementItem(params.id)` (the same lookup the controller does at line 64).
3. Walk back to the `OutboundStockMovement` via `stockMovementItem.requisition` and check `stockMovementType == StockMovementType.STOCK_MOVEMENT`. If `RETURN_ORDER` (or no parent), return true (let the request proceed).
4. For each `picklistItems[].inventoryItem.id`, load the `InventoryItem` and apply the canonical predicate (`expirationDate != null && expirationDate < today`). If any expired, render HTTP 400 with `{errorCode: 'outboundExpiryRestrictions.expired.cannotShip', errorMessages: [...]}` and return false.

- **Why an interceptor (not a service edit):** Grails interceptors live in `grails-app/controllers/.../<feature>/` — fully custom code, no upstream backend edit.
- **Why this specific endpoint:** verified at `StockMovementItemApiController.groovy:60-82` that this action is the one the wizard's pick step submits to. The line-items endpoint (`StockMovementApiController.updateItems` at line 300) deserializes `inventoryItem.id` (line 448) but the outbound wizard's Add Items step does not populate it — so the picklist endpoint is the actual choke point for lot decisions.
- **Trade-off:** parses the body twice (once in interceptor, once in controller). Pick payloads are small (line items per request typically <50). Acceptable.

### D4 — "Expired" rule lives in a single Groovy helper

`ExpiryRule` (a `final` Groovy class with static methods) under `src/main/groovy/org/pih/warehouse/custom/outboundExpiryRestrictions/support/` exposes:

```groovy
static boolean isExpired(InventoryItem item)            // null-safe; null expirationDate → false
static boolean isExpired(Date expirationDate)           // for callers that only have the date
```

The interceptor and the (optional) frontend response-annotator both call this. The predicate matches upstream `ProductAvailabilityService.groovy:555` (`expirationDate < today`).

- **Why:** one rule, one place. Future tweaks (grace period, end-of-day handling, etc.) change exactly one file.

### D5 — Frontend reads `expirationDate` already in the EditPickModal data

`EditPickModal.jsx` already has the `expirationDate` field on each `availableItem` row (rendered at line 75-83). No new API plumbing is needed for the UI — we just compute "is expired?" at render time using the row's `expirationDate` and disable the input.

- **Why:** zero backend change is required for the UI to do its job. The interceptor (D3) handles the server-side enforcement independently.
- **Trade-off:** each row's "isExpired" is computed in the browser. If the server clock and browser clock differ by hours/days, the UI may disable a row the server would accept (or vice versa). Mitigation: the server clock is authoritative — the interceptor decides. The UI is best-effort guidance.

### D6 — No backend file edit at all

D1+D2+D3+D4+D5 add up to:
- Backend: one new interceptor file, one new helper file, no edits to any upstream backend file. Zero column additions.
- Frontend: one new wrapper component, two-line edit on `outbound/PickPage.jsx`. No edits anywhere else.

This is materially less invasive than the original design.

## Risks / Trade-offs

| Risk | Mitigation |
|---|---|
| Frontend bundle and backend deploy out of sync — old bundle calls `updatePicklist` without the wrapper's check | Server-side interceptor (D3) catches the bypass; user gets HTTP 400. |
| `EditPickModal`'s prop signature changes upstream | Our wrapper passes props through; if upstream renames a field, our additional `getDynamicAttr` logic still runs. Tests cover the disabled state and the i18n message. |
| Server clock drift vs browser clock — UI thinks a lot expires today, server says yesterday | Server is authoritative; interceptor returns 400. UI disables proactively to minimise surprise. The `<` (strict) comparison gives a one-day grace window in the user's favour. |
| Upstream renames `controller: 'stockMovementItemApi'` | Interceptor's `match` block fails open (no rejection) — that is, the rule stops working silently. **Mitigation:** integration test in tasks.md asserts an expired pick is rejected; if it isn't, the test fails. |
| `stockMovementItem.requisition` traversal is null in some pick edge cases | Branch defensively: if traversal can't reach `stockMovementType`, log a warning and let the request through. Returns don't reach this endpoint anyway (verified above), so the worst case is failing open on a malformed payload — which the controller already handles. |
| Performance — interceptor adds an `InventoryItem.load(...)` per submitted picklist item | Pick payloads have at most ~10 lots in practice. One indexed PK load each. Negligible. |
| Existing customers may already be sending picks of expired stock and their workflow depends on it | Verified there is no existing config flag turning on/off expiry restriction. The change introduces strict behaviour. If any deployment objects, gate the interceptor on `grailsApplication.config.openboxes.custom.outboundExpiryRestrictions.enabled` (default true). Wire is one-line. |

## Migration Plan

1. Implement behind no flag — the rule is universally desired across customers (per the "strict" framing). If a customer wants it off later, expose `openboxes.custom.outboundExpiryRestrictions.enabled` (boolean, default true) at the top of the interceptor and the React wrapper.
2. Deploy backend + frontend together. If backend lands first, the rule still works (interceptor enforces; UI does not yet disable). If frontend lands first, the UI disables but server allows direct API bypass — risk window is short.
3. **Rollback:** revert the two PR commits. There are no database changes, no row mutations, and no upstream-backend-file rewrites — the rollback is symmetric.
4. **Data note:** existing in-flight outbound stock movements with already-allocated expired picks remain valid. The guard fires only on new `updatePicklist` calls. If retroactive cleanup is needed, that's a separate change.

## Upstream Touch Points

This section is the merge-conflict hitlist for future upstream pulls (per `.claude/rules/custom-package-isolation.md` § "When You MUST Touch an Upstream File").

| File | Reason | Diff size |
|---|---|---|
| `src/js/components/stock-movement-wizard/outbound/PickPage.jsx` | Replace the `EditPickModal` import + JSX usage with custom `ExpiryAwareEditPickModal` wrapper so the lot picker disables expired rows. | Two lines (one import, one component reference). No other lines change. |

No backend upstream files are edited. No upstream Liquibase changesets are edited. No upstream domain classes, services, or controllers are modified.

## Open Questions

1. **Does `EditPickModal` get reused outside the outbound wizard?** Quick `grep` to confirm before swapping the import. If reused for inbound or transfer flows, swap only the outbound `PickPage` reference and leave others unchanged. Resolved in task 1 of tasks.md.
2. **Should we also surface expiry status in the Add Items product picker** (e.g. badge a product whose only stock is expired)? Possible follow-up; not in v1 scope per the original "selecting expired products" framing being mainly about the lot decision.
3. **Bulk picklist import?** If outbound supports CSV import of picklist allocations, the interceptor needs to cover that endpoint too. Out of scope for v1.

## Validation

Each criterion below is verifiable from a concrete artifact (file:line, captured curl, grep output). "Tests pass" alone is not a valid criterion.

### Source-receipt criteria (verified during this design phase)

- [x] **R1 — Movement type discriminator.** `enum StockMovementType` defines exactly `STOCK_MOVEMENT` and `RETURN_ORDER` at `src/main/groovy/org/pih/warehouse/api/StockMovementType.groovy:5-6`. Verified.
- [x] **R2 — Field on `OutboundStockMovement`.** `StockMovementType stockMovementType` declared at `grails-app/domain/org/pih/warehouse/inventory/OutboundStockMovement.groovy:61`; not in transients (lines 81-95). Verified.
- [x] **R3 — `InventoryItem.expirationDate`.** `Date expirationDate` at `grails-app/domain/org/pih/warehouse/inventory/InventoryItem.groovy:47`; not transient. Verified.
- [x] **R4 — Add Items step does not pick lots.** `AddItemsPage.jsx:540-554` (`saveRequisitionItems` payload mapping) sends only `{product.id, quantityRequested, recipient, sortOrder}` — no `inventoryItem.id`. Verified — lot picking happens at the Pick step.
- [x] **R5 — Pick step is the lot-picking step.** `EditPickModal.jsx:212-220` (`onSave` payload) sends `picklistItems[].inventoryItem.id` to `STOCK_MOVEMENT_UPDATE_PICKLIST`. Mapped at `UrlMappings.groovy:219-221` to `stockMovementItemApi.updatePicklist`. Implementation at `StockMovementItemApiController.groovy:60-82`. Verified.
- [x] **R6 — Outbound returns are a separate component tree.** `src/js/components/returns/outbound/CreateOutboundReturn.jsx:179` sets `type: 'RETURN_ORDER'`. Returns do not flow through `stock-movement-wizard/outbound/`. Verified.
- [x] **R7 — Upstream "is expired" predicate.** `ProductAvailabilityService.groovy:555` uses `inventoryItem.expirationDate >= maxDate` (strict-less-than today, when removeExpiredStock is on). We adopt the same predicate. Verified.
- [x] **R8 — `EditPickModal` already has `expirationDate` on each row.** Rendered at `EditPickModal.jsx:75-83` from `availableItems[].expirationDate`. No new API call required for the UI to compute "is expired?". Verified.
- [x] **R9 — No existing config flag for outbound expiry restriction.** `application.yml:460` has `daysUntilExpiry: 60` for the **stock-alert email job** only; `application.yml:614` has a domain-level `expirationDate.minValue` constraint. Neither restricts outbound shipment selection. Verified.

### Code-after-implementation criteria

- [ ] **C1 — Custom-isolation: every new file path matches the expected pattern.** `git diff --name-only develop..HEAD | grep -v 'PickPage.jsx$'` lists only paths containing `org/pih/warehouse/custom/outboundExpiryRestrictions/` or `src/js/custom/outboundExpiryRestrictions/` or `grails-app/i18n/custom/`. The only allowed exception is the single `PickPage.jsx` upstream edit.
- [ ] **C2 — `PickPage.jsx` diff is exactly two lines of net change.** `git diff develop..HEAD -- src/js/components/stock-movement-wizard/outbound/PickPage.jsx | grep -E '^[-+][^-+]' | wc -l` outputs `2`.
- [ ] **C3 — Server-side rejection produces HTTP 400.** With seeded data containing an expired lot, `curl -X POST http://localhost:8080/openboxes/api/stockMovementItems/<sm-item-id>/updatePicklist -H 'Content-Type: application/json' -d '{"picklistItems": [{"inventoryItem": {"id": "<expired-lot-id>"}, "binLocation": {"id": "<bin>"}, "quantityPicked": 1}], "reasonCode": ""}'` returns HTTP `400` with body containing `"errorCode": "outboundExpiryRestrictions.expired.cannotShip"`.
- [ ] **C4 — Same payload accepted for RETURN_ORDER pseudo-path.** Repeat C3 against a stockMovementItem whose parent has `stockMovementType=RETURN_ORDER` — confirms the interceptor's branch. (Per R6, returns don't actually reach this endpoint via the UI; this test confirms defence-in-depth doesn't false-positive.)
- [ ] **C5 — Browser DevTools on the Pick step shows the expired row visibly disabled.** Open EditPickModal on a stockMovementItem whose product has both an expired and a fresh lot. Expired row has `text-disabled` class and the `quantityPicked` input has `disabled` attribute and a tooltip showing the expiration date.
- [ ] **C6 — Disabled row cannot accept input.** Click into the expired row's `quantityPicked` input — focus is rejected; type a number — value does not change. Verified manually + via Jest test.
- [ ] **C7 — i18n key defined.** `grep 'outboundExpiryRestrictions.expired.cannotShip' grails-app/i18n/custom/` returns a non-empty result.

### Conformance criteria (verifiable against rules in this repo)

- [ ] **N1 — Backend custom path uses camelCase per `.claude/rules/custom-package-isolation.md`.** All new `.groovy` files live under `src/main/groovy/org/pih/warehouse/custom/outboundExpiryRestrictions/` or `grails-app/.../org/pih/warehouse/custom/outboundExpiryRestrictions/`.
- [ ] **N2 — Frontend custom path.** All new `.jsx` files live under `src/js/custom/outboundExpiryRestrictions/`.
- [ ] **N3 — No `addColumn` against any upstream table.** `git diff develop..HEAD -- grails-app/migrations/ | grep -i addColumn` returns nothing. (No migrations expected at all.)
- [ ] **N4 — No `metaClass` mutation against upstream domain classes.** `git diff develop..HEAD | grep -E 'metaClass\\.(InventoryItem|OutboundStockMovement|StockMovement|Product)'` returns nothing.

## Unverified Assumptions

All previous unverified assumptions have been resolved by reading current source. Remaining assumptions are framework/runtime semantics with low risk:

1. **A Grails 3.3.16 interceptor's `before()` returning `false` after writing `response.status = 400` and `render(... as JSON)` halts the request without invoking the matched controller action.** Pattern matches existing Grails interceptor conventions but not verified against a Grails 3.3 source citation. Mitigation: integration test (task 4.2) verifies the controller is not invoked when the interceptor rejects. **Low stakes** — if the contract differs, the test fails immediately.
2. **`stockMovementItem.requisition.stockMovement.stockMovementType` is the correct traversal path from a picklist's parent line item to the stockMovementType enum.** Inferred from the controller's `getStockMovementItem` lookup at line 64 plus the `OutboundStockMovement.requisition` field. Mitigation: integration tests on both STOCK_MOVEMENT and RETURN_ORDER (tasks 4.2, C3+C4). **Low stakes** — fails loudly in test.

## Confidence: 8/10

Up from 6/10 after verification. Every load-bearing claim from the previous design is now backed by a `file:line` receipt cited in this document and recorded in the `R1`–`R9` validation rows.

**Why not 9–10:**
- The two remaining unverified assumptions (interceptor halt semantics, stockMovementItem→stockMovementType traversal) depend on Grails 3.3.16 runtime behaviour that we will validate during implementation via integration tests. They are not factual claims about external systems — they're about how our own custom code interacts with the Grails framework, and the tests are the receipts.
- The change scope is now genuinely small (2 lines upstream, 4–5 new custom files), so the surface area for surprises is bounded.
- The original design (pre-verification) chose the **wrong target step** — Add Items instead of Pick — which would have shipped a feature that did not block lot selection. Verification found and corrected that. This is exactly the failure mode the cap was designed to prevent.
