## ADDED Requirements

### Requirement: System SHALL expose a configurable flag to disable direct damaged-stock adjustments

The system SHALL read a single config key `openboxes.custom.adjustments.damaged.enabled` from the standard Grails config chain. Per-instance YAML lives in `docker/openboxes.yml` (with a commented-out template in `docker/openboxes.client-template.yml`); when the key is absent, the in-code `?: false` fallback resolves the default to `false`. When the effective value is `false`, all user-initiated UI paths for creating a stock adjustment with reason code `DAMAGED` or transaction type `Damaged` SHALL be unavailable.

The flag SHALL be readable from services, controllers, interceptors, and GSP views via `grailsApplication.config.openboxes.custom.adjustments.damaged.enabled`.

#### Scenario: Flag defaults to false when not explicitly set

- **WHEN** a Grails instance starts with no `openboxes.custom.adjustments.damaged.enabled` value set in `docker/openboxes.yml`
- **THEN** `grailsApplication.config.openboxes.custom.adjustments.damaged.enabled` SHALL resolve to `false` (either by the YAML default in `application.yml` or by the `?: false` fallback in code)
- **AND** all enforcement points (reason-code filter, interceptor, GSP guard) SHALL behave as if the flag is `false`

#### Scenario: Per-instance override flips the flag to true

- **WHEN** `docker/openboxes.yml` contains `openboxes.custom.adjustments.damaged.enabled: true` and the Grails instance is restarted
- **THEN** `grailsApplication.config.openboxes.custom.adjustments.damaged.enabled` SHALL resolve to `true`
- **AND** all three enforcement points SHALL revert to upstream behavior (DAMAGED visible in dropdowns, `createDamaged` returns the normal page, menu link is rendered)

---

### Requirement: Inventory-adjustment reason-code dropdowns SHALL exclude DAMAGED when the flag is false

A custom service `CustomReasonCodeService` SHALL wrap `ReasonCode.listInventoryAdjustmentReasonCodes()` and filter `ReasonCode.DAMAGED` out of the returned list when the flag is `false`. The upstream taglib `selectInventoryAdjustmentReasonCode` SHALL be modified to source its options from the custom service instead of calling `ReasonCode.listInventoryAdjustmentReasonCodes()` directly.

The **Adjust Stock** modal GSP (`grails-app/views/inventoryItem/_adjustStock.gsp`) currently bypasses the taglib by calling the static enum method directly via `<g:select from="...">`. It SHALL be converted to use `<g:selectInventoryAdjustmentReasonCode>` so it also flows through the custom service.

After these edits, both the Adjust Stock modal dropdown (rendered by `_adjustStock.gsp` via `InventoryItemController.adjustStock`) and the Create Adjustment per-line dropdown (rendered by `_inventoryAdjustment.gsp` via `InventoryController.createAdjustment`) SHALL share a single chokepoint at the custom service.

#### Scenario: Adjust Stock modal omits DAMAGED when flag is false

- **WHEN** a user opens the Adjust Stock modal from the stock card row
- **AND** the flag is `false`
- **THEN** the reason-code dropdown SHALL NOT contain `DAMAGED`
- **AND** the dropdown SHALL contain the other 12 entries from `ReasonCode.listInventoryAdjustmentReasonCodes()` (`CONSUMED`, `CORRECTION`, `DATA_ENTRY_ERROR`, `EXPIRED`, `FOUND`, `MISSING`, `RECOUNTED`, `REJECTED`, `RETURNED`, `SCRAPPED`, `STOLEN`, `OTHER`) in the same order

#### Scenario: Create Adjustment per-line dropdown omits DAMAGED when flag is false

- **WHEN** a user opens the Create Adjustment form for any product
- **AND** the flag is `false`
- **THEN** each line's reason-code dropdown SHALL NOT contain `DAMAGED`
- **AND** if a user submits the form with the line-level DAMAGED reason injected via DOM manipulation, the save action SHALL still persist the line (server-side enforcement of this particular path is **out of scope** — only the dropdown is gated; the policy assumes good-faith use)

#### Scenario: Dropdowns are unchanged when flag is true

- **WHEN** the flag is `true`
- **THEN** both dropdowns SHALL be identical to the upstream behavior (DAMAGED present, position preserved)

---

### Requirement: The "Create Damaged Transaction" controller path SHALL return forbidden when the flag is false

A new URL interceptor `DamagedAdjustmentInterceptor` (under `org.pih.warehouse.custom.damagedAdjustments`) SHALL match `controller: 'inventory', action: 'createDamaged'` and SHALL redirect to `errors/handleForbidden` when the flag is `false`. No modification SHALL be made to `InventoryController.createDamaged` itself.

#### Scenario: Direct URL to createDamaged is blocked when flag is false

- **WHEN** an authenticated user requests `/openboxes/inventory/createDamaged?product.id=<any>`
- **AND** the flag is `false`
- **THEN** the response SHALL redirect to `/openboxes/errors/handleForbidden`
- **AND** the server log SHALL contain an INFO-level entry naming the blocked action and the flag value

#### Scenario: Direct URL to createDamaged works when flag is true

- **WHEN** the same user requests the same URL
- **AND** the flag is `true`
- **THEN** the response SHALL render the same content as upstream (forwarded to `createTransaction.gsp`)

---

### Requirement: The "Create Damaged" menu entry SHALL be hidden when the flag is false

The `<div class="action-menu-item">` in `grails-app/views/product/_actions.gsp` that contains the `createDamaged` link SHALL be wrapped in `<g:if test="${grailsApplication.config.openboxes.custom.adjustments.damaged.enabled}">…</g:if>`. When the flag is `false`, the rendered HTML SHALL NOT contain this menu entry at all (not just hide it via CSS).

#### Scenario: Product menu omits the Damaged item when flag is false

- **WHEN** a user opens the action menu on any product page
- **AND** the flag is `false`
- **THEN** the rendered HTML SHALL NOT contain a link to `controller="inventory" action="createDamaged"`
- **AND** the menu SHALL render correctly without leaving an empty `<div>` placeholder

#### Scenario: Product menu shows the Damaged item when flag is true

- **WHEN** the flag is `true`
- **THEN** the rendered HTML SHALL contain the upstream link `<g:link controller="inventory" action="createDamaged" params="['product.id':productInstance?.id]">` with its i18n label `inventory.inventoryDamaged.label`

---

### Requirement: The reason-codes JSON API SHALL filter DAMAGED from the ADJUST_INVENTORY branch when the flag is false

The endpoint `GET /api/reasonCodes?activityCode=ADJUST_INVENTORY` (`ReasonCodeApiController.list()`) SHALL return a JSON body whose `data` array does not include the `DAMAGED` reason code when the flag is `false`. Other `activityCode` branches (`SUBSTITUTE_REQUISITION_ITEM`, `MODIFY_REQUISITION_ITEM`, `CYCLE_COUNT`, default) SHALL be unaffected — they use different reason-code lists.

This is defense-in-depth: no current React/JS consumer of this branch exists in the codebase. The filter is here to prevent a future regression where a new frontend caller exposes DAMAGED to the user via this API.

#### Scenario: API returns no DAMAGED in ADJUST_INVENTORY branch when flag is false

- **WHEN** a client GETs `/openboxes/api/reasonCodes?activityCode=ADJUST_INVENTORY`
- **AND** the flag is `false`
- **THEN** the response SHALL be JSON
- **AND** `data[].id` SHALL NOT contain the string `DAMAGED`
- **AND** `data[].id` SHALL contain the other 12 entries (CONSUMED, CORRECTION, DATA_ENTRY_ERROR, EXPIRED, FOUND, MISSING, RECOUNTED, REJECTED, RETURNED, SCRAPPED, STOLEN, OTHER)

#### Scenario: API returns DAMAGED in ADJUST_INVENTORY branch when flag is true

- **WHEN** the same request is made
- **AND** the flag is `true`
- **THEN** `data[].id` SHALL include `DAMAGED` at its position from `ReasonCode.listInventoryAdjustmentReasonCodes()` (3rd entry)

#### Scenario: Other activityCode branches are unaffected when flag is false

- **WHEN** a client GETs `/openboxes/api/reasonCodes?activityCode=CYCLE_COUNT`
- **AND** the flag is `false`
- **THEN** the response SHALL contain whatever `ReasonCode.listCycleCountReasonCodes()` returns, unchanged from upstream

---

### Requirement: Programmatic creation of damaged-type transactions SHALL remain unaffected by the flag

The flag SHALL gate **only** user-initiated UI paths. Service-layer code that creates a `Transaction` with `transactionType = TransactionType.get(Constants.DAMAGE_TRANSACTION_TYPE_ID)` (id=5) directly — for example, a future "write-off from Damaged bin" service triggered by the stock-transfer-documents workflow — SHALL succeed regardless of the flag's value. The `Damaged` row in the `transaction_type` table SHALL NOT be deleted, deactivated, or otherwise modified.

#### Scenario: Programmatic damaged transaction persists when flag is false

- **GIVEN** a service method that constructs a `Transaction` with `transactionType.id = '5'` (the `Damaged` TransactionType) and saves it via `transaction.save(flush: true)`
- **WHEN** the flag is `false`
- **THEN** the transaction SHALL persist successfully
- **AND** no interceptor SHALL block it (interceptors only run on HTTP requests)

#### Scenario: TransactionType row for Damaged remains queryable

- **WHEN** the flag is `false`
- **THEN** `TransactionType.get(Constants.DAMAGE_TRANSACTION_TYPE_ID)` SHALL return a non-null instance
- **AND** the instance's `name` SHALL equal `"Damaged|fr:Endommagé"` (or whatever the seeded value is — unchanged from upstream)
