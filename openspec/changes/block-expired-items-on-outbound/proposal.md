## Why

Warehouse staff can currently allocate expired inventory lots to an outbound stock movement during the **Pick** step of the outbound wizard. The lot picker (`EditPickModal`) shows the expiration date for context but applies no restriction — the user can type a `quantityPicked` against an expired lot and the server accepts it. Once shipped, the recipient receives unusable stock, creating downstream waste, audit findings (humanitarian/health programs require expiry traceability), and reverse-logistics overhead.

The fix targets the **Pick step only** — that is the wizard step where users select specific lots; the Add Items step picks products only and never touches inventory items. Outbound returns flow through a separate component tree (`src/js/components/returns/outbound/`) and are out of scope: returning expired stock to a supplier or hub is a legitimate flow that must remain unaffected.

## What Changes

- The lot picker in `EditPickModal` (the modal opened from the outbound wizard's Pick step) renders expired-lot rows as disabled — the row remains visible with its expiration date, but the `quantityPicked` input refuses input and a tooltip explains why.
- The backend rejects any `POST /openboxes/api/stockMovementItems/<id>/updatePicklist` whose `picklistItems[]` references an expired inventory item, when the parent movement is `STOCK_MOVEMENT`. Defence-in-depth against bypass via direct API calls or stale UI bundles.
- The Add Items step, the inbound wizard, the outbound returns wizard, the stock-transfer wizard, and every other surface remain unchanged.
- "Expired" is defined as `inventoryItem.expirationDate != null && expirationDate < today` in the server's timezone — matching the existing predicate at `ProductAvailabilityService.groovy:555`.

## Capabilities

### New Capabilities

- `outbound-expiry-restrictions`: rules and behaviour that prevent expired inventory from being allocated to outbound `STOCK_MOVEMENT` picks, while leaving `RETURN_ORDER` and inbound flows unaffected.

### Modified Capabilities

(none — net-new behaviour layered onto upstream flows; no upstream OpenSpec capability exists for this area.)

## Impact

- **Backend (custom):** new `org.pih.warehouse.custom.outboundExpiryRestrictions` package with two files — a `support/ExpiryRule.groovy` helper holding the canonical `isExpired` predicate, and an `OutboundExpiryGuardInterceptor.groovy` matching `controller: 'stockMovementItemApi', action: 'updatePicklist'`.
- **Frontend (custom):** new `src/js/custom/outboundExpiryRestrictions/components/ExpiryAwareEditPickModal.jsx` — a thin wrapper over upstream `EditPickModal` that adds disabled styling and a tooltip on expired rows.
- **Upstream touch points (surgical, unavoidable):**
  - `src/js/components/stock-movement-wizard/outbound/PickPage.jsx` — swap one import + one component reference from upstream `EditPickModal` to the custom wrapper. No other lines change.
- **i18n:** new keys for the disabled-row tooltip and the server-side error, added under `grails-app/i18n/custom/`.
- **Migration:** none. No schema changes — uses existing `inventory_item.expiration_date`.
- **No upstream backend file is edited.** The interceptor is the entire backend extension surface.
