## Why

Even with the new "block expired at pick" guard from `block-expired-items-on-outbound`, users can still commit to a quantity they cannot fulfill: the Edit step's "Available" column shows `quantityAvailable` from the response of `GET /api/stockMovements/{id}/stockMovementItems?stepNumber=3`, which sums every `availableItem.quantityAvailable` regardless of expiry (computed at `StockMovementService.groovy:1050`). A user seeing "83 available" against a request of 60 advances through Edit unwarned, then hits a hard wall at the Pick step where only 33 lots are pickable. The existing `lowerQty` validator at `outbound/EditPage.jsx:415-416` (and the identical one at `request/EditPage.jsx:1098`) was designed for exactly this scenario — *"Revise quantity! Quantity available is lower than requested"* — but compares against the inflated total.

Fixing the data, not the validator, restores the existing UX contract. Once `quantityPickable` is in the response, the validator and the in-cell colour cue both fire when a request can't be fulfilled with non-expired stock.

## What Changes

- **Backend:** `StockMovementService.buildEditPageItems()` (line 1013-1095) gains a `quantityPickable` entry on each row's map literal. Computed as the sum of `availableItems[].quantityAvailable` whose lot is not expired per `ExpiryRule.isExpired` (the helper introduced in the prior change). The dead-code `EditPageItem` class in `StockMovementItem.groovy:589` is left untouched — `buildEditPageItems` returns a Groovy `Map`, not an `EditPageItem` instance, so the response shape is controlled here.
- **Frontend:** the `lowerQty` validator and the red/bold styling in `outbound/EditPage.jsx` and `request/EditPage.jsx` switch from comparing `quantityRequested > quantityAvailable` to `quantityRequested > quantityPickable`. The "Available" column shows the breakdown inline (e.g. `83 (50 expired)`).
- **`quantityAvailable` keeps its existing semantics.** No other endpoint, page, or report is altered.
- **No flow other than the outbound and request Edit steps is touched.** Outbound returns, inbound receiving, putaway, stock transfers, replenishment — all unchanged.

## Capabilities

### New Capabilities

- `expiry-aware-edit-availability`: Edit-step pre-flight checks (the validator and its visual cue) operate on non-expired stock totals, so a user who couldn't actually fulfill a request gets the existing "Revise quantity!" warning at the right step.

### Modified Capabilities

- (none — no existing capability covers this surface; the related `outbound-expiry-restrictions` capability remains untouched.)

## Impact

- **Backend upstream touch (surgical, ~4 lines):** `grails-app/services/org/pih/warehouse/inventory/StockMovementService.groovy` — add an `import` for `ExpiryRule`, one `quantityPickable` computation alongside the existing `quantityAvailable`/`quantityOnHand` lines (~1050), and one entry in the row map literal (~1054). Reuses `ExpiryRule.isExpired` from the prior change.
- **Frontend upstream touches (surgical, ~10 lines total):** `src/js/components/stock-movement-wizard/outbound/EditPage.jsx` and `src/js/components/stock-movement-wizard/request/EditPage.jsx`. Each gets one validator field swap, one `getDynamicAttr` field swap on the Available column, and one inline display tweak.
- **Cross-flow impact:**
  - **Outbound stock movements (Edit step):** target — validator fires correctly under the new picking rules.
  - **Stock requests (request-wizard Edit step):** uses the same backend endpoint and an identical validator. Same fix applied so behaviour stays consistent across both wizards.
  - **Outbound returns:** unaffected. Use `/api/stockTransfers/...` (different controller, doesn't read `EditPageItem`).
  - **Inbound receiving (supplier → warehouse):** unaffected. Doesn't use the request-wizard endpoint.
- **i18n:** new key for the inline "(N expired)" hint (and matching `crowdin.yml` glob is already in place from the prior change).
- **Migration:** none. No schema changes. Reuses the existing `inventory_item.expiration_date`.
- **No upstream domain class is extended via metaclass or subclassing.** The single new method on `EditPageItem` is a surgical addition to the upstream API model; it has no persistence or side effects.
