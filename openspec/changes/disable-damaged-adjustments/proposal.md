## Why

Damaged stock currently leaves the warehouse via direct stock-adjustment flows that write straight to `Transaction` / `TransactionEntry`. `Transaction` has no `documents` relationship, so the operator records a quantity drop with a free-text reason and no proof. This is unauditable: there is no photo of the damage, no signed report, no invoice from the disposal vendor.

The fork already ships a `stock-transfer-documents` capability ([archived 2026-04-29](../archive/2026-04-29-stock-transfer-document-upload/)) that supports document-mandatory stock transfers via the `REQUIRE_TRANSFER_OUT_DOCUMENT` / `REQUIRE_TRANSFER_IN_DOCUMENT` activity codes. The intended workflow for damaged goods is: transfer them into a "Damaged" bin (with proof attached to the transfer), then write them off downstream — never as a direct user-initiated `DAMAGED` adjustment.

To enforce that workflow at Tajikistan (and let other client deployments opt in), we need a configurable gate that closes the three UI paths through which a user can record `DAMAGED` as an adjustment reason today.

## What Changes

- Add config flag `openboxes.custom.adjustments.damaged.enabled` (default `false` → damaged adjustments blocked) following the existing `openboxes.<feature>.enabled` convention (e.g. `openboxes.forecasting.enabled`, `openboxes.bom.enabled`).
- Add a custom service that wraps `ReasonCode.listInventoryAdjustmentReasonCodes()` and filters `DAMAGED` out when the flag is false. Route four chokepoints through it: the upstream taglib `<g:selectInventoryAdjustmentReasonCode>` (covers Create Adjustment per-line), the Adjust Stock GSP (currently bypasses the taglib via `<g:select from="...">` — convert it to use the taglib), the `ReasonCodeApiController.list()` `ADJUST_INVENTORY` branch (defense-in-depth for future React consumers).
- Guard the **Create Damaged Transaction** header path (`InventoryController.createDamaged`) so it returns a forbidden response when the flag is false, and hide its menu link in `grails-app/views/product/_actions.gsp`.
- Flag default is `false` so existing client deployments see no behavior change unless they opt in via their own `docker/openboxes.yml`. Tajikistan (`release/est/tjk/0.9.7`) flips it to `true`.

This is **non-breaking** in the upstream sense (default off matches current behavior); it is intentionally **restrictive** at sites that opt in.

## Capabilities

### New Capabilities

- `damaged-adjustment-restrictions`: A configurable gate that suppresses the `DAMAGED` reason code from inventory-adjustment dropdowns and blocks the "Create Damaged Transaction" path. When enabled, damaged stock must instead flow through the existing `stock-transfer-documents` workflow (transfer into a Damaged bin with proof attached, then write off).

### Modified Capabilities

_None._ The `stock-transfer-documents` capability is unchanged — this proposal restricts a sibling path, it does not modify that one.

## Impact

**Custom code (new)**
- `grails-app/services/org/pih/warehouse/custom/adjustments/CustomReasonCodeService.groovy` — filters `ReasonCode.listInventoryAdjustmentReasonCodes()` by the flag.
- `grails-app/controllers/org/pih/warehouse/custom/adjustments/DamagedAdjustmentInterceptor.groovy` (Grails 3 URL interceptor) — guards `InventoryController.createDamaged` and any sibling actions that hardcode the DAMAGED transaction type.

**Upstream files touched (5 surgical edits, listed in `design.md` → Upstream touch points)**
- `grails-app/conf/application.yml` — add nested `openboxes.custom.adjustments.damaged.enabled: false` default. Lowest-impact wire-up of the default.
- `grails-app/taglib/org/pih/warehouse/SelectTagLib.groovy` — one line swap in the `selectInventoryAdjustmentReasonCode` taglib body, from `ReasonCode.listInventoryAdjustmentReasonCodes()` to `customReasonCodeService.listInventoryAdjustmentReasonCodes()`.
- `grails-app/views/inventoryItem/_adjustStock.gsp` — replace `<g:select from="${... .listInventoryAdjustmentReasonCodes()}">` with `<g:selectInventoryAdjustmentReasonCode>` so it routes through the taglib.
- `grails-app/controllers/org/pih/warehouse/api/ReasonCodeApiController.groovy` — in the `ADJUST_INVENTORY` branch, call `customReasonCodeService.listInventoryAdjustmentReasonCodes()` instead of the static enum method. Other branches unchanged.
- `grails-app/views/product/_actions.gsp` — wrap the "Create Damaged" link with `<g:if test="${grailsApplication.config.openboxes.custom.adjustments.damaged.enabled}">`.

**Per-instance config**
- `docker/openboxes.yml` (Tajikistan): set `openboxes.custom.adjustments.damaged.enabled: true`.
- `docker/openboxes.client-template.yml`: documented as opt-in for other clients.

**Behavior**
- Default (`enabled: false`): identical to current upstream — DAMAGED appears in all three places.
- Enabled (`enabled: true`): DAMAGED removed from adjustment dropdowns; `createDamaged` returns 403; "Create Damaged" menu link hidden. The `TransactionType` row for "Damaged" stays in the database — programmatic transactions created by downstream write-off flows still work.

**Out of scope**
- Programmatic creation of damaged transactions via services (e.g., from a future "write-off from Damaged bin" flow) is intentionally not blocked. Only user-initiated UI paths are gated.
- No DB migration. The change is purely config + filter logic.
